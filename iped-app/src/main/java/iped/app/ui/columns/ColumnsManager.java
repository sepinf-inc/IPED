package iped.app.ui.columns;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.table.TableColumn;

import org.apache.lucene.document.Document;
import org.apache.tika.metadata.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.app.ui.App;
import iped.app.ui.Messages;
import iped.app.ui.ResultTableModel;
import iped.data.IItemId;
import iped.engine.config.AnalysisConfig;
import iped.engine.config.ConfigurationManager;
import iped.engine.data.IPEDSource;
import iped.engine.data.Item;
import iped.engine.search.LoadIndexFields;
import iped.engine.task.HashDBLookupTask;
import iped.engine.task.LanguageDetectTask;
import iped.engine.task.NamedEntityTask;
import iped.engine.task.PhotoDNALookup;
import iped.engine.task.index.IndexItem;
import iped.engine.task.regex.RegexTask;
import iped.engine.util.Util;
import iped.parsers.evtx.EvtxParser;
import iped.parsers.ocr.OCRParser;
import iped.parsers.standard.StandardParser;
import iped.properties.ExtraProperties;
import iped.viewers.api.IColumnsManager;
import iped.viewers.util.ProgressDialog;

public class ColumnsManager implements Serializable, IColumnsManager {

    private static final long serialVersionUID = 1057562688829969313L;

    private static Logger LOGGER = LoggerFactory.getLogger(ColumnsManager.class);

    private static final File globalCols = getGlobalColsFile();

    public static final String[] groupNames = { Messages.getString("ColumnsManager.Basic"),
            Messages.getString("ColumnsManager.HashDB"), Messages.getString("ColumnsManager.Advanced"), //$NON-NLS-2$ //$NON-NLS-2$
            Messages.getString("ColumnsManager.Common"), // $NON-NLS-2$
            Messages.getString("ColumnsManager.Communication"), Messages.getString("ColumnsManager.Audio"), //$NON-NLS-2$
            Messages.getString("ColumnsManager.Image"), Messages.getString("ColumnsManager.Video"), //$NON-NLS-1$
            Messages.getString("ColumnsManager.PDF"), Messages.getString("ColumnsManager.Office"), //$NON-NLS-1$ //$NON-NLS-2$
            Messages.getString("ColumnsManager.HTML"), Messages.getString("ColumnsManager.Regex"), //$NON-NLS-1$ //$NON-NLS-2$
            Messages.getString("ColumnsManager.Language"), Messages.getString("ColumnsManager.NamedEntity"), //$NON-NLS-1$ //$NON-NLS-2$
            Messages.getString("ColumnsManager.PeerToPeer"), Messages.getString("ColumnsManager.UFED"), //$NON-NLS-1$ //$NON-NLS-2$
            Messages.getString("ColumnsManager.WindowsEvt"), //$NON-NLS-1$ //$NON-NLS-2$
            Messages.getString("ColumnsManager.Other"), Messages.getString("ColumnsManager.All") }; //$NON-NLS-1$ //$NON-NLS-2$

    private static final File getGlobalColsFile() {
        String name = "visibleCols"; //$NON-NLS-1$
        String locale = System.getProperty(iped.localization.Messages.LOCALE_SYS_PROP); // $NON-NLS-1$
        if (locale != null && !locale.equals("pt-BR")) //$NON-NLS-1$
            name += "-" + locale; //$NON-NLS-1$
        name += ".dat"; //$NON-NLS-1$
        return new File(System.getProperty("user.home") + "/.iped/" + name); //$NON-NLS-1$ //$NON-NLS-2$
    }


    protected static final List<Integer> defaultWidths = Arrays.asList(50, 100, 200, 50, 50, 100, 60, 150, 155, 155, 155, 155,
            155, 155, 250, 2000);
    
    protected static final String[] defaultFields = { ResultTableModel.SCORE_COL, ResultTableModel.BOOKMARK_COL,
            IndexItem.NAME, IndexItem.EXT, IndexItem.TYPE, IndexItem.LENGTH, IndexItem.DELETED, IndexItem.CATEGORY, IndexItem.CREATED,
            IndexItem.MODIFIED, IndexItem.ACCESSED, IndexItem.CHANGED, IndexItem.TIMESTAMP, IndexItem.TIME_EVENT,
            IndexItem.HASH, IndexItem.PATH };

