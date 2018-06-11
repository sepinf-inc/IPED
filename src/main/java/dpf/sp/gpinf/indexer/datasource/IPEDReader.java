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
import gpinf.dev.data.DataSource;
import gpinf.dev.data.EvidenceFile;
import gpinf.dev.filetypes.GenericFileType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.tika.mime.MediaType;

import dpf.sp.gpinf.indexer.CmdLineArgs;
import dpf.sp.gpinf.indexer.desktop.ColumnsManager;
import dpf.sp.gpinf.indexer.parsers.OCRParser;
import dpf.sp.gpinf.indexer.parsers.OutlookPSTParser;
import dpf.sp.gpinf.indexer.parsers.util.BasicProps;
import dpf.sp.gpinf.indexer.parsers.util.ExtraProperties;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.process.task.CarveTask;
import dpf.sp.gpinf.indexer.process.task.HashTask;
import dpf.sp.gpinf.indexer.process.task.ImageThumbTask;
import dpf.sp.gpinf.indexer.process.task.ParsingTask;
import dpf.sp.gpinf.indexer.search.IPEDSearcher;
import dpf.sp.gpinf.indexer.search.IPEDSource;
import dpf.sp.gpinf.indexer.search.Marcadores;
import dpf.sp.gpinf.indexer.search.MultiMarcadores;
import dpf.sp.gpinf.indexer.search.SearchResult;
import dpf.sp.gpinf.indexer.search.LuceneSearchResult;
import dpf.sp.gpinf.indexer.util.DateUtil;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.MetadataInputStreamFactory;
import dpf.sp.gpinf.indexer.util.Util;

/*
 * Enfileira para processamento os arquivos selecionados via interface de pesquisa de uma indexação anterior.
 */
public class IPEDReader extends DataSourceReader {
  
  IPEDSource ipedCase;
  HashSet<Integer> selectedLabels;
  boolean extractCheckedItems = false;
  Marcadores state;
  File indexDir;
  String basePath;
  private int[] oldToNewIdMap;
  private List<IPEDSource> srcList = new ArrayList<IPEDSource>();
  private String deviceName;

  public IPEDReader(CaseData caseData, File output, boolean listOnly) {
    super(caseData, output, listOnly);
  }

  public boolean isSupported(File report) {
    String name = report.getName().toLowerCase();
    return name.endsWith(Marcadores.EXT);
  }

  public int read(File file) throws Exception {
	  
	Logger.getLogger("org.sleuthkit").setLevel(Level.SEVERE); //$NON-NLS-1$

    caseData.setContainsReport(true);
    caseData.setIpedReport(true);

    // Configuração para não expandir containers
    ParsingTask.expandContainers = false;
    CarveTask.enableCarving = false;
    
    deviceName = getEvidenceName(file);

    Object obj = Util.readObject(file.getAbsolutePath());
    if(obj instanceof MultiMarcadores){
    	MultiMarcadores mm = (MultiMarcadores) obj;
    	for(Marcadores m : mm.getSingleBookmarks())
    		processBookmark(m);
    }else
    	processBookmark((Marcadores)obj);
    
    return 0;

  }
  
  private void processBookmark(Marcadores state) throws Exception {
	    this.state = state;
	  	selectedLabels = new HashSet<Integer>();
	  	indexDir = state.getIndexDir().getCanonicalFile();
	    basePath = indexDir.getParentFile().getParentFile().getAbsolutePath();
	    ipedCase = new IPEDSource(new File(basePath));
	    ipedCase.checkImagePaths();
	    /*
	     * Necessário guardar referência aos SleuthkitCase para não serem coletados e finalizados,
	     * o que geraria erro ao acessar o conteúdo dos itens
	     */
	    srcList.add(ipedCase);
	    
	    oldToNewIdMap = new int[ipedCase.getLastId() + 1];
	    for(int i = 0; i < oldToNewIdMap.length; i++)
	    	oldToNewIdMap[i] = -1;
	    
	    IPEDSearcher pesquisa = new IPEDSearcher(ipedCase, new MatchAllDocsQuery());
	    LuceneSearchResult result = state.filterInReport(pesquisa.luceneSearch(), ipedCase);
	    if(result.getLength() == 0) {
	        result = state.filtrarSelecionados(pesquisa.luceneSearch(), ipedCase);
	        extractCheckedItems = true;
	    }

	    insertIntoProcessQueue(result, false);

	    //Inclui anexos de emails de PST
	    insertEmailAttachs(result);

	    //Inclui pais para visualização em árvore
	    insertParentTreeNodes(result);
	    
	    copyBookmarksToReport();
  }
  
