package iped.app.ui.bookmarks;

import java.awt.Color;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import iped.utils.IOUtil;

public class BookmarkColorsManager {
    private static final String colorsMemoFile = System.getProperty("user.home") + "/.iped/bkmclr.dat";

    private static Map<Integer, Color> colorsMemo = Collections.synchronizedMap(new LinkedHashMap<Integer, Color>());

    private static final Map<Color, Color> backgroundToForeground = Collections
            .synchronizedMap(new HashMap<Color, Color>());

    static {
        for (int i = 0; i < BookmarkStandardColors.numStandardColors; i++) {
            Color foreground = i * 2 >= BookmarkStandardColors.numStandardColors ? Color.black : Color.white;
            backgroundToForeground.put(BookmarkStandardColors.colors[i], foreground);
        }
        readColorsMemo();
    }

    private static int nameToKey(String name) {
        return name.toLowerCase().hashCode();
    }

    public static void storeNameToColor(String name, Color color) {
        colorsMemo.put(nameToKey(name), color);
        writeColorsMemo();
    }

    public static Color getInitialColor(Set<Color> usedColors, String name) {
        int key = nameToKey(name);
        if (colorsMemo.containsKey(key)) {
            return colorsMemo.get(key);
        }
        int off = key % BookmarkStandardColors.numStandardColors;
        Color ret = BookmarkStandardColors.colors[off];
        for (int i = 0; i < BookmarkStandardColors.numStandardColors; i++) {
            int idx = (off + i) % BookmarkStandardColors.numStandardColors;
            Color c = BookmarkStandardColors.colors[idx];
            if (!usedColors.contains(c)) {
                ret = c;
                break;
            }
        }
        return ret;
    }

    static Color getForeground(Color background) {
        Color foreground = backgroundToForeground.get(background);
        if (foreground == null) {
            int[] c = new int[] { background.getRed(), background.getGreen(), background.getBlue() };
            Arrays.sort(c);
            foreground = c[0] >= 100 && c[1] >= 150 && c[2] >= 200 ? Color.black : Color.white;
            backgroundToForeground.put(background, foreground);
        }
        return foreground;
    }

    private static synchronized void writeColorsMemo() {
        ObjectOutputStream oos = null;
        try {
            File tmp = new File(colorsMemoFile + ".tmp");
            tmp.deleteOnExit();
            if (tmp.exists()) {
                tmp.delete();
            }
            oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(tmp)));
            oos.writeObject(colorsMemo);
            oos.close();
            Files.move(tmp.toPath(), Path.of(colorsMemoFile), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            IOUtil.closeQuietly(oos);
        }
    }

    @SuppressWarnings("unchecked")
    private static synchronized void readColorsMemo() {
        ObjectInputStream ois = null;
        try {
            File in = new File(colorsMemoFile);
            if (in.exists()) {
                ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(in)));
                colorsMemo = (Map<Integer, Color>) ois.readObject();
                ois.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            IOUtil.closeQuietly(ois);
        }
    }
}
