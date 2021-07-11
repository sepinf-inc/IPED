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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.carver.CarverTask;
import dpf.sp.gpinf.indexer.WorkerProvider;
import dpf.sp.gpinf.indexer.config.CategoryToExpandConfig;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.datasource.ftk.FTKDatabase;
import dpf.sp.gpinf.indexer.localization.Messages;
import dpf.sp.gpinf.indexer.util.Util;
import iped3.ICaseData;

public class FTK3ReportReader extends DataSourceReader {

    private Set<Integer> ADList = new HashSet<Integer>();
    public static boolean wasExecuted = false;
    private static Logger LOGGER = LoggerFactory.getLogger(FTK3ReportReader.class);

    public FTK3ReportReader(ICaseData caseData, File output, boolean listOnly) {
        super(caseData, output, listOnly);
    }

    @Override
    public boolean isSupported(File source) {
        return (new File(source, "files")).exists() && bookmarkExists(source); //$NON-NLS-1$
    }

    public int read(File report) throws Exception {

        // Configuração para não expandir containers
        CategoryToExpandConfig expandConfig = ConfigurationManager.get().findObject(CategoryToExpandConfig.class);
        expandConfig.setEnabled(false);

        CarverTask.setEnabled(false);

        caseData.setContainsReport(true);
        wasExecuted = true;

        String relativePath = Util.getRelativePath(output, report);
        if (!relativePath.isEmpty()) {
            relativePath += "/"; //$NON-NLS-1$
        }

        int alternativeFiles = 0;
        if (listOnly) {
            lerListaDeArquivos(new File(report, "files")); //$NON-NLS-1$
        }

        if (!listOnly) {
            String caseName = getFTK3CaseName(report);

            WorkerProvider.getInstance().firePropertyChange("mensagem", "", //$NON-NLS-1$ //$NON-NLS-2$
                    Messages.getString("FTK3ReportReader.LoadDatabaseProps")); //$NON-NLS-1$
            LOGGER.info("Loading properties from database..."); //$NON-NLS-1$

            FTKDatabase ds = FTKDatabase.get(caseName, report);
            ds.getCaseData(caseData, new File(report, "files"), relativePath + "files", ADList); //$NON-NLS-1$ //$NON-NLS-2$
        }

        return alternativeFiles;
    }

    private int lerListaDeArquivos(File file) throws Exception {

        int alternativeFiles = 0;
        String[] names = file.list();
        if (names != null) {
            for (int i = 0; i < names.length; i++) {
                if (Thread.interrupted()) {
                    throw new InterruptedException(Thread.currentThread().getName() + " interrupted."); //$NON-NLS-1$
                }

                File subFile = new File(file, names[i]);
                if (subFile.isDirectory()) {
                    alternativeFiles += lerListaDeArquivos(subFile);
                } else {
                    // TODO contagem de descobertos e alternativos falha caso só
                    // haja versão de visualização do arquivo
                    // realizar contagem correta utilizará mais memória
                    /*
                     * if(names[i].contains(".[AD].")){ if(listOnly) alternativeFiles++; else try{
                     * int id = Integer.valueOf(names[i].substring(0, names[i].indexOf(".")));
                     * ADList.add(id); }catch(NumberFormatException e){ throw new
                     * NumberFormatException("ID do arquivo '" + subFile.getPath() +
                     * "' não identificado. Os arquivos foram exportados usando o ID como nomenclatura?"
                     * ); } }else if(listOnly){
                     */
                    caseData.incDiscoveredEvidences(1);
                    // IndexFiles.getInstance().firePropertyChange("discovered", 0,
                    // caseData.getDiscoveredEvidences());
                    caseData.incDiscoveredVolume(subFile.length());
                    // }

                }
            }
        }
        return alternativeFiles;
    }

    public static boolean bookmarkExists(File report) {
        boolean hasBookmark = false;
        for (String fileName : report.list()) {
            if (fileName.contains("Bookmark_bk_ID")) { //$NON-NLS-1$
                hasBookmark = true;
                break;
            }
        }
        return hasBookmark;
    }

