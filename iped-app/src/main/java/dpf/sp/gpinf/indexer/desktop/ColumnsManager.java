package dpf.sp.gpinf.indexer.desktop;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dialog.ModalityType;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableColumn;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.util.Bits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.config.AnalysisConfig;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.parsers.OCRParser;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.process.task.HashDBLookupTask;
import dpf.sp.gpinf.indexer.process.task.LanguageDetectTask;
import dpf.sp.gpinf.indexer.process.task.NamedEntityTask;
import dpf.sp.gpinf.indexer.process.task.PhotoDNALookup;
import dpf.sp.gpinf.indexer.process.task.regex.RegexTask;
import dpf.sp.gpinf.indexer.search.IPEDSource;
import dpf.sp.gpinf.indexer.search.LoadIndexFields;
import dpf.sp.gpinf.indexer.util.Util;
import gpinf.dev.data.Item;
import iped3.IItemId;
import iped3.desktop.IColumnsManager;
import iped3.desktop.ProgressDialog;
import iped3.util.BasicProps;
import iped3.util.ExtraProperties;

public class ColumnsManager implements ActionListener, Serializable, IColumnsManager {

    private static final long serialVersionUID = 1057562688829969313L;

    private static Logger LOGGER = LoggerFactory.getLogger(ColumnsManager.class);

    private static final File globalCols = getGlobalColsFile();

    private static final List<Integer> defaultWidths = Arrays.asList(50, 100, 200, 50, 100, 60, 150, 155, 155, 155, 155,
            155, 155, 250, 2000);

    public static final String[] groupNames = { Messages.getString("ColumnsManager.Basic"),
            Messages.getString("ColumnsManager.HashDB"), Messages.getString("ColumnsManager.Advanced"), //$NON-NLS-2$ //$NON-NLS-2$
            Messages.getString("ColumnsManager.Message"), Messages.getString("ColumnsManager.Audio"), //$NON-NLS-2$
            Messages.getString("ColumnsManager.Image"), Messages.getString("ColumnsManager.Video"), //$NON-NLS-1$
            Messages.getString("ColumnsManager.PDF"), Messages.getString("ColumnsManager.Office"), //$NON-NLS-1$ //$NON-NLS-2$
            Messages.getString("ColumnsManager.HTML"), Messages.getString("ColumnsManager.Regex"), //$NON-NLS-1$ //$NON-NLS-2$
            Messages.getString("ColumnsManager.Language"), Messages.getString("ColumnsManager.NamedEntity"), //$NON-NLS-1$ //$NON-NLS-2$
            Messages.getString("ColumnsManager.UFED"), Messages.getString("ColumnsManager.Other"), //$NON-NLS-1$ //$NON-NLS-2$
            Messages.getString("ColumnsManager.All") }; //$NON-NLS-1$

    private static final String emptyFilter = Messages.getString("ColumnsManager.Filter");

