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

import iped.app.ui.utils.UiIconSize;
import iped.utils.QualityIcon;

/**
 * Load icons to memory
 *
 * @author guilherme.dutra
 *
 */
public class IconManager {

    public static final int defaultSize = 18;
    public static final int defaultGallerySize = 24;
    public static final int defaultCategorySize = 20;

    private static final String ICON_EXTENSION = ".png";

    private static final int[] initialSizes = UiIconSize.loadUserSetting();
    private static int currentIconSize = initialSizes[2];

    private static final Map<String, QualityIcon> catIconMap = loadIconsFromJar("cat", initialSizes[0]);

    private static final Map<String, QualityIcon> extIconMap = loadIconsFromJar("file", currentIconSize);
    private static final Map<String, QualityIcon> treeIconMap = loadIconsFromJar("tree", currentIconSize);
    private static final Map<String, QualityIcon> mimeIconMap = initMimeToIconMap(currentIconSize);

    private static final Map<String, QualityIcon> extIconMapGallery = initIconsMapSize(extIconMap, initialSizes[1]);
    private static final Map<String, QualityIcon> mimeIconMapGallery = initIconsMapSize(mimeIconMap, initialSizes[1]);
    private static final Map<String, QualityIcon> treeIconMapGallery = initIconsMapSize(treeIconMap, initialSizes[1]);

    private static final String folderOpenedKey = "folder-opened";
    private static final String folderClosedKey = "folder-closed";
    private static final String diskKey = "drive";
    private static final String fileKey = "file";

    private static final QualityIcon defaultCategoryIcon = catIconMap.get("blank");

    private static Map<String, QualityIcon> loadIconsFromJar(String iconPath, int size) {
        Map<String, QualityIcon> map = new HashMap<>();
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
                        String path = IconManager.class.getName().toString().replace(".", separator).replace(IconManager.class.getSimpleName(), "") + iconPath + separator;
                        String nameWithPath = e.getName();
                        String name = nameWithPath.replace(path, "");
                        if (nameWithPath.startsWith(path) && name.toLowerCase().endsWith(ICON_EXTENSION)) {
                            BufferedImage img = ImageIO.read(IconManager.class.getResource(iconPath + separator + name));
                            map.put(name.replace(ICON_EXTENSION, "").toLowerCase(), new QualityIcon(img, size));
                        }
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return map;
    }

    public static Icon getDiskIcon() {
        return getTreeIcon(diskKey);
    }

    public static Icon getDiskIconGallery() {
        return getTreeIconGallery(diskKey);
    }

    public static Icon getFolderIcon() {
        return getFolderIcon(false);
    }

    public static Icon getFolderIcon(boolean isOpened) {
        return getTreeIcon(isOpened ? folderOpenedKey : folderClosedKey);
    }

    public static Icon getFolderIconGallery() {
        return getFolderIconGallery(false);
    }

    public static Icon getFolderIconGallery(boolean isOpened) {
        return getTreeIconGallery(isOpened ? folderOpenedKey : folderClosedKey);
    }

    public static Icon getFileIconGallery(String mimeType, String extension) {
        return getFileIcon(mimeType, extension, mimeIconMapGallery, extIconMapGallery, extIconMapGallery.get(fileKey));
    }

    public static Icon getFileIcon(String mimeType, String extension) {
        return getFileIcon(mimeType, extension, extIconMap.get(fileKey));
    }

    public static Icon getFileIcon(String mimeType, String extension, Icon defaultIcon) {
        return getFileIcon(mimeType, extension, mimeIconMap, extIconMap, defaultIcon);
    }

    private static Icon getFileIcon(String mimeType, String extension, Map<String, QualityIcon> mimeIconMap, Map<String, QualityIcon> extIconMap, Icon defaultIcon) {
        if (mimeType != null && !mimeType.isBlank()) {
            Icon icon = mimeIconMap.get(mimeType.strip());
            if (icon != null) {
                return icon;
            }
        }
        if (extension != null && !extension.isBlank()) {
            Icon icon = extIconMap.get(extension.strip());
            if (icon != null) {
                return icon;
            }
        }
        return defaultIcon;
    }

    public static Icon getCategoryIcon(String category) {
        if (category != null && !category.isBlank()) {
            return catIconMap.getOrDefault(category.strip(), defaultCategoryIcon);
        }
        return defaultCategoryIcon;
    }

