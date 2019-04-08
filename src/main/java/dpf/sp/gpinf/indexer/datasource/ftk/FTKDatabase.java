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
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.datasource.FTK3ReportReader;
import gpinf.dev.data.DataSourceImpl;
import gpinf.dev.data.FileGroupImpl;
import iped3.CaseData;
import iped3.datasource.DataSource;

/*
 * Classe abstrata que representa uma conexão ao banco  de dados do FTK3+.
 * Nome não pode ser alterado, pois é utilizada pelo ASAP para testar a conexão ao banco.
 */
public abstract class FTKDatabase {

  private static String FTKDatabaseConfig = "/conf/FTKDatabaseConfig.txt"; //$NON-NLS-1$
  /*
   * Dados para conexÃ£o com o banco Oracle
   */
  String user;
  String password;
  String driverType;
  String serviceName;
  String serverName;
  int portNumber;
  String caso;
  String databaseType;

  static String schemaVersion = ""; //$NON-NLS-1$
  File report;

  /*
   * Objeto para conexÃ£o com o banco
   */
  javax.sql.DataSource ods;
  
  DataSource ipedDataSource;

  Connection conn;
  Map<String, String> bookmarksMap;

  public static FTKDatabase get(String caseName, File report) throws Exception {

    Properties properties = new Properties();
    properties.load(new FileInputStream(Configuration.getInstance().appRoot + FTKDatabaseConfig));
    schemaVersion = properties.getProperty("VersaoFTK"); //$NON-NLS-1$

    if (schemaVersion.equalsIgnoreCase("auto")) { //$NON-NLS-1$
      String version = FTK3ReportReader.getFTKVersion(report);
      schemaVersion = translateFTKToDBVersion(version);
      if (schemaVersion == null) {
        throw new Exception("New FTK version detected. Configure the database schema 'VersaoFTK' on " + FTKDatabaseConfig); //$NON-NLS-1$
      }
    }

    if (schemaVersion.compareTo("42") > 0) { //$NON-NLS-1$
      return new FTK42Database(properties, caseName, report);
    } else {
      return new FTK3Database(properties, caseName, report);
    }

  }

  public static boolean testConnection(String configPathStr) throws SQLException, FileNotFoundException, IOException {

    Properties props = new Properties();
    props.load(new FileInputStream(configPathStr + FTKDatabaseConfig));
    schemaVersion = props.getProperty("VersaoFTK"); //$NON-NLS-1$
    FTKDatabase dataSrc;
    if (schemaVersion.equalsIgnoreCase("auto")) { //$NON-NLS-1$
      dataSrc = new FTK42Database(props, "", null); //$NON-NLS-1$
      Connection conn = dataSrc.ods.getConnection();
      conn.close();
      return true;
    } else {
      if (schemaVersion.compareTo("42") > 0) { //$NON-NLS-1$
        dataSrc = new FTK42Database(props, "", null); //$NON-NLS-1$
      } else {
        dataSrc = new FTK3Database(props, "", null); //$NON-NLS-1$
      }

      dataSrc.conn = dataSrc.ods.getConnection();
      try {
        dataSrc.loadTableSpace();
        dataSrc.conn.close();
        return false;

      } catch (SQLException e) {
        dataSrc.conn.close();
        if (e instanceof CaseNameException) {
          return true;
        } else {
          throw e;
        }
      }

    }

  }

  protected FTKDatabase(Properties properties, String caseName, File report) {
    this.report = report;
    user = properties.getProperty("User"); //$NON-NLS-1$
    password = properties.getProperty("Password"); //$NON-NLS-1$
    driverType = properties.getProperty("DriverType"); //$NON-NLS-1$
    serviceName = properties.getProperty("ServiceName"); //$NON-NLS-1$
    serverName = properties.getProperty("ServerName"); //$NON-NLS-1$
    portNumber = Integer.parseInt(properties.getProperty("PortNumber")); //$NON-NLS-1$
    databaseType = properties.getProperty("DatabaseType"); //$NON-NLS-1$
    caso = caseName;
  }