    protected static final String[] extraFields = { IndexItem.CARVED, IndexItem.CONTENTTYPE,
            IndexItem.HASCHILD, IndexItem.ID, IndexItem.ISDIR, IndexItem.ISROOT, IndexItem.PARENTID,
            IndexItem.PARENTIDs, IndexItem.SUBITEMID, IndexItem.ID_IN_SOURCE, IndexItem.SOURCE_PATH,
            IndexItem.SOURCE_DECODER, IndexItem.SUBITEM, IndexItem.TIMEOUT, IndexItem.TREENODE, IndexItem.EVIDENCE_UUID,
            StandardParser.PARSER_EXCEPTION, OCRParser.OCR_CHAR_COUNT, ExtraProperties.CSAM_HASH_HITS,
            ExtraProperties.P2P_REGISTRY_COUNT, ExtraProperties.SHARED_HASHES, ExtraProperties.SHARED_ITEMS,
            ExtraProperties.LINKED_ITEMS, ExtraProperties.TIKA_PARSER_USED, IndexItem.META_ADDRESS,
            IndexItem.MFT_SEQUENCE, IndexItem.FILESYSTEM_ID };

    public static final String VISIBLE_COLUMNS_FILENAME = "data/visiblecols.dat";

    private static ColumnsManager instance;
    private static File moduleDir;

    private IPEDSource lastCase;
    private File caseCols;

    String[] indexFields = null;

    public String[][] fieldGroups;

    public ColumnState colState = new ColumnState();

    protected ArrayList<String> loadedFields = new ArrayList<String>();

    private boolean autoManageCols;

    public static ColumnsManager getInstance() {
        if (instance == null)
            instance = new ColumnsManager();
        return instance;
    }

    public boolean isAutoManageCols() {
        return autoManageCols;
    }
    public void  setAutoManageCols(boolean autoManageCOls) {
        this.autoManageCols = autoManageCOls;
    }

    @Override
    public String[] getLoadedCols() {
        String[] cols = loadedFields.toArray(new String[0]);
        return cols;
    }

    public ArrayList<String> getLoadedFields() {
        return loadedFields;
    }

    public static class ColumnState implements Serializable {

        private static final long serialVersionUID = 1L;
        public List<Integer> initialWidths = new ArrayList<Integer>();
        public ArrayList<String> visibleFields = new ArrayList<String>();
    }

    public static class CheckBoxState {
        boolean isSelected;
        boolean isEnabled;

        public CheckBoxState(boolean isSelected) {
            this.isSelected = isSelected;
            this.isEnabled = true;
        }

        public CheckBoxState(boolean isSelected, boolean isEnabled) {
            this.isSelected = isSelected;
            this.isEnabled = isEnabled;
        }
    };

    protected ColumnsManager() {
        AnalysisConfig analysisConfig = ConfigurationManager.get().findObject(AnalysisConfig.class);
        autoManageCols = analysisConfig.isAutoManageCols();

        moduleDir = App.get().appCase.getAtomicSourceBySourceId(0).getModuleDir();

        updateDinamicFields();

        loadSavedCols();
    }

    public void saveColumnsState() {
        try {
            ColumnState cs = new ColumnState();
            for (int i = 0; i < App.get().getResultsTable().getColumnModel().getColumnCount(); i++) {
                TableColumn tc = App.get().getResultsTable().getColumnModel().getColumn(i);
                if (tc.getModelIndex() >= ResultTableModel.fixedCols.length) {
                    int idx = tc.getModelIndex() - ResultTableModel.fixedCols.length;
                    cs.visibleFields.add(loadedFields.get(idx));
                    cs.initialWidths.add(tc.getWidth());
                }
            }
            if (cs.visibleFields.size() > 0) {
                globalCols.getParentFile().mkdirs();
                Util.writeObject(cs, globalCols.getAbsolutePath());
                Util.writeObject(cs, caseCols.getAbsolutePath());
            }
        } catch (Exception e1) {
            e1.printStackTrace();
        }
    }