    public static QualityIcon getTreeIcon(String key) {
        return treeIconMap.get(key);
    }

    public static QualityIcon getTreeIconGallery(String key) {
        return treeIconMapGallery.get(key);
    }

    /**
     * Icons associated to one or more mime types should be added here.
     */
    private static Map<String, QualityIcon> initMimeToIconMap(int size) {
        Map<String, QualityIcon> availableIconsMap = loadIconsFromJar("mime", size);
        Map<String, QualityIcon> mimeIconMap = new HashMap<>();

        QualityIcon icon = availableIconsMap.get("emule");
        if (icon != null) {
            mimeIconMap.put("application/x-emule", icon);
            mimeIconMap.put("application/x-emule-part-met", icon);
            mimeIconMap.put("application/x-emule-searches", icon);
            mimeIconMap.put("application/x-emule-preferences-ini", icon);
            mimeIconMap.put("application/x-emule-preferences-dat", icon);
        }

        icon = availableIconsMap.get("emule-entry");
        if (icon != null) {
            mimeIconMap.put("application/x-emule-known-met-entry", icon);
            mimeIconMap.put("application/x-emule-part-met-entry", icon);
        }

        icon = availableIconsMap.get("ares");
        if (icon != null) {
            mimeIconMap.put("application/x-ares-galaxy", icon);
        }

        icon = availableIconsMap.get("ares-entry");
        if (icon != null) {
            mimeIconMap.put("application/x-ares-galaxy-entry", icon);
        }

        icon = availableIconsMap.get("shareaza");
        if (icon != null) {
            mimeIconMap.put("application/x-shareaza-searches-dat", icon);
            mimeIconMap.put("application/x-shareaza-library-dat", icon);
            mimeIconMap.put("application/x-shareaza-download", icon);
        }

        icon = availableIconsMap.get("shareaza-entry");
        if (icon != null) {
            mimeIconMap.put("application/x-shareaza-library-dat-entry", icon);
        }

        icon = availableIconsMap.get("torrent");
        if (icon != null) {
            mimeIconMap.put("application/x-bittorrent-resume-dat", icon);
            mimeIconMap.put("application/x-bittorrent-resume-dat-entry", icon);
            mimeIconMap.put("application/x-bittorrent-settings-dat", icon);
            mimeIconMap.put("application/x-bittorrent", icon);
        }

        icon = availableIconsMap.get("transmission");
        if (icon != null) {
            mimeIconMap.put("application/x-transmission-resume", icon);
        }

        icon = availableIconsMap.get("auto-dest");
        if (icon != null) {
            mimeIconMap.put("application/x-automaticdestinations", icon);
        }

        icon = availableIconsMap.get("auto-dest-entry");
        if (icon != null) {
            mimeIconMap.put("application/x-automaticdestinations-entry", icon);
        }

        icon = availableIconsMap.get("custom-dest");
        if (icon != null) {
            mimeIconMap.put("application/x-customdestinations", icon);
        }

        icon = availableIconsMap.get("custom-dest-entry");
        if (icon != null) {
            mimeIconMap.put("application/x-customdestinations-entry", icon);
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
            mimeIconMap.put("application/x-ufed-chat-preview-whatsapp", icon);
        }

        icon = availableIconsMap.get("threema");
        if (icon != null) {
            mimeIconMap.put("application/x-threema-chat", icon);
            mimeIconMap.put("application/x-threema-user-plist", icon);
            mimeIconMap.put("application/x-threema-chatstorage", icon);
            mimeIconMap.put("application/x-ufed-chat-preview-threema", icon);
        }

        icon = availableIconsMap.get("facebook");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-chat-preview-facebook", icon);
        }