  private void copyBookmarksToReport() throws ClassNotFoundException, IOException{
	  if (listOnly)
	  	return;
	  int lastId = ipedCase.getLastId();
	  int totalItens = ipedCase.getTotalItens();
	  File stateFile = new File(output, Marcadores.STATEFILENAME);
	  if(stateFile.exists()){
		  Marcadores reportState = Marcadores.load(stateFile);
		  lastId += reportState.getLastId() + 1;
		  totalItens += reportState.getTotalItens();
	  }
	  Marcadores reportState = new Marcadores(totalItens, lastId, output);
	  reportState.loadState();
  	
      for(int oldLabelId : selectedLabels){
    	  String labelName = state.getLabelName(oldLabelId);
    	  String labelComment = state.getLabelComment(oldLabelId);
    	  int newLabelId = reportState.newLabel(labelName);
    	  reportState.setLabelComment(newLabelId, labelComment);
    	  ArrayList<Integer> newIds = new ArrayList<Integer>();
    	  for(int oldId = 0; oldId <= ipedCase.getLastId(); oldId++)
    		  if(state.hasLabel(oldId, oldLabelId) && oldToNewIdMap[oldId] != -1)
    			newIds.add(oldToNewIdMap[oldId]);
    	  reportState.addLabel(newIds, newLabelId);
      }
      reportState.saveState();
  }

  private void insertParentTreeNodes(LuceneSearchResult result) throws Exception {
    boolean[] isParentToAdd = new boolean[ipedCase.getLastId() + 1];
    for (int docID : result.getLuceneIds()) {
      String parentIds = ipedCase.getReader().document(docID).get(IndexItem.PARENTIDs);
      if(!parentIds.trim().isEmpty())
	      for (String parentId : parentIds.trim().split(" ")) { //$NON-NLS-1$
	        isParentToAdd[Integer.parseInt(parentId)] = true;
	      }
    }
    for (int docID : result.getLuceneIds()) {
      String id = ipedCase.getReader().document(docID).get(IndexItem.ID);
      isParentToAdd[Integer.parseInt(id)] = false;
    }
    int num = 0;
    BooleanQuery query = new BooleanQuery();
    for (int i = 0; i <= ipedCase.getLastId(); i++) {
      if (isParentToAdd[i]) {
        query.add(NumericRangeQuery.newIntRange(IndexItem.ID, i, i, true, true), Occur.SHOULD);
        num++;
      }
      if (num == 1000 || (num > 0 && i == ipedCase.getLastId())) {
    	IPEDSearcher searchParents = new IPEDSearcher(ipedCase, query);
  		searchParents.setTreeQuery(true);
        result = searchParents.luceneSearch();
        insertIntoProcessQueue(result, true);
        query = new BooleanQuery();
        num = 0;
      }
    }
  }

