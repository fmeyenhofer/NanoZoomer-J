import ij.IJ;

import loci.common.services.ServiceFactory;
import loci.formats.ome.OMEXMLMetadata;
import loci.formats.services.OMEXMLService;
import net.imagej.ImageJ;

import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.*;
import loci.formats.ome.OMEXMLMetadata;
import loci.formats.services.OMEXMLService;
import net.imagej.ImageJ;
import ome.xml.meta.OMEXMLMetadataRoot;
import ome.xml.model.Image;
import ome.xml.model.enums.DimensionOrder;
import ome.xml.model.enums.EnumerationException;
import ome.xml.model.enums.PixelType;
import ome.xml.model.primitives.PositiveInteger;
import org.apache.commons.io.FilenameUtils;
import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.Initializable;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.FileWidget;
import org.scijava.widget.NumberWidget;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/**
 * Convert a given series from a NDPI file to OME-TIF
 *
 * @author Felix Meyenhofer
 */
@Plugin(type = Command.class, menuPath = "Plugins > NanoZoomer NDPI > Single Channel 2 OME-TIF")
public class Ndpi2OmeTif extends DynamicCommand implements Command, Initializable {


    // Input Dialog
    @Parameter(label = "Input directory", style = FileWidget.DIRECTORY_STYLE)
    private File inputDir;

    @Parameter(label = "Output directory", style = FileWidget.DIRECTORY_STYLE)
    private File outputDir;

    // TODO: dialog callbacks (as soon as Curtis provides it)
    @Parameter(label = "Series to convert", style = NumberWidget.SPINNER_STYLE, min = "1", max = "5", stepSize = "1")
    private int series = 1;

    @Parameter(label = "Channel name", callback = "updateChannelIndex")
    private String channelName = "DAPI";

    @Parameter(label = "Channel Index", callback = "enforceChannelIndex",
            style = NumberWidget.SPINNER_STYLE, min = "-1", max = "2", stepSize = "1")
    private int channelIndex = 2;

    @Parameter(label="Use channel name as file filter", callback = "enforceMatchChannelName")
    private boolean matchChannelName = true;

    @Parameter(label = "Output file compression", choices = {"LZW", "None"})
    private String compression = "LZW";

    @Parameter(visibility = ItemVisibility.MESSAGE)
    private final String note = "<html>" +
            "<p>The input folder is searched for ndpi-files or ndpis-files if the RGB channel option is selected<br>" +
            "NDPI files have multiple series corresponding to different magnifications.<br>" +
            "Use the Bio-Formats Importer to check which series you want to convert</p>" +
            "</html>";


    // Services
    @Parameter
    private LogService logger;

    @Parameter
    private StatusService status;


