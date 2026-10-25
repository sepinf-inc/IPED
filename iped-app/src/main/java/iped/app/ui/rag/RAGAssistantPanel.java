package iped.app.ui.rag;

/**
 * UI Panel for the IPED AI Assistant (RAG Chatbot), providing multi-turn chat,
 * evidence source navigation, history management, and context window warnings.
 *
 * @author Rui Sant'Ana Junior
 */
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JEditorPane;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.TableColumn;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.app.ui.App;
import iped.app.ui.BookmarksController;
import iped.app.ui.ExternalFileOpen;
import iped.app.ui.FileProcessor;
import iped.app.ui.Messages;
import iped.data.IIPEDSource;
import iped.data.IItemId;
import iped.engine.data.IPEDSource;
import iped.engine.config.RAGConfig;
import iped.engine.data.ItemId;
import iped.engine.rag.LLMProvider;
import iped.engine.rag.LocalLLMProvider;
import iped.engine.rag.RAGService;
import iped.engine.rag.RAGSearchScope;
import iped.engine.rag.RAGService.RAGSourceDoc;
import iped.parsers.standard.StandardParser;
import iped.search.IMultiSearchResult;
import iped.properties.ExtraProperties;

/**
 * Swing docking panel for the RAG (Retrieval-Augmented Generation) AI
 * Assistant.
 *
 * Layout (vertical split inside a horizontal split):
 * 
 * <pre>
 * +------------------------------------+--------------------------------------------+
 * |  [History list ??? left panel]       |  [Question text area + Ask / Cancel btns]  |
 * |                                    |--------------------------------------------|
 * |                                    |  [HTML response viewer ??? centre-right]      |
 * |                                    |--------------------------------------------|
 * |                                    |  [Sources table ??? bottom-right]             |
 * +------------------------------------+--------------------------------------------+
 * </pre>
 *
 * Double-clicking a row in the sources table calls
 * {@code new FileProcessor(luceneId, false).execute()} to open the evidence
 * item in the standard IPED viewer and mark it as checked.
 */