  private static String translateFTKToDBVersion(String ftkVersion) {
    if (ftkVersion.startsWith("6.0")) { //$NON-NLS-1$
      return "ADG6"; //$NON-NLS-1$
    }
    if (ftkVersion.startsWith("5.6")) { //$NON-NLS-1$
      return "ADG510"; //$NON-NLS-1$
    }
    if (ftkVersion.startsWith("5.1")) { //$NON-NLS-1$
      return "ADG55"; //$NON-NLS-1$
    }
    if (ftkVersion.startsWith("5.0")) { //$NON-NLS-1$
      return "ADG54"; //$NON-NLS-1$
    }
    if (ftkVersion.startsWith("4.2")) { //$NON-NLS-1$
      return "ADG53"; //$NON-NLS-1$
    }
    if (ftkVersion.startsWith("4.1")) { //$NON-NLS-1$
      return "42"; //$NON-NLS-1$
    }
    if (ftkVersion.startsWith("4.0")) { //$NON-NLS-1$
      return "41"; //$NON-NLS-1$
    }
    if (ftkVersion.startsWith("3.4")) { //$NON-NLS-1$
      return "40"; //$NON-NLS-1$
    }
    if (ftkVersion.startsWith("3.3")) { //$NON-NLS-1$
      return "33"; //$NON-NLS-1$
    }
    if (ftkVersion.startsWith("3.2")) { //$NON-NLS-1$
      return "32"; //$NON-NLS-1$
    }
    if (ftkVersion.startsWith("3.1")) { //$NON-NLS-1$
      return "31"; //$NON-NLS-1$
    }
    if (ftkVersion.startsWith("30")) { //$NON-NLS-1$
      return "30"; //$NON-NLS-1$
    }

    return null;
  }

  public void getCaseData(CaseData caseData, File file, String path, Set<Integer> ADList) throws Exception {

	ipedDataSource = new DataSourceImpl(file);
	
    conn = ods.getConnection();

    loadTableSpace();
    bookmarksMap = getBookmarksMap(report);
    for (String bookmark : bookmarksMap.values()) {
      caseData.addBookmark(new FileGroupImpl(bookmark, "", "")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    HashMap<Integer, ArrayList<String>> fileList = new HashMap<Integer, ArrayList<String>>();
    lerListaDeArquivos(caseData, path, file, fileList, ADList);
    if (fileList.size() > 0) {
      addFileListToCaseData(caseData, fileList);
    }

    conn.close();

  }

  private void lerListaDeArquivos(CaseData caseData, String path, File file, Map<Integer, ArrayList<String>> fileList, Set<Integer> ADList) throws Exception {
    String[] names = file.list();
    if (names != null) {
      for (int k = 0; k < names.length; k++) {
        if (Thread.interrupted()) {
          throw new InterruptedException(Thread.currentThread().getName() + " interrupted."); //$NON-NLS-1$
        }

        File subFile = new File(file, names[k]);
        if (subFile.isDirectory()) {
          lerListaDeArquivos(caseData, path + "/" + names[k], subFile, fileList, ADList); //$NON-NLS-1$
        } else {
          int id;
          try {
            if (names[k].contains(".")) { //$NON-NLS-1$
              id = Integer.valueOf(names[k].substring(0, names[k].indexOf("."))); //$NON-NLS-1$
            } else {
              id = Integer.valueOf(names[k]);
            }
          } catch (NumberFormatException e) {
            throw new NumberFormatException("File ID '" + path + "/" + names[k] + "' not detected. The files were exported using their IDs as filename?"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
          }
          if (names[k].contains("[AD]") || !ADList.contains(id)) { //$NON-NLS-1$
            ArrayList<String> paths = fileList.get(id);
            if (paths == null) {
              paths = new ArrayList<String>();
            }

            paths.add(path + "/" + names[k]); //$NON-NLS-1$
            fileList.put(id, paths);

            if (fileList.size() % 1000 == 0) {
              addFileListToCaseData(caseData, fileList);
              fileList.clear();
            }
          }
        }
      }
    }

  }

  abstract protected Map<String, String> getBookmarksMap(File report) throws Exception;

  abstract protected void addFileListToCaseData(CaseData caseData, Map<Integer, ArrayList<String>> fileList) throws Exception;

  abstract protected void loadTableSpace() throws SQLException;
}
