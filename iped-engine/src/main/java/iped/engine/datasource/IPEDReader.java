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
package iped.engine.datasource;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.tika.mime.MediaType;
import org.slf4j.LoggerFactory;

import iped.data.IBookmarks;
import iped.data.ICaseData;
import iped.data.IIPEDSource;
import iped.data.IMultiBookmarks;
import iped.datasource.IDataSource;
import iped.engine.CmdLineArgs;
import iped.engine.config.CategoryToExpandConfig;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.FileSystemConfig;
import iped.engine.core.Manager;
import iped.engine.data.BitmapBookmarks;
import iped.engine.data.Bookmarks;
import iped.engine.data.DataSource;
import iped.engine.data.IPEDSource;
import iped.engine.data.Item;
import iped.engine.io.MetadataInputStreamFactory;
import iped.engine.preview.PreviewConstants;
import iped.engine.preview.PreviewRepositoryManager;
import iped.engine.search.IPEDSearcher;
import iped.engine.search.LuceneSearchResult;
import iped.engine.search.SimilarFacesSearch;
import iped.engine.task.EmbeddedDiskProcessTask;
import iped.engine.task.HashDBLookupTask;
import iped.engine.task.HashTask;
import iped.engine.task.MinIOTask.MinIOInputInputStreamFactory;
import iped.engine.task.ParsingTask;
import iped.engine.task.QRCodeTask;
import iped.engine.task.carver.CarverTask;
import iped.engine.task.carver.LedCarveTask;
import iped.engine.task.die.DIETask;
import iped.engine.task.index.IndexItem;
import iped.engine.util.Util;
import iped.parsers.mail.OutlookPSTParser;
import iped.parsers.mail.win10.Win10MailParser;
import iped.parsers.ocr.OCRParser;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.properties.MediaTypes;
import iped.search.IIPEDSearcher;
import iped.search.SearchResult;
import iped.utils.DateUtil;
import iped.utils.HashValue;
import iped.utils.SeekableInputStreamFactory;
import jep.NDArray;

/*
 * Enfileira para processamento os arquivos selecionados via interface de pesquisa de uma indexação anterior.
 */
public class IPEDReader extends DataSourceReader {

    public static final String REPORTING_CASES = "reporting_cases";

    private static org.slf4j.Logger LOGGER = LoggerFactory.getLogger(IPEDReader.class);

    private static Map<String, SeekableInputStreamFactory> inputStreamFactories = new HashMap<>();

    IPEDSource ipedCase;
    HashSet<Integer> selectedLabels;
    boolean extractCheckedItems = false;
    IBookmarks state;
    File indexDir;
    String basePath;
    private int[] oldToNewIdMap;
    private List<IIPEDSource> srcList = new ArrayList<IIPEDSource>();
    private String deviceName;

    private BitSet addedItems = new BitSet();

    public IPEDReader(ICaseData caseData, File output, boolean listOnly) {
        super(caseData, output, listOnly);
    }

    public boolean isSupported(File report) {
        String name = report.getName().toLowerCase();
        return name.endsWith(Bookmarks.EXT);
    }

    public void read(File file) throws Exception {

        Logger.getLogger("org.sleuthkit").setLevel(Level.SEVERE); //$NON-NLS-1$

        caseData.setContainsReport(true);
        caseData.setIpedReport(true);

        // Configuração para não expandir containers
        CategoryToExpandConfig expandConfig = ConfigurationManager.get().findObject(CategoryToExpandConfig.class);
        expandConfig.setEnabled(false);
        EmbeddedDiskProcessTask.setEnabled(false);
        CarverTask.setEnabled(false);
        LedCarveTask.setEnabled(false);
        HashDBLookupTask.setEnabled(false);
        DIETask.setEnabled(false);
        QRCodeTask.setEnabled(false);

        deviceName = getEvidenceName(file);
        if (deviceName.endsWith(Bookmarks.EXT)) {
            deviceName = null;
        }

        Object obj = Util.readObject(file.getAbsolutePath());
        if (obj instanceof IMultiBookmarks) {
            IMultiBookmarks mm = (IMultiBookmarks) obj;
            if (mm.getSingleBookmarks().size() > 1) {
                // Currently robust Image reading does not work with multicases.
                FileSystemConfig fsConfig = ConfigurationManager.get().findObject(FileSystemConfig.class);
                fsConfig.setRobustImageReading(false);
            }
            for (IBookmarks m : mm.getSingleBookmarks())
                processBookmark(m);
        } else {
            processBookmark((IBookmarks) obj);
        }
    }