public class RAGAssistantPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = LoggerFactory.getLogger(RAGAssistantPanel.class);

    // ------------------------------------------------------------ prompt budget
    /**
     * Characters-per-token estimate used to size prompts for local models.
     * Measured on Portuguese case text with qwen3: 2.9 on average, 2.5 at least.
     */
    static final double CHARS_PER_TOKEN = 2.7;
    static final int MAX_HISTORY_TURNS = 5;
    /** Share of the local prompt budget available to chat history. */
    static final double HISTORY_BUDGET_SHARE = 0.20;
    /** History limit for remote providers, whose context window is not configured here. */
    static final int REMOTE_HISTORY_CHARS = 6000;
    /** Longest value of each item detail in the fragment header. */
    static final int MAX_DETAIL_CHARS = 200;
    /** Share of the local prompt budget kept free to absorb token estimation errors. */
    static final double PROMPT_SAFETY_SHARE = 0.10;
    /** Fixed prompt text around the evidence: context and question labels, and chat roles. */
    static final int PROMPT_TEMPLATE_CHARS = 128;
    /** Per-turn wrapper added by providers (question label and chat roles). */
    static final int HISTORY_TURN_TEMPLATE_CHARS = 32;
    static final String TRUNCATION_MARKER = " [...]";

    // ------------------------------------------------------------------ widgets
    private final DefaultListModel<String> historyListModel = new DefaultListModel<>();
    private final JList<String> historyList = new JList<>(historyListModel);
    private final JTextArea questionArea;
    private final JButton askButton;
    private final JButton cancelButton;
    private final JCheckBox chkScopeOnly;
    private final JEditorPane responsePane;
    private final SourcesTableModel sourcesModel = new SourcesTableModel();
    private final JTable sourcesTable = new JTable(sourcesModel);

    // ------------------------------------------------------------------ state
    private SwingWorker<String, String> currentWorker;
    private final List<ChatSession> sessions = new ArrayList<>();
    private ChatSession activeSession;
    private ChatSession workerSession;
    private final RAGContextSnapshot.Cache contextSnapshots = new RAGContextSnapshot.Cache();
    private RAGContextPanel contextPanel;
    private JDialog contextDialog;
    private volatile boolean disposed;
    private volatile boolean caseLoading;
    private boolean externalServicesConfirmed;

    // ---------------------------------------------------------------- constructor

    public RAGAssistantPanel() {
        super(new BorderLayout());

        // ---------- LEFT PANEL: chat history --------------------------------
        historyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        historyList.setFont(UIManager.getFont("Label.font"));
        JScrollPane historyScroll = new JScrollPane(historyList);
        historyScroll.setBorder(null);

        JButton newChatBtn = new JButton("+ " + Messages.getString("RAGAssistant.NewChat"));
        newChatBtn.setFont(UIManager.getFont("Button.font"));
        newChatBtn.addActionListener(e -> startNewSession());

        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
        leftPanel.setBorder(BorderFactory.createTitledBorder(
                Messages.getString("RAGAssistant.History")));
        leftPanel.add(newChatBtn, BorderLayout.NORTH);
        leftPanel.add(historyScroll, BorderLayout.CENTER);
        leftPanel.setPreferredSize(new Dimension(220, 0));

        // ---------- TOP-RIGHT: question bar ---------------------------------
        questionArea = new JTextArea(3, 40);
        questionArea.setLineWrap(true);
        questionArea.setWrapStyleWord(true);
        questionArea.setFont(UIManager.getFont("TextField.font"));
        String placeholder = Messages.getString("RAGAssistant.Question");
        questionArea.setText(placeholder);
        questionArea.setForeground(Color.GRAY);
        questionArea.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                if (questionArea.getText().equals(placeholder)) {
                    questionArea.setText("");
                    questionArea.setForeground(UIManager.getColor("TextField.foreground"));
                }
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                if (questionArea.getText().trim().isEmpty()) {
                    questionArea.setForeground(Color.GRAY);
                    questionArea.setText(placeholder);
                }
            }
        });
        questionArea.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER) {
                    if (e.isShiftDown()) {
                        questionArea.insert("\n", questionArea.getCaretPosition());
                    } else {
                        e.consume();
                        onAsk();
                    }
                }
            }
        });
        JScrollPane questionScroll = new JScrollPane(questionArea);

        askButton = new JButton(Messages.getString("RAGAssistant.Ask"));
        cancelButton = new JButton(Messages.getString("RAGAssistant.Cancel"));
        cancelButton.setEnabled(false);

        chkScopeOnly = new JCheckBox(getMessageSafely("RAGAssistant.ScopeSelected", "Restringir à seleção"));
        chkScopeOnly.setToolTipText(getMessageSafely("RAGAssistant.ScopeSelectedTooltip",
                "Restringe a busca RAG apenas aos itens marcados, selecionados na tabela ou visíveis nos filtros atuais."));
        chkScopeOnly.setFont(UIManager.getFont("Label.font"));

        askButton.addActionListener(e -> onAsk());
        cancelButton.addActionListener(e -> onCancel());

        JPanel actionBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        actionBtns.add(askButton);
        actionBtns.add(cancelButton);

        JPanel chkHolder = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 4));
        chkHolder.add(chkScopeOnly);

        JPanel btnPanel = new JPanel();
        btnPanel.setLayout(new BoxLayout(btnPanel, BoxLayout.Y_AXIS));
        btnPanel.add(actionBtns);
        btnPanel.add(chkHolder);

        JPanel topRight = new JPanel(new BorderLayout(4, 4));
        topRight.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        topRight.add(questionScroll, BorderLayout.CENTER);
        topRight.add(btnPanel, BorderLayout.EAST);
        topRight.setPreferredSize(new Dimension(0, 110));
        topRight.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));

        // ---------- CENTRE-RIGHT: response area -----------------------------
        responsePane = new JEditorPane("text/html", "");
        responsePane.setEditable(false);
        responsePane.setBackground(UIManager.getColor("TextArea.background"));
        responsePane.addHyperlinkListener(e -> {
            if (e.getEventType() == javax.swing.event.HyperlinkEvent.EventType.ENTERED) {
                responsePane.setToolTipText(e.getDescription() != null && e.getDescription().startsWith("context:")
                        ? Messages.getString("RAGAssistant.ContextTooltip") : null);
            } else if (e.getEventType() == javax.swing.event.HyperlinkEvent.EventType.EXITED) {
                responsePane.setToolTipText(null);
            }
            if (e.getEventType() == javax.swing.event.HyperlinkEvent.EventType.ACTIVATED) {
                String description = e.getDescription();
                if (description != null && description.startsWith("context:") && isDebugMode()) {
                    try {
                        showContext(Integer.parseInt(description.substring(8)));
                    } catch (NumberFormatException nfe) {
                        // Ignore malformed response links.
                    }
                } else if (description != null && description.startsWith("sources:")) {
                    try {
                        int entryIndex = Integer.parseInt(description.substring(8));
                        if (activeSession != null && entryIndex >= 0 && entryIndex < activeSession.entries.size()) {
                            HistoryEntry entry = activeSession.entries.get(entryIndex);
                            if (entry != null && entry.displaySources != null) {
                                sourcesModel.setRows(new ArrayList<>(entry.displaySources));
                                showSourcesInTable(entry.displaySources);
                            }
                        }
                    } catch (NumberFormatException nfe) {
                        // ignore
                    }
                }
            }
        });
        JScrollPane responseScroll = new JScrollPane(responsePane);
        responseScroll.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));

        // ---------- BOTTOM-RIGHT: sources table -----------------------------
        configureSourcesTable();
        JScrollPane sourcesScroll = new JScrollPane(sourcesTable);
        sourcesScroll.setBorder(BorderFactory.createTitledBorder(
                Messages.getString("RAGAssistant.Sources")));
        sourcesScroll.setPreferredSize(new Dimension(0, 140));

        // ---------- RIGHT SPLIT: response + sources -------------------------
        JPanel rightContent = new JPanel();
        rightContent.setLayout(new BoxLayout(rightContent, BoxLayout.Y_AXIS));
        rightContent.add(topRight);
        rightContent.add(responseScroll);
        rightContent.add(sourcesScroll);

        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, responseScroll, sourcesScroll);
        rightSplit.setResizeWeight(0.65);
        rightSplit.setBorder(null);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(topRight, BorderLayout.NORTH);
        rightPanel.add(rightSplit, BorderLayout.CENTER);

        // ---------- MAIN SPLIT: history + right -----------------------------
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        mainSplit.setResizeWeight(0.0);
        mainSplit.setDividerLocation(220);
        mainSplit.setBorder(null);

        add(mainSplit, BorderLayout.CENTER);

        // History selection listener ??? added here so that questionArea and responsePane
        // (final fields) are guaranteed to be initialized before the lambda captures
        // them.
        historyList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int idx = historyList.getSelectedIndex();
                if (idx >= 0 && idx < sessions.size()) {
                    if (activeSession != sessions.get(idx)) {
                        closeContext();
                    }
                    activeSession = sessions.get(idx);

                    // Render the conversation thread for this session
                    String conversationHtml = renderConversation(activeSession);
                    responsePane.setContentType("text/html");
                    responsePane.setText(conversationHtml);

                    // Scroll to the bottom of the conversation
                    SwingUtilities
                            .invokeLater(() -> responsePane.setCaretPosition(responsePane.getDocument().getLength()));

                    // Restore sources of the last entry in the session (if any)
                    if (!activeSession.entries.isEmpty()) {
                        HistoryEntry lastEntry = activeSession.entries.get(activeSession.entries.size() - 1);
                        sourcesModel.setRows(lastEntry.displaySources);
                    } else {
                        sourcesModel.setRows(new ArrayList<>());
                    }

                    // Keep the question area clear for the next question (chat continuation)
                    String placeholderMsg = Messages.getString("RAGAssistant.Question");
                    if (questionArea.hasFocus()) {
                        questionArea.setText("");
                        questionArea.setForeground(UIManager.getColor("TextField.foreground"));
                    } else {
                        questionArea.setForeground(Color.GRAY);
                        questionArea.setText(placeholderMsg);
                    }
                }
            }
        });

        // Popup menu for history list operations (rename / delete)
        javax.swing.JPopupMenu popupMenu = new javax.swing.JPopupMenu();
        javax.swing.JMenuItem renameItem = new javax.swing.JMenuItem(Messages.getString("RAGAssistant.RenameChat"));
        renameItem.addActionListener(e -> renameSelectedHistory());
        popupMenu.add(renameItem);
        javax.swing.JMenuItem deleteItem = new javax.swing.JMenuItem(Messages.getString("RAGAssistant.DeleteChat"));
        deleteItem.addActionListener(e -> deleteSelectedHistory());
        popupMenu.add(deleteItem);

        historyList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                showPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                showPopup(e);
            }

            private void showPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int index = historyList.locationToIndex(e.getPoint());
                    if (index >= 0) {
                        historyList.setSelectedIndex(index);
                        popupMenu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }
        });

        // Key listener for Delete key on history list
        historyList.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_DELETE) {
                    deleteSelectedHistory();
                }
            }
        });

        loadHistoryFromDatabase();
    }

    // ---------------------------------------------------------------- table setup

    private void configureSourcesTable() {
        sourcesTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        sourcesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sourcesTable.setFillsViewportHeight(true);
        sourcesTable.setFont(UIManager.getFont("Label.font"));

        DefaultTableColumnModel colModel = new DefaultTableColumnModel();
        String[] headers = {
                Messages.getString("RAGAssistant.Header.Id"),
                "",
                Messages.getString("RAGAssistant.Header.Name"),
                Messages.getString("RAGAssistant.Header.Score"),
                Messages.getString("RAGAssistant.Header.Path")
        };
        int[] widths = { 50, 25, 240, 70, 500 };
        for (int i = 0; i < headers.length; i++) {
            TableColumn col = new TableColumn(i, widths[i]);
            col.setHeaderValue(headers[i]);
            col.setPreferredWidth(widths[i]);
            if (i == SourcesTableModel.CHECK_COLUMN) {
                col.setCellRenderer(sourcesTable.getDefaultRenderer(Boolean.class));
                col.setCellEditor(sourcesTable.getDefaultEditor(Boolean.class));
            }
            colModel.addColumn(col);
        }
        sourcesTable.setColumnModel(colModel);

        // As the subitem and duplicate tables: selecting shows the item in the viewers, the checkbox
        // checks it and double-click opens it in an external program.
        sourcesTable.getSelectionModel().addListSelectionListener(e -> {
            int row = sourcesTable.getSelectedRow();
            if (!e.getValueIsAdjusting() && row >= 0) {
                previewSourceItem(sourcesTable.convertRowIndexToModel(row));
            }
        });
        sourcesTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                int row = sourcesTable.rowAtPoint(e.getPoint());
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2 && row >= 0
                        && sourcesTable.columnAtPoint(e.getPoint()) != SourcesTableModel.CHECK_COLUMN) {
                    int luceneId = sourceLuceneId(sourcesTable.convertRowIndexToModel(row));
                    if (luceneId >= 0) {
                        ExternalFileOpen.open(luceneId);
                    }
                }
            }
        });
    }

    // ---------------------------------------------------------------- actions

    private void onAsk() {
        if (disposed || caseLoading || currentWorker != null) {
            return;
        }
        String question = questionArea.getText().trim();
        String placeholder = Messages.getString("RAGAssistant.Question");
        if (question.isEmpty() || question.equals(placeholder)) {
            return;
        }

        RAGService rag = RAGService.getInstance();
        if (rag == null || !rag.getConfig().isEnabled()) {
            JOptionPane.showMessageDialog(
                    App.get(),
                    Messages.getString("RAGAssistant.NotEnabled"),
                    Messages.getString("RAGAssistant.ErrorTitle"),
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Snapshot the selection on the EDT before changing the conversation or starting a worker.
        final RAGSearchScope searchScope = collectSearchScope();
        if (searchScope != null && searchScope.isEmpty()) {
            JOptionPane.showMessageDialog(this, Messages.getString("RAGAssistant.ScopeEmpty"),
                    Messages.getString("RAGAssistant.ErrorTitle"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        if (!confirmExternalServices(rag.getConfig())) {
            return;
        }

        startAnswer(question, rag, searchScope);
    }

    /**
     * Asks once per opened case before evidence text goes to an external provider. Local
     * providers (Ollama), even on another machine of the network, are not external.
     */
    private boolean confirmExternalServices(RAGConfig config) {
        List<String> services = externalServices(config);
        if (services.isEmpty() || externalServicesConfirmed) {
            return true;
        }
        int option = JOptionPane.showConfirmDialog(this,
                String.format(Messages.getString("RAGAssistant.ExternalProviderConfirm"), String.join("\n", services)),
                Messages.getString("RAGAssistant.ExternalProviderTitle"), JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        externalServicesConfirmed = option == JOptionPane.YES_OPTION;
        return externalServicesConfirmed;
    }

    /** External services that receive the question and evidence text, one per line. */
    static List<String> externalServices(RAGConfig config) {
        List<String> services = new ArrayList<>();
        if (config == null) {
            return services;
        }
        if (!"local".equalsIgnoreCase(config.getLlmProvider())) {
            services.add("LLM: " + config.getLlmProvider() + " (" + config.getLlmModel() + ")");
        }
        if (!"local".equalsIgnoreCase(config.getEmbeddingProvider())) {
            services.add("Embeddings: " + config.getEmbeddingProvider() + " (" + config.getEmbeddingModel() + ")");
        }
        return services;
    }

    void startAnswer(String question, RAGService rag, RAGSearchScope searchScope) {
        if (disposed || caseLoading || currentWorker != null) {
            return;
        }
        if (activeSession == null) {
            startNewSession();
        }

        final String q = question;
        final ChatSession originSession = activeSession;
        final String currentSessionId = originSession.sessionId;
        final String requestId = java.util.UUID.randomUUID().toString();
        final String conversationBody = extractBodyContent(renderConversation(originSession));

        // Size the prompt before selecting history, so history shares the same budget as evidence.
        final int promptBudget = rag != null && rag.getConfig() != null ? promptCharBudget(rag.getConfig())
                : Integer.MAX_VALUE;

        // Assemble chat history context (last turns bounded by count and by the history budget)
        int entryCount = originSession.entries.size();
        List<RAGService.HistoryTurn> candidateTurns = new ArrayList<>();
        for (HistoryEntry he : originSession.entries) {
            String cleanAns = he.rawAnswer != null ? he.rawAnswer : stripHtml(he.answerHtml);
            candidateTurns.add(new RAGService.HistoryTurn(he.question, cleanAns));
        }
        final List<RAGService.HistoryTurn> historyTurns = boundHistory(candidateTurns, MAX_HISTORY_TURNS,
                historyCharBudget(promptBudget));

        // Display current conversation thread + the new question + Thinking...
        StringBuilder tempHtml = new StringBuilder();
        tempHtml.append("<html><body style='font-family:sans-serif;font-size:13px;padding:8px;'>");
        if (entryCount > 0) {
            tempHtml.append(extractBodyContent(renderConversation(activeSession)));
            tempHtml.append("<hr style='border:0; border-top:1px solid #E0E0E0; margin-bottom:15px;'/>");
        }
        // User Question
        tempHtml.append("<div style='background-color:#E8F0FE; padding:8px; border-radius:4px; margin-bottom:6px;'>");
        tempHtml.append("<b>").append(Messages.getString("RAGAssistant.YouLabel")).append(":</b> ")
                .append(markdownToHtml(q));
        tempHtml.append("</div>");
        // Thinking...
        tempHtml.append("<div style='padding:8px; margin-bottom:15px;'>");
        tempHtml.append("<b>").append(Messages.getString("RAGAssistant.AssistantLabel")).append(":</b> <i>")
                .append(Messages.getString("RAGAssistant.Thinking")).append("</i>");
        tempHtml.append("</div>");
        tempHtml.append("</body></html>");

        setUIBusy(true);
        responsePane.setContentType("text/html");
        responsePane.setText(tempHtml.toString());
        SwingUtilities.invokeLater(() -> responsePane.setCaretPosition(responsePane.getDocument().getLength()));
        sourcesModel.setRows(new ArrayList<>());

        workerSession = originSession;
        currentWorker = new SwingWorker<String, String>() {
            private List<RAGSourceDoc> foundDocs = new ArrayList<>();
            private int omittedChunksCount = 0;
            private boolean contextLimitReached = false;
            private int contextWindowValue = 4096;
            private int totalRetrievedChunksCount = 0;
            private final StringBuilder streamingRawAnswer = new StringBuilder();
            private RAGContextSnapshot contextSnapshot;

            private void checkCancelled() {
                if (isCancelled() || disposed || Thread.currentThread().isInterrupted()) {
                    throw new CancellationException();
                }
            }

            @Override
            protected String doInBackground() throws Exception {
                checkCancelled();
                // 1. Prepare contextualized search query for multi-turn questions
                String searchQuery = extractSearchQueryWithHistory(q, historyTurns);

                // 2. Embed the question
                float[] qvec = rag.getEmbedding(searchQuery);
                checkCancelled();
                if (qvec == null) {
                    String reason = rag.getUnavailableReason();
                    throw new IOException("Could not generate embedding for the question."
                            + (reason != null ? " RAG unavailable: " + reason + "." : ""));
                }

                // 3. Hybrid search using contextualized search query and scope filter
                List<RAGSourceDoc> docs = rag.performHybridSearchInScope(searchQuery, qvec, searchScope);
                checkCancelled();
                foundDocs.addAll(docs);

                if (docs.isEmpty()) {
                    return Messages.getString("RAGAssistant.NoContextFound");
                }

                // 3. Build context
                StringBuilder ctx = new StringBuilder();
                int docIndex = 1;
                int omittedCount = 0;
                boolean limitReached = false;

                contextWindowValue = rag.getConfig().getLlmContextWindow();
                int maxChars = promptBudget;
                LLMProvider llm = rag.getLlmProvider();
                String systemPrompt = llm != null ? llm.getSystemPrompt(q) : null;
                int baseLength = promptBaseChars(systemPrompt, q, historyTurns, maxChars);
                totalRetrievedChunksCount = docs.size();
                RAGContextSnapshot.Builder snapshot = new RAGContextSnapshot.Builder();
                Map<String, iped.data.IItem> items = new java.util.HashMap<>();

                for (int retrievedOrdinal = 0; retrievedOrdinal < docs.size(); retrievedOrdinal++) {
                    checkCancelled();
                    RAGSourceDoc doc = docs.get(retrievedOrdinal);
                    String content = doc.content;
                    String itemId = doc.itemId;
                    String track = doc.contenttrackID;
                    float score = doc.score;
                    if (content != null && !content.isEmpty()) {
                        iped.data.IItem item = items.computeIfAbsent(itemId, id -> getSourceItem(doc));
                        String docName = item != null ? item.getName() : null;
                        content = stripTrailingMetadata(content);
                        String header = item != null
                                ? fragmentHeader(itemId, docName, item.getPath(), item.getMetadata())
                                : fragmentHeader(itemId, null);
                        List<String> copies = new ArrayList<>();
                        for (RAGSourceDoc copy : doc.identicalCopies) {
                            iped.data.IItem copyItem = items.computeIfAbsent(copy.itemId, id -> getSourceItem(copy));
                            copies.add("[" + copy.itemId + "]" + (copyItem != null ? " " + copyItem.getName() : ""));
                        }
                        StringBuilder tempChunk = new StringBuilder(withIdenticalCopies(header, copies));
                        int bodyStart = ctx.length() + tempChunk.length();
                        tempChunk.append(content).append("\n\n========================================\n\n");

                        if (baseLength + ctx.length() + tempChunk.length() > maxChars) {
                            limitReached = true;
                            omittedCount++;
                            snapshot.omit(retrievedOrdinal, itemId, track, false);
                            continue;
                        }

                        ctx.append(tempChunk);
                        snapshot.include(itemId, track, docName, content, score, retrievedOrdinal, bodyStart);
                        docIndex++;
                    } else {
                        snapshot.omit(retrievedOrdinal, itemId, track, true);
                    }
                }

                omittedChunksCount = omittedCount;
                contextLimitReached = limitReached;
                if (llm != null) {
                    // Same builder the provider serializes, so the inspection matches the request.
                    snapshot.prompt(llm.buildPrompt(q, ctx.toString(), historyTurns));
                }
                contextSnapshot = snapshot.build();
                checkCancelled();

                // 4. Ask the LLM with Token Streaming
                return rag.getLlmProvider().generateAnswerStream(q, ctx.toString(), historyTurns, token -> {
                    if (!isCancelled() && !disposed) {
                        publish(token);
                    }
                });
            }

            @Override
            protected void process(List<String> chunks) {
                if (disposed || currentWorker != this || isCancelled()) {
                    return;
                }
                for (String chunk : chunks) {
                    streamingRawAnswer.append(chunk);
                }
                if (activeSession != originSession) {
                    return;
                }

                StringBuilder liveHtml = new StringBuilder();
                liveHtml.append("<html><body style='font-family:sans-serif;font-size:13px;padding:8px;'>");
                if (entryCount > 0) {
                    liveHtml.append(conversationBody);
                    liveHtml.append("<hr style='border:0; border-top:1px solid #E0E0E0; margin-bottom:15px;'/>");
                }
                // User Question
                liveHtml.append("<div style='background-color:#E8F0FE; padding:8px; border-radius:4px; margin-bottom:6px;'>");
                liveHtml.append("<b>").append(Messages.getString("RAGAssistant.YouLabel")).append(":</b> ")
                        .append(markdownToHtml(q));
                liveHtml.append("</div>");
                // Streaming Assistant Answer
                liveHtml.append("<div style='padding:8px; margin-bottom:15px;'>");
                liveHtml.append("<b>").append(Messages.getString("RAGAssistant.AssistantLabel")).append(":</b> ")
                        .append(markdownToHtml(streamingRawAnswer.toString()));
                liveHtml.append("</div>");
                liveHtml.append("</body></html>");

                responsePane.setText(liveHtml.toString());
                responsePane.setCaretPosition(responsePane.getDocument().getLength());
            }

            @Override
            protected void done() {
                if (disposed || currentWorker != this || originSession.deleted) {
                    return;
                }
                try {
                    // Providers return the complete answer; process() events may arrive later.
                    String answer = get();
                    String htmlAnswer = markdownToHtml(answer);
                    if (contextLimitReached) {
                        String formattedWarning = String.format(
                                Messages.getString("RAGAssistant.ContextWindowWarning"),
                                contextWindowValue, omittedChunksCount, totalRetrievedChunksCount);
                        htmlAnswer = htmlAnswer
                                + "<div style='color:#c5221f; font-family:sans-serif; font-size:11px; margin-top:12px; border-top:1px solid #E0E0E0; padding-top:8px;'>"
                                + formattedWarning + "</div>";
                    }
                    String html = "<html><body style='font-family:sans-serif;font-size:13px;padding:8px;'>"
                            + htmlAnswer + "</body></html>";

                    // Determine which sources to display (filtered) vs. full context (unchanged)
                    List<RAGSourceDoc> displayDocs = filterTopSources(foundDocs, q, answer);

                    // Save to database
                    long dbId = -1;
                    if (rag != null) {
                        dbId = rag.saveHistoryEntry(currentSessionId, q, html, displayDocs);
                    }

                    // Commit to the session that submitted this request, even after navigation.
                    HistoryEntry entry = new HistoryEntry(dbId, q, html, answer, displayDocs, requestId);
                    originSession.entries.add(entry);
                    if (rag != null && rag.getConfig() != null && rag.getConfig().isDebugMode()) {
                        contextSnapshots.put(requestId, currentSessionId, contextSnapshot);
                    }

                    if (!sessions.contains(originSession)) {
                        if (originSession.title == null
                                || originSession.title.equals(Messages.getString("RAGAssistant.NewChat"))) {
                            String title = q.length() > 30 ? q.substring(0, 27) + "..." : q;
                            originSession.title = title;
                        }
                        sessions.add(originSession);
                        historyListModel.addElement(originSession.title);
                        // Selecting triggers automatic list selection listener logic
                        if (activeSession == originSession) {
                            historyList.setSelectedIndex(sessions.size() - 1);
                        }
                    }
                    if (activeSession == originSession) {
                        // Just re-render conversation thread
                        responsePane.setContentType("text/html");
                        responsePane.setText(renderConversation(activeSession));
                        SwingUtilities.invokeLater(
                                () -> responsePane.setCaretPosition(responsePane.getDocument().getLength()));
                        sourcesModel.setRows(new ArrayList<>(displayDocs));
                    }

                    // Do not clear a question being composed in another session.
                    if (activeSession != originSession) {
                        return;
                    }
                    String placeholderMsg = Messages.getString("RAGAssistant.Question");
                    if (questionArea.hasFocus()) {
                        questionArea.setText("");
                        questionArea.setForeground(UIManager.getColor("TextField.foreground"));
                    } else {
                        questionArea.setForeground(Color.GRAY);
                        questionArea.setText(placeholderMsg);
                    }

                } catch (CancellationException ce) {
                    // Restore conversation thread without thinking state
                    if (activeSession == originSession) {
                        responsePane.setContentType("text/html");
                        responsePane.setText(renderConversation(originSession));
                    }
                } catch (Exception ex) {
                    LOGGER.error("RAG answer generation error", ex);
                    if (activeSession != originSession) {
                        return;
                    }
                    // Append error message to thread
                    StringBuilder errHtml = new StringBuilder();
                    errHtml.append("<html><body style='font-family:sans-serif;font-size:13px;padding:8px;'>");
                    if (!activeSession.entries.isEmpty()) {
                        errHtml.append(extractBodyContent(renderConversation(activeSession)));
                        errHtml.append("<hr style='border:0; border-top:1px solid #E0E0E0; margin-bottom:15px;'/>");
                    }
                    errHtml.append(
                            "<div style='background-color:#E8F0FE; padding:8px; border-radius:4px; margin-bottom:6px;'>");
                    errHtml.append("<b>").append(Messages.getString("RAGAssistant.YouLabel")).append(":</b> ")
                            .append(markdownToHtml(q));
                    errHtml.append("</div>");
                    errHtml.append("<div style='padding:8px; margin-bottom:15px; color:red;'>");
                    errHtml.append("<b>").append(Messages.getString("RAGAssistant.ErrorTitle")).append(":</b><br/>")
                            .append(iped.utils.SimpleHTMLEncoder.htmlEncode(String.valueOf(ex.getMessage())));
                    errHtml.append("</div>");
                    errHtml.append("</body></html>");
                    responsePane.setContentType("text/html");
                    responsePane.setText(errHtml.toString());
                } finally {
                    if (currentWorker == this) {
                        currentWorker = null;
                        workerSession = null;
                        setUIBusy(false);
                    }
                }
            }
        };
        currentWorker.execute();
    }

    private void onCancel() {
        if (currentWorker != null) {
            SwingWorker<String, String> worker = currentWorker;
            currentWorker = null;
            workerSession = null;
            worker.cancel(true);
            if (!disposed) {
                setUIBusy(false);
                responsePane.setContentType("text/html");
                responsePane.setText(renderConversation(activeSession));
            }
        }
    }

    /** Invalidates callbacks before the owning case closes its readers and services. */
    public void dispose() {
        disposed = true;
        closeContext();
        onCancel();
        contextSnapshots.clear();
    }

    /** Called on the EDT before reloading case readers, and after loading finishes. */
    public void setCaseLoading(boolean loading) {
        if (disposed) {
            return;
        }
        caseLoading = loading;
        if (loading) {
            externalServicesConfirmed = false; // a new case needs a new confirmation
            closeContext();
            onCancel();
            contextSnapshots.clear();
        }
        setUIBusy(loading || currentWorker != null);
        cancelButton.setEnabled(!loading && currentWorker != null);
    }

    private void setUIBusy(boolean busy) {
        askButton.setEnabled(!busy);
        cancelButton.setEnabled(busy);
        questionArea.setEnabled(!busy);
    }

    /** Resolve the selected answer, rather than assuming the last answer owns the sources. */
    RAGContextSnapshot contextForEntry(int entryIndex) {
        if (disposed || caseLoading || activeSession == null || entryIndex < 0
                || entryIndex >= activeSession.entries.size()) {
            return null;
        }
        return contextSnapshots.get(activeSession.entries.get(entryIndex).contextRequestId);
    }

    private void showContext(int entryIndex) {
        RAGContextSnapshot snapshot = contextForEntry(entryIndex);
        String title = Messages.getString("RAGAssistant.Context");
        if (snapshot == null) {
            JOptionPane.showMessageDialog(this, Messages.getString("RAGAssistant.ContextUnavailable"),
                    title, JOptionPane.INFORMATION_MESSAGE);
        } else {
            closeContext();
            RAGContextPanel inspection = new RAGContextPanel(snapshot);
            JOptionPane optionPane = new JOptionPane(inspection, JOptionPane.PLAIN_MESSAGE);
            JDialog dialog = optionPane.createDialog(this, title);
            contextPanel = inspection;
            contextDialog = dialog;
            try {
                dialog.setVisible(true);
            } finally {
                inspection.close();
                dialog.dispose();
                if (contextDialog == dialog) {
                    contextPanel = null;
                    contextDialog = null;
                }
            }
        }
    }

    private void closeContext() {
        if (contextPanel != null) {
            contextPanel.close();
            contextPanel = null;
        }
        if (contextDialog != null) {
            contextDialog.dispose();
            contextDialog = null;
        }
    }

    private static boolean isDebugMode() {
        RAGService rag = RAGService.getInstance();
        return rag != null && rag.getConfig() != null && rag.getConfig().isDebugMode();
    }

    /** Restricts the result table to the sources of an answer, as a filter that can be cleared. */
    private void showSourcesInTable(List<RAGSourceDoc> sources) {
        App app = App.get();
        if (app == null || app.appCase == null || app.ragSourcesFilterer == null) {
            return;
        }
        Map<IItemId, Float> relevance = new java.util.LinkedHashMap<>();
        for (RAGSourceDoc doc : sources) {
            IItemId itemId = sourcesModel.resolve(doc);
            if (itemId != null && !relevance.containsKey(itemId)) {
                // The score column is a 0-100 bar.
                relevance.put(itemId, doc.score * 100);
            }
        }
        if (!relevance.isEmpty()) {
            app.ragSourcesFilterer.showInTable(relevance);
        }
    }

    /** Lucene ID of a source in the case, or -1 when it cannot be found, after warning the user. */
    private int sourceLuceneId(int row) {
        App app = App.get();
        RAGSourceDoc doc = sourcesModel.getRow(row);
        IItemId itemId = doc != null ? sourcesModel.resolve(doc) : null;
        if (app == null || app.appCase == null || itemId == null) {
            JOptionPane.showMessageDialog(this, Messages.getString("RAGAssistant.SourceNotFound"),
                    Messages.getString("RAGAssistant.ErrorTitle"), JOptionPane.WARNING_MESSAGE);
            return -1;
        }
        return app.appCase.getLuceneId(itemId);
    }

    /** Shows the selected source in the viewers, as the subitem and duplicate tables do. */
    private void previewSourceItem(int row) {
        int luceneId = sourceLuceneId(row);
        if (luceneId >= 0) {
            App.get().getTextViewer().textTable.scrollRectToVisible(new Rectangle());
            new FileProcessor(luceneId, false).execute();
        }
    }

    /** Resolves a source without choosing arbitrarily between cases with the same numeric ID. */
    public static IItemId resolveSourceItem(List<? extends IIPEDSource> sources, RAGSourceDoc doc) throws IOException {
        if (doc == null || doc.itemId == null) {
            return null;
        }
        int id;
        try {
            id = Integer.parseInt(doc.itemId);
        } catch (NumberFormatException e) {
            return null;
        }
        if (id < 0) {
            return null;
        }
        String globalId = null;
        if (doc.contenttrackID != null) {
            int sep = doc.contenttrackID.lastIndexOf('#');
            if (sep > 0) {
                globalId = doc.contenttrackID.substring(0, sep);
            }
        }
        IItemId resolved = null;
        for (IIPEDSource source : sources) {
            if (id > source.getLastId()) {
                continue;
            }
            int luceneId = source.getLuceneId(id);
            if (luceneId < 0) {
                continue;
            }
            if (globalId != null && !globalId.equals(source.getSearcher()
                    .doc(luceneId, Collections.singleton(ExtraProperties.GLOBAL_ID)).get(ExtraProperties.GLOBAL_ID))) {
                continue;
            }
            if (resolved != null) {
                return null;
            }
            resolved = new ItemId(source.getSourceId(), id);
        }
        return resolved;
    }

    private void loadHistoryFromDatabase() {
        RAGService rag = RAGService.getInstance();
        if (rag != null && rag.getConfig().isEnabled()) {
            Map<String, String> customTitles = rag.loadSessionTitles();
            List<RAGService.HistoryEntryRecord> records = rag.loadHistoryEntries();
            Map<String, ChatSession> sessionMap = new java.util.LinkedHashMap<>();
            for (RAGService.HistoryEntryRecord rec : records) {
                String sid = rec.sessionId;
                if (sid == null || sid.trim().isEmpty()) {
                    sid = "legacy_" + rec.id;
                }

                HistoryEntry entry = new HistoryEntry(rec.id, rec.question, rec.answerHtml, null, rec.displaySources);

                ChatSession session = sessionMap.get(sid);
                if (session == null) {
                    String title = customTitles.get(sid);
                    if (title == null || title.trim().isEmpty()) {
                        title = rec.question.length() > 30 ? rec.question.substring(0, 27) + "..." : rec.question;
                    }
                    session = new ChatSession(sid, title);
                    sessionMap.put(sid, session);
                    sessions.add(session);
                }
                session.entries.add(entry);
            }

            for (ChatSession session : sessions) {
                historyListModel.addElement(session.title);
            }
        }

        if (sessions.isEmpty()) {
            startNewSession();
        } else {
            historyList.setSelectedIndex(sessions.size() - 1);
        }
    }

    private void startNewSession() {
        closeContext();
        String newSid = java.util.UUID.randomUUID().toString();
        activeSession = new ChatSession(newSid, Messages.getString("RAGAssistant.NewChat"));

        responsePane.setContentType("text/html");
        responsePane.setText("");
        sourcesModel.setRows(new ArrayList<>());

        historyList.clearSelection();

        String placeholderMsg = Messages.getString("RAGAssistant.Question");
        if (questionArea.hasFocus()) {
            questionArea.setText("");
            questionArea.setForeground(UIManager.getColor("TextField.foreground"));
        } else {
            questionArea.setForeground(Color.GRAY);
            questionArea.setText(placeholderMsg);
        }
        questionArea.requestFocusInWindow();
    }

    private void renameSelectedHistory() {
        int idx = historyList.getSelectedIndex();
        if (idx >= 0 && idx < sessions.size()) {
            ChatSession session = sessions.get(idx);
            String newTitle = JOptionPane.showInputDialog(
                    this,
                    Messages.getString("RAGAssistant.RenameChatPrompt"),
                    session.title);
            if (newTitle != null && !newTitle.trim().isEmpty() && !newTitle.equals(session.title)) {
                newTitle = newTitle.trim();
                session.title = newTitle;
                historyListModel.set(idx, newTitle);

                if (session.sessionId != null && !session.sessionId.startsWith("legacy_")) {
                    final String finalTitle = newTitle;
                    RAGService rag = RAGService.getInstance();
                    if (rag != null) {
                        new SwingWorker<Void, Void>() {
                            @Override
                            protected Void doInBackground() throws Exception {
                                rag.updateSessionTitle(session.sessionId, finalTitle);
                                return null;
                            }
                        }.execute();
                    }
                }
            }
        }
    }

    private void deleteSelectedHistory() {
        int idx = historyList.getSelectedIndex();
        if (idx >= 0 && idx < sessions.size()) {
            int option = JOptionPane.showConfirmDialog(
                    this,
                    Messages.getString("RAGAssistant.ConfirmDeleteChat"),
                    Messages.getString("RAGAssistant.ConfirmDeleteTitle"),
                    JOptionPane.YES_NO_OPTION);
            if (option == JOptionPane.YES_OPTION) {
                removeSession(idx);
            }
        }
    }

    void removeSession(int idx) {
        closeContext();
        ChatSession session = sessions.get(idx);
        session.deleted = true;
        if (workerSession == session) {
            onCancel();
        }
        contextSnapshots.removeSession(session.sessionId);
        sessions.remove(idx);
        historyListModel.remove(idx);

        if (sessions.isEmpty()) {
            startNewSession();
        } else {
            int newSel = Math.max(0, idx - 1);
            historyList.setSelectedIndex(newSel);
        }

        if (session.sessionId != null && !session.sessionId.startsWith("legacy_")) {
            RAGService rag = RAGService.getInstance();
            if (rag != null) {
                new SwingWorker<Void, Void>() {
                    @Override
                    protected Void doInBackground() throws Exception {
                        rag.deleteHistorySession(session.sessionId);
                        return null;
                    }
                }.execute();
            }
        }
    }

    private String renderConversation(ChatSession session) {
        if (session == null || session.entries.isEmpty()) {
            return "<html><body style='font-family:sans-serif;font-size:13px;padding:8px;'></body></html>";
        }
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family:sans-serif;font-size:13px;padding:8px;'>");

        for (int i = 0; i < session.entries.size(); i++) {
            HistoryEntry entry = session.entries.get(i);

            // User Question
            html.append("<div style='background-color:#E8F0FE; padding:8px; border-radius:4px; margin-bottom:6px;'>");
            html.append("<b>").append(Messages.getString("RAGAssistant.YouLabel")).append(":</b> ")
                    .append(markdownToHtml(entry.question));
            html.append("</div>");

            // Assistant Answer
            html.append("<div style='padding:8px; margin-bottom:15px;'>");
            html.append("<b>").append(Messages.getString("RAGAssistant.AssistantLabel")).append(":</b> ").append(
                    entry.answerHtml != null ? extractBodyContent(entry.answerHtml) : markdownToHtml(entry.rawAnswer));

            if (entry.displaySources != null && !entry.displaySources.isEmpty()) {
                int count = entry.displaySources.size();
                String label = Messages.getString("RAGAssistant.SourcesConsulted", count);
                String hint = Messages.getString("RAGAssistant.SourcesConsultedHint");
                html.append("<div style='margin-top:6px; font-size:11px; color:#5f6368;'>");
                html.append("&#128193; <a href='sources:").append(i).append("' style='color:#1a73e8; text-decoration:none;'><b>")
                        .append(label).append("</b> ").append(hint).append("</a>");
                html.append("</div>");
            }
            if (isDebugMode()) {
                html.append("<div style='margin-top:6px; font-size:11px;'><a href='context:")
                        .append(i).append("'>").append(Messages.getString("RAGAssistant.Context"))
                        .append("</a></div>");
            }
            html.append("</div>");

            if (i < session.entries.size() - 1) {
                html.append("<hr style='border:0; border-top:1px solid #E0E0E0; margin-bottom:15px;'/>");
            }
        }

        html.append("</body></html>");
        return html.toString();
    }

    private String extractBodyContent(String html) {
        if (html == null)
            return "";
        int bodyStart = html.indexOf("<body");
        if (bodyStart == -1)
            return html;
        int bodyClose = html.indexOf(">", bodyStart);
        if (bodyClose == -1)
            return html;
        int bodyEnd = html.indexOf("</body>");
        if (bodyEnd == -1) {
            return html.substring(bodyClose + 1);
        }
        return html.substring(bodyClose + 1, bodyEnd);
    }

    // ---------------------------------------------------------------- helpers

    private List<RAGSourceDoc> deduplicateByItemId(List<RAGSourceDoc> list) {
        if (list == null || list.isEmpty())
            return list;
        java.util.Map<String, RAGSourceDoc> uniqueDocs = new java.util.LinkedHashMap<>();
        for (RAGSourceDoc doc : list) {
            if (doc.itemId != null) {
                String key = getSourceItemKey(doc);
                if (!uniqueDocs.containsKey(key)) {
                    uniqueDocs.put(key, doc);
                } else {
                    if (doc.score > uniqueDocs.get(key).score) {
                        uniqueDocs.put(key, doc);
                    }
                }
            }
        }
        return new ArrayList<>(uniqueDocs.values());
    }

    static String getSourceItemKey(RAGSourceDoc doc) {
        if (doc.contenttrackID != null) {
            int sep = doc.contenttrackID.lastIndexOf('#');
            if (sep > 0) {
                return doc.contenttrackID.substring(0, sep);
            }
        }
        return doc.itemId;
    }

    /**
     * Detects whether the LLM answer is a negative response indicating that no
     * relevant information was found in the retrieved context.
     *
     * Uses a broad regex pattern instead of a fixed list of literal strings so
     * that variations in phrasing across different LLM models and both PT-BR/EN
     * are handled uniformly.
     */
    private List<RAGSourceDoc> filterTopSources(List<RAGSourceDoc> docs, String query, String answer) {
        if (docs == null || docs.isEmpty() || answer == null) {
            return new ArrayList<>();
        }

        Set<String> citedIds = new HashSet<>();

        // 1. Match bracket citations like [5], [5, 6]
        java.util.regex.Pattern pBrackets = java.util.regex.Pattern.compile("\\[([\\d,\\s]+)\\]");
        java.util.regex.Matcher m = pBrackets.matcher(answer);
        while (m.find()) {
            for (String part : m.group(1).split(",")) {
                String id = part.trim();
                if (!id.isEmpty())
                    citedIds.add(id);
            }
        }

        List<RAGSourceDoc> citedList = new ArrayList<>();
        Set<String> added = new java.util.HashSet<>();

        // Match docs cited by bracket ID [5]
        for (RAGSourceDoc doc : docs) {
            if (doc.itemId != null && citedIds.contains(doc.itemId)) {
                if (added.add(getSourceItemKey(doc))) {
                    citedList.add(doc);
                }
            }
        }

        // 2. Also match docs cited by filename/title in answer text (e.g.
        // "PGR-00149505/2023" or "Relotacao.pdf")
        String lowerAnswer = answer.toLowerCase(java.util.Locale.ROOT);
        for (RAGSourceDoc doc : docs) {
            if (doc.itemId != null && !added.contains(getSourceItemKey(doc))) {
                String name = getDocName(doc);
                if (name != null && !name.trim().isEmpty()) {
                    String baseName = name;
                    int dotIdx = name.lastIndexOf('.');
                    if (dotIdx > 0) {
                        baseName = name.substring(0, dotIdx);
                    }
                    if (baseName.length() >= 3 && lowerAnswer.contains(baseName.toLowerCase(java.util.Locale.ROOT))) {
                        added.add(getSourceItemKey(doc));
                        citedList.add(doc);
                    }
                }
            }
        }

        if (citedList.isEmpty()) {
            return deduplicateByItemId(docs);
        }
        return citedList;
    }

    static String extractSearchQueryWithHistory(String question, List<RAGService.HistoryTurn> historyTurns) {
        if (historyTurns == null || historyTurns.isEmpty() || question == null) {
            return question;
        }
        StringBuilder searchStr = new StringBuilder(question);
        RAGService.HistoryTurn lastTurn = historyTurns.get(historyTurns.size() - 1);
        if (lastTurn != null && lastTurn.question != null) {
            String[] tokens = lastTurn.question.split("[^\\p{L}\\p{N}]");
            for (String token : tokens) {
                String trimmed = token.trim();
                // Stop words are stored without accents; keep the original token for the search.
                String unaccented = java.text.Normalizer.normalize(trimmed.toLowerCase(), java.text.Normalizer.Form.NFD)
                        .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
                if (trimmed.length() >= 3 && !isStopWord(unaccented)) {
                    if (!question.toLowerCase().contains(trimmed.toLowerCase())) {
                        searchStr.append(" ").append(trimmed);
                    }
                }
            }
        }
        return searchStr.toString();
    }

    private RAGSearchScope collectSearchScope() {
        if (chkScopeOnly == null || !chkScopeOnly.isSelected()) {
            return null;
        }
        try {
            App app = App.get();
            if (app != null && app.appCase != null) {
                List<IPEDSource> sources = app.appCase.getAtomicSources();
                return new RAGSearchScope(sources, collectScopeItems(sources, app.getResultsTable(), app.getResults()));
            }
        } catch (Exception e) {
            LOGGER.error("Error collecting selected items for RAG scope filter", e);
        }
        // A failure to read a restricted scope must never turn into an unrestricted search.
        return new RAGSearchScope(Collections.emptyList(), Collections.emptySet());
    }

    static Set<IItemId> collectScopeItems(List<? extends IIPEDSource> sources, JTable table, IMultiSearchResult results) {
        Set<IItemId> selected = new LinkedHashSet<>();
        // Checked items take priority, including items outside the current result table.
        for (IIPEDSource source : sources) {
            if (source.getBookmarks() != null) {
                for (int id = 0; id <= source.getLastId(); id++) {
                    if (source.getBookmarks().isChecked(id)) {
                        selected.add(new ItemId(source.getSourceId(), id));
                    }
                }
            }
        }
        if (selected.isEmpty() && table != null && results != null) {
            for (int row : table.getSelectedRows()) {
                IItemId item = results.getItem(table.convertRowIndexToModel(row));
                if (item != null) {
                    selected.add(item);
                }
            }
        }
        if (selected.isEmpty() && results != null) {
            for (IItemId item : results.getIterator()) {
                if (item != null) {
                    selected.add(item);
                }
            }
        }
        return selected;
    }

    private String getMessageSafely(String key, String defaultValue) {
        try {
            return Messages.getString(key);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String getDocName(RAGSourceDoc doc) {
        iped.data.IItem item = getSourceItem(doc);
        return item != null ? item.getName() : null;
    }

    private iped.data.IItem getSourceItem(RAGSourceDoc doc) {
        if (doc == null || doc.itemId == null)
            return null;
        try {
            App app = App.get();
            if (app != null && app.appCase != null) {
                IItemId itemId = resolveSourceItem(app.appCase.getAtomicSources(), doc);
                if (itemId != null) {
                    return app.appCase.getItemByItemId(itemId);
                }
            }
        } catch (Exception ignore) {
        }
        return null;
    }

    /**
     * Stop-words used to filter out noise tokens when building the lexical query
     * context.
     */
    private static final Set<String> STOP_WORDS = new HashSet<>(java.util.Arrays.asList(
            // Portuguese function words
            "como", "para", "com", "uma", "mais", "este", "esta", "estes", "estas",
            "esse", "essa", "esses", "essas", "aquele", "aquela",
            "pelo", "pela", "pelos", "pelas", "seus", "suas",
            "onde", "quando", "quem", "qual", "quais",
            "todo", "toda", "todos", "todas", "tudo",
            "sobre", "entre", "sempre", "nunca", "talvez",
            "ainda", "muito", "pouco", "tanto", "cada",
            "outro", "outra", "outros", "outras",
            "mesmo", "mesma", "pode", "podem",
            "base", "documento", "documentos", "fornecido", "fornecidos",
            "arquivo", "arquivos", "conteudo", "resumir", "listar", "descritos",
            "tamanho", "tipo", "existem", "dois", "identificados", "nomes",
            "explicitos", "vez",
            "nos", "nas", "num", "numa", "mas", "nao",
            "sim", "aos", "sua", "seu", "ele", "ela", "eles", "elas",
            "isso", "isto", "aquilo",
            "ser", "sao", "era", "eram", "seja", "sejam", "foi", "foram",
            "ter", "tem", "tinha", "tinham",
            "poderia", "poderiam", "deve", "devem",
            "primeiro", "segundo", "terceiro",
            "bloco", "blocos", "linha", "linhas",
            "item", "itens", "id", "ids",
            "foto", "fotos", "imagem", "imagens",
            "contexto", "contextos", "metadado", "metadados",
            // English function words
            "out", "this", "that", "with", "from", "have", "been",
            "type", "regular", "folder", "name"));

    private static boolean isStopWord(String word) {
        return STOP_WORDS.contains(word);
    }

    /**
     * Minimal markdown-to-HTML conversion for LLM responses:
     * bold, italic, code blocks, inline code and newlines.
     */
    static String markdownToHtml(String md) {
        if (md == null)
            return "";
        String html = md
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
        // code blocks
        html = html.replaceAll("(?s)```[a-z]*\n?(.*?)```",
                "<pre style='background:#f4f4f4;padding:8px;border-radius:4px;'>$1</pre>");
        // inline code
        html = html.replaceAll("`([^`]+)`",
                "<code style='background:#f4f4f4;padding:2px 4px;border-radius:3px;'>$1</code>");
        // bold
        html = html.replaceAll("\\*\\*([^*]+)\\*\\*", "<b>$1</b>");
        // Underscore emphasis only at word boundaries, so names like nota_fiscal_2024.pdf stay intact.
        html = html.replaceAll("(?<!\\w)__([^_]+)__(?!\\w)", "<b>$1</b>");
        // italic
        html = html.replaceAll("\\*([^*]+)\\*", "<i>$1</i>");
        html = html.replaceAll("(?<!\\w)_([^_]+)_(?!\\w)", "<i>$1</i>");
        // newlines
        html = html.replace("\n", "<br/>");
        return html;
    }

    private static String stripHtml(String html) {
        if (html == null)
            return "";
        String text = html.replaceAll("(?i)<br\\s*/?>", "\n");
        text = text.replaceAll("<[^>]*>", "");
        text = text.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&");
        return text.trim();
    }

    /** The chat tab needs RAG enabled and LLM queries allowed (enableLLMQueries). */
    public static boolean isAssistantEnabled(RAGConfig config) {
        return config != null && config.isEnabled() && config.isEnableLLMQueries();
    }

    static String fragmentHeader(String itemId, String docName) {
        return fragmentHeader(itemId, docName, null, null);
    }

    /**
     * Header of one fragment in the model context. The ID is the one the model
     * cites. The retrieval score is not sent: it is normalized per query, so the
     * first fragment always shows 1.000, even when unrelated to the question.
     * The item details are repeated in every fragment, because the parser writes
     * the metadata only at the end of the text, which only the last fragment has.
     */
    /** Labels of the fragment header, in the language of the prompt sent with them. */
    private static String label(String pt, String en) {
        return "pt".equalsIgnoreCase(iped.localization.LocaleResolver.getLocale().getLanguage()) ? pt : en;
    }

    static String fragmentHeader(String itemId, String docName, String path, Metadata metadata) {
        StringBuilder header = new StringBuilder("[").append(itemId).append("]");
        if (docName != null) {
            header.append(label(" Documento: ", " Document: ")).append(docName);
        }
        header.append('\n');
        for (String detail : itemDetails(docName, path, metadata)) {
            header.append(detail).append('\n');
        }
        return header.append("---\n").toString();
    }

    /** Adds to the header the items with the same or mostly the same text, which are not repeated in the context. */
    static String withIdenticalCopies(String header, List<String> copies) {
        if (copies.isEmpty()) {
            return header;
        }
        String separator = "---\n";
        return header.substring(0, header.length() - separator.length()) + label("Mesmo conteúdo em: ", "Same content in: ")
                + String.join("; ", copies) + "\n" + separator;
    }

    /** Subject or title, sender or author, recipients, date and path, when indexed. */
    static List<String> itemDetails(String docName, String path, Metadata metadata) {
        List<String> details = new ArrayList<>();
        if (metadata != null) {
            String subject = metadataValue(metadata, ExtraProperties.MESSAGE_SUBJECT);
            String title = metadataValue(metadata, ExtraProperties.COMMON_META_PREFIX + TikaCoreProperties.TITLE.getName());
            if (subject != null) {
                details.add(label("Assunto: ", "Subject: ") + subject);
            } else if (title != null && !title.equals(docName)) {
                details.add(label("Título: ", "Title: ") + title);
            }
            String from = metadataValue(metadata, ExtraProperties.COMMUNICATION_FROM);
            String author = metadataValue(metadata, ExtraProperties.COMMON_META_PREFIX + TikaCoreProperties.CREATOR.getName());
            if (from != null) {
                details.add(label("De: ", "From: ") + from);
            } else if (author != null) {
                details.add(label("Autor: ", "Author: ") + author);
            }
            String to = metadataValue(metadata, ExtraProperties.COMMUNICATION_TO);
            if (to != null) {
                details.add(label("Para: ", "To: ") + to);
            }
            String date = metadataValue(metadata, ExtraProperties.COMMUNICATION_DATE.getName());
            String created = metadataValue(metadata, ExtraProperties.COMMON_META_PREFIX + TikaCoreProperties.CREATED.getName());
            if (date != null) {
                details.add(label("Data: ", "Date: ") + date);
            } else if (created != null) {
                details.add(label("Criado em: ", "Created: ") + created);
            }
        }
        if (path != null && !path.isEmpty()) {
            details.add(label("Caminho: ", "Path: ") + path);
        }
        return details;
    }

    private static String metadataValue(Metadata metadata, String name) {
        String[] values = metadata.getValues(name);
        if (values == null || values.length == 0) {
            return null;
        }
        String value = String.join("; ", values).replaceAll("\\s+", " ").trim();
        if (value.isEmpty()) {
            return null;
        }
        return value.length() > MAX_DETAIL_CHARS ? value.substring(0, MAX_DETAIL_CHARS) + "(...)" : value;
    }

    /**
     * Removes the metadata block the parser writes at the end of the text, already
     * summarized in the fragment header, from what is sent to the model. The index
     * keeps it, so keyword search still finds it. Only a trailing block of
     * "name: value" lines is removed. A fragment with nothing else, such as an
     * image with only EXIF data, is kept whole.
     */
    static String stripTrailingMetadata(String content) {
        String header = StandardParser.METADATA_HEADER;
        String text = content;
        boolean removed = false;
        int start;
        while ((start = text.lastIndexOf(header)) != -1 && (start == 0 || text.charAt(start - 1) == '\n')
                && isMetadataTail(text.substring(start + header.length()))) {
            text = text.substring(0, start);
            removed = true;
        }
        int end = text.length();
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return !removed || end == 0 ? content : text.substring(0, end);
    }

    /**
     * Most lines must be "name: value". Values may span lines, as the message body
     * preview, and the last line may be cut at the fragment boundary.
     */
    private static boolean isMetadataTail(String tail) {
        int metadataLines = 0, otherLines = 0;
        String[] lines = tail.split("\n");
        int complete = tail.endsWith("\n") ? lines.length : lines.length - 1;
        for (int i = 0; i < complete; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.equals(StandardParser.METADATA_FOOTER)) {
                continue;
            }
            if (isMetadataLine(line)) {
                metadataLines++;
            } else {
                otherLines++;
            }
        }
        // an empty tail is the inner, empty block some parsers write before the outer one
        return metadataLines > otherLines || (metadataLines == 0 && otherLines == 0);
    }

    private static boolean isMetadataLine(String line) {
        return line.indexOf(':') > 0 && !Character.isWhitespace(line.charAt(0));
    }

    /**
     * Characters available to the whole prompt, after reserving room for the
     * answer. Remote providers are not bounded here.
     */
    static int promptCharBudget(RAGConfig config) {
        if (!"local".equalsIgnoreCase(config.getLlmProvider())) {
            return Integer.MAX_VALUE;
        }
        int contextWindow = config.getLlmContextWindow();
        int reservedTokens = LocalLLMProvider.generationTokenReserve(contextWindow);
        return (int) ((contextWindow - reservedTokens) * CHARS_PER_TOKEN);
    }

    static int historyCharBudget(int promptBudget) {
        if (promptBudget == Integer.MAX_VALUE) {
            return REMOTE_HISTORY_CHARS;
        }
        return (int) (promptBudget * HISTORY_BUDGET_SHARE);
    }

    /**
     * Keeps the most recent turns that fit the budget, in chronological order.
     * Older turns that do not fit are dropped whole; only the most recent turn is
     * truncated, so a single long answer cannot exceed the budget.
     */
    static List<RAGService.HistoryTurn> boundHistory(List<RAGService.HistoryTurn> turns, int maxTurns,
            int maxChars) {
        java.util.LinkedList<RAGService.HistoryTurn> selected = new java.util.LinkedList<>();
        int used = 0;
        for (int i = turns.size() - 1; i >= 0 && selected.size() < maxTurns; i--) {
            RAGService.HistoryTurn turn = turns.get(i);
            int cost = historyTurnChars(turn);
            if (used + cost <= maxChars) {
                selected.addFirst(turn);
                used += cost;
                continue;
            }
            if (selected.isEmpty()) {
                int room = maxChars - HISTORY_TURN_TEMPLATE_CHARS;
                if (room > 0) {
                    String question = truncate(turn.question != null ? turn.question : "", room);
                    String answer = truncate(turn.answer != null ? turn.answer : "", room - question.length());
                    selected.addFirst(new RAGService.HistoryTurn(question, answer));
                }
            }
            break;
        }
        return new ArrayList<>(selected);
    }

    /**
     * Estimates the prompt characters outside the evidence fragments. Local
     * budgets also keep a proportional margin for token estimation errors.
     */
    static int promptBaseChars(String systemPrompt, String question, List<RAGService.HistoryTurn> history,
            int promptBudget) {
        long chars = PROMPT_TEMPLATE_CHARS + (systemPrompt != null ? systemPrompt.length() : 0)
                + (question != null ? question.length() : 0);
        for (RAGService.HistoryTurn turn : history) {
            chars += historyTurnChars(turn);
        }
        if (promptBudget != Integer.MAX_VALUE) {
            chars += (long) (promptBudget * PROMPT_SAFETY_SHARE);
        }
        return (int) Math.min(Integer.MAX_VALUE, chars);
    }

    private static int historyTurnChars(RAGService.HistoryTurn turn) {
        return HISTORY_TURN_TEMPLATE_CHARS + (turn.question != null ? turn.question.length() : 0)
                + (turn.answer != null ? turn.answer.length() : 0);
    }

    private static String truncate(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return text;
        }
        if (maxChars <= TRUNCATION_MARKER.length()) {
            return "";
        }
        int end = maxChars - TRUNCATION_MARKER.length();
        if (Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end) + TRUNCATION_MARKER;
    }

    // ================================================================ inner
    // classes

    private static class ChatSession {
        final String sessionId;
        String title;
        final List<HistoryEntry> entries = new ArrayList<>();
        boolean deleted;

        ChatSession(String sessionId, String title) {
            this.sessionId = sessionId;
            this.title = title;
        }
    }

    /**
     * Stores a single history entry: the original question text, the rendered
     * HTML response and the filtered list of source documents to display.
     */
    private static class HistoryEntry {
        final Long dbId;
        final String question;
        final String answerHtml;
        final String rawAnswer;
        final List<RAGSourceDoc> displaySources;
        final String contextRequestId;

        HistoryEntry(Long dbId, String question, String answerHtml, String rawAnswer,
                List<RAGSourceDoc> displaySources) {
            this(dbId, question, answerHtml, rawAnswer, displaySources, null);
        }

        HistoryEntry(Long dbId, String question, String answerHtml, String rawAnswer,
                List<RAGSourceDoc> displaySources, String contextRequestId) {
            this.dbId = dbId;
            this.question = question;
            this.answerHtml = answerHtml;
            this.rawAnswer = rawAnswer;
            this.displaySources = displaySources;
            this.contextRequestId = contextRequestId;
        }
    }

    static class SourcesTableModel extends AbstractTableModel {

        private static final long serialVersionUID = 1L;
        static final int CHECK_COLUMN = 1;

        private List<RAGSourceDoc> rows = new ArrayList<>();
        /** Items of the rows, resolved once: the table is repainted often. */
        private final Map<RAGSourceDoc, Optional<IItemId>> resolved = new java.util.IdentityHashMap<>();

        void setRows(List<RAGSourceDoc> rows) {
            this.rows = rows == null ? new ArrayList<>() : rows;
            resolved.clear();
            fireTableDataChanged();
        }

        RAGSourceDoc getRow(int rowIndex) {
            if (rowIndex < 0 || rowIndex >= rows.size())
                return null;
            return rows.get(rowIndex);
        }

        IItemId resolve(RAGSourceDoc doc) {
            return resolved.computeIfAbsent(doc, d -> {
                try {
                    App app = App.get();
                    if (app != null && app.appCase != null) {
                        return Optional.ofNullable(resolveSourceItem(app.appCase.getAtomicSources(), d));
                    }
                } catch (Exception e) {
                    LOGGER.warn("Error resolving RAG source item: {}", e.toString());
                }
                return Optional.empty();
            }).orElse(null);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return 5;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == CHECK_COLUMN && resolve(rows.get(rowIndex)) != null;
        }

        @Override
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            IItemId itemId = resolve(rows.get(rowIndex));
            if (columnIndex == CHECK_COLUMN && itemId != null) {
                App.get().appCase.getMultiBookmarks().setChecked((Boolean) value, itemId);
                BookmarksController.get().updateUISelection();
            }
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            RAGSourceDoc doc = rows.get(rowIndex);
            IItemId itemId = resolve(doc);
            App app = App.get();
            switch (columnIndex) {
                case 0:
                    return doc.itemId;
                case CHECK_COLUMN:
                    return itemId != null && app.appCase.getMultiBookmarks().isChecked(itemId);
                case 3:
                    return String.format("%.3f", doc.score);
                case 2:
                case 4: {
                    try {
                        iped.data.IItem item = itemId != null ? app.appCase.getItemByItemId(itemId) : null;
                        if (item != null) {
                            return columnIndex == 2 ? item.getName() : item.getPath();
                        }
                    } catch (Exception ignore) {
                    }
                    return columnIndex == 2 ? doc.itemId : "";
                }
                default:
                    return "";
            }
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == CHECK_COLUMN ? Boolean.class : String.class;
        }
    }
}