    protected File getColStateFile() {
        caseCols = new File(moduleDir, VISIBLE_COLUMNS_FILENAME);
        File cols = caseCols;
        if (!cols.exists())
            cols = globalCols;
        return cols;
    }

    private void loadSavedCols() {
        File cols = getColStateFile();
        boolean lastColsOk = false;
        if (cols.exists()) {
            try {
                colState = (ColumnState) Util.readObject(cols.getAbsolutePath());
                loadedFields = (ArrayList<String>) colState.visibleFields.clone();
                if (loadedFields.size() > 0) {
                    lastColsOk = true;
                    HashSet<String> indexedSet = new HashSet<String>();
                    indexedSet.addAll(Arrays.asList(indexFields));
                    // remove inexistent columns in current case
                    int removed = 0;
                    for (int i = 0; i < loadedFields.size(); i++) {
                        String field = loadedFields.get(i);
                        if (!indexedSet.contains(field) && !field.equals(ResultTableModel.SCORE_COL)
                                && !field.equals(ResultTableModel.BOOKMARK_COL)) {
                            colState.visibleFields.remove(i - removed);
                            colState.initialWidths.remove(i - removed);
                            removed++;
                        }
                    }
                    if (removed > 0)
                        loadedFields = (ArrayList<String>) colState.visibleFields.clone();
                }

            } catch (ClassNotFoundException | IOException e) {
                e.printStackTrace();
            }
        }
        if (!lastColsOk) {
            LOGGER.info("Loading default columns"); //$NON-NLS-1$
            for (String col : defaultFields)
                loadedFields.add(col);
            colState.visibleFields = (ArrayList<String>) loadedFields.clone();
            colState.initialWidths = defaultWidths;
        }
    }

    public Set<String> getVisibleColumns() {
        return new TreeSet<String>(colState.visibleFields);
    }

