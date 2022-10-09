package iped.app.ui.utils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import iped.app.ui.IconManager;
import iped.utils.UTF8Properties;

public class UiIconSize {
    private static final File file = new File(System.getProperty("user.home") + "/.iped", "UiIconSize.txt");
    private static final String key = "CatIconSize";

    public static int loadUserSetting() {
        try {
            if (!file.exists()) {
                saveUserSetting(IconManager.defaultSize);
            } else {
                UTF8Properties prop = new UTF8Properties();
                prop.load(file);
                String value = prop.getProperty(key);
                if (value != null) {
                    int size = Integer.parseInt(value.trim());
                    return size;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return IconManager.defaultSize;
    }

    public static void saveUserSetting(int size) {
        try {
            if (file.getParentFile() != null && !file.getParentFile().exists())
                file.getParentFile().mkdirs();
            List<String> l = new ArrayList<String>();
            l.add("# Size of icons used for categories");
            l.add(key + " = " + size);
            Files.write(file.toPath(), l, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
