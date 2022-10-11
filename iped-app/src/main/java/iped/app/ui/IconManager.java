/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de Evidências Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package iped.app.ui;

import java.awt.image.BufferedImage;
import java.net.URL;
import java.security.CodeSource;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.UIManager;

import iped.app.ui.utils.UiIconSize;
import iped.utils.QualityIcon;

/**
 * Load icons to memory
 * 
 * @author guilherme.dutra
 *
 */
public class IconManager {

    public static final Icon FOLDER_ICON = UIManager.getIcon("FileView.directoryIcon"); //$NON-NLS-1$
    public static final Icon DISK_ICON = UIManager.getIcon("FileView.hardDriveIcon"); //$NON-NLS-1$

    public static final int defaultSize = 16;

    private static final String ICON_EXTENSION = ".png";

    private static final Map<String, Icon> extIconMap = loadIconsFromJar("file", defaultSize);
    private static final Map<String, Icon> catIconMap = loadIconsFromJar("cat", UiIconSize.loadUserSetting());
    private static final Map<String, Icon> mimeIconMap = initMimeToIconMap();

    private static final Icon DEFAULT_FILE_ICON = UIManager.getIcon("FileView.fileIcon"); //$NON-NLS-1$
    private static final Icon DEFAULT_CATEGORY_ICON = catIconMap.get("blank"); //$NON-NLS-1$

    private static Map<String, Icon> loadIconsFromJar(String iconPath, int size) {
        Map<String, Icon> map = new HashMap<>();
        try {
            String separator = "/";
            CodeSource src = IconManager.class.getProtectionDomain().getCodeSource();
            if (src != null) {
                URL jar = src.getLocation();
                try (ZipInputStream zip = new ZipInputStream(jar.openStream())) {
                    while (true) {
                        ZipEntry e = zip.getNextEntry();
                        if (e == null) {
                            break;
                        }
                        String path = IconManager.class.getName().toString().replace(".", separator)
                                .replace(IconManager.class.getSimpleName(), "") + iconPath + separator;
                        String nameWithPath = e.getName();
                        String name = nameWithPath.replace(path, "");
                        if (nameWithPath.startsWith(path) && name.toLowerCase().endsWith(ICON_EXTENSION)) {
                            BufferedImage img = ImageIO
                                    .read(IconManager.class.getResource(iconPath + separator + name));
                            map.put(name.replace(ICON_EXTENSION, "").toLowerCase(),
                                    new QualityIcon(new ImageIcon(img), size));
                        }
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return map;
    }

    public static Icon getFileIcon(String mimeType, String extension) {
        if (mimeType != null && !mimeType.isBlank()) {
            Icon icon = mimeIconMap.get(mimeType);
            if (icon != null) {
                return icon;
            }
        }
        if (extension != null && !extension.isBlank()) {
            return extIconMap.getOrDefault(extension.strip(), DEFAULT_FILE_ICON);
        }
        return DEFAULT_FILE_ICON;
    }

    public static Icon getCategoryIcon(String category) {
        if (category != null && !category.isBlank()) {
            return catIconMap.getOrDefault(category.strip(), DEFAULT_CATEGORY_ICON);
        }
        return DEFAULT_CATEGORY_ICON;
    }

    /**
     * Icons associated to one or more mime types should be added here.
     */
    private static Map<String, Icon> initMimeToIconMap() {
        Map<String, Icon> availableIconsMap = loadIconsFromJar("mime", defaultSize);
        Map<String, Icon> mimeIconMap = new HashMap<String, Icon>();

        Icon icon = availableIconsMap.get("emule");
        if (icon != null) {
            mimeIconMap.put("application/x-emule", icon);
            mimeIconMap.put("application/x-emule-part-met", icon);
            mimeIconMap.put("application/x-emule-emule-searches", icon);
            mimeIconMap.put("application/x-emule-emule-preferences-ini", icon);
        }

        icon = availableIconsMap.get("ares");
        if (icon != null) {
            mimeIconMap.put("application/x-ares-galaxy", icon);
        }

        icon = availableIconsMap.get("shareaza");
        if (icon != null) {
            mimeIconMap.put("application/x-shareaza-searches-dat", icon);
            mimeIconMap.put("application/x-shareaza-library-dat", icon);
        }

        icon = availableIconsMap.get("torrent");
        if (icon != null) {
            mimeIconMap.put("application/x-bittorrent-resume-dat", icon);
            mimeIconMap.put("application/x-bittorrent", icon);
        }

        icon = availableIconsMap.get("registry");
        if (icon != null) {
            mimeIconMap.put("application/x-windows-registry", icon);
            mimeIconMap.put("application/x-windows-registry-main", icon);
            mimeIconMap.put("application/x-windows-registry-sam", icon);
            mimeIconMap.put("application/x-windows-registry-software", icon);
            mimeIconMap.put("application/x-windows-registry-system", icon);
            mimeIconMap.put("application/x-windows-registry-security", icon);
            mimeIconMap.put("application/x-windows-registry-ntuser", icon);
            mimeIconMap.put("application/x-windows-registry-usrclass", icon);
            mimeIconMap.put("application/x-windows-registry-amcache", icon);
        }

        icon = availableIconsMap.get("reg-report");
        if (icon != null) {
            mimeIconMap.put("application/x-windows-registry-report", icon);
        }

        icon = availableIconsMap.get("whatsapp");
        if (icon != null) {
            mimeIconMap.put("application/x-whatsapp-db", icon);
            mimeIconMap.put("application/x-whatsapp-db-f", icon);
            mimeIconMap.put("application/x-whatsapp-chatstorage", icon);
            mimeIconMap.put("application/x-whatsapp-chat", icon);
            mimeIconMap.put("application/x-ufed-chat-whatsapp", icon);
            mimeIconMap.put("application/x-ufed-chat-preview-whatsapp", icon);
        }

        icon = availableIconsMap.get("skype");
        if (icon != null) {
            mimeIconMap.put("application/sqlite-skype", icon);
            mimeIconMap.put("application/skype", icon);
            mimeIconMap.put("application/x-skype-conversation", icon);
            mimeIconMap.put("application/x-ufed-chat-preview-skype", icon);
        }

        icon = availableIconsMap.get("telegram");
        if (icon != null) {
            mimeIconMap.put("application/x-telegram-chat", icon);
            mimeIconMap.put("application/x-telegram-db", icon);
            mimeIconMap.put("application/x-ufed-chat-telegram", icon);
            mimeIconMap.put("application/x-ufed-chat-preview-telegram", icon);
        }

        icon = availableIconsMap.get("deviceinfo");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-deviceinfo", icon);
        }

        icon = availableIconsMap.get("location");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-location", icon);
        }

        icon = availableIconsMap.get("password");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-password", icon);
        }

        icon = availableIconsMap.get("user");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-useraccount", icon);
            mimeIconMap.put("application/x-ufed-user", icon);
            mimeIconMap.put("application/x-gdrive-account-info", icon);
            mimeIconMap.put("application/x-ufed-contact", icon);
        }

        icon = availableIconsMap.get("user-telegram");
        if (icon != null) {
            mimeIconMap.put("application/x-telegram-account", icon);
            mimeIconMap.put("application/x-telegram-user-conf", icon);
            mimeIconMap.put("contact/x-telegram-contact", icon);
        }

        icon = availableIconsMap.get("user-whatsapp");
        if (icon != null) {
            mimeIconMap.put("application/x-whatsapp-account", icon);
            mimeIconMap.put("application/x-whatsapp-wadb", icon);
            mimeIconMap.put("application/x-whatsapp-user-xml", icon);
            mimeIconMap.put("contact/x-whatsapp-contact", icon);
        }

        icon = availableIconsMap.get("calendar");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-calendarentry", icon);
        }

