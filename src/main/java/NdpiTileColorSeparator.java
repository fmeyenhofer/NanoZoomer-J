import ij.ImagePlus;
import ij.process.ByteProcessor;
import io.scif.SCIFIO;
import loci.common.services.*;
import loci.common.services.DependencyException;
import loci.formats.*;
import loci.formats.FormatException;
import loci.formats.meta.IMetadata;
import loci.formats.services.OMEXMLService;
import net.imagej.ImageJ;
import org.apache.commons.io.FilenameUtils;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.FileWidget;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plugin to separate the channels from the NanoZoomer tif tile export generated with the NDP.Toolbox.
 * Tile naming: XXXX_YYYY.tif (single layer z)
 *              ZZZZ_XXXX_YYYY (multilayer z)
 * The tiles index starts from the upper left corner.
 *
 * @author Felix Meyenhofer
 *         creation: 21.10.15
 */
@Plugin(type = Command.class, menuPath = "Plugins > NanoZoomer > Tif-Tiles Color Separation")
public class NdpiTileColorSeparator implements Command {

    /** Debug switch */
    private static final boolean DEBUG = false;

    /** Reference to the file list in the input directory */
    private List<File> tileList;


    // Hardcoded paramter
    private static final String TILE_EXTENSION = ".tif";
    private static final int RED = 1;
    private static final int GREEN = 2;
    private static final int BLUE = 3;


    // Dialog
    @Parameter(visibility = ItemVisibility.MESSAGE)
    private final String note = "<html>Process the tiles produced by Hamamatsu's NDP.Toolbox<br>" +
            "The plugin will consolidate all the tiles in one directory<br>" +
            "and write each channel into a separate file.<br>" +
            "The regular expression extracts three groups: date, slice, roi.<br>" +
            "Input an empty string to use the original name.";

    @Parameter(style = FileWidget.DIRECTORY_STYLE, label = "Input directory:")
    private File inputDir = new File("/");

    @Parameter(style = FileWidget.DIRECTORY_STYLE, label = "Output directory:")
    private File outputDir;

    @Parameter(label = "Red")
    private boolean processRed = true;

    @Parameter(label = "Green")
    private boolean processGreen = true;

    @Parameter(label = "Blue")
    private boolean processBlue = true;

    @Parameter(label="File name regexp")
    private String regex = ".*(\\d{6})_.*_(\\d{1,2}).*_ROI(\\d{1,3}).*";


    // Services
    @Parameter
    private LogService logger;

    @Parameter
    SCIFIO scifio;


    /**
     * {@inheritDoc}
     */
    public void run() {
        ArrayList<File> directories = getSubDirectories(inputDir);

        if (directories == null) {
            logger.error("Could not find sub-directories. This plugins expects one directory for each ndpi-file (and ROI) containing the corresponding tif-tiles.");
            return;
        }

        for (File directory : directories) {
            logger.info("Processing tiles in :" + directory.getAbsolutePath());

            tileList = getTifTiles(directory, "");
            if (tileList == null || tileList.isEmpty()) {
                logger.info("     no tiles found.");
            } else {
                try {
                    while (!tileList.isEmpty()) {
                        List<File> stackList = nextStackFileSet();
                        if (processRed)
                            rgbTiffs2GcStack(stackList, RED);

                        if (processGreen)
                            rgbTiffs2GcStack(stackList, GREEN);

                        if (processBlue)
                            rgbTiffs2GcStack(stackList, BLUE);
                    }
                } catch (IOException | ServiceException | DependencyException | FormatException e) {
                    logger.error(e);
                }
            }
        }

        logger.info("done.");
    }

    /**
     * If a regular expression is defined the file name is re-arranged
     * according to the matched groups
     * @param file file name
     * @return re-arranged file name
     */
    private File getOutputFile(File file, int channel) {
        String fileName;

        // If no info is extracted from the input path, just take the input name
        if (regex.isEmpty()) {
            fileName = file.getParentFile().getName() + "_" + file.getName();
        } else {

            // Extract information from the
            Pattern pattern = Pattern.compile(regex);
            Matcher match = pattern.matcher(file.getParentFile().getName());
            if (!(match.find() && match.groupCount() == 3))
                throw new RuntimeException("The pattern '" + regex + "' could not extract date, slice and roi from the filename");

            // Create the output file
            DecimalFormat formatter = new DecimalFormat("00");

            // Extract tile coordinates
            String[] parts = FilenameUtils.removeExtension(file.getName()).split("_");
            String x;
            String y;
            if (parts.length == 2) {
                x = parts[0];
                y = parts[1];
            } else if (parts.length == 3) {
                x = parts[1];
                y = parts[2];
            } else {
                throw new RuntimeException("Could not extract the x-y-z indices from the file name: " + file.getName());
            }

            fileName = match.group(1) +
                    "_channel-" + channel +
                    "_slice-" + formatter.format(Double.parseDouble(match.group(2))) +
                    "_roi-" + match.group(3) +
                    "_tile-" + x + "-" + y +
                    ".tif";
        }

        return new File(outputDir, fileName);
    }

    /**
     * Get all the subdirectories
     * @param parentDirectory to look for sub-directories
     * @return list of sub-directories
     */
    private ArrayList<File> getSubDirectories(File parentDirectory) {
        File[] files = parentDirectory.listFiles();
        if (files == null || files.length == 0)
            return null;

        ArrayList<File> subDirectories = new ArrayList<File>();
        for (File file : files)
            if (file.isDirectory())
                subDirectories.add(file);

        return subDirectories;
    }