    protected Set<String> getUsedCols(ProgressDialog progress) {
        Collator collator = Collator.getInstance();
        collator.setStrength(Collator.PRIMARY);
        TreeSet<String> dinamicFields = new TreeSet<>(collator);

        int[] docs = new int[App.get().getResults().getLength()];
        int i = 0;
        for (IItemId item : App.get().getResults().getIterator())
            docs[i++] = App.get().appCase.getLuceneId(item);

        int MAX_ITEMS_TO_CHECK = 50;
        progress.setMaximum(MAX_ITEMS_TO_CHECK);

        int interval = docs.length / MAX_ITEMS_TO_CHECK;
        if (interval == 0)
            interval = 1;

        int p = 0;
        for (i = 0; i < docs.length; i += interval) {
            if (progress.isCanceled())
                return null;
            try {
                Document doc = App.get().appCase.getReader().document(docs[i]);
                for (String field : indexFields) {
                    if (doc.getField(field) != null)
                        dinamicFields.add(field);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            progress.setProgress(++p);
        }
        return dinamicFields;
    }

    void updateDinamicFields() {

        if (indexFields == null || lastCase != App.get().appCase) {
            lastCase = App.get().appCase;
            indexFields = LoadIndexFields.getFields(App.get().appCase.getAtomicSources());
        }

        TreeSet<String> extraAttrs = new TreeSet<String>();
        extraAttrs.addAll(Arrays.asList(extraFields));
        extraAttrs.addAll(Item.getAllExtraAttributes());
        String[] allExtraAttrs = extraAttrs.toArray(new String[0]);
        extraAttrs.clear();

        ArrayList<String> regexFields = new ArrayList<String>();
        ArrayList<String> languageFields = new ArrayList<String>();
        ArrayList<String> audioFields = new ArrayList<String>();
        ArrayList<String> imageFields = new ArrayList<String>();
        ArrayList<String> videoFields = new ArrayList<String>();
        ArrayList<String> pdfFields = new ArrayList<String>();
        ArrayList<String> officeFields = new ArrayList<String>();
        ArrayList<String> htmlFields = new ArrayList<String>();
        ArrayList<String> nerFields = new ArrayList<String>();
        ArrayList<String> p2pFields = new ArrayList<String>();
        ArrayList<String> ufedFields = new ArrayList<String>();
        ArrayList<String> hashDbFields = new ArrayList<String>();
        ArrayList<String> communicationFields = new ArrayList<String>();
        ArrayList<String> commonFields = new ArrayList<String>();
        ArrayList<String> winEvtFields = new ArrayList<String>();

        for (String f : allExtraAttrs) {
            if (f.startsWith(RegexTask.REGEX_PREFIX))
                regexFields.add(f);
            else if (f.startsWith(LanguageDetectTask.LANGUAGE_PREFIX))
                languageFields.add(f);
            else if (f.startsWith(HashDBLookupTask.ATTRIBUTES_PREFIX)
                    || f.startsWith(PhotoDNALookup.PHOTO_DNA_HIT_PREFIX))
                hashDbFields.add(f);
            else
                extraAttrs.add(f);
        }

        for (String f : indexFields) {
            if (f.startsWith(ExtraProperties.AUDIO_META_PREFIX))
                audioFields.add(f);
            else if (f.startsWith(ExtraProperties.IMAGE_META_PREFIX))
                imageFields.add(f);
            else if (f.startsWith(ExtraProperties.VIDEO_META_PREFIX))
                videoFields.add(f);
            else if (f.startsWith(ExtraProperties.PDF_META_PREFIX))
                pdfFields.add(f);
            else if (f.startsWith(ExtraProperties.OFFICE_META_PREFIX))
                officeFields.add(f);
            else if (f.startsWith(ExtraProperties.HTML_META_PREFIX))
                htmlFields.add(f);
            else if (f.startsWith(NamedEntityTask.NER_PREFIX))
                nerFields.add(f);
            else if (f.startsWith(ExtraProperties.P2P_META_PREFIX))
                p2pFields.add(f);
            else if (f.startsWith(ExtraProperties.UFED_META_PREFIX))
                ufedFields.add(f);
            else if (f.startsWith(Message.MESSAGE_PREFIX) || ExtraProperties.COMMUNICATION_BASIC_PROPS.contains(f))
                communicationFields.add(f);
            else if (f.startsWith(ExtraProperties.COMMON_META_PREFIX))
                commonFields.add(f);
            else if (f.startsWith(EvtxParser.EVTX_METADATA_PREFIX)) {
                winEvtFields.add(f);
            }
        }

        String[][] customGroups = new String[][] { defaultFields.clone(), hashDbFields.toArray(new String[0]),
                extraAttrs.toArray(new String[0]), commonFields.toArray(new String[0]),
                communicationFields.toArray(new String[0]),
                audioFields.toArray(new String[0]), imageFields.toArray(new String[0]),
                videoFields.toArray(new String[0]), pdfFields.toArray(new String[0]),
                officeFields.toArray(new String[0]), htmlFields.toArray(new String[0]),
                regexFields.toArray(new String[0]), languageFields.toArray(new String[0]),
                nerFields.toArray(new String[0]), p2pFields.toArray(new String[0]),
                ufedFields.toArray(new String[0]), winEvtFields.toArray(new String[0]) };

        ArrayList<String> otherFields = new ArrayList<String>();
        for (String f : indexFields) {
            boolean insertField = true;
            for (int i = 0; i < customGroups.length; i++) {
                if (Arrays.asList(customGroups[i]).contains(f)) {
                    insertField = false;
                    break;
                }
            }
            if (insertField)
                otherFields.add(f);
        }

        fieldGroups = new String[customGroups.length + 2][];
        for (int i = 0; i < customGroups.length; i++)
            fieldGroups[i] = customGroups[i];
        fieldGroups[fieldGroups.length - 2] = otherFields.toArray(new String[0]);
        fieldGroups[fieldGroups.length - 1] = indexFields.clone();

        for (String[] fields : fieldGroups)
            Arrays.sort(fields, Collator.getInstance());

    }

}
