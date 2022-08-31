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

import java.awt.Component;
import java.io.IOException;

import javax.swing.Icon;


import javax.swing.ImageIcon;

import java.util.List;
import java.util.Arrays;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.jar.*;

import java.util.zip.*;
import java.security.CodeSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import iped.utils.QualityIcon;


//import iped.engine.config.Configuration;


/**
 * Load icons to memory
 * 
 * @author guilherme.dutra
 *
 */

public class FileTypeIconRenderer {

    private static final long serialVersionUID = -1L;  
    private static Logger LOGGER = LoggerFactory.getLogger(FileTypeIconRenderer.class);

    public  static Map<String,Icon> extesionIconMap = new HashMap<String,Icon>();

    private static String ICON_PATH_JAR [] = {"cat","file"};
    //private static String ICON_PATH_APP = Configuration.CONF_DIR+"/icon/";
    private static String ICON_EXTENSION = ".png";

    static {

        loadIconsInJar(ICON_PATH_JAR, ICON_EXTENSION, extesionIconMap);
        //loadIconsInPath(ICON_PATH_APP, ICON_EXTENSION, extesionIconMap);
       
    }

    private static void loadIconsInJar(String [] iconPathArray, String iconExtension, Map<String,Icon> map){

        if (map == null)
            return;

        try {
            String separator = "/";
            CodeSource src = FileTypeIconRenderer.class.getProtectionDomain().getCodeSource();
            if (src != null) {
                URL jar = src.getLocation();
                ZipInputStream zip = new ZipInputStream(jar.openStream());
                while(true) {
                    ZipEntry e = zip.getNextEntry();
                    if (e == null)
                        break;
                    for (String iconPath: iconPathArray){
                        String path = FileTypeIconRenderer.class.getName().toString().replace(".",separator).replace(FileTypeIconRenderer.class.getSimpleName(),"")+iconPath+separator;

                        String nameWithPath = e.getName();
                        String name = nameWithPath.replace(path,"");
                        if (nameWithPath.startsWith(path) && name.toLowerCase().endsWith(iconExtension)) {
                            map.put(name.replace(iconExtension,"").toLowerCase(),new QualityIcon(new ImageIcon(FileTypeIconRenderer.class.getResource(iconPath+separator+name))));
                        }
                    }
                }
            }
        }catch (Exception ex){
            ex.printStackTrace();
        }
   }

    private static void loadIconsInPath(String iconPath, String iconExtension, Map<String,Icon> map){

        File path = new File(iconPath);

        if (path == null || !path.exists() || map == null)
            return;

        File[] files = path.listFiles(new FilenameFilter(){
            @Override
            public boolean accept(File dir, String name)
            {
                if(name.toLowerCase().endsWith(iconExtension))
                    return true;
                return false;
            }

        });

        for (int i =0; i < files.length; i++){
            try {
                map.put(files[i].getName().replace(iconExtension,"").toLowerCase(),new ImageIcon(files[i].getAbsolutePath()));
            }catch (Exception e){
                LOGGER.warn("Error loading icon. Error:{}", e.toString());
            }
        }

    }

}