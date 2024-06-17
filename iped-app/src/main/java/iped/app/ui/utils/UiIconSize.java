package iped.app.ui.utils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import iped.app.ui.IconManager;
import iped.utils.UTF8Properties;

public class UiIconSize {
    private static final File file = new File(System.getProperty("user.home") + "/.iped", "UiIconSize.txt");
    private static final String[] keys = new String[] { "CatIconSize", "GalleryIconSize", "TableIconSize" };
    private static final String[] comments = new String[] { "for categories", "in the gallery",
            "in tables and other UI elements" };
    private static final String lastSavedKey = "LastSaved";
    private static final int[] defaultSizes = new int[] { IconManager.defaultCategorySize,
            IconManager.defaultGallerySize, IconManager.defaultSize };

    public static int[] loadUserSetting() {
        int[] sizes = defaultSizes.clone();
        try {
            if (!file.exists()) {
                saveUserSetting(sizes);
            } else {
                UTF8Properties prop = new UTF8Properties();
                prop.load(file);

                boolean missing = prop.getProperty(lastSavedKey) == null;
                for (int i = 0; i < keys.length; i++) {
                    String value = prop.getProperty(keys[i]);
                    if (value != null) {
                        sizes[i] = Integer.parseInt(value.trim());
                    } else {
                        missing = true;
                    }
                }

                if (missing) {
                    for (int i = 0; i < keys.length; i++) {
                        if (sizes[i] < defaultSizes[i]) {
                            sizes[i] = defaultSizes[i];
                        }
                    }
                    saveUserSetting(sizes);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return sizes;
    }

    public static void saveUserSetting(int[] sizes) {
        try {
            if (file.getParentFile() != null && !file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            List<String> l = new ArrayList<String>();
            for (int i = 0; i < keys.length; i++) {
                l.add("# Size of icons used " + comments[i]);
                l.add(keys[i] + " = " + sizes[i]);
                l.add("");
            }
            l.add(lastSavedKey + " = " + new Date());
            Files.write(file.toPath(), l, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
