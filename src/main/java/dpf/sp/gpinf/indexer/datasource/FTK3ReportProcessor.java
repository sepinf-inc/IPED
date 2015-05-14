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
package dpf.sp.gpinf.indexer.datasource;

import gpinf.dev.data.CaseData;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import dpf.sp.gpinf.indexer.IndexFiles;
import dpf.sp.gpinf.indexer.database.DataSource;
import dpf.sp.gpinf.indexer.util.Util;

public class FTK3ReportProcessor {
	
	private CaseData caseData;
	private boolean listOnly;
	private File baseFile;
	private Set<Integer> ADList = new HashSet<Integer>();
	public static boolean wasInstantiated = false;

	public FTK3ReportProcessor(CaseData caseData, File basePath, boolean listOnly) {
		this.caseData = caseData;
		this.listOnly = listOnly;
		this.baseFile = basePath;
		wasInstantiated = true;
	}

	public int process(File report, String caseName) throws Exception {
		
		caseData.setContainsReport(true);
		
		String relativePath = Util.getRelativePath(baseFile, report);
		if (!relativePath.isEmpty())
			relativePath += "/";

		int alternativeFiles = 0;
		if (listOnly)
			lerListaDeArquivos(new File(report, "files"));

		if (!listOnly) {
			IndexFiles.getInstance().firePropertyChange("mensagem", "", "Obtendo  propriedades do banco...");
			System.out.println(new Date() + "\t[INFO]\t" + "Obtendo propriedades do banco...");

			DataSource ds = DataSource.get(caseName, report);
			ds.getCaseData(caseData, new File(report, "files"), relativePath + "files", ADList);
		}

		return alternativeFiles;
	}

	private int lerListaDeArquivos(File file) throws Exception {

		int alternativeFiles = 0;
		String[] names = file.list();
		if (names != null)
			for (int i = 0; i < names.length; i++) {
				if (Thread.interrupted())
					throw new InterruptedException(Thread.currentThread().getName() + "interrompida.");

				File subFile = new File(file, names[i]);
				if (subFile.isDirectory())
					alternativeFiles += lerListaDeArquivos(subFile);
				else {
					// TODO contagem de descobertos e alternativos falha caso só
					// haja versão de visualização do arquivo
					// realizar contagem correta utilizará mais memória
					/*
					 * if(names[i].contains(".[AD].")){ if(listOnly)
					 * alternativeFiles++; else try{ int id =
					 * Integer.valueOf(names[i].substring(0,
					 * names[i].indexOf("."))); ADList.add(id);
					 * }catch(NumberFormatException e){ throw new
					 * NumberFormatException("ID do arquivo '" +
					 * subFile.getPath() +
					 * "' não identificado. Os arquivos foram exportados usando o ID como nomenclatura?"
					 * ); } }else if(listOnly){
					 */
					caseData.incDiscoveredEvidences(1);
					//IndexFiles.getInstance().firePropertyChange("discovered", 0, caseData.getDiscoveredEvidences());
					caseData.incDiscoveredVolume(subFile.length());
					// }

				}
			}
		return alternativeFiles;
	}

	public static boolean bookmarkExists(File report) {
		boolean hasBookmark = false;
		for (String fileName : report.list()) {
			if (fileName.contains("Bookmark_bk_ID")) {
				hasBookmark = true;
				break;
			}
		}
		return hasBookmark;
	}

	public static String getFTKVersion(File report) throws Exception {
		String version = "";
		if ((new File(report, "CaseInfo.html")).exists()) {
			File file = new File(report, "CaseInfo.html");
			Reader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
			String contents = "";
			char[] buf = new char[(int) file.length()];
			int count;
			while ((count = reader.read(buf)) != -1)
				contents += new String(buf, 0, count);
			reader.close();

			String str = "Versão</td><td>Versão do AccessData Forensic Toolkit: ";
			if (contents.contains(str)) {
				int start = contents.indexOf(str) + str.length();
				version = contents.substring(start, contents.indexOf("</td>", start));
			} else
				throw new Exception("'Versão' não encontrada em CaseInfo.html. O relatório está em português?");
		} else
			throw new FileNotFoundException("'CaseInfo.html' não encontrado. Defina manualmente 'versaoFTK' nas configurações.");

		System.out.println(new Date() + "\t[INFO]\t" + "Detectado relatório gerado pelo FTK " + version);
		return version;
	}

	public static HashSet<String> getBookmarks(File report) throws Exception {
		HashSet<String> bookmarks = new HashSet<String>();
		File file = new File(report, "Bookmarks.html");
		if (file.exists()) {
			Reader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
			String contents = "";
			char[] buf = new char[(int) file.length()];
			int count;
			while ((count = reader.read(buf)) != -1)
				contents += new String(buf, 0, count);
			reader.close();

			String str1 = ".html\"><span";
			String str2 = "</span>";
			int off = 0, idx1 = 0, idx2 = 0;
			while ((off = contents.indexOf(str1, idx2)) != -1) {
				idx1 = contents.indexOf(">", off + str1.length()) + 1;
				idx2 = contents.indexOf(str2, idx1);
				String bookmark = contents.substring(idx1, idx2);
				bookmarks.add(bookmark);
			}

			if (bookmarks.size() == 0)
				throw new Exception("Bookmarks não encontrados em " + file.getAbsolutePath());
			else
				System.out.println(new Date() + "\t[INFO]\t" + "Detectados " + bookmarks.size() + " bookmarks no relatório.");

		} else
			throw new FileNotFoundException(file.getName() + " não encontrado!");

		return bookmarks;
	}

	public static List<String> getFTK3CaseNames(List<File> reports) throws Exception {
		List<String> caseNames = new ArrayList<String>();
		for (File report : reports) {
			if ((new File(report, "CaseInfo.html")).exists() && bookmarkExists(report)) {
				File file = new File(report, "CaseInfo.html");
				Reader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
				String contents = "";
				char[] buf = new char[(int) file.length()];
				int count;
				while ((count = reader.read(buf)) != -1)
					contents += new String(buf, 0, count);

				reader.close();
				String str = "Nome do caso</td><td>";
				if (contents.contains(str)) {
					int start = contents.indexOf(str) + str.length();
					String caseName = contents.substring(start, contents.indexOf("</td>", start));
					caseNames.add(caseName);
					System.out.println(new Date() + "\t[INFO]\t" + "Detectado caso " + caseName);
				} else
					throw new Exception("Nome do caso não encontrado em CaseInfo.html. O relatório está em português?");
			}
		}
		return caseNames;
	}

}
