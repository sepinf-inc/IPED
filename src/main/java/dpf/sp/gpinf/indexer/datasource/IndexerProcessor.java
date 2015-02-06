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
import gpinf.dev.data.EvidenceFile;
import gpinf.dev.filetypes.UnknownFileType;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.HashSet;

import org.apache.lucene.document.Document;
import org.apache.tika.mime.MediaType;
import org.sleuthkit.datamodel.SleuthkitCase;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.analysis.CategoryTokenizer;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.process.task.CarveTask;
import dpf.sp.gpinf.indexer.process.task.ExpandContainerTask;
import dpf.sp.gpinf.indexer.search.App;
import dpf.sp.gpinf.indexer.search.InicializarBusca;
import dpf.sp.gpinf.indexer.search.Marcadores;
import dpf.sp.gpinf.indexer.search.PesquisarIndice;
import dpf.sp.gpinf.indexer.search.SearchResult;
import dpf.sp.gpinf.indexer.util.IOUtil;

/*
 * Enfileira para processamento os arquivos selecionados via interface de pesquisa de uma indexação anterior.
 */
public class IndexerProcessor {

	private static Object lock = new Object();
	//private static volatile int resultLen;

	private CaseData caseData;
	private File output;
	private boolean listOnly;
	private SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
	
	//Referência estática para a JVM não finalizar o objeto que será usado futuramente
	//via referência interna ao JNI para acessar os itens do caso
	static SleuthkitCase sleuthCase;

	public IndexerProcessor(CaseData caseData, File output, boolean listOnly) {
		this.caseData = caseData;
		this.listOnly = listOnly;
		this.output = output;
	}

	public static boolean isSupported(File report) {
		String name = report.getName().toLowerCase();
		return name.endsWith(Marcadores.EXT);
	}

	public void process(File file) throws Exception {

		caseData.setContainsReport(true);
		
		// Configuração para não expandir containers
		ExpandContainerTask.expandContainers = false;
		CarveTask.enableCarving = false;

		Marcadores state = (Marcadores) IOUtil.readObject(file.getAbsolutePath());
		File indexDir = state.getIndexDir().getCanonicalFile();
		String basePath = indexDir.getParentFile().getParentFile().getAbsolutePath();
		String dbPath = basePath + File.separator + SleuthkitProcessor.DB_NAME;
		
		synchronized (lock) {
			if (new File(dbPath).exists() && sleuthCase == null)
				sleuthCase = SleuthkitCase.openCase(dbPath);
				
			if(App.get().reader == null)
				InicializarBusca.inicializar(indexDir.getAbsolutePath());
		}
		
		
		HashSet<Integer> selectedLabels = new HashSet<Integer>(); 
		App.get().marcadores = state;
		PesquisarIndice pesquisa = new PesquisarIndice(PesquisarIndice.getQuery(""));
		SearchResult result = pesquisa.filtrarSelecionados(pesquisa.pesquisar());
		
		

		for (int docID : result.docs) {

			Document doc = App.get().reader.document(docID);

			String value = doc.get(IndexItem.LENGTH);
			Long len = null;
			if (value != null && !value.isEmpty())
				len = Long.valueOf(value);
				
			if (listOnly){
				caseData.incDiscoveredEvidences(1);
				caseData.incDiscoveredVolume(len);
				continue;
			}
			
			EvidenceFile evidence = new EvidenceFile();
			evidence.setName(doc.get(IndexItem.NAME));
			
			evidence.setLength(len);
			
			int id = Integer.valueOf(doc.get(IndexItem.ID));
			evidence.setId(id);
			
			for(int labelId : state.getLabelIds(id))
				selectedLabels.add(labelId);
			
			evidence.setLabels(state.getLabels(id));

			value = doc.get(IndexItem.PARENTID);
			if (value != null)
				evidence.setParentId(value);

			value = doc.get(IndexItem.TYPE);
			if (value != null)
				evidence.setType(new UnknownFileType(value));

			value = doc.get(IndexItem.CATEGORY);
			for(String category : value.split(CategoryTokenizer.SEPARATOR + ""))
				evidence.addCategory(category);
			

			value = doc.get(IndexItem.ACCESSED);
			if (!value.isEmpty())
				evidence.setAccessDate(df.parse(value));

			value = doc.get(IndexItem.CREATED);
			if (!value.isEmpty())
				evidence.setCreationDate(df.parse(value));

			value = doc.get(IndexItem.MODIFIED);
			if (!value.isEmpty())
				evidence.setModificationDate(df.parse(value));

			evidence.setPath(doc.get(IndexItem.PATH));

			value = doc.get(IndexItem.EXPORT);
			if (value != null && !value.isEmpty())
				evidence.setFile(IOUtil.getRelativeFile(basePath, value));

			else {
				value = doc.get(IndexItem.SLEUTHID);
				if (value != null && !value.isEmpty()) {
					evidence.setSleuthId(value);
					evidence.setSleuthFile(sleuthCase.getAbstractFileById(Long.valueOf(value)));
				}
			}

			value = doc.get(IndexItem.CONTENTTYPE);
			if (value != null)
				evidence.setMediaType(MediaType.parse(value));

			//provoca problema ao extrair item com timeout, pois hash não é encontrado no mapa de hashes
			//evidence.timeOut = Boolean.parseBoolean(doc.get("timeout"));

			value = doc.get(IndexItem.HASH);
			if (value != null)
				evidence.setHash(value);
			
			value = doc.get(IndexItem.DELETED);
			evidence.setDeleted(Boolean.parseBoolean(value));
			
			value = doc.get(IndexItem.ISDIR);
			evidence.setIsDir(Boolean.parseBoolean(value));
			
			value = doc.get(IndexItem.CARVED);
			evidence.setCarved(Boolean.parseBoolean(value));
			
			value = doc.get(IndexItem.OFFSET);
			if(value != null)
				evidence.setFileOffset(Long.parseLong(value));

			evidence.setToExtract(true);

			caseData.addEvidenceFile(evidence);
		}
		
		if(!listOnly){
			for(int labelId : state.getLabelMap().keySet().toArray(new Integer[0]))
				if(!selectedLabels.contains(labelId))
					state.delLabel(labelId);
			
			state.resetAndSetIndexDir(new File(output, "index"));
			state.saveState(new File(output, Marcadores.STATEFILENAME));
		}

		if (!listOnly)
			App.get().destroy();

	}

}
