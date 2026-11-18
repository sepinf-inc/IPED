package iped.app.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;

import org.apache.lucene.document.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.data.IItemId;
import iped.data.IItem;
import iped.viewers.HtmlViewer;
import iped.viewers.api.AbstractViewer;
import iped.io.IStreamSource;
import iped.engine.data.IPEDMultiSource;
import iped.engine.task.index.IndexItem;

/** Incremental Word export from the highlighted analysis results. */
public class WordReportDialog extends JDialog {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = LoggerFactory.getLogger(WordReportDialog.class);
    private static WordReportDialog current;
    private final IPEDMultiSource caseSource = App.get().appCase;
    private final String caseKey = caseSource.getAtomicSources().stream()
            .map(source -> source.getCaseDir().toPath().toAbsolutePath().normalize().toString()).sorted()
            .collect(java.util.stream.Collectors.joining("\n"));

    private final List<ReportItem> items = new ArrayList<>();
    private static boolean capturingChat;

    private static class ReportItem {
        final IItemId id;
        final WordReportChatSelection.Result capture;
        ReportItem beside;
        int besideLocation = -1;

        ReportItem(IItemId id, WordReportChatSelection.Result capture) {
            this.id = id;
            this.capture = capture;
        }
    }
    private final JComboBox<String> sourceType = new JComboBox<>(new String[] { message("LocalFile"), message("SharedLink"), message("Template") });
    private final JTextField input = new JTextField();
    private final JComboBox<String> templates = new JComboBox<>();
    private final List<Path> templatePaths = new ArrayList<>();
    private final JComboBox<WordReportDocument.Section> sections = new JComboBox<>();
    private final DefaultListModel<WordReportDocument.Location> locationModel = new DefaultListModel<>();
    private final JList<WordReportDocument.Location> locations = new JList<>(locationModel);
    private final JComboBox<String> layout = new JComboBox<>(new String[] { message("OneEvidence"), message("TwoEvidence") });
    private final JCheckBox text = new JCheckBox(message("Text"), true);
    private final JLabel status = new JLabel(message("SelectDocument"));
    private final JLabel documentHint = new JLabel(message("NewCopyHint"));
    private final JLabel selectionCount = new JLabel();
    private final JLabel pageImage = new JLabel(message("PreviewHint"), JLabel.CENTER);
    private final JLabel pageNumber = new JLabel(" ");
    private final JScrollPane pageScroll = new JScrollPane(pageImage);
    private final JComboBox<String> zoom = new JComboBox<>(new String[] { message("FitPage"), "100%", "150%" });
    private final JPanel content = new JPanel(new BorderLayout(8, 8));
    private final JPanel loading = new JPanel(new GridBagLayout());
    private final JLabel activity = new JLabel();
    private final JButton loadDocument = button("Load", this::load);
    private final JButton addSelection = button("AddSelection", this::addSelectedItems);
    private final JButton view = button("Preview", this::preview);
    private final JButton saveReport = button("Save", () -> save(false));
    private final JButton saveAs = button("SaveAs", () -> save(true));
    private final JButton openReport = button("OpenReport", this::openReport);
    private final JButton close = button("Close", this::close);
    private final JButton previousPage = button("Previous", () -> showPage(this.page - 1));
    private final JButton nextPage = button("Next", () -> showPage(this.page + 1));
    private final DefaultTableModel tableModel;
    private final JTable table;
    private Path work;
    private Path source;
    private Path prepared;
    private WordReportDocument loaded;
    private WordReportPreview preview;
    private WordReportOffice office;
    private BufferedImage renderedPage;
    private int page;
    private List<ReportItem> preparedItems = new ArrayList<>();
    private int nextLocation;
    private boolean updatingSource;
    private boolean updatingEvidence;
    private boolean busy;
    private boolean updatingLocations;
    private boolean autoLoadSelection;
    private boolean workingDocument;

    private static String message(String key, Object... args) {
        return Messages.getString("WordReport." + key, args);
    }

    public static void showDialog() {
        if (current != null && current.isDisplayable() && current.busy) {
            current.toFront();
            return;
        }
        prepareSelection(selected -> {
            boolean created = current == null || !current.isDisplayable() || current.caseSource != App.get().appCase;
            if (created) {
                if (current != null) {
                    current.cleanup();
                    current.dispose();
                }
                current = new WordReportDialog();
            }
            current.setVisible(true);
            current.toFront();
            Runnable add = () -> {
                current.addItems(selected);
                if (created) {
                    current.layout.setSelectedIndex(selected.size() > 1 ? 1 : 0);
                }
            };
            if (created && current.autoLoadSelection) {
                current.load(add);
            } else {
                add.run();
            }
        });
    }

