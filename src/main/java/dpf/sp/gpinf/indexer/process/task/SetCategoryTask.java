/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de EvidÃªncias Digitais (IPED).
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
package dpf.sp.gpinf.indexer.process.task;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Properties;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;

import dpf.sp.gpinf.indexer.Messages;
import iped3.Item;

/**
 * Classe que carrega o mapeamento de mimeTypes para Categoria da aplicação. Além disso utiliza
 * regras javascript de definição de categorias baseadas nas propriedades dos itens. Também é
 * responsável por definir a categoria do item.
 */
public class SetCategoryTask extends AbstractTask {

  public static String CATEGORIES_BY_TYPE = "CategoriesByTypeConfig.txt"; //$NON-NLS-1$

  private static String FOLDER_CATEGORY = Messages.getString("SetCategoryTask.Folders"); //$NON-NLS-1$
  public static String SCANNED_CATEGORY = Messages.getString("SetCategoryTask.ScannedDocs"); //$NON-NLS-1$

  private static MediaTypeRegistry registry = TikaConfig.getDefaultConfig().getMediaTypeRegistry();
  private static HashMap<String, String> mimetypeToCategoryMap;

  @Override
  public void init(Properties confProps, File configPath) throws Exception {
    load(new File(configPath, CATEGORIES_BY_TYPE));
  }

  @Override
  public void finish() throws Exception {
    // TODO Auto-generated method stub

  }

  public static synchronized void load(File file) throws FileNotFoundException, IOException {

    if (mimetypeToCategoryMap != null) {
      return;
    }
    
    mimetypeToCategoryMap = new HashMap<>();

    try(BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))){ //$NON-NLS-1$
        String line = reader.readLine();
        while ((line = reader.readLine()) != null) {
          if (line.startsWith("#")) { //$NON-NLS-1$
            continue;
          }
          String[] keyValuePair = line.split("="); //$NON-NLS-1$
          if (keyValuePair.length == 2) {
            String category = keyValuePair[0].trim();
            String mimeTypes = keyValuePair[1].trim();
            for (String mimeType : mimeTypes.split(";")) { //$NON-NLS-1$
              mimeType = mimeType.trim();
              MediaType mt = MediaType.parse(mimeType);
              if(mt != null)
                  mimeType = registry.normalize(mt).toString();
              mimetypeToCategoryMap.put(mimeType, category);
            }
          }
        }
    }
  }

  private static String get(MediaType type) {

    String category;
    do {
      category = mimetypeToCategoryMap.get(type.toString());
      if (category == null) {
        category = mimetypeToCategoryMap.get(type.getType());
      }
      if (category != null) {
        return category;
      }

      type = registry.getSupertype(type);

    } while (type != null);

    return ""; //$NON-NLS-1$
  }

  public void process(Item e) throws Exception {

    if (e.isDir()) {
        e.setCategory(FOLDER_CATEGORY);
    }else {
        String category = get(e.getMediaType());
        e.setCategory(category);  
    }
    
  }

}