    public static String getFTKVersion(File report) throws Exception {
        String version = ""; //$NON-NLS-1$
        if ((new File(report, "CaseInfo.html")).exists()) { //$NON-NLS-1$
            File file = new File(report, "CaseInfo.html"); //$NON-NLS-1$
            Reader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8")); //$NON-NLS-1$
            String contents = ""; //$NON-NLS-1$
            char[] buf = new char[(int) file.length()];
            int count;
            while ((count = reader.read(buf)) != -1) {
                contents += new String(buf, 0, count);
            }
            reader.close();

            String str = "Versão</td><td>Versão do AccessData Forensic Toolkit: "; //$NON-NLS-1$
            if (contents.contains(str)) {
                int start = contents.indexOf(str) + str.length();
                version = contents.substring(start, contents.indexOf("</td>", start)); //$NON-NLS-1$
            } else {
                throw new Exception("'Versão' not found on CaseInfo.html. Is the report on portuguese?"); //$NON-NLS-1$
            }
        } else {
            throw new FileNotFoundException(
                    "'CaseInfo.html' not found. Configure 'versaoFTK' on FTKDatabaseConfig.txt"); //$NON-NLS-1$
        }

        LOGGER.info("Detected report of FTK version {}", version); //$NON-NLS-1$
        return version;
    }

    public static HashSet<String> getBookmarks(File report) throws Exception {
        HashSet<String> bookmarks = new HashSet<String>();
        File file = new File(report, "Bookmarks.html"); //$NON-NLS-1$
        if (file.exists()) {
            Reader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8")); //$NON-NLS-1$
            String contents = ""; //$NON-NLS-1$
            char[] buf = new char[(int) file.length()];
            int count;
            while ((count = reader.read(buf)) != -1) {
                contents += new String(buf, 0, count);
            }
            reader.close();

            String str1 = ".html\"><span"; //$NON-NLS-1$
            String str2 = "</span>"; //$NON-NLS-1$
            int off = 0, idx1 = 0, idx2 = 0;
            while ((off = contents.indexOf(str1, idx2)) != -1) {
                idx1 = contents.indexOf(">", off + str1.length()) + 1; //$NON-NLS-1$
                idx2 = contents.indexOf(str2, idx1);
                String bookmark = contents.substring(idx1, idx2);
                bookmarks.add(bookmark);
            }

            if (bookmarks.size() == 0) {
                throw new Exception("Bookmarks not found in " + file.getAbsolutePath()); //$NON-NLS-1$
            } else {
                LOGGER.info("Detected {} bookmarks in report.", bookmarks.size()); //$NON-NLS-1$
            }

        } else {
            throw new FileNotFoundException(file.getName() + " not found!"); //$NON-NLS-1$
        }

        return bookmarks;
    }

    public String getFTK3CaseName(File report) throws Exception {
        File file = new File(report, "CaseInfo.html"); //$NON-NLS-1$
        if (file.exists() && bookmarkExists(report)) {
            Reader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8")); //$NON-NLS-1$
            String contents = ""; //$NON-NLS-1$
            char[] buf = new char[(int) file.length()];
            int count;
            while ((count = reader.read(buf)) != -1) {
                contents += new String(buf, 0, count);
            }

            reader.close();
            String str = "Nome do caso</td><td>"; //$NON-NLS-1$
            if (contents.contains(str)) {
                int start = contents.indexOf(str) + str.length();
                String caseName = contents.substring(start, contents.indexOf("</td>", start)); //$NON-NLS-1$
                LOGGER.info("Detected FTK case {}", caseName); //$NON-NLS-1$
                return caseName;
            } else {
                throw new Exception("Case name not found on CaseInfo.html. Is the report on portuguese?"); //$NON-NLS-1$
            }
        } else {
            throw new Exception("File not found: CaseInfo.html"); //$NON-NLS-1$
        }
    }

}