    private static final File getGlobalColsFile() {
        String name = "visibleCols"; //$NON-NLS-1$
        String locale = System.getProperty(iped3.util.Messages.LOCALE_SYS_PROP); // $NON-NLS-1$
        if (locale != null && !locale.equals("pt-BR")) //$NON-NLS-1$
            name += "-" + locale; //$NON-NLS-1$
        name += ".dat"; //$NON-NLS-1$
        return new File(System.getProperty("user.home") + "/.indexador/" + name); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static final String[] defaultFields = { ResultTableModel.SCORE_COL, ResultTableModel.BOOKMARK_COL,
            IndexItem.NAME, IndexItem.TYPE, IndexItem.LENGTH, IndexItem.DELETED, IndexItem.CATEGORY, IndexItem.CREATED,
            IndexItem.MODIFIED, IndexItem.ACCESSED, IndexItem.RECORDDATE, IndexItem.TIMESTAMP, IndexItem.TIME_EVENT,
            IndexItem.HASH, IndexItem.PATH };

    private static final String[] extraFields = { IndexItem.CARVED, IndexItem.CONTENTTYPE, IndexItem.DUPLICATE,
            IndexItem.EXPORT, IndexItem.HASCHILD, IndexItem.ID, IndexItem.ISDIR, IndexItem.ISROOT, IndexItem.PARENTID,
            IndexItem.PARENTIDs, IndexItem.SUBITEMID, IndexItem.SLEUTHID, IndexItem.ID_IN_SOURCE, IndexItem.SOURCE_PATH,
            IndexItem.SOURCE_DECODER, IndexItem.SUBITEM, IndexItem.TIMEOUT, IndexItem.TREENODE, IndexItem.EVIDENCE_UUID,
            IndexerDefaultParser.PARSER_EXCEPTION, OCRParser.OCR_CHAR_COUNT, ExtraProperties.CSAM_HASH_HITS,
            ExtraProperties.P2P_REGISTRY_COUNT, ExtraProperties.SHARED_HASHES, ExtraProperties.SHARED_ITEMS,
            ExtraProperties.LINKED_ITEMS, ExtraProperties.TIKA_PARSER_USED, IndexItem.META_ADDRESS,
            IndexItem.MFT_SEQUENCE, IndexItem.FILESYSTEM_ID };

    public static final String[] email = ExtraProperties.EMAIL_PROPS;

    private static ColumnsManager instance;

    private IPEDSource lastCase;

    private File caseCols;

    String[] indexFields = null;

    public String[][] fieldGroups;

    ColumnState colState = new ColumnState();

    private ArrayList<String> loadedFields = new ArrayList<String>();

    private JDialog dialog = new JDialog();
    private JPanel listPanel = new JPanel();
    private JComboBox<Object> combo;
    private JCheckBox autoManage = new JCheckBox(Messages.getString("ColumnsManager.AutoManageCols")); //$NON-NLS-1$
    private JTextField textFieldNameFilter;
    private int firstColsToPin = 7;

    private boolean autoManageCols;

    public static ColumnsManager getInstance() {
        if (instance == null)
            instance = new ColumnsManager();
        return instance;
    }

    public void dispose() {
        dialog.setVisible(false);
        instance = null;
        getInstance().loadedFields = this.loadedFields;
        getInstance().colState = this.colState;
    }

    public void setVisible() {
        clearNameFilter();
        updateDinamicFields();
        updateList();
        dialog.setVisible(true);
        combo.requestFocus();
    }

    private void clearNameFilter() {
        textFieldNameFilter.setText(emptyFilter);
        textFieldNameFilter.setForeground(Color.gray);
    }

    public void setPinnedColumns(int firstColsToPin) {
        this.firstColsToPin = firstColsToPin;
    }

    public int getPinnedColumns() {
        return this.firstColsToPin;
    }

    @Override
    public String[] getLoadedCols() {
        String[] cols = loadedFields.toArray(new String[0]);
        return cols;
    }

    static class ColumnState implements Serializable {

        private static final long serialVersionUID = 1L;
        List<Integer> initialWidths = new ArrayList<Integer>();
        ArrayList<String> visibleFields = new ArrayList<String>();
    }

    public void saveColumnsState() {
        try {
            ColumnState cs = new ColumnState();
            for (int i = 0; i < App.get().resultsTable.getColumnModel().getColumnCount(); i++) {
                TableColumn tc = App.get().resultsTable.getColumnModel().getColumn(i);
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

    private ColumnsManager() {
        AnalysisConfig analysisConfig = ConfigurationManager.findObject(AnalysisConfig.class);
        autoManageCols = analysisConfig.isAutoManageCols();

        dialog.setBounds(new Rectangle(400, 400));
        dialog.setTitle(Messages.getString("ColumnsManager.Title")); //$NON-NLS-1$
        dialog.setAlwaysOnTop(true);

        JLabel label = new JLabel(Messages.getString("ColumnsManager.ShowCols")); //$NON-NLS-1$
        label.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        label.setAlignmentX(0);

        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(Color.WHITE);

        combo = new JComboBox<Object>(groupNames);
        combo.setAlignmentX(0);

        autoManage.setSelected(autoManageCols);
        autoManage.setAlignmentX(0);
        autoManage.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        autoManage.addActionListener(this);

        textFieldNameFilter = new JTextField();
        textFieldNameFilter.setAlignmentX(0);
        clearNameFilter();
        textFieldNameFilter.addFocusListener(new FocusListener() {
            public void focusLost(FocusEvent e) {
                String text = textFieldNameFilter.getText().trim();
                if (text.isEmpty() || text.equals(emptyFilter)) {
                    clearNameFilter();
                }
            }

            public void focusGained(FocusEvent e) {
                String text = textFieldNameFilter.getText().trim();
                if (text.equals(emptyFilter)) {
                    textFieldNameFilter.setText("");
                }
                textFieldNameFilter.setForeground(Color.black);
            }
        });
        textFieldNameFilter.getDocument().addDocumentListener(new DocumentListener() {
            public void removeUpdate(DocumentEvent e) {
                changedUpdate(e);
            }

            public void insertUpdate(DocumentEvent e) {
                changedUpdate(e);
            }

            public void changedUpdate(DocumentEvent e) {
                if (textFieldNameFilter.isFocusOwner()) {
                    updateList();
                }
            }
        });

        Box topPanel = Box.createVerticalBox();
        topPanel.add(autoManage);
        topPanel.add(label);
        topPanel.add(combo);
        topPanel.add(textFieldNameFilter);
        combo.addActionListener(this);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        panel.add(topPanel, BorderLayout.NORTH);

        JScrollPane scrollList = new JScrollPane(listPanel);
        scrollList.getVerticalScrollBar().setUnitIncrement(10);
        panel.add(scrollList, BorderLayout.CENTER);

        dialog.getContentPane().add(panel);
        dialog.setLocationRelativeTo(App.get());

        updateDinamicFields();

        loadSavedCols();

        updateList();
    }

    private File getColStateFile() {
        File moduleDir = App.get().appCase.getAtomicSourceBySourceId(0).getModuleDir();
        caseCols = new File(moduleDir, "visibleCols.dat"); //$NON-NLS-1$
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

    public void updateDinamicCols() {
        if (!autoManageCols)
            return;

        if (App.get().ipedResult.getLength() == App.get().appCase.getTotalItens()
                || App.get().ipedResult.getLength() == 0)
            return;

        final ProgressDialog progress = new ProgressDialog(App.get(), null, false, 100, ModalityType.TOOLKIT_MODAL);
        progress.setNote(Messages.getString("ColumnsManager.LoadingCols")); //$NON-NLS-1$

        new Thread() {
            public void run() {
                final Set<String> usedCols = getUsedCols(progress);

                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        updateDinamicCols(usedCols);
                        progress.close();
                    }
                });
            }
        }.start();
    }

    private Set<String> getUsedCols2(ProgressDialog progress) {

        progress.setMaximum(indexFields.length);

        Collator collator = Collator.getInstance();
        collator.setStrength(Collator.PRIMARY);
        TreeSet<String> dinamicFields = new TreeSet<>(collator);

        int[] docs = new int[App.get().ipedResult.getLength()];
        int i = 0;
        for (IItemId item : App.get().ipedResult.getIterator())
            docs[i++] = App.get().appCase.getLuceneId(item);
        Arrays.sort(docs);

        int[] docBases = new int[App.get().appCase.getReader().leaves().size() + 1];
        for (i = 0; i < docBases.length - 1; i++)
            docBases[i] = App.get().appCase.getReader().leaves().get(i).docBase;
        docBases[docBases.length - 1] = Integer.MAX_VALUE;

        int p = 0;
        for (String field : indexFields) {
            if (progress.isCanceled())
                return null;
            try {
                int baseOrd = 0;
                int MAX_ITEMS_TO_CHECK = 50;
                int interval = docs.length / MAX_ITEMS_TO_CHECK;
                if (interval == 0)
                    interval = 1;
                LeafReader reader = null;
                Bits bits0 = null, bits1 = null, bits2 = null;
                for (i = 0; i < docs.length; i += interval) {
                    while (docs[i] >= docBases[baseOrd + 1]) {
                        baseOrd++;
                        reader = null;
                    }
                    if (reader == null) {
                        reader = App.get().appCase.getReader().leaves().get(baseOrd).reader();
                        bits0 = reader.getDocsWithField(field);
                        bits1 = reader.getDocsWithField(IndexItem.POSSIBLE_NUM_DOCVALUES_PREFIX + field); // $NON-NLS-1$
                        bits2 = reader.getDocsWithField(IndexItem.POSSIBLE_STR_DOCVALUES_PREFIX + field); // $NON-NLS-1$
                    }
                    int doc = docs[i] - docBases[baseOrd];
                    if ((bits2 != null && bits2.get(doc)) || (bits1 != null && bits1.get(doc))
                            || (bits0 != null && bits0.get(doc))) {
                        dinamicFields.add(field);
                        break;
                    }
                }
                // t1 += System.currentTimeMillis() - tb;

            } catch (IOException e) {
                e.printStackTrace();
            }
            progress.setProgress(++p);
        }
        // System.out.println("t0 = " + t0);
        // System.out.println("t1 = " + t1);

        return dinamicFields;
    }

    private Set<String> getUsedCols(ProgressDialog progress) {

        Collator collator = Collator.getInstance();
        collator.setStrength(Collator.PRIMARY);
        TreeSet<String> dinamicFields = new TreeSet<>(collator);

        int[] docs = new int[App.get().ipedResult.getLength()];
        int i = 0;
        for (IItemId item : App.get().ipedResult.getIterator())
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

    private void updateDinamicCols(Set<String> dinamicFields) {

        if (dinamicFields == null)
            return;

        Set<String> colNamesToPin = new HashSet<>();
        for (int i = 2; i < Math.min(firstColsToPin, App.get().resultsTable.getColumnCount()); i++) {
            colNamesToPin.add(
                    App.get().resultsTable.getColumnModel().getColumn(i).getHeaderValue().toString().toLowerCase());
        }

        for (String field : (List<String>) colState.visibleFields.clone()) {
            if (!dinamicFields.contains(field) && !colNamesToPin.contains(field.toLowerCase())
                    && !field.equals(BasicProps.LENGTH)) // length header changes to listed items total size info
            {
                updateGUICol(field, false);
            }
        }

        int newColStart = App.get().resultsTable.getColumnCount();

        for (String field : dinamicFields) {
            if (!colState.visibleFields.contains(field))
                updateGUICol(field, true);
        }

        // move important new cols to front
        int newPosEmail = firstColsToPin;
        int newPosOther = firstColsToPin;
        for (int i = newColStart; i < App.get().resultsTable.getColumnCount(); i++) {
            TableColumn col = App.get().resultsTable.getColumnModel().getColumn(i);
            String colName = col.getHeaderValue().toString();
            if (colName.startsWith(ExtraProperties.MESSAGE_PREFIX)) {
                App.get().resultsTable.moveColumn(i, newPosEmail++);
                newPosOther++;
            } else if (colName.toLowerCase().startsWith(ExtraProperties.UFED_META_PREFIX)) {
                App.get().resultsTable.moveColumn(i, newPosOther++);
            }
        }

        // move important old cols to front
        int lastOldCol = newColStart - 1 + newPosOther - firstColsToPin;
        newPosEmail = firstColsToPin;
        for (int i = newPosOther; i <= lastOldCol; i++) {
            TableColumn col = App.get().resultsTable.getColumnModel().getColumn(i);
            String colName = col.getHeaderValue().toString();
            if (colName.startsWith(ExtraProperties.MESSAGE_PREFIX)) {
                App.get().resultsTable.moveColumn(i, newPosEmail++);
            }
        }
    }

    public void moveTimelineColumns(int newPos) {
        String[] timeFields = { BasicProps.TIMESTAMP, BasicProps.TIME_EVENT };
        for (int i = 0; i < App.get().resultsTable.getColumnCount(); i++) {
            TableColumn col = App.get().resultsTable.getColumnModel().getColumn(i);
            String colName = col.getHeaderValue().toString();
            for (int k = 0; k < timeFields.length; k++) {
                if (colName.equalsIgnoreCase(timeFields[k])) {
                    if (!colState.visibleFields.contains(timeFields[k])) {
                        updateGUICol(colName, true);
                    }
                    App.get().resultsTable.moveColumn(i, newPos);
                    if (newPos > i) {
                        i--;
                    } else {
                        newPos++;
                    }
                    timeFields[k] = null;
                }
            }
        }
    }

    public void resetToLastLayout() {
        File cols = this.getColStateFile();
        try {
            ColumnState lastState = (ColumnState) Util.readObject(cols.getAbsolutePath());
            resetColumns(lastState.visibleFields, lastState.initialWidths);

        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }

    public void resetToDefaultLayout() {
        // resetColumns(Arrays.asList(defaultFields).stream().map(f ->
        // BasicProps.getLocalizedField(f))
        // .collect(Collectors.toList()), defaultWidths);
        resetColumns(Arrays.asList(defaultFields), defaultWidths);
    }

    public void resetColumns(List<String> newCols, List<Integer> widths) {
        for (String field : (List<String>) colState.visibleFields.clone())
            if (!newCols.contains(field) && !field.equals(ResultTableModel.SCORE_COL)
                    && !field.equals(ResultTableModel.BOOKMARK_COL))
                updateGUICol(field, false);

        for (String field : newCols) {
            if (!colState.visibleFields.contains(field))
                updateGUICol(field, true);
        }

        int newPos = 2;
        for (String col : newCols) {
            col = BasicProps.getLocalizedField(col);
            for (int i = 0; i < App.get().resultsTable.getColumnModel().getColumnCount(); i++) {
                TableColumn tc = App.get().resultsTable.getColumnModel().getColumn(i);
                if (tc.getHeaderValue() instanceof String && ((String) tc.getHeaderValue())
                        .startsWith(col.substring(0, 1).toUpperCase() + col.substring(1))) {
                    App.get().resultsTable.moveColumn(i, newPos++);
                }
            }
        }

        int j = 0;
        for (int i = 0; i < App.get().resultsTable.getColumnModel().getColumnCount(); i++) {
            TableColumn tc = App.get().resultsTable.getColumnModel().getColumn(i);
            if (tc.getModelIndex() >= ResultTableModel.fixedCols.length && j < widths.size()) {
                tc.setPreferredWidth(widths.get(j++));
            }
        }
    }

    private void updateDinamicFields() {

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
        ArrayList<String> ufedFields = new ArrayList<String>();
        ArrayList<String> hashDbFields = new ArrayList<String>();

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
            else if (f.startsWith(ExtraProperties.UFED_META_PREFIX))
                ufedFields.add(f);
        }

        String[][] customGroups = new String[][] { defaultFields.clone(), hashDbFields.toArray(new String[0]),
                extraAttrs.toArray(new String[0]), email,
                audioFields.toArray(new String[0]), imageFields.toArray(new String[0]),
                videoFields.toArray(new String[0]), pdfFields.toArray(new String[0]),
                officeFields.toArray(new String[0]), htmlFields.toArray(new String[0]),
                regexFields.toArray(new String[0]), languageFields.toArray(new String[0]),
                nerFields.toArray(new String[0]), ufedFields.toArray(new String[0]) };

        ArrayList<String> otherFields = new ArrayList<String>();
        for (String f : indexFields) {
            boolean insertField = true;
            for (int i = 0; i < customGroups.length; i++)
                if (Arrays.asList(customGroups[i]).contains(f)) {
                    insertField = false;
                    break;
                }
            if (f.startsWith(BasicProps.SIMILARITY_FEATURES))
                continue;
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

    private void updateList() {
        listPanel.removeAll();
        String[] fields = fieldGroups[combo.getSelectedIndex()];
        String filter = textFieldNameFilter.getText();
        if (filter.equals(emptyFilter)) {
            filter = "";
        } else {
            filter = filter.trim().toLowerCase();
        }
        for (String f : fields) {
            if (filter.isEmpty() || f.toLowerCase().indexOf(filter) >= 0) {
                JCheckBox check = new JCheckBox();
                check.setText(BasicProps.getLocalizedField(f));
                if (colState.visibleFields.contains(f))
                    check.setSelected(true);
                check.addActionListener(this);
                listPanel.add(check);
            }
        }
        dialog.revalidate();
        dialog.repaint();
    }

    @Override
    public void actionPerformed(ActionEvent e) {

        if (e.getSource().equals(autoManage))
            autoManageCols = autoManage.isSelected();
        else if (e.getSource().equals(combo)) {
            updateList();
        } else {
            JCheckBox source = (JCheckBox) e.getSource();
            updateGUICol(source.getText(), source.isSelected());
        }

    }

    private Map<String, Integer> lastWidths = new HashMap<>();

    private void updateGUICol(String colName, boolean insert) {

        colName = BasicProps.getNonLocalizedField(colName);
        int modelIdx = loadedFields.indexOf(colName);
        if (insert) {
            colState.visibleFields.add(colName);
            if (modelIdx == -1) {
                loadedFields.add(colName);
                App.get().resultsModel.updateCols();
                modelIdx = ResultTableModel.fixedCols.length + loadedFields.size() - 1;
            } else
                modelIdx += ResultTableModel.fixedCols.length;

            TableColumn tc = new TableColumn(modelIdx);
            if (lastWidths.containsKey(colName))
                tc.setPreferredWidth(lastWidths.get(colName));
            else
                tc.setPreferredWidth(150);
            App.get().resultsTable.addColumn(tc);
            setColumnRenderer(tc);
        } else {
            colState.visibleFields.remove(colName);
            modelIdx += ResultTableModel.fixedCols.length;
            int viewIdx = App.get().resultsTable.convertColumnIndexToView(modelIdx);
            if (viewIdx > -1) {
                TableColumn col = App.get().resultsTable.getColumnModel().getColumn(viewIdx);
                lastWidths.put(colName, col.getWidth());
                App.get().resultsTable.removeColumn(col);
            }
        }
    }

    public void setColumnRenderer(TableColumn tc) {
        if (ResultTableModel.SCORE_COL.equals(loadedFields.get(tc.getModelIndex() - ResultTableModel.fixedCols.length)))
            tc.setCellRenderer(new ProgressCellRenderer());
    }

}