        icon = availableIconsMap.get("call");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-call", icon);
        }

        icon = availableIconsMap.get("web");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-webbookmark", icon);
            mimeIconMap.put("application/x-ufed-visitedpage", icon);
        }

        icon = availableIconsMap.get("chrome");
        if (icon != null) {
            mimeIconMap.put("application/x-chrome-history-registry", icon);
            mimeIconMap.put("application/x-chrome-downloads-registry", icon);
            mimeIconMap.put("application/x-chrome-downloads", icon);
            mimeIconMap.put("application/x-chrome-history", icon);
            mimeIconMap.put("application/x-chrome-searches", icon);
            mimeIconMap.put("application/x-chrome-sqlite", icon);
        }

        icon = availableIconsMap.get("package");
        if (icon != null) {
            mimeIconMap.put("application/vnd.android.package-archive", icon);
            mimeIconMap.put("application/x-ufed-installedapplication", icon);
        }
        
        icon = availableIconsMap.get("network");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-networkusage", icon);
        }

        icon = availableIconsMap.get("app-usage");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-appsusagelog", icon);
        }
        
        return mimeIconMap;
    }

    public static void setCategoryIconSize(int size) {
        for (Icon icon : catIconMap.values()) {
            if (icon instanceof QualityIcon) {
                ((QualityIcon) icon).setSize(size);
            }
        }
    }
}