        icon = availableIconsMap.get("signal");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-chat-preview-signal", icon);
        }

        icon = availableIconsMap.get("snapchat");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-chat-preview-snapchat", icon);
        }

        icon = availableIconsMap.get("tiktok");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-chat-preview-tiktok", icon);
        }

        icon = availableIconsMap.get("viber");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-chat-preview-viber", icon);
        }

        icon = availableIconsMap.get("instagram");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-chat-preview-instagram", icon);
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
            mimeIconMap.put("application/x-ufed-chat-preview-telegram", icon);
        }

        icon = availableIconsMap.get("apple-config");
        if (icon != null) {
            mimeIconMap.put("application/x-plist-itunes", icon);
            mimeIconMap.put("application/x-bplist-itunes", icon);
            mimeIconMap.put("application/x-bplist-memgraph", icon);
            mimeIconMap.put("application/x-bplist", icon);
            mimeIconMap.put("application/x-apple-nskeyedarchiver", icon);
            mimeIconMap.put("application/x-plist", icon);
            mimeIconMap.put("application/x-bplist-webarchive", icon);
            mimeIconMap.put("application/x-plist-webarchive", icon);
            mimeIconMap.put("application/x-plist-memgraph", icon);
        }

        icon = availableIconsMap.get("deviceinfo");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-deviceinfo", icon);
            mimeIconMap.put("application/x-ufed-deviceinfoentry", icon);
            mimeIconMap.put("application/x-ufed-simdata", icon);
            mimeIconMap.put("application/x-ufed-html-simdata", icon);
            mimeIconMap.put("application/x-ufed-html-summary", icon);
        }

        icon = availableIconsMap.get("location");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-location", icon);
            mimeIconMap.put("application/x-ios-locations-db", icon);
            mimeIconMap.put("application/x-ufed-html-locations", icon);
            mimeIconMap.put("application/x-apple-location", icon);
        }

        icon = availableIconsMap.get("password");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-password", icon);
            mimeIconMap.put("application/x-ufed-html-passwords", icon);
        }

        icon = availableIconsMap.get("user");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-useraccount", icon);
            mimeIconMap.put("application/x-ufed-user", icon);
            mimeIconMap.put("application/x-gdrive-account-info", icon);
            mimeIconMap.put("application/x-ufed-contact", icon);
            mimeIconMap.put("application/x-ios-addressbook-db", icon);
            mimeIconMap.put("application/windows-adress-book", icon);
            mimeIconMap.put("application/outlook-contact", icon);
            mimeIconMap.put("contact/x-skype-account", icon);
            mimeIconMap.put("contact/x-skype-contact", icon);
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
            mimeIconMap.put("application/x-whatsapp-contactsv2", icon);
        }

        icon = availableIconsMap.get("calendar");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-calendarentry", icon);
            mimeIconMap.put("application/x-ios-calendar-db", icon);
            mimeIconMap.put("application/x-ufed-html-calendar", icon);
            mimeIconMap.put("application/x-win10-mail-appointment", icon);
        }

        icon = availableIconsMap.get("call");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-call", icon);
            mimeIconMap.put("call/x-whatsapp-call", icon);
            mimeIconMap.put("call/x-telegram-call", icon);
            mimeIconMap.put("call/x-threema-call", icon);
            mimeIconMap.put("application/x-ios-calllog-db", icon);
            mimeIconMap.put("application/x-ios8-calllog-db", icon);
            mimeIconMap.put("call/x-discord-call", icon);
        }

        icon = availableIconsMap.get("web");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-webbookmark", icon);
            mimeIconMap.put("application/x-ufed-visitedpage", icon);
            mimeIconMap.put("application/x-safari-history-registry", icon);
            mimeIconMap.put("application/x-safari-history", icon);
        }

        icon = availableIconsMap.get("safari-sqlite");
        if (icon != null) {
            mimeIconMap.put("application/x-safari-sqlite", icon);
        }

        icon = availableIconsMap.get("chrome");
        if (icon != null) {
            mimeIconMap.put("application/x-chrome-history-registry", icon);
            mimeIconMap.put("application/x-chrome-downloads-registry", icon);
            mimeIconMap.put("application/x-chrome-downloads", icon);
            mimeIconMap.put("application/x-chrome-history", icon);
            mimeIconMap.put("application/x-chrome-searches", icon);
        }

        icon = availableIconsMap.get("chrome-sqlite");
        if (icon != null) {
            mimeIconMap.put("application/x-chrome-sqlite", icon);
        }

        icon = availableIconsMap.get("edge");
        if (icon != null) {
            mimeIconMap.put("application/x-edge-history", icon);
            mimeIconMap.put("application/x-edge-history-registry", icon);
            mimeIconMap.put("application/x-edge-web-cache", icon);
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
            mimeIconMap.put("application/x-ufed-applicationusage", icon);
            mimeIconMap.put("application/x-ufed-deviceevent", icon);
        }

        icon = availableIconsMap.get("attachment");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-attachment", icon);
        }

        icon = availableIconsMap.get("message");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-instantmessage", icon);
            mimeIconMap.put("application/x-ufed-chat-preview", icon);
            mimeIconMap.put("message/x-chat-message", icon);
            mimeIconMap.put("message/x-discord-message", icon);
        }

        icon = availableIconsMap.get("message-whatsapp");
        if (icon != null) {
            mimeIconMap.put("message/x-whatsapp-message", icon);
            mimeIconMap.put("message/x-whatsapp-attachment", icon);
        }

        icon = availableIconsMap.get("message-threema");
        if (icon != null) {
            mimeIconMap.put("message/x-threema-message", icon);
            mimeIconMap.put("message/x-threema-attachment", icon);
        }

        icon = availableIconsMap.get("message-telegram");
        if (icon != null) {
            mimeIconMap.put("message/x-telegram-message", icon);
            mimeIconMap.put("message/x-telegram-attachment", icon);
        }

        icon = availableIconsMap.get("message-skype");
        if (icon != null) {
            mimeIconMap.put("message/x-skype-message", icon);
            mimeIconMap.put("message/x-skype-filetransfer", icon);
        }

        icon = availableIconsMap.get("database");
        if (icon != null) {
            mimeIconMap.put("application/x-database-table", icon);
        }

        icon = availableIconsMap.get("search");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-searcheditem", icon);
            mimeIconMap.put("application/x-ufed-html-searches", icon);
        }

        icon = availableIconsMap.get("note");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-html-notes", icon);
            mimeIconMap.put("application/x-ufed-note", icon);
            mimeIconMap.put("application/x-ios-notes-db", icon);
            mimeIconMap.put("application/x-ios-oldnotes-db", icon);
        }

        icon = availableIconsMap.get("activity-sensor");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-activitysensordata", icon);
            mimeIconMap.put("application/x-ufed-activitysensordatameasurement", icon);
            mimeIconMap.put("application/x-ufed-activitysensordatasample", icon);
        }

        icon = availableIconsMap.get("autofill");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-autofill", icon);
            mimeIconMap.put("application/x-ufed-html-autofill", icon);
        }

        icon = availableIconsMap.get("email");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-html-mails", icon);
            mimeIconMap.put("application/x-ufed-email", icon);
            mimeIconMap.put("multipart/related", icon);
            mimeIconMap.put("message/outlook-pst", icon);
            mimeIconMap.put("message/x-emlx", icon);
            mimeIconMap.put("message/rfc822-partial", icon);
            mimeIconMap.put("message/x-emlx-partial", icon);
            mimeIconMap.put("message/x-rfc822-mac", icon);
        }

        icon = availableIconsMap.get("chat-activity");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-chatactivity", icon);
        }

        icon = availableIconsMap.get("cookie");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-cookie", icon);
            mimeIconMap.put("application/x-ufed-html-cookies", icon);
        }

        icon = availableIconsMap.get("creditcard");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-creditcard", icon);
        }

        icon = availableIconsMap.get("device-connectivity");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-deviceconnectivity", icon);
            mimeIconMap.put("application/x-ufed-recognizeddevice", icon);
        }

        icon = availableIconsMap.get("download");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-filedownload", icon);
        }

        icon = availableIconsMap.get("upload");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-fileupload", icon);
        }

        icon = availableIconsMap.get("log");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-logentry", icon);
            mimeIconMap.put("application/x-ufed-html-logs", icon);
        }

        icon = availableIconsMap.get("sms");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-html-sms", icon);
            mimeIconMap.put("application/x-ufed-sms", icon);
            mimeIconMap.put("application/x-ios-sms-db", icon);
        }

        icon = availableIconsMap.get("wireless");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-wirelessnetwork", icon);
            mimeIconMap.put("application/x-ufed-html-wifi", icon);
        }

        icon = availableIconsMap.get("social");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-socialmediaactivity", icon);
        }

        icon = availableIconsMap.get("tower");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-html-celltowers", icon);
            mimeIconMap.put("application/x-ufed-celltower", icon);
        }

        icon = availableIconsMap.get("bluetooth");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-html-bluetooth", icon);
            mimeIconMap.put("application/x-ufed-bluetoothdevice", icon);
        }

        icon = availableIconsMap.get("journey");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-journey", icon);
        }

        icon = availableIconsMap.get("power");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-poweringevent", icon);
        }

        icon = availableIconsMap.get("dictionary");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-dictionaryword", icon);
        }

        icon = availableIconsMap.get("financial-account");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-financialaccount", icon);
        }

        icon = availableIconsMap.get("transfer-funds");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-transferoffunds", icon);
        }

        icon = availableIconsMap.get("fuzzy-object");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-fuzzyentitymodel", icon);
        }

        icon = availableIconsMap.get("fuzzy-event");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-fuzzytimelinemodel", icon);
        }

        icon = availableIconsMap.get("discord");
        if (icon != null) {
            mimeIconMap.put("application/x-discord-index", icon);
            mimeIconMap.put("application/x-discord-chat", icon);
            mimeIconMap.put("application/x-ufed-chat-preview-discord", icon);
        }

        icon = availableIconsMap.get("discord-attachment");
        if (icon != null) {
            mimeIconMap.put("message/x-discord-attachment", icon);
        }

        icon = availableIconsMap.get("edb-table");
        if (icon != null) {
            mimeIconMap.put("application/x-edb-table", icon);
        }

        icon = availableIconsMap.get("live");
        if (icon != null) {
            mimeIconMap.put("application/x-livecontacts-table", icon);
        }

        icon = availableIconsMap.get("elf");
        if (icon != null) {
            mimeIconMap.put("application/x-elf-record", icon);
        }

        icon = availableIconsMap.get("drive");
        if (icon != null) {
            mimeIconMap.put("application/x-e01-image", icon);
            mimeIconMap.put("application/x-ewf-image", icon);
            mimeIconMap.put("application/x-ewf2-image", icon);
            mimeIconMap.put("application/x-ex01-image", icon);
            mimeIconMap.put("application/x-disk-image", icon);
            mimeIconMap.put("application/x-raw-image", icon);
        }

        icon = availableIconsMap.get("rfb");
        if (icon != null) {
            mimeIconMap.put("application/irpf", icon);
        }

        icon = availableIconsMap.get("usnjournal");
        if (icon != null) {
            mimeIconMap.put("application/x-usnjournal-$j", icon);
        }

        icon = availableIconsMap.get("vlc-ini");
        if (icon != null) {
            mimeIconMap.put("application/x-vlc-ini", icon);
        }

        icon = availableIconsMap.get("markdown");
        if (icon != null) {
            mimeIconMap.put("text/x-web-markdown", icon);
        }

        icon = availableIconsMap.get("notification");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-notification", icon);
        }

        icon = availableIconsMap.get("mobilecard");
        if (icon != null) {
            mimeIconMap.put("application/x-ufed-mobilecard", icon);
        }

        return mimeIconMap;
    }

    /**
     * Create a new icons map, based on an existing map, for a different size.
     */
    private static Map<String, QualityIcon> initIconsMapSize(Map<String, QualityIcon> map, int size) {
        Map<String, QualityIcon> newMap = new HashMap<>();
        for (String key : map.keySet()) {
            QualityIcon icon = map.get(key);
            newMap.put(key, icon.getIconWidth() == size ? icon : new QualityIcon(icon, size));
        }
        return newMap;
    }

    private static void setMapIconSize(Map<String, QualityIcon> map, int size) {
        for (Icon icon : map.values()) {
            if (icon instanceof QualityIcon) {
                ((QualityIcon) icon).setSize(size);
            }
        }
    }

    public static void setCategoryIconSize(int size) {
        setMapIconSize(catIconMap, size);
    }

    public static void setGalleryIconSize(int size) {
        setMapIconSize(extIconMapGallery, size);
        setMapIconSize(mimeIconMapGallery, size);
        setMapIconSize(treeIconMapGallery, size);
    }

    public static void setIconSize(int size) {
        currentIconSize = size;
        setMapIconSize(extIconMap, size);
        setMapIconSize(mimeIconMap, size);
        setMapIconSize(treeIconMap, size);
    }

    public static int getIconSize() {
        return currentIconSize;
    }
}
