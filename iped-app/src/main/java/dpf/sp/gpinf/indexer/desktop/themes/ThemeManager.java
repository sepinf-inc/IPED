package dpf.sp.gpinf.indexer.desktop.themes;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Window;
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
                UIManager.put("nimbusOrange", new Color(47, 92, 180));
                UIManager.put("nimbusRed", Color.blue);
                UIManager.put("Table[Enabled+Selected].textForeground", Color.white);
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
            themes.get(0).apply(); // TODO
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
        for (Object key : savedDefaults.keySet()) {
            UIManager.put(key, savedDefaults.get(key));
        }
        newTheme.apply();
        currentTheme = newTheme;
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
        App.get().updateUI();
    }

    private void updateUI(Window window) {
        for (Window child : window.getOwnedWindows()) {
            updateUI(child);
        }
        SwingUtilities.updateComponentTreeUI(window);
    }
}