    private static void prepareSelection(Consumer<List<ReportItem>> done) {
        if (capturingChat) {
            return;
        }
        IPEDMultiSource source = App.get().appCase;
        List<ReportItem> selected = new ArrayList<>();
        ReportItem chat = null;
        try {
            for (int row : App.get().resultsTable.getSelectedRows()) {
                IItemId id = App.get().ipedResult.getItem(App.get().resultsTable.convertRowIndexToModel(row));
                Document doc = source.getSearcher().doc(source.getLuceneId(id));
                ReportItem item = new ReportItem(id, null);
                if (WordReportEvidence.isChat(doc.get(IndexItem.CONTENTTYPE), doc.get(IndexItem.NAME))) {
                    if (chat != null) {
                        JOptionPane.showMessageDialog(App.get(), message("OneChatAtATime"));
                        return;
                    }
                    chat = item;
                }
                selected.add(item);
            }
            if (chat == null) {
                done.accept(selected);
                return;
            }
            ViewerController controller = App.get().getViewerController();
            IItem viewed = controller.getViewedItem();
            AbstractViewer viewer = controller.getMultiViewer().getCurrentViewer();
            IStreamSource content = controller.getViewedContent();
            Document doc = source.getSearcher().doc(source.getLuceneId(chat.id));
            if (!(viewer instanceof HtmlViewer) || !viewer.getPanel().isShowing() || viewed == null
                    || viewed.getId() != chat.id.getId() || viewed.getDataSource() == null
                    || !viewed.getDataSource().getUUID().equals(doc.get(IndexItem.EVIDENCE_UUID))) {
                JOptionPane.showMessageDialog(App.get(), message("OpenChatPreview"));
                return;
            }
            ReportItem selectedChat = chat;
            capturingChat = true;
            App.get().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            ((HtmlViewer) viewer).captureVisible(content).whenComplete((capture, failure) -> SwingUtilities.invokeLater(() -> {
                capturingChat = false;
                App.get().setCursor(Cursor.getDefaultCursor());
                if (App.get().appCase != source) {
                    return;
                }
                if (failure != null || controller.getViewedItem() != viewed || controller.getViewedContent() != content
                        || controller.getMultiViewer().getCurrentViewer() != viewer || capture.messages.isEmpty()) {
                    JOptionPane.showMessageDialog(App.get(), message("OpenChatPreview"));
                    return;
                }
                WordReportChatSelection.Result crop = WordReportChatCropDialog.show(capture);
                if (crop != null) {
                    selected.set(selected.indexOf(selectedChat), new ReportItem(selectedChat.id, crop));
                    done.accept(selected);
                }
            }));
        } catch (Exception e) {
            LOGGER.warn("Word chat capture failed: {}", e.getClass().getSimpleName());
            JOptionPane.showMessageDialog(App.get(), message("OpenChatPreview"));
        }
    }

    public WordReportDialog() {
        super(App.get(), message("Title"), false);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        Dimension size = new Dimension(Math.min(1200, screen.width), Math.min(900, screen.height));
        setMinimumSize(new Dimension(Math.min(850, size.width), Math.min(600, size.height)));
        setSize(size);
        setLocationRelativeTo(App.get());
        tableModel = new DefaultTableModel(new Object[] { message("Include"), message("ItemId"), message("Name"), message("ItemStatus") }, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public Class<?> getColumnClass(int column) {
                return column == 0 ? Boolean.class : String.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0;
            }
        };
        table = new JTable(tableModel);
        table.setAutoCreateRowSorter(true);
        table.getColumnModel().getColumn(0).setMaxWidth(65);
        try {
            work = Files.createTempDirectory("iped-word-report-");
            office = new WordReportOffice(work.resolve("office-worker"));
            refreshTemplates(null);
        } catch (Exception e) {
            error(e);
        }
        content.setBorder(BorderFactory.createEmptyBorder(8, 12, 0, 12));
        JPanel document = documentPanel();
        document.setBorder(BorderFactory.createTitledBorder(message("DocumentPanel")));
        content.add(document, BorderLayout.NORTH);
        JPanel evidence = evidencePanel();
        evidence.setBorder(BorderFactory.createTitledBorder(message("EvidencePanel")));
        JPanel position = positionPanel();
        position.setBorder(BorderFactory.createTitledBorder(message("LocationPanel")));
        JSplitPane choices = new JSplitPane(JSplitPane.VERTICAL_SPLIT, evidence, position);
        choices.setResizeWeight(0.65);
        choices.setDividerLocation(380);
        JPanel review = reviewPanel();
        review.setBorder(BorderFactory.createTitledBorder(message("ReviewPanel")));
        JSplitPane workspace = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, choices, review);
        workspace.setResizeWeight(0.48);
        workspace.setDividerLocation(530);
        choices.setMinimumSize(new Dimension(360, 250));
        review.setMinimumSize(new Dimension(260, 250));
        content.add(workspace, BorderLayout.CENTER);
        add(content, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout(8, 8));
        footer.setBorder(BorderFactory.createEmptyBorder(8, 12, 12, 12));
        footer.add(status, BorderLayout.NORTH);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(view);
        actions.add(saveReport);
        actions.add(saveAs);
        openReport.setToolTipText(message("OpenReportHint"));
        actions.add(openReport);
        actions.add(close);
        footer.add(actions, BorderLayout.SOUTH);
        add(footer, BorderLayout.SOUTH);

