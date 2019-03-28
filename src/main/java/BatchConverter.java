import ij.IJ;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.formats.FormatException;
import net.imagej.ImageJ;
import ome.xml.model.enums.EnumerationException;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * @author Felix Meyenhofer
 */
@Plugin(type = Command.class, menuPath = "Plugins > NanoZoomer NDPI > Batch OME-TIF Converter")
public class BatchConverter implements Command {

    @Parameter
    LogService log;

    @Parameter
    StatusService status;


    @Override
    public void run() {
        IJ.run("Console", "uiservice=[org.scijava.ui.DefaultUIService [priority = 0.0]]");
        log.info("Run NDPI batch converter");

        // Dialog
        BatchConverterDialog dialog = BatchConverterDialog.createAndShow();

        if (dialog.isCancelled()) {
            log.info("Aborted");
            return;
        }

        File directory = dialog.getSelectedDirectory();
        List<HTplusFluo.Channel> channelNames = dialog.getSelectedChannels();
        int seriesIndex = dialog.getSelectedSeries();
        HashMap<HTplusFluo.Channel, List<File>> allFiles = NdpiUtils.getFiles(directory);

        // Select channel subset and sort
        HashMap<HTplusFluo.Channel, List<File>> files = new HashMap<>();
        for (HTplusFluo.Channel channel : channelNames) {
            files.put(channel, allFiles.get(channel));
            Collections.sort(files.get(channel));
        }

        if (files.values().size() < 1) {
            log.error("Did not find any ndpi files in " + directory.getAbsolutePath());
            return;
        }

        // Convert
        int N = files.get(files.keySet().iterator().next()).size();
        int n = 0;
        while (true) {
            status.showStatus(n++, N, "Converting files...");

            HashMap<HTplusFluo.Channel, String> pathSet = popPathSet(files);
            if (pathSet.isEmpty()) {
                break;
            }

            String outputPath = generateOutputPath(pathSet, seriesIndex);
            if (new File(outputPath).exists()) {
                log.warn("File already exists: " + outputPath);
                log.warn("... Skipping conversion");
                continue;
            }

            log.info("Converting: ");
            for (HTplusFluo.Channel channel : pathSet.keySet()) {
                log.info("\t    " + pathSet.get(channel));
            }
            log.info("\tto: " + outputPath);

            try {
                NdpiUtils.convert(pathSet, seriesIndex, outputPath, "None");
            } catch (IOException |
                    FormatException |
                    DependencyException |
                    ServiceException |
                    EnumerationException e) {
                e.printStackTrace();
            }
        }
        status.showStatus(N, N, "Conversions done.");
        log.info("Done.");
    }

    private String generateOutputPath(HashMap<HTplusFluo.Channel, String> hash, Integer series) {
        HTplusFluo.Channel channel = hash.keySet().iterator().next();
        String path = hash.get(channel);
        path = path.replace("." + HTplusFluo.FILE_EXTENSION, "_series-" + (series + 1) +  ".ome.tif");
        path = path.replaceAll(channel.getName() + "[_-]?", "");

        return path;
    }

    private HashMap<HTplusFluo.Channel, String> popPathSet(HashMap<HTplusFluo.Channel, List<File>> hash) {
        HashMap<HTplusFluo.Channel, String> pair = new HashMap<>();

        for (HTplusFluo.Channel channel : hash.keySet()) {
            List<File> files = hash.get(channel);
            if (files.isEmpty()) {
                break;
            }

            File file = files.get(0);
            pair.put(channel, file.getAbsolutePath());
            files.remove(file);
        }

        return pair;
    }

    public static void main(String[] args) {
        ImageJ ij = new ImageJ();
        ij.ui().showUI();

        ij.command().run(BatchConverter.class, true);
    }
}
