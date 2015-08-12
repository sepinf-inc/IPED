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

import gpinf.dev.data.CaseData;
import gpinf.dev.data.EvidenceFile;
import gpinf.dev.filetypes.GenericFileType;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;

import oracle.jdbc.pool.OracleDataSource;
import dpf.sp.gpinf.indexer.datasource.FTK3ReportReader;
import dpf.sp.gpinf.indexer.process.task.HashTask;
import dpf.sp.gpinf.indexer.util.NtfsTimeConverter;

/*
 * Classe que obtém informações do banco de dados do FTK3 até o FTK4.1.
 */
public class FTK3Database extends FTKDatabase {
	private String tableSpace;
	private String tableSpaceBase;

	protected FTK3Database(Properties properties, String caseName, File report) throws SQLException {

		super(properties, caseName, report);

		tableSpaceBase = "FTK_" + schemaVersion;

		if ("oracle".equalsIgnoreCase(databaseType)) {
			OracleDataSource oSource = new OracleDataSource();
			oSource.setUser(user);
			oSource.setPassword(password);
			oSource.setDriverType(driverType);
			oSource.setServiceName(serviceName);
			oSource.setServerName(serverName);
			oSource.setPortNumber(portNumber);

			ods = oSource;
		} else if ("postgreSQL".equalsIgnoreCase(databaseType)) {
			org.postgresql.ds.PGSimpleDataSource oSource = new org.postgresql.ds.PGSimpleDataSource();
			oSource.setUser(user);
			oSource.setPassword(password);
			oSource.setServerName(serverName);
			oSource.setPortNumber(portNumber);
			oSource.setDatabaseName(serviceName);

			ods = oSource;

		} else if ("sqlserver".equalsIgnoreCase(databaseType)) {
			throw new SQLException("Banco 'sqlserver' incompatível com versão do FTK configurada.");
		}

	}

	@Override
	protected void loadTableSpace() throws SQLException {

		tableSpace = tableSpaceBase + "_C" + getCaseID(conn);

	}

	private int getCaseID(Connection conn) throws SQLException {

		int caseID;
		ResultSet rset;
		Statement stmt = conn.createStatement();
		String sql = "select CASEID from " + tableSpaceBase + ".CASES where CASENAME='" + caso + "'";

		try {
			rset = stmt.executeQuery(sql);
		} catch (SQLException e) {
			throw new SQLException("Versão detectada/configurada do FTK pode estar incorreta!");
		}

		// Se não retornou nada no ResultSet, sai
		if (!rset.next())
			throw new SQLException("Nome do caso não encontrado no banco: " + sql + ". Conectou-se ao banco correto?");

		caseID = rset.getInt(1);

		if (rset.next())
			throw new SQLException("Nome do caso duplicado no banco. Remova o caso com mesmo nome que o atual.");

		// Close the RseultSet
		rset.close();
		rset = null;

		// Close the Statement
		stmt.close();
		stmt = null;

		return caseID;
	}

	private Map<Integer, ArrayList<String>> getFileToBookmarksMap(String objectIDs) throws Exception {

		Statement stmt = conn.createStatement();
		String sql = "select a.OBJECTID, a.BOOKMARK_ID from " + tableSpace + ".BOOKMARK_FILES a where a.OBJECTID in (" + objectIDs + ") AND a.DELETED=0";
		ResultSet rset = stmt.executeQuery(sql);
		rset.setFetchSize(1000);

		HashMap<Integer, ArrayList<String>> result = new HashMap<Integer, ArrayList<String>>();

		while (rset.next()) {
			int fileId = rset.getInt("OBJECTID");
			ArrayList<String> bookmarkNames = result.get(fileId);
			if (bookmarkNames == null)
				bookmarkNames = new ArrayList<String>();
			String bookmark = bookmarksMap.get(rset.getString("BOOKMARK_ID"));
			if (bookmark != null)
				bookmarkNames.add(bookmark);
			result.put(fileId, bookmarkNames);
		}

		return result;
	}

