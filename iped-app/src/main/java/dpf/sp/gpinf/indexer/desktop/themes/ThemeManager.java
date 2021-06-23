package dpf.sp.gpinf.indexer.desktop.themes;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Window;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.SwingUtilities;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.UnsupportedLookAndFeelException;

import bibliothek.extension.gui.dock.theme.EclipseTheme;
import bibliothek.extension.gui.dock.theme.eclipse.EclipseColorScheme;
import dpf.sp.gpinf.indexer.desktop.App;

public class ThemeManager {
    private static final ThemeManager instance = new ThemeManager();
    private final Map<Object, Object> savedDefaults = new HashMap<Object, Object>();
    private final List<Theme> themes = new ArrayList<Theme>();
    private Theme currentTheme;

    public static ThemeManager getInstance() {
        return instance;
    }

    public synchronized void setLookAndFeel() throws Exception {
        boolean nimbusFound = false;
        for (LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
            if ("Nimbus".equals(info.getName())) {
                UIManager.setLookAndFeel(info.getClassName());
                UIDefaults defaults = UIManager.getLookAndFeel().getDefaults();
                defaults.put("ScrollBar.thumbHeight", 12);
                // Workaround JDK-8134828
                defaults.put("ScrollBar.minimumThumbSize", new Dimension(30, 30));
                for (Object key : defaults.keySet()) {
                    savedDefaults.put(key, defaults.get(key));
                }
                nimbusFound = true;
                break;
            }
        }
        if (nimbusFound) {
            themes.add(new LightTheme());
            themes.add(new DarkTheme());

            Theme selTheme = loadThemeOption();
            if (selTheme == null)
                selTheme = themes.get(0);
            setTheme(selTheme);
        } else {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        }
    }

    public List<Theme> getThemes() {
        return Collections.unmodifiableList(themes);
    }

    public Theme getCurrentTheme() {
        return currentTheme;
    }

    public synchronized void setTheme(Theme newTheme) {
        if (newTheme == null || newTheme.equals(currentTheme))
            return;
        if (currentTheme != null)
            currentTheme.clean();
        for (Object key : savedDefaults.keySet()) {
            Object value = savedDefaults.get(key);
            UIManager.put(key, value);
            UIManager.getLookAndFeelDefaults().put(key, value);
        }
        newTheme.apply();
        currentTheme = newTheme;
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    UIManager.setLookAndFeel(UIManager.getLookAndFeel());
                } catch (UnsupportedLookAndFeelException e) {
                }
                updateUI(App.get());
                App.get().dockingControl.putProperty(EclipseTheme.ECLIPSE_COLOR_SCHEME, new EclipseColorScheme() {
                    @Override
                    public void updateUI() {
                        super.updateUI();
                        Theme t = getCurrentTheme();
                        if (t != null) {
                            Map<String, Color> map = t.getDockSettings();
                            for (String key : map.keySet()) {
                                setColor(key, map.get(key));
                            }
                        }
                    }
                });
                App.get().updateUI(true);
            }
        });

        saveThemeOption();
        File file = getSelectedThemeFile();
        if (!file.getParentFile().exists())
            file.getParentFile().mkdirs();
        try (BufferedWriter out = new BufferedWriter(new FileWriter(file))) {
            out.write(currentTheme.getClass().getName());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void updateUI(Window window) {
        for (Window child : window.getOwnedWindows()) {
            updateUI(child);
        }
        SwingUtilities.updateComponentTreeUI(window);
    }

    private static File getSelectedThemeFile() {
        return new File(System.getProperty("user.home") + "/.indexador/theme.dat");
    }

    private Theme loadThemeOption() {
        Theme selTheme = null;
        File file = getSelectedThemeFile();
        if (file.exists()) {
            try {
                List<String> l = Files.readAllLines(file.toPath());
                if (!l.isEmpty()) {
                    for (Theme theme : themes) {
                        if (l.get(0).equals(theme.getClass().getName())) {
                            selTheme = theme;
                            break;
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return selTheme;
    }

    private void saveThemeOption() {
        File file = getSelectedThemeFile();
        if (file != null && file.getParentFile() != null && !file.getParentFile().exists())
            file.getParentFile().mkdirs();
        try (BufferedWriter out = new BufferedWriter(new FileWriter(file))) {
            out.write(currentTheme.getClass().getName());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
