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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.tika.mime.MediaType;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.carver.CarverTask;
import dpf.sp.gpinf.indexer.CmdLineArgs;
import dpf.sp.gpinf.indexer.parsers.OCRParser;
import dpf.sp.gpinf.indexer.parsers.OutlookPSTParser;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.process.Manager;
import dpf.sp.gpinf.indexer.process.task.DIETask;
import dpf.sp.gpinf.indexer.process.task.HashTask;
import dpf.sp.gpinf.indexer.process.task.KFFCarveTask;
import dpf.sp.gpinf.indexer.process.task.KFFTask;
import dpf.sp.gpinf.indexer.process.task.LedKFFTask;
import dpf.sp.gpinf.indexer.process.task.ParsingTask;
import dpf.sp.gpinf.indexer.search.IPEDSearcher;
import dpf.sp.gpinf.indexer.search.IPEDSource;
import dpf.sp.gpinf.indexer.search.Marcadores;
import dpf.sp.gpinf.indexer.util.DateUtil;
import dpf.sp.gpinf.indexer.util.HashValue;
import dpf.sp.gpinf.indexer.util.MetadataInputStreamFactory;
import dpf.sp.gpinf.indexer.util.SeekableInputStreamFactory;
import dpf.sp.gpinf.indexer.util.Util;
import gpinf.dev.data.DataSource;
import gpinf.dev.data.Item;
import gpinf.dev.filetypes.GenericFileType;
import iped3.ICaseData;
import iped3.IIPEDSource;
import iped3.datasource.IDataSource;
import iped3.search.IIPEDSearcher;
import iped3.search.IMarcadores;
import iped3.search.IMultiMarcadores;
import iped3.search.LuceneSearchResult;
import iped3.search.SearchResult;
import iped3.util.BasicProps;
import iped3.util.MediaTypes;
import iped3.util.ExtraProperties;

/*
 * Enfileira para processamento os arquivos selecionados via interface de pesquisa de uma indexação anterior.
 */
public class IPEDReader extends DataSourceReader {

    private static org.slf4j.Logger LOGGER = LoggerFactory.getLogger(IPEDReader.class);
    
    private static Map<Path, SeekableInputStreamFactory> inputStreamFactories = new ConcurrentHashMap<>();
    
    IPEDSource ipedCase;
    HashSet<Integer> selectedLabels;
    boolean extractCheckedItems = false;
    IMarcadores state;
    File indexDir;
    String basePath;
    private int[] oldToNewIdMap;
    private List<IIPEDSource> srcList = new ArrayList<IIPEDSource>();
    private String deviceName;
    
    public IPEDReader(ICaseData caseData, File output, boolean listOnly) {
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
        ParsingTask.setExpandContainers(false);
        CarverTask.setEnabled(false);
        KFFCarveTask.setEnabled(false);
        KFFTask.setEnabled(false);
        LedKFFTask.setEnabled(false);
        DIETask.setEnabled(false);

        deviceName = getEvidenceName(file);

        Object obj = Util.readObject(file.getAbsolutePath());
        if (obj instanceof IMultiMarcadores) {
            IMultiMarcadores mm = (IMultiMarcadores) obj;
            for (IMarcadores m : mm.getSingleBookmarks())
                processBookmark(m);
        } else
            processBookmark((IMarcadores) obj);

        return 0;

    }
    
    public void read(Set<HashValue> parentsWithLostSubitems, Manager manager) throws Exception {
        
        try(IPEDSource ipedSrc = new IPEDSource(output.getParentFile(), manager.getIndexWriter())){
            ipedCase = ipedSrc;
            basePath = ipedCase.getCaseDir().getAbsolutePath();
            indexDir = ipedCase.getIndex();
            
            BooleanQuery parents = new BooleanQuery();
            for(HashValue persistentId : parentsWithLostSubitems) {
                TermQuery tq = new TermQuery(new Term(IndexItem.PERSISTENT_ID, persistentId.toString().toLowerCase()));
                parents.add(tq, Occur.SHOULD);
            }
            BooleanQuery subitems = new BooleanQuery();
            TermQuery tq = new TermQuery(new Term(BasicProps.SUBITEM, Boolean.TRUE.toString()));
            subitems.add(tq, Occur.SHOULD);
            tq = new TermQuery(new Term(BasicProps.CARVED, Boolean.TRUE.toString()));
            subitems.add(tq, Occur.SHOULD);
            BooleanQuery query = new BooleanQuery();
            query.add(parents, Occur.MUST);
            query.add(subitems, Occur.MUST);
            IIPEDSearcher searcher = new IPEDSearcher(ipedCase, query);
            LuceneSearchResult result = searcher.luceneSearch();
            insertIntoProcessQueue(result, false);
        }
    }

