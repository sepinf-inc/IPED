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

import java.net.URL;
import java.security.CodeSource;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.utils.QualityIcon;

/**
 * Load icons to memory
 * 
 * @author guilherme.dutra
 *
 */

public class IconLoader {

    private static Logger LOGGER = LoggerFactory.getLogger(IconLoader.class);

    public static Map<String, Icon> extesionIconMap = new HashMap<String, Icon>();

    private static String ICON_PATH_JAR[] = { "cat", "file" };
    // private static String ICON_PATH_APP = Configuration.CONF_DIR+"/icon/";
    private static String ICON_EXTENSION = ".png";

    static {
        loadIconsInJar(ICON_PATH_JAR, ICON_EXTENSION, extesionIconMap);
    }

    private static void loadIconsInJar(String[] iconPathArray, String iconExtension, Map<String, Icon> map) {

        if (map == null)
            return;

        try {
            String separator = "/";
            CodeSource src = IconLoader.class.getProtectionDomain().getCodeSource();
            if (src != null) {
                URL jar = src.getLocation();
                ZipInputStream zip = new ZipInputStream(jar.openStream());
                while (true) {
                    ZipEntry e = zip.getNextEntry();
                    if (e == null)
                        break;
                    for (String iconPath : iconPathArray) {
                        String path = IconLoader.class.getName().toString().replace(".", separator).replace(IconLoader.class.getSimpleName(), "") + iconPath + separator;

                        String nameWithPath = e.getName();
                        String name = nameWithPath.replace(path, "");
                        if (nameWithPath.startsWith(path) && name.toLowerCase().endsWith(iconExtension)) {
                            map.put(name.replace(iconExtension, "").toLowerCase(), new QualityIcon(new ImageIcon(IconLoader.class.getResource(iconPath + separator + name))));
                        }
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

}