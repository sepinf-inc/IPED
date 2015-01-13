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
package dpf.sp.gpinf.indexer.database;

import gpinf.dev.data.CaseData;
import gpinf.dev.data.FileGroup;

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
import dpf.sp.gpinf.indexer.datasource.FTK3ReportProcessor;

/*
 * Classe abstrata que representa uma conexão ao banco  de dados do FTK3+.
 * Nome não pode ser alterado, pois é utilizada pelo ASAP para testar a conexão ao banco.
 */
public abstract class DataSource {
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

	static String schemaVersion = "";
	File report;

	/*
	 * Objeto para conexÃ£o com o banco
	 */
	javax.sql.DataSource ods;

	Connection conn;
	Map<String, String> bookmarksMap;

	public static DataSource get(Properties properties, String caseName, File report) throws Exception {

		schemaVersion = properties.getProperty("VersaoFTK");

		if (schemaVersion.equalsIgnoreCase("auto")) {
			String version = FTK3ReportProcessor.getFTKVersion(report);
			schemaVersion = translateFTKToDBVersion(version);
			if (schemaVersion == null)
				throw new Exception("Nova Versão do FTK detectada. Defina manualmente a versão do schema de dados em 'VersaoFTK' no IndexerConfig.txt.");
		}

		if (schemaVersion.compareTo("42") > 0)
			return new FTK42Database(properties, caseName, report);
		else
			return new FTK3Database(properties, caseName, report);

	}

	public static boolean testConnection(String configPathStr) throws SQLException, FileNotFoundException, IOException {

		Properties props = new Properties();
		props.load(new FileInputStream(new File(configPathStr + "/" + Configuration.CONFIG_FILE)));
		schemaVersion = props.getProperty("VersaoFTK");
		DataSource dataSrc;
		if (schemaVersion.equalsIgnoreCase("auto")) {
			dataSrc = new FTK42Database(props, "", null);
			Connection conn = dataSrc.ods.getConnection();
			conn.close();
			return true;
		} else {
			if (schemaVersion.compareTo("42") > 0)
				dataSrc = new FTK42Database(props, "", null);
			else
				dataSrc = new FTK3Database(props, "", null);

			dataSrc.conn = dataSrc.ods.getConnection();
			try {
				dataSrc.loadTableSpace();
				dataSrc.conn.close();
				return false;

			} catch (SQLException e) {
				dataSrc.conn.close();
				if (e.toString().contains("Nome do caso"))
					return true;
				else
					throw e;
			}

		}

	}

	protected DataSource(Properties properties, String caseName, File report) {
		this.report = report;
		user = properties.getProperty("User");
		password = properties.getProperty("Password");
		driverType = properties.getProperty("DriverType");
		serviceName = properties.getProperty("ServiceName");
		serverName = properties.getProperty("ServerName");
		portNumber = Integer.parseInt(properties.getProperty("PortNumber"));
		databaseType = properties.getProperty("DatabaseType");
		caso = caseName;
	}

	private static String translateFTKToDBVersion(String ftkVersion) {
		if (ftkVersion.startsWith("5.6"))
			return "ADG510";
		if (ftkVersion.startsWith("5.1"))
			return "ADG55";
		if (ftkVersion.startsWith("5.0"))
			return "ADG54";
		if (ftkVersion.startsWith("4.2"))
			return "ADG53";
		if (ftkVersion.startsWith("4.1"))
			return "42";
		if (ftkVersion.startsWith("4.0"))
			return "41";
		if (ftkVersion.startsWith("3.4"))
			return "40";
		if (ftkVersion.startsWith("3.3"))
			return "33";
		if (ftkVersion.startsWith("3.2"))
			return "32";
		if (ftkVersion.startsWith("3.1"))
			return "31";
		if (ftkVersion.startsWith("30"))
			return "30";

		return null;
	}

	public void getCaseData(CaseData caseData, File file, String path, Set<Integer> ADList) throws Exception {

		conn = ods.getConnection();

		loadTableSpace();
		bookmarksMap = getBookmarksMap(report);
		for (String bookmark : bookmarksMap.values())
			caseData.addBookmark(new FileGroup(bookmark, "", ""));

		HashMap<Integer, ArrayList<String>> fileList = new HashMap<Integer, ArrayList<String>>();
		lerListaDeArquivos(caseData, path, file, fileList, ADList);
		if (fileList.size() > 0)
			addFileListToCaseData(caseData, fileList);

		conn.close();

	}

	private void lerListaDeArquivos(CaseData caseData, String path, File file, Map<Integer, ArrayList<String>> fileList, Set<Integer> ADList) throws Exception {
		String[] names = file.list();
		if (names != null)
			for (int k = 0; k < names.length; k++) {
				if (Thread.interrupted())
					throw new InterruptedException(Thread.currentThread().getName() + "interrompida.");

				File subFile = new File(file, names[k]);
				if (subFile.isDirectory())
					lerListaDeArquivos(caseData, path + "/" + names[k], subFile, fileList, ADList);
				else {
					int id;
					try {
						if (names[k].contains("."))
							id = Integer.valueOf(names[k].substring(0, names[k].indexOf(".")));
						else
							id = Integer.valueOf(names[k]);
					} catch (NumberFormatException e) {
						throw new NumberFormatException("ID do arquivo '" + path + "/" + names[k] + "' não identificado. Os arquivos foram exportados usando o ID como nomenclatura?");
					}
					if (names[k].contains("[AD]") || !ADList.contains(id)) {
						ArrayList<String> paths = fileList.get(id);
						if (paths == null)
							paths = new ArrayList<String>();

						paths.add(path + "/" + names[k]);
						fileList.put(id, paths);

						if (fileList.size() % 1000 == 0) {
							addFileListToCaseData(caseData, fileList);
							fileList.clear();
						}
					}
				}
			}

	}

	abstract protected Map<String, String> getBookmarksMap(File report) throws Exception;

	abstract protected void addFileListToCaseData(CaseData caseData, Map<Integer, ArrayList<String>> fileList) throws Exception;

	abstract protected void loadTableSpace() throws SQLException;
}