    private void processBookmark(IMarcadores state) throws Exception {
        this.state = state;
        selectedLabels = new HashSet<Integer>();
        indexDir = state.getIndexDir().getCanonicalFile();
        basePath = indexDir.getParentFile().getParentFile().getAbsolutePath();
        ipedCase = new IPEDSource(new File(basePath));
        ipedCase.checkImagePaths();
        /*
         * Necessário guardar referência aos SleuthkitCase para não serem coletados e
         * finalizados, o que geraria erro ao acessar o conteúdo dos itens
         */
        srcList.add(ipedCase);

        oldToNewIdMap = new int[ipedCase.getLastId() + 1];
        for (int i = 0; i < oldToNewIdMap.length; i++)
            oldToNewIdMap[i] = -1;

        IIPEDSearcher pesquisa = new IPEDSearcher(ipedCase, new MatchAllDocsQuery());
        LuceneSearchResult result = state.filterInReport(pesquisa.luceneSearch(), ipedCase);
        if (result.getLength() == 0) {
            result = state.filtrarSelecionados(pesquisa.luceneSearch(), ipedCase);
            extractCheckedItems = true;
        }

        insertIntoProcessQueue(result, false);

        // Inclui anexos de emails de PST
        insertEmailAttachs(result);

        // insert items referenced by bookmarked items
        CmdLineArgs args = (CmdLineArgs) caseData.getCaseObject(CmdLineArgs.class.getName());
        if (!args.isNoLinkedItems())
            insertLinkedItems(result);

        // Inclui pais para visualização em árvore
        insertParentTreeNodes(result);

        copyBookmarksToReport();
    }

    private void copyBookmarksToReport() throws ClassNotFoundException, IOException {
        if (listOnly)
            return;
        int lastId = ipedCase.getLastId();
        int totalItens = ipedCase.getTotalItens();
        File stateFile = new File(output, Marcadores.STATEFILENAME);
        if (stateFile.exists()) {
            IMarcadores reportState = Marcadores.load(stateFile);
            lastId += reportState.getLastId() + 1;
            totalItens += reportState.getTotalItens();
        }
        IMarcadores reportState = new Marcadores(totalItens, lastId, output);
        reportState.loadState();

        for (int oldLabelId : selectedLabels) {
            String labelName = state.getLabelName(oldLabelId);
            String labelComment = state.getLabelComment(oldLabelId);
            int newLabelId = reportState.newLabel(labelName);
            reportState.setLabelComment(newLabelId, labelComment);
            ArrayList<Integer> newIds = new ArrayList<Integer>();
            for (int oldId = 0; oldId <= ipedCase.getLastId(); oldId++)
                if (state.hasLabel(oldId, oldLabelId) && oldToNewIdMap[oldId] != -1)
                    newIds.add(oldToNewIdMap[oldId]);
            reportState.addLabel(newIds, newLabelId);
        }
        reportState.saveState();
    }

