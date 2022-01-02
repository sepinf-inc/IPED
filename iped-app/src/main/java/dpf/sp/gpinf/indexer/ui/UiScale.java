package dpf.sp.gpinf.indexer.ui;

import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class UiScale {
    private static final String key = "uiScale";

    public static void loadUserSetting() {
        try {
            File file = new File(System.getProperty("user.home") + "/.iped", "UiScale.txt");
            if (!file.exists()) {
                if (file.getParentFile() != null && !file.getParentFile().exists())
                    file.getParentFile().mkdirs();
                // Create a file with the default setting
                List<String> l = new ArrayList<String>();
                l.add("# Sets the user interface scaling factor.");
                l.add("# \"auto\" to use the default automatic scaling based on the screen resolution.");
                l.add("# Use a float point value to set a fixed scale, e.g. " + key + " = 1.5");
                l.add(key + " = auto");
                Files.write(file.toPath(), l, StandardOpenOption.CREATE_NEW);
            } else {
                Properties prop = new Properties();
                prop.load(new FileReader(file));
                String value = prop.getProperty(key);
                if (value != null) {
                    value = value.trim();
                    if (!value.equalsIgnoreCase("auto")) {
                        double factor = Double.parseDouble(value);
                        DecimalFormat df = new DecimalFormat("0.000");
                        System.setProperty("sun.java2d.uiScale", df.format(factor));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