    /**
     * {@inheritDoc}
     */
    @Override
    public void initialize() {
        final MutableModuleItem<String> item = getInfo().getMutableInput("Channel name", String.class);
        item.setChoices(HTplusFluo.Channel.getNames());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run() {
        IJ.run("Console", "uiservice=[org.scijava.ui.DefaultUIService [priority = 0.0]]");

        // Deduce file extension and filter string from the inputs
        String fileExtension = (channelName.equals("RGB") ? ".ndpis" : "ndpi");
        String fileNameFilter = (matchChannelName && !channelName.equals("RGB")) ? channelName : "";

        logger.info("File extension: " + fileExtension);
        logger.info("File name filter: " + fileNameFilter);

        // Get the file list
        List<File> fileList = getNdpiFileList(inputDir, fileExtension, fileNameFilter);
        logger.info("Input directory: " + inputDir);
        logger.info("Found " + fileList.size() + " files.");

        // Process file by file
        int nfiles = fileList.size();
        int nfile = 1;
        for (File file : fileList) {
            String fileName = FilenameUtils.removeExtension(file.getName());
            fileName += (channelName.equals("RGB")) ? "" : ("_" + channelName);
            fileName += ".ome.tif";
            File outputPath = new File(outputDir, fileName);

            logger.info("converting: " + file.getAbsolutePath());
            if (outputPath.exists()) {
                logger.info("        already processed");
                continue;
            } else {
                logger.info("        to: " + outputPath.getAbsolutePath());
            }

            try {
                convert(file.getAbsolutePath(), series - 1, channelIndex, outputPath.getAbsolutePath());
            } catch (IOException |
                    FormatException |
                    ServiceException |
                    DependencyException |
                    EnumerationException e) {
                logger.error(e);
            }

            status.showProgress(nfile++, nfiles);
        }

        status.showStatus("Converted " + nfiles + " ndpi-files to ome-tiff");
        logger.info("done.");
    }

    /**
     * Convert a a given series of the input file
     *
     * @param inId input file path
     * @param outSeries series to write to the output file
     * @param outColInd color index to be written to the output (0->red, 1->green, 2->blue)
     * @param outId output file
     * @throws IOException Image file cannot be opened
     * @throws FormatException {@inheritDoc}
     * @throws DependencyException {@inheritDoc}
     * @throws ServiceException {@inheritDoc}
     * @throws EnumerationException {@inheritDoc}
     */
    @SuppressWarnings("JavaDoc")
    private void convert(String inId, int outSeries, int outColInd, String outId)
            throws IOException, FormatException, DependencyException, ServiceException, EnumerationException {

        // Record metadata to OME-XML format
        ServiceFactory factory = new ServiceFactory();
        OMEXMLService service = factory.getInstance(OMEXMLService.class);
        OMEXMLMetadata inMeta = service.createOMEXMLMetadata();

        // Initialize a file reader wrapped in a channel separator
        ChannelSeparator channelSeparator = new ChannelSeparator();
        channelSeparator.setMetadataStore(inMeta);
        channelSeparator.setId(inId);
        channelSeparator.setSeries(outSeries);

        // Clone the metadata and remove all the series in the metadata except the one we process
        OMEXMLMetadata outMeta = service.createOMEXMLMetadata(inMeta.dumpXML());
        OMEXMLMetadataRoot root = (OMEXMLMetadataRoot) outMeta.getRoot();
        List<Image> inSeries = root.copyImageList();
        for (int i = 0; i < inSeries.size(); i++) {
            if (i != outSeries) {
                root.removeImage(inSeries.get(i));
            }
        }
        outMeta.setRoot(root);

        // Deduce the output image dimensions
        int numCol = 3;//(channelSeparator.isRGB()) ? 3 : 1;
        int inPlanes = channelSeparator.getImageCount();
        int planeStartIndex = (outColInd == -1) ? 0 : outColInd;
        int planeIncrement = (outColInd == -1) ? 1 : 3;
        int pixelSizeC = (outColInd == -1) ? 3 : 1;
        int pixelSizeZ = (inPlanes >= 3) ? inPlanes / numCol : 1;

        // Adjust the metadata attributes affected during this process.
        outMeta.setImageName(null, 0);
        outMeta.setPixelsSizeC(new PositiveInteger(pixelSizeC), 0);
        outMeta.setPixelsSizeZ(new PositiveInteger(pixelSizeZ), 0);
        outMeta.setPixelsSizeT(new PositiveInteger(1), 0);
        outMeta.setPixelsBinDataBigEndian(Boolean.FALSE, 0, 0);
        outMeta.setPixelsDimensionOrder(DimensionOrder.fromString("XYZCT"), 0);
        outMeta.setChannelSamplesPerPixel(new PositiveInteger(1), 0, 0);
        outMeta.setPixelsType(PixelType.fromString(FormatTools.getPixelTypeString(FormatTools.UINT8)), 0);

        // Setup the writer
        ImageWriter writer = new ImageWriter();
        writer.setMetadataRetrieve(outMeta);
        if (!compression.equals("None"))
            writer.setCompression(compression);
        writer.setId(outId);

        // TODO: save tile by tile
        // Copy the planes
        int outPlaneInd = 0;
        byte[] img = new byte[channelSeparator.getSizeX() * channelSeparator.getSizeY()];
        for (int inPlaneInd = planeStartIndex; inPlaneInd < inPlanes; inPlaneInd += planeIncrement) {
            logger.info("        writing plane: " + outPlaneInd);
            channelSeparator.openBytes(inPlaneInd, img);
            writer.saveBytes(outPlaneInd++, img);
        }

        // Cleanup
        channelSeparator.close();
        writer.close();
    }

    /**
     * Get the list of ndpi files for a given input directory
     *
     * @param inputDir directory containing the input files
     * @param filter string that has to be contained in the file name
     * @return list of files in inputDir containing the filter string
     */
    private List<File> getNdpiFileList(File inputDir, String extension, String filter) {
        List<File> list = new ArrayList<>();
        filter = filter.toLowerCase();

        File[] content = inputDir.listFiles();
        if (content != null) {
            for (File file : content) {
                if (file.getName().toLowerCase().contains(extension)) {
                    if (!matchChannelName || file.getName().toLowerCase().contains(filter)) {
                        list.add(file);
                    }
                }
            }
        }

        return list;
    }

    /**
     * Callback for the channel name combobox
     */
    @SuppressWarnings("unused")
    protected void updateChannelIndex() {
        channelIndex = HTplusFluo.Channel.get(channelName).getColorIndex();
    }

    /**
     * Callback for the channel index spinner
     */
    @SuppressWarnings("unused")
    protected void enforceChannelIndex() {
        if (channelName.equals("RGB") && (channelIndex != -1)) {
            channelIndex = -1;
            logger.warn("If the channel selection is RGB, all channels are converted " +
                    "(-1) and the channel index is overridden.");
        }
    }

    /**
     * Callback for the channel matcher checkbox
     */
    @SuppressWarnings("unused")
    protected void enforceMatchChannelName() {
        if (channelName.equals("RGB") && matchChannelName) {
            matchChannelName = false;
            logger.warn("If the channel selection is RGB the ndpis-files are used, " +
                    "which do not contain a channel sequence in the file name");
        }
    }

    /**
     * Run
     *
     * @param args input arguments
     */
    public static void main(final String... args) {
        final ImageJ ij = new net.imagej.ImageJ();
        ij.command().run(Ndpi2OmeTif.class, true);
    }
}