  private void insertEmailAttachs(LuceneSearchResult result) throws Exception {
    CmdLineArgs args = (CmdLineArgs) caseData.getCaseObject(CmdLineArgs.class.getName());
    if (!args.isNopstattachs()) {
      boolean[] isSelectedEmail = new boolean[ipedCase.getLastId() + 1];
      boolean hasEmail = false;
      for (int docID : result.getLuceneIds()) {
        String mimetype = ipedCase.getReader().document(docID).get(IndexItem.CONTENTTYPE);
        if (OutlookPSTParser.OUTLOOK_MSG_MIME.equals(mimetype) || UfedXmlReader.UFED_EMAIL_MIME.equals(mimetype)) {
          hasEmail = true;
          isSelectedEmail[Integer.parseInt(ipedCase.getReader().document(docID).get(IndexItem.ID))] = true;
        }
      }
      if(!hasEmail)
        return;
      
      //search attachs
      int num = 0;
      boolean[] isAttachToAdd = new boolean[ipedCase.getLastId() + 1];
      BooleanQuery query = new BooleanQuery();
      for (int i = 0; i <= ipedCase.getLastId(); i++) {
          if (isSelectedEmail[i]) {
        	  query.add(NumericRangeQuery.newIntRange(IndexItem.PARENTID, i, i, true, true), Occur.SHOULD);
              num++;
          }
          if (num == 1000 || (num > 0 && i == ipedCase.getLastId())) {
            IPEDSearcher searchAttachs = new IPEDSearcher(ipedCase, query);
            SearchResult attachs = searchAttachs.search();
      	    for(int j = 0; j < attachs.getLength(); j++)
      	    	isAttachToAdd[attachs.getId(j)] = true;
      	    query = new BooleanQuery();
            num = 0;
          }
      }
      
      //remove duplicate attachs
      for (int docID : result.getLuceneIds()) {
        String id = ipedCase.getReader().document(docID).get(IndexItem.ID);
        isAttachToAdd[Integer.parseInt(id)] = false;
      }
      
      num = 0;
      query = new BooleanQuery();
      for (int i = 0; i <= ipedCase.getLastId(); i++) {
        if (isAttachToAdd[i]) {
          query.add(NumericRangeQuery.newIntRange(IndexItem.ID, i, i, true, true), Occur.SHOULD);
          num++;
        }
        if (num == 1000 || (num > 0 && i == ipedCase.getLastId())) {
          IPEDSearcher searchAttachs = new IPEDSearcher(ipedCase, query);
    	  LuceneSearchResult attachs = searchAttachs.luceneSearch();
          insertIntoProcessQueue(attachs, false);
          query = new BooleanQuery();
          num = 0;
        }
      }
    }
  }