    public void read(Set<HashValue> parentsWithLostSubitems, Manager manager) throws Exception {

        try (IPEDSource ipedSrc = new IPEDSource(output.getParentFile(), manager.getIndexWriter())) {
            ipedCase = ipedSrc;
            basePath = ipedCase.getCaseDir().getAbsolutePath();
            indexDir = ipedCase.getIndex();

            BooleanQuery.Builder parents = new BooleanQuery.Builder();
            for (HashValue trackID : parentsWithLostSubitems.toArray(new HashValue[0])) {
                TermQuery tq = new TermQuery(new Term(IndexItem.TRACK_ID, trackID.toString().toLowerCase()));
                parents.add(tq, Occur.SHOULD);
            }
            BooleanQuery.Builder subitems = new BooleanQuery.Builder();
            TermQuery tq = new TermQuery(new Term(BasicProps.SUBITEM, Boolean.TRUE.toString()));
            subitems.add(tq, Occur.SHOULD);
            tq = new TermQuery(new Term(BasicProps.CARVED, Boolean.TRUE.toString()));
            subitems.add(tq, Occur.SHOULD);
            BooleanQuery.Builder query = new BooleanQuery.Builder();
            query.add(parents.build(), Occur.MUST);
            query.add(subitems.build(), Occur.MUST);
            IIPEDSearcher searcher = new IPEDSearcher(ipedCase, query.build());
            LuceneSearchResult result = LuceneSearchResult.get(ipedCase, searcher.search());
            insertIntoProcessQueue(result, false, false);
        }
    }

