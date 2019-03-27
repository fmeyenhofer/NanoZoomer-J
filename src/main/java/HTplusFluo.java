import org.scijava.util.ListUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


/**
 * Conventions for the NanoZoomer model HT+Fluo v2.0
 *
 * @author Felix Meyenhofer
 */

class HTplusFluo {

    static String FILE_EXTENSION = "ndpi";

    public enum Channel {
        DAPI  ("DAPI", 2),
        FITC  ("FITC", 1),
        CY3   ("Cy3", 1),
        TRITC ("TRITC", 0),
        CY5   ("Cy5", 0),
        RGB   ("RGB", -1);

        private String name;
        private int index;

        Channel(String name, int colorIndex) {
            this.name = name;
            this.index = colorIndex;
        }

        @Override
        public String toString() {
            return getName();
        }

        public int getColorIndex() {
            return this.index;
        }

        public String getName() {
            return this.name;
        }

        public static List<String> getNames() {
            List<String> names = new ArrayList<>(Channel.class.getEnumConstants().length);
            for (Channel ch : Channel.class.getEnumConstants()) {
                names.add(ch.getName());
            }

            return names;
        }

        public static Channel get(String name) {
            for (Channel ch : Channel.class.getEnumConstants()) {
                if (ch.getName().toLowerCase().equals(name.toLowerCase())) {
                    return ch;
                }
            }

            throw new RuntimeException("The is no Nanozoomer channel named " + name +
                    " Choices are: " + ListUtils.string(Channel.getNames()));
        }

        public static Channel match(File file) {
            for (Channel ch : Channel.class.getEnumConstants()) {
                if (file.getName().contains(ch.getName())) {
                    return ch;
                }
            }

            return null;
        }


    }
}