    private void insertParentTreeNodes(LuceneSearchResult result) throws Exception {
        boolean[] isParentToAdd = new boolean[ipedCase.getLastId() + 1];
        for (int docID : result.getLuceneIds()) {
            String parentIds = ipedCase.getReader().document(docID).get(IndexItem.PARENTIDs);
            if (!parentIds.trim().isEmpty())
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
                IIPEDSearcher searchParents = new IPEDSearcher(ipedCase, query);
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
                if (OutlookPSTParser.OUTLOOK_MSG_MIME.equals(mimetype)
                        || UfedXmlReader.UFED_EMAIL_MIME.equals(mimetype)) {
                    hasEmail = true;
                    isSelectedEmail[Integer.parseInt(ipedCase.getReader().document(docID).get(IndexItem.ID))] = true;
                }
            }
            if (!hasEmail)
                return;

            // search attachs
            int num = 0;
            boolean[] isAttachToAdd = new boolean[ipedCase.getLastId() + 1];
            BooleanQuery query = new BooleanQuery();
            for (int i = 0; i <= ipedCase.getLastId(); i++) {
                if (isSelectedEmail[i]) {
                    query.add(NumericRangeQuery.newIntRange(IndexItem.PARENTID, i, i, true, true), Occur.SHOULD);
                    num++;
                }
                if (num == 1000 || (num > 0 && i == ipedCase.getLastId())) {
                    IIPEDSearcher searchAttachs = new IPEDSearcher(ipedCase, query);
                    SearchResult attachs = searchAttachs.search();
                    for (int j = 0; j < attachs.getLength(); j++)
                        isAttachToAdd[attachs.getId(j)] = true;
                    query = new BooleanQuery();
                    num = 0;
                }
            }

            // remove duplicate attachs
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
                    IIPEDSearcher searchAttachs = new IPEDSearcher(ipedCase, query);
                    LuceneSearchResult attachs = searchAttachs.luceneSearch();
                    insertIntoProcessQueue(attachs, false);
                    query = new BooleanQuery();
                    num = 0;
                }
            }
        }
    }

    private void insertLinkedItems(LuceneSearchResult result) {
        int[] luceneIds = result.getLuceneIds();
        Arrays.sort(luceneIds);
        String queryText = ExtraProperties.LINKED_ITEMS + ":*"; //$NON-NLS-1$
        IIPEDSearcher searcher = new IPEDSearcher(ipedCase, queryText);
        try {
            SearchResult itemsWithLinks = searcher.search();
            for (int i = 0; i < itemsWithLinks.getLength(); i++) {
                int luceneId = ipedCase.getLuceneId(itemsWithLinks.getId(i));
                if (Arrays.binarySearch(luceneIds, luceneId) < 0)
                    continue;

                Document doc = ipedCase.getReader().document(luceneId);
                String[] items = doc.getValues(ExtraProperties.LINKED_ITEMS);
                StringBuilder query = new StringBuilder();
                for (String item : items)
                    query.append("(").append(item).append(") "); //$NON-NLS-1$

                StringBuilder queryBuilder = new StringBuilder();
                queryBuilder.append(IndexItem.LENGTH + ":[3 TO *] AND ("); //$NON-NLS-1$
                queryBuilder.append(query.toString());
                queryBuilder.append(")"); //$NON-NLS-1$
                searcher = new IPEDSearcher(ipedCase, queryBuilder.toString());

                LuceneSearchResult linkedItems = searcher.luceneSearch();
                if (linkedItems.getLength() > 0) {
                    LOGGER.info("Linked items to '" + doc.get(IndexItem.NAME) + "' found: " + linkedItems.getLength()); //$NON-NLS-1$
                    insertIntoProcessQueue(linkedItems, false);
                }
            }
        } catch (Exception e1) {
            e1.printStackTrace();

        }
    }
    
    private int getId(String value) {
        int id = Integer.valueOf(value);
        if(oldToNewIdMap != null) {
            int newId = oldToNewIdMap[id];
            if (newId == -1) {
                newId = Item.getNextId();
                oldToNewIdMap[id] = newId;
            }
            id = newId;
        }
        return id;
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

            Item evidence = new Item();
            evidence.setName(doc.get(IndexItem.NAME));

            evidence.setLength(len);
            if (treeNode) {
                evidence.setSumVolume(false);
            }

            // TODO obter source corretamente
            IDataSource dataSource = new DataSource(null);
            dataSource.setUUID(doc.get(IndexItem.EVIDENCE_UUID));
            evidence.setDataSource(dataSource);

            int id = getId(doc.get(IndexItem.ID));
            evidence.setId(id);

            if (!treeNode && caseData.isIpedReport()) {
                if (extractCheckedItems) {
                    selectedLabels.addAll(state.getLabelIds(id));
                    evidence.setLabels(state.getLabelList(id));
                } else
                    for (int labelId : state.getLabelIds(id)) {
                        if (state.isInReport(labelId)) {
                            selectedLabels.add(labelId);
                            evidence.getLabels().add(state.getLabelName(labelId));
                        }
                    }
            }

            value = doc.get(IndexItem.PARENTID);
            if (value != null) {
                id = getId(value);
                evidence.setParentId(id);
            }

            value = doc.get(IndexItem.PARENTIDs);
            ArrayList<Integer> parents = new ArrayList<Integer>();
            for (String parent : value.split(" ")) { //$NON-NLS-1$
                if (!parent.isEmpty()) {
                    id = getId(parent);
                    parents.add(id);
                }
            }
            evidence.addParentIds(parents);
            
            value = doc.get(IndexItem.SUBITEMID);
            if (value != null) {
                evidence.setSubitemId(Integer.valueOf(value));
            }

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
            if (value != null)
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
            if (deviceName != null) {
                int idx = path.indexOf("/", 1); //$NON-NLS-1$
                if (idx == -1)
                    path = "/" + deviceName; //$NON-NLS-1$
                else
                    path = "/" + deviceName + path.substring(idx); //$NON-NLS-1$
            }
            evidence.setPath(path);

            String mimetype = doc.get(IndexItem.CONTENTTYPE);
            if (mimetype != null) {
                evidence.setMediaType(MediaType.parse(mimetype));
            }

            if (!treeNode) {
                value = doc.get(IndexItem.EXPORT);
                if (value != null && !value.isEmpty()) {
                    evidence.setFile(Util.getResolvedFile(basePath, value));
                } else {
                    value = doc.get(IndexItem.SLEUTHID);
                    if (value != null && !value.isEmpty()) {
                        evidence.setSleuthId(Integer.valueOf(value));
                        evidence.setSleuthFile(ipedCase.getSleuthCase().getContentById(Long.valueOf(value)));

                    } else if ((value = doc.get(IndexItem.ID_IN_SOURCE)) != null) {
                        evidence.setIdInDataSource(value.trim());
                        String relPath = doc.get(IndexItem.SOURCE_PATH);
                        Path absPath = Util.getResolvedFile(basePath, relPath).toPath();
                        SeekableInputStreamFactory sisf = inputStreamFactories.get(absPath);
                        if (sisf == null) {
                            String className = doc.get(IndexItem.SOURCE_DECODER);
                            Class<?> clazz = Class.forName(className);
                            Constructor<SeekableInputStreamFactory> c = (Constructor) clazz.getConstructor(Path.class);
                            sisf = c.newInstance(absPath);
                            inputStreamFactories.put(absPath, sisf);
                        }
                        evidence.setInputStreamFactory(sisf);

                    } else if (evidence.getMediaType().toString().contains(UfedXmlReader.UFED_MIME_PREFIX))
                        evidence.setInputStreamFactory(new MetadataInputStreamFactory(evidence.getMetadata()));
                    
                    else {
                        MediaType type = evidence.getMediaType();
                        while (type != null && !type.equals(MediaType.OCTET_STREAM)) {
                            if(type.equals(MediaTypes.METADATA_ENTRY)) {
                                evidence.setInputStreamFactory(new MetadataInputStreamFactory(evidence.getMetadata(), true));
                                break;
                            }
                            type = MediaTypes.getParentType(type);
                        }                   
                    }
                }
            } else {
                evidence.setExtraAttribute(IndexItem.TREENODE, "true"); //$NON-NLS-1$
                evidence.setAddToCase(false);
            }

            evidence.setTimeOut(Boolean.parseBoolean(doc.get(IndexItem.TIMEOUT)));

            value = doc.get(IndexItem.HASH);
            if (value != null && !treeNode) {
                value = value.toUpperCase();
                evidence.setHash(value);

                if (!value.isEmpty() && caseData.isIpedReport()) {
                    File viewFile = Util.findFileFromHash(new File(indexDir.getParentFile(), "view"), value); //$NON-NLS-1$
                    if (viewFile != null) {
                        evidence.setViewFile(viewFile);
                    }

                    OCRParser.copyOcrResults(value, indexDir.getParentFile(), output);
                }
            }
            
            if (doc.getBinaryValue(BasicProps.THUMB) != null) {
                evidence.setThumb(doc.getBinaryValue(BasicProps.THUMB).bytes);
            }

            for (HashTask.HASH hash : HashTask.HASH.values()) {
                value = doc.get(hash.toString());
                if (value != null)
                    evidence.setExtraAttribute(hash.toString(), value);
            }

            // armazena metadados de emails, necessário para emails de PST
            if (OutlookPSTParser.OUTLOOK_MSG_MIME.equals(mimetype))
                for (String key : ExtraProperties.EMAIL_PROPS) {
                    for (String val : doc.getValues(key))
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
            if (value != null)
                evidence.setExtraAttribute(ParsingTask.HAS_SUBITEM, "true"); //$NON-NLS-1$

            value = doc.get(IndexItem.OFFSET);
            if (value != null) {
                evidence.setFileOffset(Long.parseLong(value));
            }

            value = doc.get(IndexItem.ISROOT);
            if (value != null) {
                evidence.setRoot(true);
                if (deviceName != null) {
                    evidence.setName(deviceName);
                }
            }

            Set<String> multiValuedFields = new HashSet<>();
            for (IndexableField f : doc.getFields()) {
                if (BasicProps.SET.contains(f.name()))
                    continue;
                if (Item.getAllExtraAttributes().contains(f.name())) {
                    if (multiValuedFields.contains(f.name()))
                        continue;
                    Class<?> c = IndexItem.getMetadataTypes().get(f.name());
                    if (isExtraAttrMultiValued(f.name())) {
                        multiValuedFields.add(f.name());
                        List<Object> fieldList = new ArrayList<>();
                        IndexableField[] fields = doc.getFields(f.name());
                        for (IndexableField field : fields)
                            fieldList.add(IndexItem.getCastedValue(c, field));
                        evidence.setExtraAttribute(f.name(), fieldList);
                    } else
                        evidence.setExtraAttribute(f.name(), IndexItem.getCastedValue(c, f));
                } else
                    evidence.getMetadata().add(f.name(), f.stringValue());
            }

            caseData.addItem(evidence);
        }

    }

    private boolean isExtraAttrMultiValued(String field) throws IOException {
        Object docValues = ipedCase.getAtomicReader().getSortedSetDocValues(field);
        if (docValues != null)
            return true;
        docValues = ipedCase.getAtomicReader().getSortedNumericDocValues(field);
        if (docValues != null)
            return true;

        return false;
    }

}