        JPanel activityBox = new JPanel(new BorderLayout(12, 12));
        activityBox.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEtchedBorder(),
                BorderFactory.createEmptyBorder(24, 24, 24, 24)));
        JLabel loadingTitle = new JLabel(message("LoadingTitle"));
        loadingTitle.setFont(loadingTitle.getFont().deriveFont(java.awt.Font.BOLD));
        activityBox.add(loadingTitle, BorderLayout.NORTH);
        activityBox.add(activity, BorderLayout.CENTER);
        JProgressBar progress = new JProgressBar();
        progress.setIndeterminate(true);
        progress.setPreferredSize(new Dimension(420, 20));
        activityBox.add(progress, BorderLayout.SOUTH);
        loading.setOpaque(false);
        loading.addMouseListener(new MouseAdapter() { });
        loading.setFocusable(true);
        loading.add(activityBox);
        setGlassPane(loading);

        input.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                documentChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                documentChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                documentChanged();
            }
        });
        tableModel.addTableModelListener(e -> {
            if (!updatingEvidence) {
                invalidatePrepared();
            }
            updateActions();
        });
        layout.addActionListener(e -> invalidatePrepared());
        text.addActionListener(e -> invalidatePrepared());
        sections.addActionListener(e -> {
            if (!updatingLocations && loaded != null && !busy) {
                updatePositions();
            }
        });
        locations.addListSelectionListener(e -> {
            if (!updatingLocations && !e.getValueIsAdjusting()) {
                invalidatePrepared();
            }
            updateActions();
        });
        zoom.addActionListener(e -> fitPage());
        pageScroll.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                fitPage();
            }
        });
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                close();
            }
        });
        restoreSelection();
        updateActions();
    }

    private void restoreSelection() {
        try {
            WordReportFiles.SourceSelection last = WordReportFiles.lastSelection(caseKey);
            if (last != null) {
                sourceType.setSelectedIndex(last.type);
                if (last.type == 2) {
                    for (int i = 0; i < templatePaths.size(); i++) {
                        if (last.value.isEmpty() && templatePaths.get(i) == null
                                || templatePaths.get(i) != null && templatePaths.get(i).toString().equals(last.value)) {
                            templates.setSelectedIndex(i);
                            break;
                        }
                    }
                } else {
                    input.setText(last.value);
                }
                autoLoadSelection = last.type != 0 || Files.isRegularFile(Path.of(last.value));
            }
        } catch (Exception e) {
            LOGGER.warn("Could not restore Word report document selection: {}", e.getClass().getSimpleName());
        }
    }

    private void rememberSelection(int type, String value) {
        try {
            WordReportFiles.rememberSelection(caseKey, type, value);
        } catch (IOException e) {
            LOGGER.warn("Could not remember Word report document selection");
        }
    }

    private JPanel documentPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 12));
        JPanel fields = new JPanel(new GridBagLayout());
        GridBagConstraints field = new GridBagConstraints();
        field.gridx = 0;
        field.weightx = 1;
        field.fill = GridBagConstraints.HORIZONTAL;
        field.insets = new Insets(0, 0, 6, 0);
        JPanel type = new JPanel(new BorderLayout(8, 0));
        type.add(new JLabel(message("DocumentSource")), BorderLayout.WEST);
        type.add(sourceType, BorderLayout.CENTER);
        type.add(loadDocument, BorderLayout.EAST);
        field.gridy = 0;
        fields.add(type, field);
        JPanel file = new JPanel(new BorderLayout(8, 0));
        file.add(input, BorderLayout.CENTER);
        JButton choose = button("Choose", () -> chooseFile(false));
        JButton openLink = button("OpenLink", this::openLink);
        JPanel fileButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        fileButtons.add(choose);
        fileButtons.add(openLink);
        file.add(fileButtons, BorderLayout.EAST);
        field.gridy = 1;
        fields.add(file, field);
        JPanel template = new JPanel(new BorderLayout(8, 0));
        template.add(templates, BorderLayout.CENTER);
        template.add(button("SaveTemplate", () -> chooseFile(true)), BorderLayout.EAST);
        field.gridy = 2;
        fields.add(template, field);
        JLabel linkHint = new JLabel(message("LinkHint"));
        field.gridy = 3;
        fields.add(linkHint, field);
        panel.add(fields, BorderLayout.NORTH);
        panel.add(documentHint, BorderLayout.SOUTH);
        Runnable update = () -> {
            int selected = sourceType.getSelectedIndex();
            file.setVisible(selected != 2);
            template.setVisible(selected == 2);
            choose.setVisible(selected == 0);
            openLink.setVisible(selected == 1);
            linkHint.setVisible(selected == 1);
            documentChanged();
            panel.revalidate();
        };
        sourceType.addActionListener(e -> update.run());
        templates.addActionListener(e -> documentChanged());
        update.run();
        return panel;
    }

    private JPanel evidencePanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 12));
        JPanel options = new JPanel(new GridLayout(0, 1, 5, 5));
        JPanel first = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JPanel add = new JPanel(new FlowLayout(FlowLayout.LEFT));
        add.add(addSelection);
        options.add(add);
        first.add(new JLabel(message("Layout")));
        first.add(layout);
        options.add(first);
        JPanel second = new JPanel(new FlowLayout(FlowLayout.LEFT));
        second.add(new JLabel(message("ReferenceInformation")));
        options.add(second);
        JPanel extraction = new JPanel(new FlowLayout(FlowLayout.LEFT));
        text.setToolTipText(message("AutomaticMediaHint"));
        extraction.add(text);
        options.add(extraction);
        panel.add(options, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(selectionCount, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel positionPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 12));
        JPanel section = new JPanel(new BorderLayout(8, 0));
        section.add(new JLabel(message("Section")), BorderLayout.WEST);
        section.add(sections, BorderLayout.CENTER);
        panel.add(section, BorderLayout.NORTH);
        locations.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane positions = new JScrollPane(locations);
        positions.setBorder(BorderFactory.createTitledBorder(message("Position")));
        panel.add(positions, BorderLayout.CENTER);
        locations.setToolTipText(message("PositionPreviewHint"));
        return panel;
    }

    private JPanel reviewPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.add(pageScroll, BorderLayout.CENTER);
        JPanel navigation = new JPanel(new FlowLayout());
        navigation.add(previousPage);
        navigation.add(pageNumber);
        navigation.add(nextPage);
        navigation.add(zoom);
        panel.add(navigation, BorderLayout.SOUTH);
        return panel;
    }

    private JButton button(String key, Runnable action) {
        JButton button = new JButton(message(key));
        button.addActionListener(e -> {
            if (!busy) {
                action.run();
            }
        });
        return button;
    }

    private void updateActions() {
        if (table == null) {
            return;
        }
        int included = selectedItems().size();
        selectionCount.setText(message("SelectedCount", included, table.getRowCount()));
        loadDocument.setEnabled(!busy && work != null
                && (sourceType.getSelectedIndex() == 2 || !input.getText().trim().isEmpty()));
        addSelection.setEnabled(!busy && caseSource == App.get().appCase);
        view.setEnabled(!busy && loaded != null && included > 0 && locations.getSelectedValue() != null);
        saveReport.setEnabled(!busy && prepared != null);
        saveAs.setEnabled(!busy && loaded != null && (prepared != null || included == 0));
        openReport.setEnabled(!busy && loaded != null && workingDocument);
        saveReport.setToolTipText(message(workingDocument ? "SaveReportHint" : "FirstSaveHint"));
        documentHint.setText(message(workingDocument ? "WorkingDocumentHint" : loaded == null ? "SaveDestinationHint" : "NewCopyHint"));
        sections.setEnabled(!busy && loaded != null);
        locations.setEnabled(!busy && loaded != null);
        layout.setEnabled(!busy && selectedItems().stream().anyMatch(item -> item.capture == null));
        close.setEnabled(!busy);
        previousPage.setEnabled(!busy && preview != null && page > 0);
        nextPage.setEnabled(!busy && preview != null && page + 1 < preview.getPageCount());
    }

    private void invalidatePrepared() {
        if (prepared != null) {
            prepared = null;
            status.setText(message("PreviewOutdated"));
        }
        updateActions();
    }

    private void addSelectedItems() {
        if (!busy && caseSource == App.get().appCase) {
            prepareSelection(this::addItems);
        }
    }

    private void addItems(List<ReportItem> selected) {
        int added = 0;
        updatingEvidence = true;
        try {
            for (ReportItem entry : selected) {
                IItemId id = entry.id;
                boolean exists = false;
                for (ReportItem item : items) {
                    if (entry.capture == null && item.capture == null
                            && item.id.getSourceId() == id.getSourceId() && item.id.getId() == id.getId()) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    if (entry.capture != null && !chooseChatPlacement(entry)) {
                        continue;
                    }
                    Document doc = caseSource.getSearcher().doc(caseSource.getLuceneId(id));
                    items.add(entry);
                    String name = doc.get(IndexItem.NAME);
                    if (entry.capture != null) {
                        long excerpt = items.stream().filter(item -> item.id.getSourceId() == id.getSourceId()
                                && item.id.getId() == id.getId()).count();
                        name = message("ChatCropName", name, excerpt, entry.capture.messageCount);
                        if (entry.beside != null || entry.besideLocation >= 0) {
                            name = message("ChatBesideName", name);
                        }
                    }
                    tableModel.addRow(new Object[] { true, id.getSourceId() + ":" + id.getId(), name, message("Pending") });
                    added++;
                }
            }
        } catch (Exception e) {
            error(e);
        } finally {
            updatingEvidence = false;
        }
        if (added > 0) {
            invalidatePrepared();
        }
        status.setText(message("AddedSelection", added));
        updateActions();
    }

    private boolean chooseChatPlacement(ReportItem entry) {
        List<ReportItem> pending = selectedItems();
        ReportItem previous = pending.isEmpty() ? null : pending.get(pending.size() - 1);
        WordReportDocument.Location position = locations.getSelectedValue();
        boolean pendingChat = previous != null && previous.capture != null && previous.beside == null && previous.besideLocation < 0;
        boolean savedChat = previous == null && loaded != null && position != null && loaded.hasPreviousChat(position.index);
        if (!pendingChat && !savedChat) {
            return true;
        }
        Object[] options = { message("ChatBesidePrevious"), message("ChatKeepSingle"), message("ChatCropCancel") };
        int choice = JOptionPane.showOptionDialog(this, message("ChatPlacementQuestion"), message("ChatPlacementTitle"),
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (choice == 0) {
            entry.beside = pendingChat ? previous : null;
            entry.besideLocation = savedChat ? position.index : -1;
        }
        return choice == 0 || choice == 1;
    }

    private void documentChanged() {
        if (busy || updatingSource) {
            return;
        }
        loaded = null;
        source = null;
        workingDocument = false;
        prepared = null;
        closePreview();
        pageImage.setIcon(null);
        pageImage.setText(message("PreviewHint"));
        updatingLocations = true;
        sections.removeAllItems();
        locationModel.clear();
        updatingLocations = false;
        status.setText(message("SelectDocument"));
        updateActions();
    }

    private JFileChooser chooser() {
        JFileChooser chooser = new JFileChooser(new java.io.File(System.getProperty("user.home")));
        chooser.setFileFilter(new FileNameExtensionFilter("Word (*.docx)", "docx"));
        chooser.setAcceptAllFileFilterUsed(false);
        return chooser;
    }

    private void chooseFile(boolean template) {
        JFileChooser chooser = chooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path selected = chooser.getSelectedFile().toPath();
        if (template) {
            task("SavingTemplate", () -> WordReportFiles.saveTemplate(selected), saved -> {
                try {
                    refreshTemplates(saved);
                    sourceType.setSelectedIndex(2);
                    status.setText(message("TemplateSaved"));
                } catch (Exception e) {
                    error(e);
                }
            });
        } else {
            sourceType.setSelectedIndex(0);
            input.setText(selected.toString());
        }
    }

    private void refreshTemplates(Path selected) throws IOException {
        templatePaths.clear();
        templatePaths.add(null);
        templatePaths.addAll(WordReportFiles.templates());
        templates.removeAllItems();
        templates.addItem(message("DefaultTemplate"));
        for (int i = 1; i < templatePaths.size(); i++) {
            templates.addItem(templatePaths.get(i).getFileName().toString());
            if (templatePaths.get(i).equals(selected)) {
                templates.setSelectedIndex(i);
            }
        }
    }

    private void openLink() {
        try {
            Desktop.getDesktop().browse(WordReportFiles.httpsUri(input.getText()));
        } catch (Exception e) {
            error(e);
        }
    }

    private void load() {
        load(null);
    }

    private void load(Runnable afterLoad) {
        if (work == null) {
            return;
        }
        int type = sourceType.getSelectedIndex();
        String value = input.getText();
        Path template = templatePaths.get(Math.max(0, templates.getSelectedIndex()));
        task(type == 1 ? "DownloadingDocument" : "ReadingDocument", () -> {
            Path file;
            if (type == 1) {
                file = WordReportFiles.download(value, work);
                reportActivity("ReadingDocument");
            } else if (type == 2) {
                file = template == null ? WordReportFiles.defaultTemplate(Files.createDirectory(work.resolve(UUID.randomUUID().toString()))) : template;
            } else {
                file = Path.of(value);
            }
            WordReportDocument document = new WordReportDocument(file);
            try {
                office.warmUp();
            } catch (Exception error) {
                LOGGER.warn("Could not prepare Word report renderer: {}", error.getClass().getSimpleName());
            }
            return document;
        }, document -> {
            closePreview();
            prepared = null;
            loaded = document;
            source = document.getSource();
            workingDocument = type == 0;
            rememberSelection(type, type == 2 ? template == null ? "" : template.toString() : value);
            populatePositions(-1);
            status.setText(message("Loaded"));
            if (afterLoad != null) {
                afterLoad.run();
            }
        });
    }

    private void populatePositions(int after) {
        String previous = sections.getSelectedItem() == null ? "" : sections.getSelectedItem().toString();
        updatingLocations = true;
        sections.removeAllItems();
        List<WordReportDocument.Section> outline = loaded.getSections();
        int preferred = outline.size() > 1 ? 1 : 0;
        for (WordReportDocument.Section section : outline) {
            sections.addItem(section);
            if (after > section.start && after <= section.end || after < 0
                    && section.title.toLowerCase(Locale.ROOT).contains("dados obtidos")) {
                preferred = sections.getItemCount() - 1;
            } else if (after < 0 && section.toString().equals(previous)) {
                preferred = sections.getItemCount() - 1;
            }
        }
        sections.setSelectedIndex(preferred);
        updatePositions();
        if (after >= 0) {
            WordReportDocument.Location next = new WordReportDocument.Location(after, message("AfterInserted"));
            locationModel.add(0, next);
            locations.setSelectedIndex(0);
        }
        updatingLocations = false;
        updateActions();
    }

    private void updatePositions() {
        boolean wasUpdating = updatingLocations;
        updatingLocations = true;
        locationModel.clear();
        WordReportDocument.Section section = (WordReportDocument.Section) sections.getSelectedItem();
        if (section != null) {
            for (WordReportDocument.Location location : loaded.getPositions(section)) {
                locationModel.addElement(location);
            }
        }
        if (!locationModel.isEmpty()) {
            locations.setSelectedIndex(0);
        }
        updatingLocations = wasUpdating;
        if (!wasUpdating) {
            invalidatePrepared();
        }
        updateActions();
    }

    private List<ReportItem> selectedItems() {
        List<ReportItem> selected = new ArrayList<>();
        for (int row = 0; row < table.getRowCount(); row++) {
            int model = table.convertRowIndexToModel(row);
            if (Boolean.TRUE.equals(tableModel.getValueAt(model, 0))) {
                selected.add(items.get(model));
            }
        }
        return selected;
    }

    private void preview() {
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
        List<ReportItem> selected = selectedItems();
        WordReportDocument.Location chosen = locations.getSelectedValue();
        if (loaded == null || selected.isEmpty() || chosen == null) {
            JOptionPane.showMessageDialog(this, message("NeedSelection"));
            return;
        }
        if (prepared != null && preview != null) {
            task("ReadingDocument", () -> {
                if (!loaded.isSourceCurrent()) {
                    throw new IOException(message("SourceChanged"));
                }
                return preview.render(page);
            }, image -> {
                displayPage(page, image);
                status.setText(message("PreviewReused"));
                LOGGER.info("Word report reused prepared preview");
            });
            return;
        }
        int location = chosen.index;
        int columns = layout.getSelectedIndex() + 1;
        boolean withMetadata = true;
        boolean withText = text.isSelected();
        Path inputFile = source;
        String fingerprint = loaded.getFingerprint();
        closePreview();
        prepared = null;
        pageImage.setIcon(null);
        pageImage.setText(message("PreparingPreview"));
        pageNumber.setText(" ");
        task("ReadingDocument", () -> {
            long started = System.nanoTime();
            WordReportDocument report = new WordReportDocument(inputFile);
            if (!fingerprint.equals(report.getFingerprint())) {
                throw new IOException(message("SourceChanged"));
            }
            List<WordReportDocument.Evidence> evidence = new ArrayList<>();
            for (int i = 0; i < selected.size(); i++) {
                reportActivity("ReadingEvidence", i + 1, selected.size());
                ReportItem item = selected.get(i);
                if (item.beside != null && (i == 0 || selected.get(i - 1) != item.beside)
                        || item.besideLocation >= 0 && (i != 0 || location != item.besideLocation)) {
                    throw new IOException(message("PreviousChatUnavailable"));
                }
                WordReportDocument.Evidence read = item.capture == null ? WordReportEvidence.read(caseSource, item.id, withText, work)
                        : WordReportEvidence.readChatCapture(caseSource, item.id, item.capture);
                read.besidePreviousChat = item.beside != null || item.besideLocation >= 0;
                evidence.add(read);
            }
            String anchor = WordReportDocument.FILE_NAME + ": " + evidence.get(0).title;
            int occurrence = report.countTextOccurrencesBefore(location, anchor);
            reportActivity("WritingPreview");
            int after = report.insert(location, evidence, withMetadata, withText, columns);
            Path output = work.resolve("preview-" + UUID.randomUUID() + ".docx");
            report.write(output);
            PreviewResult result = new PreviewResult();
            result.document = output;
            result.nextLocation = after;
            result.missingImages = (int) evidence.stream().filter(item -> item.imageUnavailable).count();
            try {
                reportActivity("RenderingPreview");
                result.preview = new WordReportPreview(output, Files.createDirectory(work.resolve(UUID.randomUUID().toString())),
                        office);
                result.page = result.preview.findPageContaining(anchor, occurrence);
                if (result.page < 0) {
                    result.page = result.preview.findPage(evidence.get(0).title);
                }
                reportActivity("RenderingPage");
                result.image = result.preview.render(result.page);
                LOGGER.info("Word report review ready in {} ms", (System.nanoTime() - started) / 1000000);
            } catch (Exception error) {
                if (result.preview != null) {
                    result.preview.close();
                    result.preview = null;
                }
                result.error = error;
            }
            return result;
        }, result -> {
            prepared = result.document;
            preparedItems = selected;
            nextLocation = result.nextLocation;
            preview = result.preview;
            if (result.error != null) {
                pageImage.setText(message("PreviewUnavailable"));
                error(result.error);
            } else {
                displayPage(result.page, result.image);
                if (result.missingImages > 0) {
                    status.setText(message("MissingImages", result.missingImages));
                }
            }
            updateActions();
        });
    }

    private void save(boolean separateCopy) {
        if (loaded == null || prepared == null && (!separateCopy || !selectedItems().isEmpty())) {
            return;
        }
        boolean update = workingDocument && !separateCopy;
        Path output = source;
        if (!update) {
            JFileChooser chooser = chooser();
            chooser.setDialogTitle(message(separateCopy ? "SaveAs" : "FirstSaveTitle"));
            String name = "relatorio.docx";
            if (workingDocument) {
                chooser.setCurrentDirectory(source.toAbsolutePath().getParent().toFile());
                name = source.getFileName().toString().replaceFirst("(?i)\\.docx$", "") + "-copia.docx";
            }
            chooser.setSelectedFile(new java.io.File(name));
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            Path selected = chooser.getSelectedFile().toPath();
            output = selected.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".docx") ? selected
                    : selected.resolveSibling(selected.getFileName() + ".docx");
        }
        Path destination = output;
        Path reviewed = prepared == null ? source : prepared;
        List<ReportItem> inserted = prepared == null ? new ArrayList<>() : new ArrayList<>(preparedItems);
        int after = prepared != null ? nextLocation : locations.getSelectedValue() == null ? -1 : locations.getSelectedValue().index;
        task(update ? "SavingReport" : "SavingCopy", () -> {
            if (update) {
                loaded.savePreparedReport(reviewed);
            } else {
                loaded.savePreparedCopy(reviewed, destination);
            }
            return new WordReportDocument(destination);
        }, document -> {
            Path saved = document.getSource();
            loaded = document;
            source = saved;
            workingDocument = true;
            updatingSource = true;
            sourceType.setSelectedIndex(0);
            input.setText(saved.toAbsolutePath().normalize().toString());
            updatingSource = false;
            updatingEvidence = true;
            for (int i = 0; i < items.size(); i++) {
                if (inserted.contains(items.get(i))) {
                    tableModel.setValueAt(false, i, 0);
                    tableModel.setValueAt(message("Inserted"), i, 3);
                    items.get(i).beside = null;
                    items.get(i).besideLocation = -1;
                }
            }
            updatingEvidence = false;
            prepared = null;
            preparedItems.clear();
            populatePositions(after);
            rememberSelection(0, saved.toAbsolutePath().normalize().toString());
            status.setText(message("ReadyForMore"));
        });
    }

    private void openReport() {
        if (loaded == null || !workingDocument || source == null) {
            return;
        }
        try {
            Desktop.getDesktop().open(source.toFile());
        } catch (Exception e) {
            error(e);
        }
    }

    private void showPage(int selected) {
        if (preview == null || selected < 0 || selected >= preview.getPageCount()) {
            return;
        }
        task("RenderingPage", () -> preview.render(selected), image -> displayPage(selected, image));
    }

    private void displayPage(int selected, BufferedImage image) {
        page = selected;
        renderedPage = image;
        pageImage.setText("");
        pageNumber.setText(message("Page", selected + 1, preview.getPageCount()));
        fitPage();
        pageScroll.getViewport().setViewPosition(new java.awt.Point(0, 0));
        status.setText(message("PreviewReady"));
        updateActions();
    }

    private void fitPage() {
        if (renderedPage == null) {
            return;
        }
        double scale = zoom.getSelectedIndex() == 2 ? 1.5 : 1;
        if (zoom.getSelectedIndex() == 0) {
            Dimension available = pageScroll.getViewport().getExtentSize();
            if (available.width <= 20 || available.height <= 20) {
                return;
            }
            scale = Math.min(1, Math.min((available.width - 20.0) / renderedPage.getWidth(),
                    (available.height - 20.0) / renderedPage.getHeight()));
        }
        int width = Math.max(1, (int) (renderedPage.getWidth() * scale));
        int height = Math.max(1, (int) (renderedPage.getHeight() * scale));
        pageImage.setIcon(new ImageIcon(scale == 1 ? renderedPage : renderedPage.getScaledInstance(width, height, Image.SCALE_SMOOTH)));
    }

    private void setBusy(boolean value, String detail) {
        busy = value;
        setEnabled(content, !value);
        activity.setText(detail);
        loading.setVisible(value);
        setCursor(Cursor.getPredefinedCursor(value ? Cursor.WAIT_CURSOR : Cursor.DEFAULT_CURSOR));
        if (value) {
            loading.requestFocusInWindow();
            status.setText(detail);
        }
        updateActions();
    }

    private void setEnabled(Container parent, boolean enabled) {
        for (Component component : parent.getComponents()) {
            component.setEnabled(enabled);
            if (component instanceof Container) {
                setEnabled((Container) component, enabled);
            }
        }
    }

    private void reportActivity(String key, Object... args) {
        String detail = message(key, args);
        SwingUtilities.invokeLater(() -> {
            activity.setText(detail);
            status.setText(detail);
        });
    }

    private <T> void task(String activityKey, Callable<T> operation, Consumer<T> done) {
        setBusy(true, message(activityKey));
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return operation.call();
            }

            @Override
            protected void done() {
                setBusy(false, "");
                try {
                    done.accept(get());
                } catch (Exception e) {
                    error(e.getCause() == null ? e : e.getCause());
                }
                updateActions();
            }
        }.execute();
    }

    private void error(Throwable error) {
        // Do not log shared URLs, document text, or evidence metadata.
        LOGGER.warn("Word report operation failed: {}", error.getClass().getSimpleName());
        status.setText(message("Failed"));
        if (prepared == null && loaded != null) {
            pageImage.setText(message("PreviewFailed"));
        }
        String detail = error instanceof IOException ? error.getMessage() : message("InvalidDocument");
        JOptionPane.showMessageDialog(this, detail, message("Title"), JOptionPane.ERROR_MESSAGE);
    }

    private void closePreview() {
        if (preview != null) {
            try {
                preview.close();
            } catch (IOException e) {
                LOGGER.warn("Could not close Word report preview");
            }
            preview = null;
        }
        renderedPage = null;
    }

    private void close() {
        if (!busy) {
            setVisible(false);
        }
    }

    static void disposeCurrent() {
        if (current != null) {
            current.cleanup();
            current.dispose();
            current = null;
        }
    }

    private void cleanup() {
        closePreview();
        if (office != null) {
            try {
                office.close();
            } catch (IOException e) {
                LOGGER.warn("Could not close Word report renderer");
            }
        }
        if (work != null) {
            try (Stream<Path> files = Files.walk(work)) {
                for (Path file : (Iterable<Path>) files.sorted(Comparator.reverseOrder())::iterator) {
                    Files.deleteIfExists(file);
                }
            } catch (IOException e) {
                LOGGER.warn("Could not remove temporary Word report files");
            }
        }
    }

    private static class PreviewResult {
        Path document;
        WordReportPreview preview;
        BufferedImage image;
        int page;
        int nextLocation;
        int missingImages;
        Exception error;
    }
}