    /**
     * Get all the tile files in a directory
     * @param inputDir directory containing the files
     * @param filter string that the files names have to contain
     * @return list of tile files
     */
    private ArrayList<File> getTifTiles(File inputDir, String filter) {
        ArrayList<File> list = new ArrayList<File>();

        File[] content = inputDir.listFiles();
        if (content != null) {
            for (File file : content) {
                if (file.getName().contains(TILE_EXTENSION) && file.getName().contains(filter)) {
                    list.add(file);
                }
            }
        }

        return list;
    }

    /**
     * Get the set of files belonging to the same
     * @return get the next files belonging to one tile (several in case it's a stack)
     */
    private List<File> nextStackFileSet() {
        List<File> files = new ArrayList<File>();

        String[] refParts = FilenameUtils.removeExtension(tileList.get(0).getName()).split("_");
        if (refParts.length == 2) { // There is one single z-plane
            files.add(tileList.get(0));
            tileList.removeAll(files);
            return files;

        }  else if (refParts.length == 3) { // Multiple z-planes
            for (File file : tileList) {
                String[] curParts = FilenameUtils.removeExtension(file.getName()).split("_");
                if (curParts.length != 3) {
                    logger.info("Skipping file: " + file.getAbsolutePath());
                    continue;
                }

                // check if x and y index of the tile are the same
                if (curParts[2].equals(refParts[2]) && curParts[1].equals(refParts[1])) {
                    files.add(file);
                }
            }

            tileList.removeAll(files);
            return files;
        }

        return null;
    }



    /**
     * Takes one or several files and converts the RGB tif to a gray-scale tif/tif-stack
     *
     * @param inpFiles list of files (one for a single image, several for stacks)
     * @param color the channel/color to be extracted
     * @throws loci.common.services.DependencyException
     * @throws ServiceException
     * @throws IOException
     * @throws loci.formats.FormatException
     */
    private void rgbTiffs2GcStack(List<File> inpFiles, int color) throws
            DependencyException,
            ServiceException,
            IOException,
            FormatException {

        File outStack = getOutputFile(inpFiles.get(0), color);
        if (outStack.exists()) {
            logger.info("     already processed");
            return;
        }

        ChannelSeparator channelSeparator = new ChannelSeparator();
        channelSeparator.setId(inpFiles.get(0).getAbsolutePath());

        int numCol = (channelSeparator.isRGB()) ? 3 : 1;
        int colOff = color - 1;

        ServiceFactory factory = new ServiceFactory();
        OMEXMLService service = factory.getInstance(OMEXMLService.class);
        IMetadata outMeta = service.createOMEXMLMetadata();
        MetadataTools.populateMetadata(outMeta,
                0,
                null,
                false,
                "XYZCT",
                FormatTools.getPixelTypeString(FormatTools.UINT8),
                channelSeparator.getSizeX(),
                channelSeparator.getSizeY(),
                inpFiles.size(),
                1, 1, 1);

        ImageWriter writer = new ImageWriter();
        writer.setMetadataRetrieve(outMeta);
        writer.setId(outStack.getAbsolutePath());

        int planeInd = 0;

        for (File inpFile : inpFiles) {
            channelSeparator.setId(inpFile.getAbsolutePath());
            channelSeparator.setSeries(0);

            for (int i = colOff; i < channelSeparator.getImageCount(); i += numCol) {
                byte[] img = channelSeparator.openBytes(i);

                if (DEBUG) {
                    ByteProcessor bytePro = new ByteProcessor(channelSeparator.getSizeX(),
                            channelSeparator.getSizeY(), img);
                    ImagePlus chunk = new ImagePlus("plane " + planeInd, bytePro);
                    chunk.show();
                }

                writer.saveBytes(planeInd++, img);
            }
        }

        channelSeparator.close();
        writer.close();
    }

//// Scifio version (could not figure out how to separate the colors)
//    /**
//     * Write a separate file for each color/channel.
//     * @param inpStack
//     * @param color
//     * @throws IOException
//     * @throws FormatException
//     * @throws ImgIOException
//     */
//
//    private void separateColors(List<File> inpStack, int color) throws IOException, FormatException, ImgIOException {
//
//        for (int p = 0; p < inpStack.size(); p++) {
//            File file = inpStack.get(p);
//
//
//            File outStack = getOutputFile(file, color);
//            if (outStack.exists()) {
//                logger.info("     already processed");
//                return;
//            }
//
//            Reader reader = scifio.initializer().initializeReader(file.getAbsolutePath());
//
//            if (DEBUG) {
//                ImagePlus imp = IJ.openImage(file.getAbsolutePath());
//                imp.show();
//            }
//
//            Writer writer = scifio.initializer().initializeWriter(file.getAbsolutePath(), outStack.getAbsolutePath());
//            for (int i = 0; i < reader.getImageCount(); i++) {
//                for (int j = 0; j < reader.getPlaneCount(i); j++) {
//                    Plane plane = reader.openPlane(i, j);
//
//                    writer.savePlane(0, p, plane);
//                }
//            }
//            reader.close();
//            writer.close();
//        }
//    }

    /**
     * Test
     * @param args input arguments
     * @throws Exception anything that can go wrong
     */
    public static void main(final String... args) throws Exception {
        final ImageJ ij = net.imagej.Main.launch(args);
        ij.command().run(NdpiTileColorSeparator.class, true);
    }

}
