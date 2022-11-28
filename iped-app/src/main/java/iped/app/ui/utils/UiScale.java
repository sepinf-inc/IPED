package iped.app.ui.utils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import iped.utils.UTF8Properties;

public class UiScale {

    public static final String AUTO = "auto";
    private static final File file = new File(System.getProperty("user.home") + "/.iped", "UiScale.txt");
    private static final String key = "uiScale";

    public static String loadUserSetting() {
        try {
            if (!file.exists()) {
                saveUserSetting(AUTO);
            } else {
                UTF8Properties prop = new UTF8Properties();
                prop.load(file);
                String value = prop.getProperty(key);
                if (value != null) {
                    value = value.trim();
                    if (!value.equalsIgnoreCase(AUTO)) {
                        double factor = Double.parseDouble(value);
                        DecimalFormat df = new DecimalFormat("0.000", DecimalFormatSymbols.getInstance(Locale.US));
                        String val = df.format(factor);
                        System.setProperty("sun.java2d.uiScale", val);
                        return val;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return AUTO;
    }

    public static void saveUserSetting(String value) {
        try {
            Files.deleteIfExists(file.toPath());
            if (file.getParentFile() != null && !file.getParentFile().exists())
                file.getParentFile().mkdirs();
            // Create a file with the default setting
            List<String> l = new ArrayList<String>();
            l.add("# Sets the user interface scaling factor.");
            l.add("# \"" + AUTO + "\" to use the default automatic scaling based on the screen resolution.");
            l.add("# Use a float point value to set a fixed scale, e.g. " + key + " = 1.5");
            l.add(key + " = " + value);
            Files.write(file.toPath(), l, StandardOpenOption.CREATE_NEW);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
