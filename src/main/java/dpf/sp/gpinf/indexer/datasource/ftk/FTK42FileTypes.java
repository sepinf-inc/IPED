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
package dpf.sp.gpinf.indexer.datasource.ftk;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.Configuration;

/*
 * Classe que contém as descrições das categorias do FTK4.2+, utilizadas em indexações de relatórios, as quais estão hard-coded.
 * Foram obtidas realizando alterações no banco de dados, setando a categoria de cada arquivo
 * idêntica ao seu id. Assim, ordenando os itens pelo id, obtemos a categoria correspondente a cada id.
 */
public class FTK42FileTypes {

  private static Map<Integer, String> mapTypeDesc;

  private static String CONFIG_FILE = "FTKCategoriesConfig.txt";

  private static Logger LOGGER = LoggerFactory.getLogger(FTK42FileTypes.class);

  private static void getFTK42FileTypes(String configPath) throws IOException {
    Properties properties = new Properties();
    File file = new File(configPath + "/conf/" + CONFIG_FILE);
    try {
      properties.load(new FileInputStream(file));
      mapTypeDesc = new HashMap<Integer, String>();
      for (int i = 0; i < 10000; i++) {
        String desc = properties.getProperty(String.valueOf(i));
        if (desc == null) {
          break;
        }
        mapTypeDesc.put(i, desc);
      }
    } catch (FileNotFoundException e) {
      LOGGER.error("Conf file not found: {}", file.getAbsolutePath());
      throw e;
    } catch (IOException e) {
      LOGGER.error("Error loading {}", file.getAbsolutePath());
      throw e;
    }

  }

  public static String getTypeDesc(int type) throws IOException {

    if (mapTypeDesc == null) {
    	getFTK42FileTypes(Configuration.configPath);
    }

    String desc = mapTypeDesc.get(type);
    if (desc == null) {
      desc = "Outros";
    }
    return desc;
  }

}
