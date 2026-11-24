package iped.app.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
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
import java.util.concurrent.CompletableFuture;
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
import javax.swing.JTextArea;
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

import iped.engine.data.IPEDSource;
import iped.data.IItemId;
import iped.data.IItem;
import iped.viewers.DocumentViewport;
import iped.viewers.HtmlViewer;
import iped.viewers.HtmlViewerCapture;
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
    private static boolean capturingExcerpt;

    private static class ReportItem {
        final IItemId id;
        final WordReportChatSelection.Result capture;
        ReportItem beside;
        int besideLocation = -1;
        String chatHash;
        int chatGroupSize;
        int mediaType;
        boolean separateRow;

        boolean isChatCrop() {
            return capture != null && !capture.document;
        }

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
    private final CardLayout previewLayout = new CardLayout();
    private final JPanel previewCards = new JPanel(previewLayout);
    private final JPanel previewPanel = new JPanel(new BorderLayout(8, 8));
    private final JLabel evidenceImage = new JLabel(message("SelectEvidencePreview"), JLabel.CENTER);
    private final JTextArea evidenceText = new JTextArea();
    private final JLabel attachmentImage = new JLabel("", JLabel.CENTER);
    private final JPanel evidenceImages = new JPanel(new GridLayout(1, 0, 4, 0));
    private final JScrollPane evidenceScroll = new JScrollPane(evidenceImages);
    private SwingWorker<WordReportDocument.Evidence, Void> evidenceWorker;
    private ReportItem previewedItem;
    private BufferedImage evidencePixels;
    private BufferedImage attachmentPixels;
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
        if (capturingExcerpt) {
            return;
        }
        IPEDMultiSource source = App.get().appCase;
        List<ReportItem> selected = new ArrayList<>();
        ReportItem excerpt = null;
        boolean chat = false;
        try {
            for (int row : App.get().resultsTable.getSelectedRows()) {
                IItemId id = App.get().ipedResult.getItem(App.get().resultsTable.convertRowIndexToModel(row));
                Document doc = source.getSearcher().doc(source.getLuceneId(id));
                ReportItem item = new ReportItem(id, null);
                String mime = doc.get(IndexItem.CONTENTTYPE);
                boolean isChat = WordReportEvidence.isChat(mime, doc.get(IndexItem.NAME));
                if (isChat || WordReportEvidence.isDocument(mime)) {
                    if (excerpt != null) {
                        JOptionPane.showMessageDialog(App.get(), message("OneExcerptAtATime"));
                        return;
                    }
                    excerpt = item;
                    chat = isChat;
                }
                selected.add(item);
            }
            if (excerpt == null) {
                done.accept(selected);
                return;
            }
            captureExcerpt(source, selected, excerpt, chat, done);
        } catch (Exception e) {
            LOGGER.warn("Word excerpt capture failed: {}", e.getClass().getSimpleName());
            JOptionPane.showMessageDialog(App.get(), message(chat ? "OpenChatPreview" : "OpenDocumentPreview"));
        }
    }

    private static void captureExcerpt(IPEDMultiSource source, List<ReportItem> selected, ReportItem excerpt,
            boolean chat, Consumer<List<ReportItem>> done) throws IOException {
        ViewerController controller = App.get().getViewerController();
        IItem viewed = controller.getViewedItem();
        AbstractViewer preview = controller.getMultiViewer().getCurrentViewer();
        if (!chat && (preview == null || !preview.getPanel().isShowing())) {
            preview = controller.getTextViewer();
        }
        AbstractViewer viewer = preview;
        IStreamSource content = viewer == controller.getTextViewer() ? viewed : controller.getViewedContent();
        Document doc = source.getSearcher().doc(source.getLuceneId(excerpt.id));
        String hint = message(chat ? "OpenChatPreview" : "OpenDocumentPreview");
        if (viewed == null || viewed.getDataSource() == null || viewed.getId() != excerpt.id.getId()
                || !viewed.getDataSource().getUUID().equals(doc.get(IndexItem.EVIDENCE_UUID))
                || viewer == null || !viewer.getPanel().isShowing() || content == null) {
            JOptionPane.showMessageDialog(App.get(), hint);
            return;
        }
        CompletableFuture<HtmlViewerCapture> snapshot;
        if (viewer instanceof HtmlViewer) {
            snapshot = ((HtmlViewer) viewer).captureVisible(content, chat);
        } else if (!chat && viewer instanceof DocumentViewport) {
            BufferedImage image = ((DocumentViewport) viewer).captureDocument(content);
            if (image == null) {
                JOptionPane.showMessageDialog(App.get(), hint);
                return;
            }
            snapshot = CompletableFuture.completedFuture(new HtmlViewerCapture(image));
        } else {
            JOptionPane.showMessageDialog(App.get(), hint);
            return;
        }
        capturingExcerpt = true;
        App.get().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        snapshot.whenComplete((capture, failure) -> SwingUtilities.invokeLater(() -> {
            try {
                if (App.get().appCase != source) {
                    return;
                }
                if (failure != null || controller.getViewedItem() != viewed || !viewer.getPanel().isShowing()
                        || (viewer != controller.getTextViewer() && (controller.getViewedContent() != content
                                || controller.getMultiViewer().getCurrentViewer() != viewer))
                        || (chat && capture.messages.isEmpty())) {
                    JOptionPane.showMessageDialog(App.get(), hint);
                    return;
                }
                App.get().setCursor(Cursor.getDefaultCursor());
                WordReportChatSelection.Result crop = chat ? WordReportChatCropDialog.show(capture, source, excerpt.id)
                        : WordReportChatCropDialog.showDocument(capture);
                if (crop != null && App.get().appCase == source) {
                    selected.set(selected.indexOf(excerpt), new ReportItem(excerpt.id, crop));
                    done.accept(selected);
                }
            } finally {
                capturingExcerpt = false;
                App.get().setCursor(Cursor.getDefaultCursor());
            }
        }));
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
        review.setBorder(BorderFactory.createTitledBorder(message("EvidencePreview")));
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
                updateActions();
            }
        });
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!updatingEvidence && !e.getValueIsAdjusting()) {
                showEvidencePreview();
            }
        });
        layout.addActionListener(e -> invalidatePrepared());
        text.addActionListener(e -> {
            invalidatePrepared();
            showEvidencePreview();
        });
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
        evidenceScroll.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                fitEvidence();
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
        evidenceImages.add(evidenceImage);
        evidenceText.setEditable(false);
        evidenceText.setLineWrap(true);
        evidenceText.setWrapStyleWord(true);
        JSplitPane evidence = new JSplitPane(JSplitPane.VERTICAL_SPLIT, evidenceScroll, new JScrollPane(evidenceText));
        evidence.setResizeWeight(0.7);
        evidenceScroll.setMinimumSize(new Dimension(180, 100));
        previewCards.add(evidence, "evidence");
        previewCards.add(panel, "report");
        previewPanel.add(previewCards, BorderLayout.CENTER);
        return previewPanel;
    }

    private void showEvidencePreview() {
        showEvidenceCard();
        if (evidenceWorker != null) {
            evidenceWorker.cancel(true);
        }
        int row = table.getSelectedRow();
        previewedItem = row < 0 ? null : items.get(table.convertRowIndexToModel(row));
        evidencePixels = null;
        attachmentPixels = null;
        evidenceImages.remove(attachmentImage);
        attachmentImage.setIcon(null);
        evidenceImages.revalidate();
        evidenceImages.repaint();
        evidenceImage.setIcon(null);
        evidenceText.setText("");
        evidenceImage.setText(message(previewedItem == null ? "SelectEvidencePreview" : "LoadingEvidencePreview"));
        if (previewedItem == null) {
            return;
        }
        ReportItem selected = previewedItem;
        boolean includeText = text.isSelected();
        evidenceWorker = new SwingWorker<WordReportDocument.Evidence, Void>() {
            @Override
            protected WordReportDocument.Evidence doInBackground() throws Exception {
                return selected.capture == null ? WordReportEvidence.preview(caseSource, selected.id, includeText, work)
                        : WordReportEvidence.readCapture(caseSource, selected.id, selected.capture);
            }

            @Override
            protected void done() {
                if (isCancelled() || previewedItem != selected || !isDisplayable()) {
                    return;
                }
                try {
                    WordReportDocument.Evidence item = get();
                    evidencePixels = item.image;
                    if (item.attachment != null) {
                        attachmentPixels = item.attachment.image;
                        attachmentImage.setToolTipText(item.attachment.title);
                        evidenceImages.add(attachmentImage);
                        evidenceImages.revalidate();
                    }
                    evidenceImage.setText(item.image == null ? message("NoEvidenceImage") : "");
                    StringBuilder details = new StringBuilder();
                    if (includeText) {
                        if (item.text != null && !item.text.isEmpty()) {
                            details.append('\n').append(item.text);
                        }
                        for (WordReportDocument.Evidence audio : item.transcripts) {
                            details.append("\n\n").append(audio.text);
                        }
                    }
                    if (details.length() > 0) {
                        details.append("\n\n");
                    }
                    for (java.util.Map.Entry<String, String> entry : item.metadata.entrySet()) {
                        details.append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
                    }
                    evidenceText.setText(details.toString());
                    evidenceText.setCaretPosition(0);
                    fitEvidence();
                } catch (Exception error) {
                    LOGGER.warn("Could not preview Word report item: {}", error.getClass().getSimpleName());
                    evidenceImage.setText(message("EvidencePreviewFailed"));
                }
            }
        };
        evidenceWorker.execute();
    }

    private void fitEvidence() {
        if (evidencePixels == null) {
            return;
        }
        Dimension available = evidenceScroll.getViewport().getExtentSize();
        int width = (available.width - 20) / evidenceImages.getComponentCount();
        setPreviewImage(evidenceImage, evidencePixels, width, available.height - 20);
        if (attachmentPixels != null) {
            setPreviewImage(attachmentImage, attachmentPixels, width, available.height - 20);
        }
    }

    private void setPreviewImage(JLabel label, BufferedImage image, int width, int height) {
        double scale = Math.min(1, Math.min(Math.max(1, width) / (double) image.getWidth(),
                Math.max(1, height) / (double) image.getHeight()));
        label.setIcon(new ImageIcon(scale == 1 ? image : image.getScaledInstance(
                Math.max(1, (int) (image.getWidth() * scale)),
                Math.max(1, (int) (image.getHeight() * scale)), Image.SCALE_SMOOTH)));
    }

    private void showReportPreview() {
        previewLayout.show(previewCards, "report");
        previewPanel.setBorder(BorderFactory.createTitledBorder(message("ReviewPanel")));
    }

    private void showEvidenceCard() {
        previewLayout.show(previewCards, "evidence");
        previewPanel.setBorder(BorderFactory.createTitledBorder(message("EvidencePreview")));
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
        showEvidenceCard();
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
                    Document doc = caseSource.getSearcher().doc(caseSource.getLuceneId(id));
                    String mime = doc.get(IndexItem.CONTENTTYPE);
                    entry.mediaType = entry.capture != null && entry.capture.document ? 1
                            : mime == null ? 5 : WordReportEvidence.mediaType(mime);
                    if (entry.isChatCrop() && entry.capture.attachment == null && entry.capture.transcripts.isEmpty()) {
                        entry.chatHash = doc.get(IndexItem.HASH);
                        if (entry.chatHash != null && !entry.chatHash.isBlank()) {
                            entry.chatGroupSize = 1;
                        }
                    }
                    if ((entry.capture != null || entry.mediaType == 1 && selected.size() == 1) && !choosePlacement(entry)) {
                        continue;
                    }
                    items.add(entry);
                    String name = doc.get(IndexItem.NAME);
                    if (entry.capture != null) {
                        long excerpt = items.stream().filter(item -> item.id.getSourceId() == id.getSourceId()
                                && item.id.getId() == id.getId()).count();
                        name = entry.capture.document ? message("DocumentCropName", name, excerpt)
                                : message("ChatCropName", name, excerpt, entry.capture.messageCount);
                        if (entry.chatGroupSize < 3 && (entry.beside != null || entry.besideLocation >= 0)) {
                            name = message("ChatBesideName", name);
                        }
                    }
                    String placement = entry.chatGroupSize >= 3 ? "SameChatPage" : entry.beside != null || entry.besideLocation >= 0 ? "BesidePrevious"
                            : entry.separateRow ? "SeparateRow" : "Pending";
                    tableModel.addRow(new Object[] { true, id.getSourceId() + ":" + id.getId(), name, message(placement) });
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
        if (!items.isEmpty()) {
            int model = items.size() - 1;
            if (!selected.isEmpty()) {
                ReportItem requested = selected.get(selected.size() - 1);
                for (int i = 0; i < items.size(); i++) {
                    if (items.get(i) == requested || requested.capture == null && items.get(i).capture == null
                            && items.get(i).id.getSourceId() == requested.id.getSourceId()
                            && items.get(i).id.getId() == requested.id.getId()) {
                        model = i;
                        break;
                    }
                }
            }
            int row = table.convertRowIndexToView(model);
            int previousSelection = table.getSelectedRow();
            table.setRowSelectionInterval(row, row);
            if (previousSelection == row) {
                showEvidencePreview();
            }
        }
        status.setText(message("AddedSelection", added));
        updateActions();
    }

    private boolean choosePlacement(ReportItem entry) {
        List<ReportItem> pending = selectedItems();
        ReportItem previous = pending.isEmpty() ? null : pending.get(pending.size() - 1);
        WordReportDocument.Location position = locations.getSelectedValue();
        boolean chat = entry.isChatCrop();
        if (chat && entry.capture.attachment != null) {
            return true;
        }
        boolean pendingFrame = previous != null && previous.beside == null && previous.besideLocation < 0
                && (chat ? previous.isChatCrop() && previous.capture.attachment == null : previous.mediaType == 1 && !previous.isChatCrop() && hasPendingImageColumn(pending));
        boolean savedFrame = previous == null && loaded != null && position != null
                && (chat ? loaded.hasPreviousChat(position.index) : loaded.hasPreviousImage(position.index));
        int previousCount = 0;
        if (entry.chatGroupSize == 1) {
            if (previous != null && entry.chatHash.equalsIgnoreCase(previous.chatHash)) {
                previousCount = previous.chatGroupSize;
            } else if (previous == null && loaded != null && position != null) {
                previousCount = loaded.previousChatCount(position.index, entry.chatHash);
            }
        }
        boolean extendGrid = previousCount == 2 || previousCount == 3;
        if (!pendingFrame && !savedFrame && !extendGrid) {
            return true;
        }
        Object[] options = { extendGrid ? message("ChatGridAdd", previousCount + 1)
                : message(chat ? "ChatBesidePrevious" : "ImageBesidePrevious"),
                message(chat ? "ChatKeepSingle" : "ImageKeepSingle"), message("ChatCropCancel") };
        int choice = JOptionPane.showOptionDialog(this, message(extendGrid ? "ChatGridQuestion" : chat ? "ChatPlacementQuestion" : "ImagePlacementQuestion"),
                message(chat ? "ChatPlacementTitle" : "ImagePlacementTitle"),
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (choice == 0) {
            entry.chatGroupSize = previousCount > 0 ? previousCount + 1 : 0;
            entry.beside = previous;
            entry.besideLocation = previous == null ? position.index : -1;
        } else if (choice == 1) {
            entry.separateRow = true;
        }
        return choice == 0 || choice == 1;
    }

    private boolean hasPendingImageColumn(List<ReportItem> pending) {
        int count = 0;
        for (ReportItem item : pending) {
            if (item.isChatCrop() || item.mediaType == 2 || item.mediaType == 4 || item.beside != null || item.besideLocation >= 0) {
                count = 0;
            } else {
                if (item.separateRow) {
                    count = 0;
                }
                count = (count + 1) % (layout.getSelectedIndex() + 1);
            }
        }
        return layout.getSelectedIndex() == 0 || count == 1;
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
        showReportPreview();
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
                    throw new IOException(message("PreviousEvidenceUnavailable"));
                }
                WordReportDocument.Evidence read = item.capture == null ? WordReportEvidence.read(caseSource, item.id, withText, work)
                        : WordReportEvidence.readCapture(caseSource, item.id, item.capture);
                read.besidePrevious = item.beside != null || item.besideLocation >= 0;
                read.separateRow = item.separateRow;
                evidence.add(read);
            }
            WordReportDocument.Evidence first = evidence.get(0);
            String anchor = WordReportDocument.FILE_NAME + ": " + first.title;
            int occurrence = report.countTextOccurrencesBefore(location, anchor, first.besidePrevious);
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
                    result.page = result.preview.findPage(first.title);
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
            checkReportDestination(destination);
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
            removeSavedItems(inserted, after);
            prepared = null;
            preparedItems.clear();
            populatePositions(after);
            rememberSelection(0, saved.toAbsolutePath().normalize().toString());
            status.setText(message("ReadyForMore"));
        });
    }

    /** Keep only the unsaved batch while retaining a valid pairing anchor in the working report. */
    private void removeSavedItems(List<ReportItem> inserted, int after) {
        updatingEvidence = true;
        try {
            for (int i = items.size() - 1; i >= 0; i--) {
                if (inserted.contains(items.get(i))) {
                    items.remove(i);
                    tableModel.removeRow(i);
                } else if (inserted.contains(items.get(i).beside)) {
                    ReportItem item = items.get(i);
                    boolean last = !inserted.isEmpty() && item.beside == inserted.get(inserted.size() - 1);
                    item.beside = null;
                    item.besideLocation = last && (item.isChatCrop() ? loaded.hasPreviousChat(after) || loaded.previousChatCount(after, item.chatHash) >= 2
                            : loaded.hasPreviousImage(after)) ? after : -1;
                    if (item.besideLocation < 0) {
                        item.chatGroupSize = item.chatHash == null || item.chatHash.isBlank() ? 0 : 1;
                        item.separateRow = true;
                        tableModel.setValueAt(message("SeparateRow"), i, 3);
                    }
                }
            }
        } finally {
            updatingEvidence = false;
        }
    }

    private void checkReportDestination(Path destination) throws IOException {
        if (IPEDSource.isReadOnlyView()) {
            Path real = Files.exists(destination) ? destination.toRealPath() : destination.toAbsolutePath().getParent().toRealPath();
            for (IPEDSource item : App.get().appCase.getAtomicSources()) {
                for (Path p = real; p != null; p = p.getParent()) {
                    if (Files.isSameFile(p, item.getCaseDir().toPath())) {
                        throw new IOException(message("OutsideCase"));
                    }
                }
            }
        }
    }

    private void openReport() {
        if (loaded == null || !workingDocument || source == null) {
            return;
        }
        try {
            checkReportDestination(source);
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
        showReportPreview();
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
        if (evidenceWorker != null) {
            evidenceWorker.cancel(true);
        }
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