  private void insertIntoProcessQueue(LuceneSearchResult result, boolean treeNode) throws Exception {

    for (int docID : result.getLuceneIds()) {
      Document doc = ipedCase.getReader().document(docID);

      String value = doc.get(IndexItem.LENGTH);
      Long len = null;
      if (value != null && !value.isEmpty()) {
        len = Long.valueOf(value);
      }

      if (listOnly) {
        caseData.incDiscoveredEvidences(1);
        if (!treeNode) {
          caseData.incDiscoveredVolume(len);
        }
        continue;
      }

      EvidenceFile evidence = new EvidenceFile();
      evidence.setName(doc.get(IndexItem.NAME));

      evidence.setLength(len);
      if (treeNode) {
        evidence.setSumVolume(false);
      }
      
      //TODO obter source corretamente
      DataSource dataSource = new DataSource(null);
      dataSource.setUUID(doc.get(IndexItem.EVIDENCE_UUID));
      evidence.setDataSource(dataSource);

      int id = Integer.valueOf(doc.get(IndexItem.ID));
      int newId = oldToNewIdMap[id];
      if(newId == -1){
    	  newId = EvidenceFile.getNextId();
    	  oldToNewIdMap[id] = newId;
      }
      evidence.setId(newId); 

      if (!treeNode) {
          if(extractCheckedItems) {
              selectedLabels.addAll(state.getLabelIds(id));
              evidence.setLabels(state.getLabelList(id));
          }else
              for (int labelId : state.getLabelIds(id)) {
                  if(state.isInReport(labelId)) {
                      selectedLabels.add(labelId);
                      evidence.getLabels().add(state.getLabelName(labelId));
                  }
              }
      }

      value = doc.get(IndexItem.PARENTID);
      if (value != null) {
    	  id = Integer.valueOf(value);
    	  newId = oldToNewIdMap[id];
          if(newId == -1){
        	  newId = EvidenceFile.getNextId();
        	  oldToNewIdMap[id] = newId;
          }
          evidence.setParentId(newId);
      }

      value = doc.get(IndexItem.PARENTIDs);
      ArrayList<Integer> parents = new ArrayList<Integer>();
      for (String parent : value.split(" ")) { //$NON-NLS-1$
        if (!parent.isEmpty()) {
        	id = Integer.valueOf(parent);
      	    newId = oldToNewIdMap[id];
            if(newId == -1){
          	  newId = EvidenceFile.getNextId();
          	  oldToNewIdMap[id] = newId;
            }
          parents.add(newId);
        }
      }
      evidence.addParentIds(parents);

      value = doc.get(IndexItem.TYPE);
      if (value != null) {
        evidence.setType(new GenericFileType(value));
      }

      for (String category : doc.getValues(IndexItem.CATEGORY)) {
        evidence.addCategory(category);
      }

      value = doc.get(IndexItem.ACCESSED);
      if (!value.isEmpty()) {
        evidence.setAccessDate(DateUtil.stringToDate(value));
      }
      
      value = doc.get(SleuthkitReader.IN_FAT_FS);
      if(value != null)
        evidence.setExtraAttribute(SleuthkitReader.IN_FAT_FS, true);

      value = doc.get(IndexItem.CREATED);
      if (!value.isEmpty()) {
        evidence.setCreationDate(DateUtil.stringToDate(value));
      }

      value = doc.get(IndexItem.MODIFIED);
      if (!value.isEmpty()) {
        evidence.setModificationDate(DateUtil.stringToDate(value));
      }
      
      value = doc.get(IndexItem.RECORDDATE);
      if (!value.isEmpty()) {
        evidence.setRecordDate(DateUtil.stringToDate(value));
      }

      String path = doc.get(IndexItem.PATH);
      if(deviceName != null){
    	  int idx = path.indexOf("/", 1); //$NON-NLS-1$
    	  if(idx == -1)
    		  path = "/" + deviceName; //$NON-NLS-1$
    	  else
    		  path = "/" + deviceName + path.substring(idx); //$NON-NLS-1$
      }
      evidence.setPath(path);
      
      String mimetype = doc.get(IndexItem.CONTENTTYPE);
      if (mimetype != null) {
        evidence.setMediaType(MediaType.parse(mimetype));
      }
      
      value = doc.get(IndexItem.EXPORT);
      if (value != null && !value.isEmpty() && !treeNode) {
        evidence.setFile(Util.getRelativeFile(basePath, value));
      } else {
        value = doc.get(IndexItem.SLEUTHID);
        if (value != null && !value.isEmpty() && !treeNode) {
          evidence.setSleuthId(Integer.valueOf(value));
          evidence.setSleuthFile(ipedCase.getSleuthCase().getContentById(Long.valueOf(value)));
          
        }else if(evidence.getMediaType().toString().contains(UfedXmlReader.UFED_MIME_PREFIX))
            evidence.setInputStreamFactory(new MetadataInputStreamFactory(evidence.getMetadata()));
      }

      if (treeNode) {
        evidence.setExtraAttribute(IndexItem.TREENODE, "true"); //$NON-NLS-1$
        evidence.setAddToCase(false);
      }

      evidence.setTimeOut(Boolean.parseBoolean(doc.get(IndexItem.TIMEOUT)));

      value = doc.get(IndexItem.HASH);
      if (value != null) {
        value = value.toUpperCase();
        evidence.setHash(value);

        if (!value.isEmpty()) {
          File viewFile = Util.findFileFromHash(new File(indexDir.getParentFile(), "view"), value); //$NON-NLS-1$
          if (viewFile != null) {
            evidence.setViewFile(viewFile);
          }

          //Copia resultado prévio do OCR
          String ocrPrefix = OCRParser.TEXT_DIR + "/" + value.charAt(0) + "/" + value.charAt(1); //$NON-NLS-1$ //$NON-NLS-2$
          File ocrSrc = new File(indexDir.getParentFile(), ocrPrefix);
          File ocrDst = new File(output, ocrPrefix);
          if (ocrSrc.exists()) {
            ocrDst.mkdirs();
            for (String name : ocrSrc.list()) {
              if (name.equals(value + ".txt") || name.startsWith(value + "-child")) { //$NON-NLS-1$ //$NON-NLS-2$
                IOUtil.copiaArquivo(new File(ocrSrc, name), new File(ocrDst, name));
              }
            }
          }

          //Copia miniaturas
          File thumbSrc = Util.getFileFromHash(new File(indexDir.getParentFile(), ImageThumbTask.thumbsFolder), value, "jpg"); //$NON-NLS-1$
          File thumbDst = Util.getFileFromHash(new File(output, ImageThumbTask.thumbsFolder), value, "jpg"); //$NON-NLS-1$
          if (thumbSrc.exists()) {
            thumbDst.getParentFile().mkdirs();
            IOUtil.copiaArquivo(thumbSrc, thumbDst);
          }
        }
      }

      for (HashTask.HASH hash : HashTask.HASH.values()) {
        value = doc.get(hash.toString());
        if (value != null)
          evidence.setExtraAttribute(hash.toString(), value);
      }

      //armazena metadados de emails, necessário para emails de PST
      if(OutlookPSTParser.OUTLOOK_MSG_MIME.equals(mimetype))	
        for (String key : ColumnsManager.email) {
          for(String val : doc.getValues(key))
              evidence.getMetadata().add(key, val);
        }

      value = doc.get(IndexItem.DELETED);
      evidence.setDeleted(Boolean.parseBoolean(value));

      value = doc.get(IndexItem.ISDIR);
      evidence.setIsDir(Boolean.parseBoolean(value));

      value = doc.get(IndexItem.CARVED);
      evidence.setCarved(Boolean.parseBoolean(value));

      value = doc.get(IndexItem.SUBITEM);
      evidence.setSubItem(Boolean.parseBoolean(value));

      value = doc.get(IndexItem.HASCHILD);
      evidence.setHasChildren(Boolean.parseBoolean(value));
      
      value = doc.get(ParsingTask.HAS_SUBITEM);
      if(value != null)
    	evidence.setExtraAttribute(ParsingTask.HAS_SUBITEM, "true"); //$NON-NLS-1$

      value = doc.get(IndexItem.OFFSET);
      if (value != null) {
        evidence.setFileOffset(Long.parseLong(value));
      }
      
      value = doc.get(IndexItem.ISROOT);
      if (value != null) {
        evidence.setRoot(true);
        if(deviceName != null)
            evidence.setName(deviceName);
      }
      
      Set<String> multiValuedFields = new HashSet<>();
      for(IndexableField f : doc.getFields()) {
          if(BasicProps.SET.contains(f.name()))
              continue;
          if(EvidenceFile.getAllExtraAttributes().contains(f.name())) {
              if(multiValuedFields.contains(f.name()))
                  continue;
              Class<?> c = IndexItem.getMetadataTypes().get(f.name());
              if(isExtraAttrMultiValued(f.name())) {
                  multiValuedFields.add(f.name());
                  List<Object> fieldList = new ArrayList<>();
                  IndexableField[] fields = doc.getFields(f.name());
                  for(IndexableField field : fields)
                      fieldList.add(IndexItem.getCastedValue(c, field));
                  evidence.setExtraAttribute(f.name(), fieldList);
              }else
                  evidence.setExtraAttribute(f.name(), IndexItem.getCastedValue(c, f));
          }else
              evidence.getMetadata().add(f.name(), f.stringValue());
      }

      caseData.addEvidenceFile(evidence);
    }

  }

  private boolean isExtraAttrMultiValued(String field) throws IOException {
      Object docValues = ipedCase.getAtomicReader().getSortedSetDocValues(field);
      if(docValues != null)
          return true;
      docValues = ipedCase.getAtomicReader().getSortedNumericDocValues(field);
      if(docValues != null)
          return true;
      
      return false;
  }

}