	@Override
	protected void addFileListToCaseData(CaseData caseData, Map<Integer, ArrayList<String>> fileList) throws Exception {
		StringBuffer fileIds = new StringBuffer();
		int i = 1;
		for (Integer ID : fileList.keySet()) {
			fileIds.append(ID);
			if (i++ < fileList.size())
				fileIds.append(",");
		}
		String objectIDs = fileIds.toString();

		// obtém nomes dos bookmarks de cada item
		Map<Integer, ArrayList<String>> fileToBookmarkMap = getFileToBookmarksMap(objectIDs);

		// Create a Statement
		Statement stmt = conn.createStatement();
		String sql = "select a.objectid, a.parentid, a.md5hash, a.deleted, a.isfromfreespace, a.objectname, b.name, a.path, a.logicalsize, a.createddate, a.modifieddate, a.last_accesseddate, a.fatlastaccesseddateasstring from "
				+ tableSpace + ".OBJECTS a,  " + tableSpaceBase + ".CATEGORY_NAMES b where a.filecategory=b.value and a.objectid in (" + objectIDs + ")";
		ResultSet rset = stmt.executeQuery(sql);
		rset.setFetchSize(1000);

		int addedEvidences = 0;
		while (rset.next()) {
			ArrayList<String> paths = fileList.get(rset.getInt("OBJECTID"));
			for (String path : paths) {
				EvidenceFile evidenceFile = new EvidenceFile();
				int ftkId = rset.getInt("OBJECTID");
				evidenceFile.setFtkID(caso + "-" + ftkId);
				int parentId = rset.getInt("PARENTID");
				evidenceFile.setParentId(caso + "-" + parentId);
				evidenceFile.setName(rset.getString("OBJECTNAME"));
				evidenceFile.setExportedFile(path);
				evidenceFile.setPath(rset.getString("PATH"));
				if("Y".equals(rset.getString("DELETED")) || "Y".equals(rset.getString("ISFROMFREESPACE")))
					evidenceFile.setDeleted(true);
				byte[] hash = rset.getBytes("md5hash");
				if (hash != null)
					evidenceFile.setHash(HashTask.getHashString(hash));
				long logicalSize = rset.getLong("LOGICALSIZE");
				if (logicalSize > -1)
					evidenceFile.setLength(logicalSize);
				String fileType = rset.getString("NAME");
				if (fileType != null)
					evidenceFile.setType(new GenericFileType(fileType));
				long createdDate = rset.getLong("CREATEDDATE");
				if (createdDate > 0)
					evidenceFile.setCreationDate(NtfsTimeConverter.fileTimeToDate(createdDate));
				long modifiedDate = rset.getLong("modifieddate");
				if (modifiedDate > 0)
					evidenceFile.setModificationDate(NtfsTimeConverter.fileTimeToDate(modifiedDate));
				long accessedDate = rset.getLong("last_accesseddate");
				if (accessedDate > 0)
					evidenceFile.setAccessDate(NtfsTimeConverter.fileTimeToDate(accessedDate));
				else {
					String fatDate = rset.getString("fatlastaccesseddateasstring");
					if (fatDate != null) {
						Calendar calendar = new GregorianCalendar();
						calendar.clear();
						calendar.set(Integer.parseInt(fatDate.substring(0, 4)), Integer.parseInt(fatDate.substring(5, 7)) - 1, Integer.parseInt(fatDate.substring(8, 10)));
						evidenceFile.setAccessDate(calendar.getTime());
					}
				}

				ArrayList<String> bookmarks = fileToBookmarkMap.get(rset.getInt("OBJECTID"));
				if (bookmarks != null)
					for (String bookmarkName : bookmarks)
						evidenceFile.addCategory(bookmarkName);

				caseData.addEvidenceFile(evidenceFile);
			}
			addedEvidences++;
		}
		rset.close();
		stmt.close();

		if (fileList.size() != addedEvidences)
			throw new Exception("Encontrados " + addedEvidences + " de " + fileList.size() + " ID's dos arquivos no banco. O nome do caso pode estar incorreto!");
	}

	@Override
	protected Map<String, String> getBookmarksMap(File report) throws Exception {

		HashSet<String> bookmarks = FTK3ReportReader.getBookmarks(report);
		Connection conn = ods.getConnection();
		HashMap<String, String> result = new HashMap<String, String>();

		Statement stmt = conn.createStatement();
		;
		String sql;
		ResultSet rset;

		sql = "select a.BOOKMARK_ID, a.BOOKMARK_NAME from " + tableSpace + ".BOOKMARKS a";

		rset = stmt.executeQuery(sql);
		rset.setFetchSize(1000);

		while (rset.next()) {
			String bookName = rset.getString(2);
			if (bookmarks.contains(bookName))
				result.put(rset.getString(1), bookName);
		}

		// Close the ResultSet
		rset.close();
		rset = null;

		// Close the Statement
		stmt.close();
		stmt = null;

		conn.close();
		return result;
	}

}
