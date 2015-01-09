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
package dpf.sp.gpinf.indexer.index;

import gpinf.dev.data.CaseData;

import java.io.File;
import java.util.Date;
import java.util.List;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.IndexFiles;
import dpf.sp.gpinf.indexer.datasource.FTK1ReportProcessor;
import dpf.sp.gpinf.indexer.datasource.FTK3ReportProcessor;
import dpf.sp.gpinf.indexer.datasource.FolderTreeProcessor;
import dpf.sp.gpinf.indexer.datasource.IndexerProcessor;
import dpf.sp.gpinf.indexer.datasource.SleuthkitProcessor;

/*
 * Responsável por instanciar e chamar o produtor específico do caso.
 */
class ItemProducer implements Runnable {
	
	public static volatile boolean indexerReport = false;
	public static volatile Object sleuthkitLock = new Object(); 

	private final CaseData caseData;
	private final boolean listOnly;
	private List<File> reports;
	private List<String> caseNames;
	private File output;
	private IndexManager manager;

	ItemProducer(IndexManager manager, CaseData caseData, boolean listOnly, List<File> reports, List<String> caseNames, File output) {
		this.caseData = caseData;
		this.listOnly = listOnly;
		this.caseNames = caseNames;
		this.reports = reports;
		this.output = output;
		this.manager = manager;
		if(listOnly)
			indexerReport = false;
	}

	@Override
	public void run() {
		try {
			int caseNameIndex = 0;
			for (File report : reports) {
				if (Thread.interrupted())
					throw new InterruptedException(Thread.currentThread().getName() + "interrompida.");

				if (!listOnly) {
					IndexFiles.getInstance().firePropertyChange("mensagem", 0, "Processando '" + report.getAbsolutePath() + "'");
					System.out.println(new Date() + "\t[INFO]\t" + "Processando '" + report.getAbsolutePath() + "'");
				}

				int alternativeFiles = 0;
				if ((new File(report, "files")).exists() && FTK3ReportProcessor.bookmarkExists(report)) {
					FTK3ReportProcessor processor = new FTK3ReportProcessor(caseData, Configuration.properties, output, listOnly);
					alternativeFiles += processor.process(report, caseNames.get(caseNameIndex++));

				} else if ((new File(report, "Export")).exists() && new File(report, "CaseInformation.htm").exists()) {
					FTK1ReportProcessor processor = new FTK1ReportProcessor(caseData, listOnly);
					alternativeFiles += processor.process(report);

				} else if (SleuthkitProcessor.isSupported(report)) {
					SleuthkitProcessor processor = new SleuthkitProcessor(caseData, output, listOnly);
					processor.process(report);

				} else if (IndexerProcessor.isSupported(report)) {
					indexerReport = true;
					IndexerProcessor processor = new IndexerProcessor(caseData, output, listOnly);
					processor.process(report);

				} else {
					FolderTreeProcessor processor = new FolderTreeProcessor(caseData, output, listOnly);
					processor.process(report);

				}

				caseData.incAlternativeFiles(alternativeFiles);
			}
			if (!listOnly) {
				/*
				 * EvidenceFile evidence = new EvidenceFile();
				 * evidence.isQueueEnd = true;
				 * caseData.addEvidenceFile(evidence);
				 */
			} else {
				IndexFiles.getInstance().firePropertyChange("taskSize", 0, (int)(caseData.getDiscoveredVolume()/1000000));
				System.out.println(new Date() + "\t[INFO]\t" + "Localizados " + caseData.getDiscoveredEvidences() + " itens");
			}

		} catch (Exception e) {
			if (manager.exception == null)
				manager.exception = e;
		}

	}
}
