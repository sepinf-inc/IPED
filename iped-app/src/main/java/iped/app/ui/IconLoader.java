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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.utils.ImageUtil;
import iped.utils.QualityIcon;

/**
 * Load icons to memory
 * 
 * @author guilherme.dutra
 *
 */

public class IconLoader {

    private static Logger LOGGER = LoggerFactory.getLogger(IconLoader.class);

    private static final String ICON_EXTENSION = ".png";

    private static final Map<String, Icon> extIconMap = loadIconsFromJar("file", null);
    private static final Map<String, Icon> catIconMap = loadIconsFromJar("cat", 16);

    private static final Icon DEFAULT_FILE_ICON = UIManager.getIcon("FileView.fileIcon"); //$NON-NLS-1$
    private static final Icon DEFAULT_CATEGORY_ICON = catIconMap.get("blank"); //$NON-NLS-1$

    private static Map<String, Icon> loadIconsFromJar(String iconPath, Integer maxSize) {

        Map<String, Icon> map = new HashMap<>();
        try {
            String separator = "/";
            CodeSource src = IconLoader.class.getProtectionDomain().getCodeSource();
            if (src != null) {
                URL jar = src.getLocation();
                try (ZipInputStream zip = new ZipInputStream(jar.openStream())) {
                    while (true) {
                        ZipEntry e = zip.getNextEntry();
                        if (e == null) {
                            break;
                        }
                        String path = IconLoader.class.getName().toString().replace(".", separator).replace(IconLoader.class.getSimpleName(), "") + iconPath + separator;
                        String nameWithPath = e.getName();
                        String name = nameWithPath.replace(path, "");
                        if (nameWithPath.startsWith(path) && name.toLowerCase().endsWith(ICON_EXTENSION)) {
                            BufferedImage img = ImageIO.read(IconLoader.class.getResource(iconPath + separator + name));
                            if (maxSize != null) {
                                img = ImageUtil.resizeImage(img, maxSize, maxSize);
                            }
                            map.put(name.replace(ICON_EXTENSION, "").toLowerCase(), new QualityIcon(new ImageIcon(img)));
                        }
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return map;
    }

    public static Icon getFileIcon(String extension) {
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

}