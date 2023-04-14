import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.*;
import loci.formats.meta.IMetadata;
import loci.formats.ome.OMEXMLMetadata;
import loci.formats.services.OMEXMLService;
import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.units.unit.Unit;
import ome.xml.meta.OMEXMLMetadataRoot;
import ome.xml.model.Image;
import ome.xml.model.enums.DimensionOrder;
import ome.xml.model.enums.EnumerationException;
import ome.xml.model.enums.PixelType;
import ome.xml.model.primitives.PositiveInteger;

import java.io.File;
import java.io.IOException;
import java.util.*;


class NdpiUtils {

    static HashMap<HTplusFluo.Channel, List<File>> getFiles(File directory) {
        HashMap<HTplusFluo.Channel, List<File>> fileLists = new HashMap<>();

        File[] dirContent = directory.listFiles();
        if (dirContent == null) {
            throw new RuntimeException("Directory is emtpy" + directory.getAbsolutePath());
        }

        for (File file : dirContent) {
            if (file.getName().endsWith(HTplusFluo.FILE_EXTENSION)) {
                HTplusFluo.Channel channel = HTplusFluo.Channel.match(file);
                if (channel != null) {
                    List<File> files = fileLists.get(channel);
                    if (files == null) {
                        files = new ArrayList<>();
                    }

                    files.add(file);
                    fileLists.put(channel, files);
                }
            }
        }

        return fileLists;
    }

    static List<HTplusFluo.Channel> getChannelList(File directory) {
        HashMap<HTplusFluo.Channel, List<File>> files = getFiles(directory);
        List<HTplusFluo.Channel> channels = new ArrayList<>(files.keySet().size());
        channels.addAll(files.keySet());

        return channels;
    }

    static List<String> getSeriesPixelSizes(File file) throws IOException, FormatException {
        final ImageReader reader = new ImageReader();
        final IMetadata meta = MetadataTools.createOMEXMLMetadata();
        reader.setMetadataStore(meta);
        reader.setId(file.getAbsolutePath());

        // Initialize a file reader wrapped in a channel separator
        ChannelSeparator channelSeparator = new ChannelSeparator();
        channelSeparator.setMetadataStore(meta);
        channelSeparator.setId(file.getAbsolutePath());

        // Compute pixel sizes
        final Unit<Length> targetUnit = UNITS.MICROMETER;
        int imgInd = 0;
        final Length sx = meta.getPixelsPhysicalSizeX(imgInd);
        final Length sxc = new Length(sx.value(targetUnit), targetUnit);
        int seriesCount = channelSeparator.getSeriesCount();

        List<String> pixelSizes = new ArrayList<>(seriesCount - 1);

        for (int i = 0; i < (seriesCount - 1); i++) {
            double factor = Math.pow(4, i);
            Double scale = sxc.value().doubleValue() * factor;
            pixelSizes.add(String.format("%.3f", scale) + " " + targetUnit.getSymbol());
        }

        reader.close();

        return pixelSizes;
    }

    static void convert(HashMap<HTplusFluo.Channel, String> inIds, int inSeries, String outId, String compression)
            throws IOException, FormatException, DependencyException, ServiceException, EnumerationException {

        int sizeC = inIds.keySet().size();
        String firstId = inIds.values().iterator().next();
        HTplusFluo.Channel inType = inIds.keySet().iterator().next();

        // Record metadata to OME-XML format
        ServiceFactory factory = new ServiceFactory();
        OMEXMLService service = factory.getInstance(OMEXMLService.class);
        OMEXMLMetadata inMeta = service.createOMEXMLMetadata();

        // Initialize a file reader wrapped in a channel separator
        ChannelSeparator channelSeparator = new ChannelSeparator();
        channelSeparator.setMetadataStore(inMeta);
        channelSeparator.setId(firstId);
        channelSeparator.setSeries(inSeries);

        // Deduce the output image dimensions
        int numCol = 3;//(channelSeparator.isRGB()) ? 3 : 1;
        int inPlanes = channelSeparator.getImageCount();
        int planeIncrement = (channelSeparator.isRGB()) ? 1 : 3;
        int pixelSizeC = (channelSeparator.isRGB()) ? 3 : sizeC;
        int pixelSizeZ = inPlanes / numCol;

        // Overwrite dimensions if input is Brightfield type
        if (inType.equals(HTplusFluo.Channel.RGB)) {
            pixelSizeC = 3;
            planeIncrement = 1;
        }

        // Clone the metadata and remove all the series in the metadata except the one we process
        OMEXMLMetadata outMeta = service.createOMEXMLMetadata(inMeta.dumpXML());
        OMEXMLMetadataRoot root = (OMEXMLMetadataRoot) outMeta.getRoot();
        List<Image> images = root.copyImageList();
        for (int i = 0; i < images.size(); i++) {
            if (i != inSeries) {
                root.removeImage(images.get(i));
            }
        }
        outMeta.setRoot(root);

        // Adjust the metadata attributes affected during this process.
        outMeta.setImageName(new File(firstId).getName().replace(".ome.tif", ""), 0);

        int chIdx = 0;
        for (HTplusFluo.Channel channel: inIds.keySet()) {
            outMeta.setChannelID(channel.getName(), 0, chIdx);
            outMeta.setChannelSamplesPerPixel(new PositiveInteger(1), 0, chIdx++);
        }

        outMeta.setPixelsSizeC(new PositiveInteger(pixelSizeC), 0);
        outMeta.setPixelsSizeZ(new PositiveInteger(pixelSizeZ), 0);
        outMeta.setPixelsSizeT(new PositiveInteger(1), 0);
        outMeta.setPixelsBinDataBigEndian(Boolean.FALSE, 0, 0);
        outMeta.setPixelsDimensionOrder(DimensionOrder.fromString("XYZCT"), 0);
        outMeta.setPixelsType(PixelType.fromString(FormatTools.getPixelTypeString(FormatTools.UINT8)), 0);

        // Setup the writer
        ImageWriter writer = new ImageWriter();

        if (!compression.equals("None")) {
            writer.setCompression(compression);
        }

        writer.setMetadataRetrieve(outMeta);
        writer.setId(outId);

        // Copy the planes
        int outPlaneInd = 0;
        byte[] img = new byte[channelSeparator.getSizeX() * channelSeparator.getSizeY()];
        for (HTplusFluo.Channel channel: inIds.keySet()) {
            channelSeparator.setId(inIds.get(channel));
            channelSeparator.setSeries(inSeries);

            for (int inPlaneInd = channel.getColorIndex(); inPlaneInd < inPlanes; inPlaneInd += planeIncrement) {
//            logger.info("        writing plane: " + outPlaneInd);
                channelSeparator.openBytes(inPlaneInd, img);
                writer.saveBytes(outPlaneInd++, img);
            }
        }

        // Cleanup
        channelSeparator.close();
        writer.close();
    }
}