    private void processBookmark(IBookmarks state) throws Exception {
        this.state = state;
        selectedLabels = new HashSet<Integer>();
        indexDir = state.getIndexDir().getCanonicalFile();
        basePath = indexDir.getParentFile().getParentFile().getAbsolutePath();
        if (!listOnly) {
            List<File> reportingCases = (List<File>) caseData.getCaseObject(REPORTING_CASES);
            if (reportingCases == null) {
                caseData.putCaseObject(REPORTING_CASES, reportingCases = new ArrayList<>());
            }
            reportingCases.add(new File(basePath));
        }

        ipedCase = new IPEDSource(new File(basePath));
        ipedCase.checkImagePaths();
        /*
         * Necessário guardar referência aos SleuthkitCase para não serem coletados e
         * finalizados, o que geraria erro ao acessar o conteúdo dos itens
         */
        srcList.add(ipedCase);

        // clearing added items is needed when creating multicase reports
        addedItems = new BitSet();
        oldToNewIdMap = new int[ipedCase.getLastId() + 1];
        for (int i = 0; i < oldToNewIdMap.length; i++)
            oldToNewIdMap[i] = -1;

        if (!listOnly) {
            PreviewRepositoryManager.configureReadOnly(ipedCase.getModuleDir());
        }

        IIPEDSearcher pesquisa = new IPEDSearcher(ipedCase, new MatchAllDocsQuery());
        SearchResult searchResult = state.filterInReport(pesquisa.search());
        if (searchResult.getLength() == 0) {
            searchResult = state.filterChecked(pesquisa.search());
            extractCheckedItems = true;
        }

        LuceneSearchResult result = LuceneSearchResult.get(ipedCase, searchResult);

        insertIntoProcessQueue(result, false);

        // Add attachments of emails from PST, OST, UFED decoding, Win10Mail
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

        int lastId = -1;
        int totalItems = 0;
        for (int i = 0; i < oldToNewIdMap.length; i++) {
            if (oldToNewIdMap[i] > lastId) {
                lastId = oldToNewIdMap[i];
            }
            if (oldToNewIdMap[i] != -1) {
                totalItems++;
            }
        }
        if (lastId == -1) {
            // Nothing was added, skip copying bookmarks (see issue #2037)
            LOGGER.info("No bookmarked items copied from {}", basePath);
            return;
        }

        IBookmarks reportState = new BitmapBookmarks(output.getParentFile(), lastId);

        reportState.loadState();

        int added = 0;
        for (int oldLabelId : selectedLabels) {
            String labelName = state.getBookmarkName(oldLabelId);
            String labelComment = state.getBookmarkComment(oldLabelId);
            Color labelColor = state.getBookmarkColor(oldLabelId);

            int newLabelId = reportState.newBookmark(labelName);
            reportState.setBookmarkComment(newLabelId, labelComment);
            reportState.setBookmarkColor(newLabelId, labelColor);

            ArrayList<Integer> newIds = new ArrayList<Integer>();
            for (int oldId = 0; oldId <= ipedCase.getLastId(); oldId++)
                if (state.hasBookmark(oldId, oldLabelId) && oldToNewIdMap[oldId] != -1)
                    newIds.add(oldToNewIdMap[oldId]);
            reportState.addBookmark(newIds, newLabelId);
            added += newIds.size();
        }
        LOGGER.info("{} bookmarked items copied from {}", added, basePath);
        reportState.saveState(true);
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
        BooleanQuery.Builder query = new BooleanQuery.Builder();
        for (int i = 0; i <= ipedCase.getLastId(); i++) {
            if (isParentToAdd[i]) {
                query.add(IntPoint.newExactQuery(IndexItem.ID, i), Occur.SHOULD);
                num++;
            }
            if (num == 1000 || (num > 0 && i == ipedCase.getLastId())) {
                IIPEDSearcher searchParents = new IPEDSearcher(ipedCase, query.build());
                searchParents.setTreeQuery(true);
                LuceneSearchResult parents = LuceneSearchResult.get(ipedCase, searchParents.search());
                insertIntoProcessQueue(parents, true);
                query = new BooleanQuery.Builder();
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
                        || UfedXmlReader.UFED_EMAIL_MIME.equals(mimetype)
                        || Win10MailParser.WIN10_MAIL_MSG.toString().equals(mimetype)) {
                    hasEmail = true;
                    isSelectedEmail[Integer.parseInt(ipedCase.getReader().document(docID).get(IndexItem.ID))] = true;
                }
            }
            if (!hasEmail)
                return;

            // search attachs
            int num = 0;
            boolean[] isAttachToAdd = new boolean[ipedCase.getLastId() + 1];
            BooleanQuery.Builder query = new BooleanQuery.Builder();
            for (int i = 0; i <= ipedCase.getLastId(); i++) {
                if (isSelectedEmail[i]) {
                    query.add(IntPoint.newExactQuery(IndexItem.PARENTID, i), Occur.SHOULD);
                    num++;
                }
                if (num == 1000 || (num > 0 && i == ipedCase.getLastId())) {
                    IIPEDSearcher searchAttachs = new IPEDSearcher(ipedCase, query.build());
                    SearchResult attachs = searchAttachs.search();
                    for (int j = 0; j < attachs.getLength(); j++)
                        isAttachToAdd[attachs.getId(j)] = true;
                    query = new BooleanQuery.Builder();
                    num = 0;
                }
            }

            // remove duplicate attachs
            for (int docID : result.getLuceneIds()) {
                String id = ipedCase.getReader().document(docID).get(IndexItem.ID);
                isAttachToAdd[Integer.parseInt(id)] = false;
            }

            num = 0;
            query = new BooleanQuery.Builder();
            for (int i = 0; i <= ipedCase.getLastId(); i++) {
                if (isAttachToAdd[i]) {
                    query.add(IntPoint.newExactQuery(IndexItem.ID, i), Occur.SHOULD);
                    num++;
                }
                if (num == 1000 || (num > 0 && i == ipedCase.getLastId())) {
                    IIPEDSearcher searchAttachs = new IPEDSearcher(ipedCase, query.build());
                    LuceneSearchResult attachs = LuceneSearchResult.get(ipedCase, searchAttachs.search());
                    insertIntoProcessQueue(attachs, false);
                    insertLinkedItems(attachs);
                    query = new BooleanQuery.Builder();
                    num = 0;
                }
            }
        }
    }

    private void insertLinkedItems(LuceneSearchResult result) {
        long t = System.currentTimeMillis();
        int[] luceneIds = result.getLuceneIds();
        Arrays.sort(luceneIds);
        String queryText = ExtraProperties.LINKED_ITEMS + ":*"; //$NON-NLS-1$
        IIPEDSearcher searcher = new IPEDSearcher(ipedCase, queryText);
        try {
            SearchResult itemsWithLinks = searcher.search();
            int numItems = 0;
            StringBuilder query = new StringBuilder();
            for (int i = 0; i < itemsWithLinks.getLength(); i++) {
                int luceneId = ipedCase.getLuceneId(itemsWithLinks.getId(i));
                if (Arrays.binarySearch(luceneIds, luceneId) < 0)
                    continue;

                Document doc = ipedCase.getReader().document(luceneId);
                String[] items = doc.getValues(ExtraProperties.LINKED_ITEMS);
                if (items.length > 0) {
                    LOGGER.debug("Linked items to '" + doc.get(IndexItem.NAME) + "' found: " + items.length); //$NON-NLS-1$
                }
                for (String item : items) {
                    query.append("(").append(item).append(") "); //$NON-NLS-1$
                    numItems++;
                }
                if (numItems >= 500) {
                    insertLinkedItemsBatch(query);
                    query = new StringBuilder();
                    numItems = 0;
                }
            }
            if (numItems > 0) {
                insertLinkedItemsBatch(query);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            t = System.currentTimeMillis() - t;
            LOGGER.info("Search for linked items took {} ms", t);
        }
    }

    private void insertLinkedItemsBatch(StringBuilder query) throws Exception {
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append(IndexItem.LENGTH + ":[3 TO *] AND ("); //$NON-NLS-1$
        queryBuilder.append(query.toString());
        queryBuilder.append(")"); //$NON-NLS-1$
        IIPEDSearcher searcher = new IPEDSearcher(ipedCase, queryBuilder.toString());

        LuceneSearchResult linkedItems = LuceneSearchResult.get(ipedCase, searcher.search());
        if (linkedItems.getLength() > 0) {
            insertIntoProcessQueue(linkedItems, false);
            insertParentTreeNodes(linkedItems);
        }
    }

    private int getId(String value) {
        int id = Integer.valueOf(value);
        if (oldToNewIdMap != null) {
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
        insertIntoProcessQueue(result, treeNode, true);
    }

    private void insertIntoProcessQueue(LuceneSearchResult result, boolean treeNode, boolean countVolume) throws Exception {

        for (int docID : result.getLuceneIds()) {
            Document doc = ipedCase.getReader().document(docID);

            Item evidence = new Item();

            int prevId = Integer.valueOf(doc.get(IndexItem.ID));

            if (addedItems.get(prevId)) {
                continue;
            }
            addedItems.set(prevId);

            String value = doc.get(IndexItem.LENGTH);
            Long len = null;
            if (value != null && !value.isEmpty()) {
                len = Long.valueOf(value);
            }

            evidence.setLength(len);
            if (treeNode || !countVolume) {
                evidence.setSumVolume(false);
            }

            if (listOnly) {
                caseData.incDiscoveredEvidences(1);
                if (!treeNode && countVolume) {
                    caseData.incDiscoveredVolume(len);
                }
                continue;
            }

            // TODO obter source corretamente
            IDataSource dataSource = new DataSource(null);
            dataSource.setUUID(doc.get(IndexItem.EVIDENCE_UUID));
            evidence.setDataSource(dataSource);

            evidence.setName(doc.get(IndexItem.NAME));

            if (!treeNode && caseData.isIpedReport()) {
                if (extractCheckedItems) {
                    selectedLabels.addAll(state.getBookmarkIds(prevId));
                    evidence.setLabels(state.getBookmarkList(prevId));
                } else
                    for (int labelId : state.getBookmarkIds(prevId)) {
                        if (state.isInReport(labelId)) {
                            selectedLabels.add(labelId);
                            evidence.getLabels().add(state.getBookmarkName(labelId));
                        }
                    }
            }

            int id = getId(doc.get(IndexItem.ID));
            evidence.setId(id);

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
                evidence.setType(value);
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

            value = doc.get(IndexItem.CHANGED);
            if (!value.isEmpty()) {
                evidence.setChangeDate(DateUtil.stringToDate(value));
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
                if ((value = doc.get(IndexItem.ID_IN_SOURCE)) != null) {
                    evidence.setIdInDataSource(value);
                }
                if (doc.get(IndexItem.SOURCE_PATH) != null && doc.get(IndexItem.SOURCE_DECODER) != null) {
                    String sourcePath = doc.get(IndexItem.SOURCE_PATH);
                    String className = doc.get(IndexItem.SOURCE_DECODER);
                    if (!MinIOInputInputStreamFactory.class.getName().equals(className)) {
                        sourcePath = Util.getResolvedFile(basePath, sourcePath).toString();
                    }
                    synchronized (inputStreamFactories) {
                        SeekableInputStreamFactory sisf = inputStreamFactories.get(sourcePath);
                        if (sisf == null) {
                            Class<?> clazz = Class.forName(className);
                            try {
                                Constructor<SeekableInputStreamFactory> c = (Constructor) clazz.getConstructor(Path.class);
                                sisf = c.newInstance(Path.of(sourcePath));

                            } catch (NoSuchMethodException e) {
                                Constructor<SeekableInputStreamFactory> c = (Constructor) clazz.getConstructor(URI.class);
                                sisf = c.newInstance(URI.create(sourcePath));
                            }
                            if (!ipedCase.isReport() && sisf.checkIfDataSourceExists()) {
                                IndexItem.checkIfExistsAndAsk(sisf, ipedCase.getModuleDir());
                            }
                            inputStreamFactories.put(sourcePath, sisf);
                        }
                        evidence.setInputStreamFactory(sisf);
                    }
                } else if (evidence.getMediaType().toString().contains(UfedXmlReader.UFED_MIME_PREFIX)) {
                    evidence.setInputStreamFactory(new MetadataInputStreamFactory(evidence.getMetadata()));

                } else {
                    if (MediaTypes.isMetadataEntryType(evidence.getMediaType())) {
                        evidence.setInputStreamFactory(new MetadataInputStreamFactory(evidence.getMetadata(), true));
                    }
                }
            } else {
                evidence.setExtraAttribute(IndexItem.TREENODE, "true"); //$NON-NLS-1$
                evidence.setAddToCase(false);
            }

            evidence.setTimeOut(Boolean.parseBoolean(doc.get(IndexItem.TIMEOUT)));

            evidence.setHasPreview(Boolean.parseBoolean(doc.get(IndexItem.HAS_PREVIEW)));
            evidence.setPreviewExt(doc.get(IndexItem.PREVIEW_EXT));
            evidence.setPreviewBaseFolder(indexDir.getParentFile());

            value = doc.get(IndexItem.HASH);
            if (value != null && !treeNode) {
                value = value.toUpperCase();
                evidence.setHash(value);

                if (!value.isEmpty() && caseData.isIpedReport()) {
                    File viewFile = Util.findFileFromHash(new File(indexDir.getParentFile(), PreviewConstants.VIEW_FOLDER_NAME), value);
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
                Class<?> c = IndexItem.getMetadataTypes().get(f.name());
                if (Item.getAllExtraAttributes().contains(f.name())) {
                    if (multiValuedFields.contains(f.name()))
                        continue;
                    if (isExtraAttrMultiValued(f.name())) {
                        multiValuedFields.add(f.name());
                        List<Object> fieldList = new ArrayList<>();
                        IndexableField[] fields = doc.getFields(f.name());
                        for (IndexableField field : fields)
                            fieldList.add(IndexItem.getCastedValue(c, field));
                        evidence.setExtraAttribute(f.name(), fieldList);
                    } else
                        evidence.setExtraAttribute(f.name(), IndexItem.getCastedValue(c, f));
                } else {
                    if (Date.class.equals(c) && f.stringValue() != null) {
                        String val = f.stringValue();
                        evidence.getMetadata().add(f.name(), val);
                    } else {
                        Object casted = IndexItem.getCastedValue(c, f);
                        if (casted != null) {
                            evidence.getMetadata().add(f.name(), casted.toString());
                        }
                    }
                }
            }

            // restore "face_encodings" to NDArray
            List<byte[]> features = (List<byte[]>) evidence.getExtraAttribute(SimilarFacesSearch.FACE_FEATURES);
            if (features != null) {
                List<NDArray<double[]>> featuresList = new ArrayList<>();
                for (byte[] featureBytes : features) {
                    float[] featureFloats = convByteArrayToFloatArray(featureBytes);
                    featuresList.add(convFloatArrayToNDArray(featureFloats));
                }
                evidence.setExtraAttribute(SimilarFacesSearch.FACE_FEATURES, featuresList);
            }

            Manager.getInstance().addItemToQueue(evidence);
        }

    }

    private static float[] convByteArrayToFloatArray(byte[] bytes) {
        float[] result = new float[bytes.length / 4];
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        for (int i = 0; i < result.length; i++) {
            result[i] = bb.getFloat();
        }
        return result;
    }

    public static final NDArray<double[]> convFloatArrayToNDArray(float[] array) {
        return new NDArray<double[]>(convFloatToDoubleArray(array));
    }

    public static final double[] convFloatToDoubleArray(float[] array) {
        double[] result = new double[array.length];
        for (int i = 0; i < array.length; i++) {
            result[i] = array[i];
        }
        return result;
    }

    private boolean isExtraAttrMultiValued(String field) throws IOException {
        Object docValues = ipedCase.getLeafReader().getSortedSetDocValues(field);
        if (docValues != null)
            return true;
        docValues = ipedCase.getLeafReader().getSortedNumericDocValues(field);
        if (docValues != null)
            return true;

        return false;
    }

}
