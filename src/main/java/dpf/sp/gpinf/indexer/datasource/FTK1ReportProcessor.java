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
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gpinf.dev.data.CaseData;
import gpinf.dev.data.EvidenceFile;
import gpinf.dev.data.FileGroup;
import gpinf.dev.preprocessor.AsapReportParser;

public class FTK1ReportProcessor extends DataSourceProcessor{

	private static Logger LOGGER = LoggerFactory.getLogger(FTK1ReportProcessor.class);
	
	public FTK1ReportProcessor(CaseData caseData, File output, boolean listOnly) {
		super(caseData, output, listOnly);
	}
	
	public int process(File report) throws Exception {

		caseData.setContainsReport(true);
		AsapReportParser parser = new AsapReportParser(listOnly);

		// caseData temporário para armazenar toda lista devido a arquivos em
		// mais de 1 bookmark
		CaseData tempData = new CaseData(Integer.MAX_VALUE);
		parser.parseReport(tempData, report);

		int alternativeFiles = 0;
		if (listOnly) {
			caseData.incDiscoveredEvidences(tempData.getDiscoveredEvidences());
			caseData.incDiscoveredVolume(tempData.getDiscoveredVolume());
		} else {
			for (EvidenceFile evidence : tempData.getEvidenceFiles()) {
				if (evidence.getAlternativeFile() != null && !evidence.getExportedFile().equals(evidence.getAlternativeFile()))
					alternativeFiles++;
			}
			while (tempData.getEvidenceFiles().size() > 0)
				caseData.addEvidenceFile(tempData.getEvidenceFiles().take());
			for (FileGroup bookmark : tempData.getBookmarks())
				caseData.addBookmark(bookmark);
		}

		return alternativeFiles;
	}

	public static void criarLinkBusca(File output) throws Exception {
		File file = new File(output.getParentFile(), "contents.htm");
		if (!file.exists())
			return;
		
		Reader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "windows-1252"));
		String contents = "";
		char[] buf = new char[(int) file.length()];
		int count;
		while ((count = reader.read(buf)) != -1)
			contents += new String(buf, 0, count);

		reader.close();
		if (!contents.contains("Busca por Palavras-chave")) {
			contents = contents.replaceFirst("<p><b><a href=\"Ajuda.htm\"",
					"<p><b><a href=\"indexador/htm/Search.htm\" class=\"SmallText2\" target=\"ReportPage\">Busca por Palavras-chave</a></b></p>\r\n<p><b><a href=\"Ajuda.htm\"");
			BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "windows-1252"));
			writer.write(contents);
			writer.close();
		}

	}

	@Override
	public boolean isSupported(File source) {
		return (new File(source, "Export")).exists() && new File(source, "CaseInformation.htm").exists();
	}

}
