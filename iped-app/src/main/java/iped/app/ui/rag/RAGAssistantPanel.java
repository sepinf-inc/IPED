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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JEditorPane;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.app.ui.App;
import iped.app.ui.FileProcessor;
import iped.app.ui.Messages;
import iped.data.IIPEDSource;
import iped.data.IItemId;
import iped.engine.data.IPEDMultiSource;
import iped.engine.data.IPEDSource;
import iped.engine.data.ItemId;
import iped.engine.rag.RAGService;
import iped.engine.rag.RAGService.RAGSourceDoc;

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
 * item in the standard IPED viewer.
 */
public class RAGAssistantPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = LoggerFactory.getLogger(RAGAssistantPanel.class);

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
    private SwingWorker<String, Void> currentWorker;
    private final List<ChatSession> sessions = new ArrayList<>();
    private ChatSession activeSession;

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

        chkScopeOnly = new JCheckBox(getMessageSafely("RAGAssistant.ScopeSelected", "Restringir ?? sele????o"));
        chkScopeOnly.setToolTipText(getMessageSafely("RAGAssistant.ScopeSelectedTooltip",
                "Restringe a busca RAG apenas aos itens marcados, selecionados na tabela ou vis??veis nos filtros atuais."));
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
            if (e.getEventType() == javax.swing.event.HyperlinkEvent.EventType.ACTIVATED) {
                String description = e.getDescription();
                if (description != null && description.startsWith("sources:")) {
                    try {
                        int entryIndex = Integer.parseInt(description.substring(8));
                        if (activeSession != null && entryIndex >= 0 && entryIndex < activeSession.entries.size()) {
                            HistoryEntry entry = activeSession.entries.get(entryIndex);
                            if (entry != null && entry.displaySources != null) {
                                sourcesModel.setRows(new ArrayList<>(entry.displaySources));
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
                Messages.getString("RAGAssistant.Header.Name"),
                Messages.getString("RAGAssistant.Header.Score"),
                Messages.getString("RAGAssistant.Header.Path")
        };
        int[] widths = { 50, 240, 70, 500 };
        for (int i = 0; i < headers.length; i++) {
            TableColumn col = new TableColumn(i, widths[i]);
            col.setHeaderValue(headers[i]);
            col.setPreferredWidth(widths[i]);
            colModel.addColumn(col);
        }
        sourcesTable.setColumnModel(colModel);

        // Double-click to open the item in IPED viewer
        sourcesTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = sourcesTable.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        openSourceItem(row);
                    }
                }
            }
        });
    }

    // ---------------------------------------------------------------- actions

    private void onAsk() {
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

        if (activeSession == null) {
            startNewSession();
        }

        final String q = question;
        final String currentSessionId = activeSession.sessionId;
        final boolean isFirstQuestion = activeSession.entries.isEmpty();

        // Assemble chat history context (last 5 turns) from active session
        final List<RAGService.HistoryTurn> historyTurns = new ArrayList<>();
        int entryCount = activeSession.entries.size();
        if (entryCount > 0) {
            int startIdx = Math.max(0, entryCount - 5);
            for (int i = startIdx; i < entryCount; i++) {
                HistoryEntry he = activeSession.entries.get(i);
                String cleanAns = he.rawAnswer != null ? he.rawAnswer : stripHtml(he.answerHtml);
                historyTurns.add(new RAGService.HistoryTurn(he.question, cleanAns));
            }
        }

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

        // Collect allowed item IDs here, on the EDT, before handing off to the worker.
        // This guarantees that all Swing component reads happen on the Event Dispatch
        // Thread.
        final Set<String> scopeAllowedIds = collectAllowedParentIds();

        currentWorker = new SwingWorker<String, Void>() {
            private List<RAGSourceDoc> foundDocs = new ArrayList<>();
            private int omittedChunksCount = 0;
            private boolean contextLimitReached = false;
            private int contextWindowValue = 4096;
            private int totalRetrievedChunksCount = 0;

            @Override
            protected String doInBackground() throws Exception {
                // 1. Prepare contextualized search query for multi-turn questions
                String searchQuery = extractSearchQueryWithHistory(q, historyTurns);

                // 2. Embed the question
                float[] qvec = rag.getEmbedding(searchQuery);
                if (qvec == null) {
                    throw new IOException("Could not generate embedding for the question.");
                }

                // 3. Hybrid search using contextualized search query and scope filter
                List<RAGSourceDoc> docs = rag.performHybridSearch(searchQuery, qvec, scopeAllowedIds);
                foundDocs.addAll(docs);

                if (docs.isEmpty()) {
                    return Messages.getString("RAGAssistant.NoContextFound");
                }

                // 3. Build context
                StringBuilder ctx = new StringBuilder();
                int docIndex = 1;
                int omittedCount = 0;
                boolean limitReached = false;

                int contextWindow = rag.getConfig().getLlmContextWindow();
                contextWindowValue = contextWindow;
                int maxChars;
                if ("local".equalsIgnoreCase(rag.getConfig().getLlmProvider())) {
                    int reservedTokens = Math.min(1024, contextWindow / 2);
                    // 1 token ??? 3.8 characters in Portuguese/English. Multiplier of 3.8 accurately
                    // reflects the character budget without premature truncation.
                    maxChars = (int) ((contextWindow - reservedTokens) * 3.8);
                } else {
                    maxChars = Integer.MAX_VALUE;
                }

                // Base length of prompt (system prompt + user prompt boilerplate + question) is
                // roughly 2500 chars
                int baseLength = 2500 + (q != null ? q.length() : 0);
                totalRetrievedChunksCount = docs.size();

                for (RAGSourceDoc doc : docs) {
                    if (doc.content != null && !doc.content.isEmpty()) {
                        String docName = getDocName(doc);
                        StringBuilder tempChunk = new StringBuilder();
                        tempChunk.append("[").append(doc.itemId).append("]\n");
                        if (docName != null) {
                            tempChunk.append("Nome do Arquivo: ").append(docName).append("\n");
                        }
                        tempChunk.append("Score H??brido: ")
                                .append(String.format(java.util.Locale.US, "%.3f", doc.score)).append("\n");
                        tempChunk.append("---\n");
                        tempChunk.append(doc.content).append("\n\n========================================\n\n");

                        if (baseLength + ctx.length() + tempChunk.length() > maxChars) {
                            limitReached = true;
                            omittedCount++;
                            continue;
                        }

                        ctx.append(tempChunk);
                        docIndex++;
                    }
                }

                omittedChunksCount = omittedCount;
                contextLimitReached = limitReached;

                // 4. Ask the LLM
                return rag.getLlmProvider().generateAnswer(q, ctx.toString(), historyTurns);
            }

            @Override
            protected void done() {
                setUIBusy(false);
                try {
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

                    // Add to active session
                    HistoryEntry entry = new HistoryEntry(dbId, q, html, answer, displayDocs);
                    activeSession.entries.add(entry);

                    if (isFirstQuestion) {
                        if (activeSession.title == null
                                || activeSession.title.equals(Messages.getString("RAGAssistant.NewChat"))) {
                            String title = q.length() > 30 ? q.substring(0, 27) + "..." : q;
                            activeSession.title = title;
                        }
                        sessions.add(activeSession);
                        historyListModel.addElement(activeSession.title);
                        // Selecting triggers automatic list selection listener logic
                        int newIdx = sessions.size() - 1;
                        historyList.setSelectedIndex(newIdx);
                    } else {
                        // Just re-render conversation thread
                        responsePane.setContentType("text/html");
                        responsePane.setText(renderConversation(activeSession));
                        SwingUtilities.invokeLater(
                                () -> responsePane.setCaretPosition(responsePane.getDocument().getLength()));
                        sourcesModel.setRows(new ArrayList<>(displayDocs));
                    }

                    // Clear question area
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
                    responsePane.setContentType("text/html");
                    responsePane.setText(renderConversation(activeSession));
                } catch (Exception ex) {
                    LOGGER.error("RAG answer generation error", ex);
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
                            .append(ex.getMessage());
                    errHtml.append("</div>");
                    errHtml.append("</body></html>");
                    responsePane.setContentType("text/html");
                    responsePane.setText(errHtml.toString());
                }
            }
        };
        currentWorker.execute();
    }

    private void onCancel() {
        if (currentWorker != null && !currentWorker.isDone()) {
            currentWorker.cancel(true);
        }
    }

    private void setUIBusy(boolean busy) {
        askButton.setEnabled(!busy);
        cancelButton.setEnabled(busy);
        questionArea.setEnabled(!busy);
    }

    private void openSourceItem(int row) {
        RAGSourceDoc doc = sourcesModel.getRow(row);
        if (doc == null || doc.itemId == null)
            return;
        try {
            int ipedId = Integer.parseInt(doc.itemId);
            // Search for the lucene docId matching this IPED itemId
            App app = App.get();
            if (app.appCase instanceof IPEDMultiSource) {
                for (IPEDSource src : ((IPEDMultiSource) app.appCase).getAtomicSources()) {
                    if (ipedId <= src.getLastId()) {
                        IItemId itemId = new ItemId(src.getSourceId(), ipedId);
                        int luceneId = app.appCase.getLuceneId(itemId);
                        if (luceneId >= 0) {
                            new FileProcessor(luceneId, false).execute();
                            return;
                        }
                    }
                }
            }
        } catch (NumberFormatException nfe) {
            // ignore
        }
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
                ChatSession session = sessions.remove(idx);
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
                boolean isPt = "pt".equalsIgnoreCase(iped.localization.LocaleResolver.getLocale().getLanguage());
                String label = isPt
                        ? (count == 1 ? "1 fonte consultada" : count + " fontes consultadas")
                        : (count == 1 ? "1 source consulted" : count + " sources consulted");
                String hint = isPt ? "(clique para carregar no grid)" : "(click to load in grid)";
                html.append("<div style='margin-top:6px; font-size:11px; color:#5f6368;'>");
                html.append("&#128193; <a href='sources:").append(i).append("' style='color:#1a73e8; text-decoration:none;'><b>")
                        .append(label).append("</b> ").append(hint).append("</a>");
                html.append("</div>");
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
                if (!uniqueDocs.containsKey(doc.itemId)) {
                    uniqueDocs.put(doc.itemId, doc);
                } else {
                    if (doc.score > uniqueDocs.get(doc.itemId).score) {
                        uniqueDocs.put(doc.itemId, doc);
                    }
                }
            }
        }
        return new ArrayList<>(uniqueDocs.values());
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

        Set<String> citedIds = new java.util.HashSet<>();

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
                if (added.add(doc.itemId)) {
                    citedList.add(doc);
                }
            }
        }

        // 2. Also match docs cited by filename/title in answer text (e.g.
        // "PGR-00149505/2023" or "Relotacao.pdf")
        String lowerAnswer = answer.toLowerCase(java.util.Locale.ROOT);
        for (RAGSourceDoc doc : docs) {
            if (doc.itemId != null && !added.contains(doc.itemId)) {
                String name = getDocName(doc);
                if (name != null && !name.trim().isEmpty()) {
                    String baseName = name;
                    int dotIdx = name.lastIndexOf('.');
                    if (dotIdx > 0) {
                        baseName = name.substring(0, dotIdx);
                    }
                    if (baseName.length() >= 3 && lowerAnswer.contains(baseName.toLowerCase(java.util.Locale.ROOT))) {
                        added.add(doc.itemId);
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

    private String extractSearchQueryWithHistory(String question, List<RAGService.HistoryTurn> historyTurns) {
        if (historyTurns == null || historyTurns.isEmpty() || question == null) {
            return question;
        }
        StringBuilder searchStr = new StringBuilder(question);
        RAGService.HistoryTurn lastTurn = historyTurns.get(historyTurns.size() - 1);
        if (lastTurn != null && lastTurn.question != null) {
            String[] tokens = lastTurn.question.split("[^a-zA-Z0-9????????????????????????????????????????????????????]");
            for (String token : tokens) {
                String trimmed = token.trim();
                if (trimmed.length() >= 3 && !isStopWord(trimmed.toLowerCase())) {
                    if (!question.toLowerCase().contains(trimmed.toLowerCase())) {
                        searchStr.append(" ").append(trimmed);
                    }
                }
            }
        }
        return searchStr.toString();
    }

    private Set<String> collectAllowedParentIds() {
        if (chkScopeOnly == null || !chkScopeOnly.isSelected()) {
            return null;
        }
        Set<String> allowedItemIds = new HashSet<>();
        try {
            App app = App.get();
            if (app != null && app.appCase != null) {
                // 1. Check for checked items in bookmarks
                if (app.appCase instanceof IPEDMultiSource) {
                    for (IPEDSource src : ((IPEDMultiSource) app.appCase).getAtomicSources()) {
                        int lastId = src.getLastId();
                        for (int id = 1; id <= lastId; id++) {
                            if (src.getBookmarks() != null && src.getBookmarks().isChecked(id)) {
                                allowedItemIds.add(String.valueOf(id));
                            }
                        }
                    }
                } else if (app.appCase.getBookmarks() != null) {
                    int lastId = app.appCase.getLastId();
                    for (int id = 1; id <= lastId; id++) {
                        if (app.appCase.getBookmarks().isChecked(id)) {
                            allowedItemIds.add(String.valueOf(id));
                        }
                    }
                }

                // 2. If no items are checked, check for selected/highlighted rows in
                // resultsTable
                javax.swing.JTable tbl = app.getResultsTable();
                iped.search.IMultiSearchResult res = app.getResults();
                if (allowedItemIds.isEmpty() && tbl != null && res != null) {
                    int[] selectedRows = tbl.getSelectedRows();
                    if (selectedRows != null) {
                        for (int row : selectedRows) {
                            int rowModel = tbl.convertRowIndexToModel(row);
                            IItemId iid = res.getItem(rowModel);
                            if (iid != null) {
                                allowedItemIds.add(String.valueOf(iid.getId()));
                            }
                        }
                    }
                }

                // 3. If still empty, use all currently filtered items visible in ipedResult
                if (allowedItemIds.isEmpty() && res != null) {
                    int count = res.getLength();
                    LOGGER.warn(
                            "RAG scope filter: no items checked or selected; falling back to all {} currently visible items.",
                            count);
                    for (int r = 0; r < count; r++) {
                        IItemId iid = res.getItem(r);
                        if (iid != null) {
                            allowedItemIds.add(String.valueOf(iid.getId()));
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error collecting allowed parent IDs for RAG scope filter", e);
        }
        return allowedItemIds.isEmpty() ? null : allowedItemIds;
    }

    private String getMessageSafely(String key, String defaultValue) {
        try {
            return Messages.getString(key);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String getDocName(RAGSourceDoc doc) {
        if (doc == null || doc.itemId == null)
            return null;
        try {
            int id = Integer.parseInt(doc.itemId);
            App app = App.get();
            if (app != null && app.appCase instanceof IPEDMultiSource) {
                for (IPEDSource src : ((IPEDMultiSource) app.appCase).getAtomicSources()) {
                    if (id <= src.getLastId()) {
                        IItemId iid = new ItemId(src.getSourceId(), id);
                        iped.data.IItem item = app.appCase.getItemByItemId(iid);
                        if (item != null)
                            return item.getName();
                    }
                }
            }
        } catch (Exception ignore) {
        }
        return null;
    }

    private static Set<String> extractNormalizedNumbers(String text) {
        Set<String> numbers = new HashSet<>();
        if (text == null)
            return numbers;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\b\\d+[.,\\d]*\\b").matcher(text);
        while (matcher.find()) {
            String numStr = matcher.group();
            String cleanNum = numStr.replaceAll("[.,]", "");
            if (cleanNum.length() >= 3) {
                numbers.add(cleanNum);
            }
        }
        return numbers;
    }

    private static boolean isYear(String numStr) {
        if (numStr.length() == 4) {
            try {
                int val = Integer.parseInt(numStr);
                return val >= 1700 && val <= 2100;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }

    private static Set<String> tokenizeAndClean(String text) {
        Set<String> words = new HashSet<>();
        if (text == null)
            return words;
        String cleanText = text.replaceAll("<[^>]*>", " ");
        // Normalize accents/diacritics
        cleanText = java.text.Normalizer.normalize(cleanText, java.text.Normalizer.Form.NFD);
        cleanText = cleanText.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");

        String[] tokens = cleanText.toLowerCase().split("[^a-zA-Z0-9]");
        for (String t : tokens) {
            String trimmed = t.trim();
            if (trimmed.length() >= 3 && !isStopWord(trimmed)) {
                words.add(trimmed);
            }
        }
        return words;
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
    private static String markdownToHtml(String md) {
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
        html = html.replaceAll("__([^_]+)__", "<b>$1</b>");
        // italic
        html = html.replaceAll("\\*([^*]+)\\*", "<i>$1</i>");
        html = html.replaceAll("_([^_]+)_", "<i>$1</i>");
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

    // ================================================================ inner
    // classes

    private static class ChatSession {
        final String sessionId;
        String title;
        final List<HistoryEntry> entries = new ArrayList<>();

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

        HistoryEntry(Long dbId, String question, String answerHtml, String rawAnswer,
                List<RAGSourceDoc> displaySources) {
            this.dbId = dbId;
            this.question = question;
            this.answerHtml = answerHtml;
            this.rawAnswer = rawAnswer;
            this.displaySources = displaySources;
        }
    }

    private static class SourcesTableModel extends AbstractTableModel {

        private static final long serialVersionUID = 1L;
        private List<RAGSourceDoc> rows = new ArrayList<>();

        void setRows(List<RAGSourceDoc> rows) {
            this.rows = rows == null ? new ArrayList<>() : rows;
            fireTableDataChanged();
        }

        RAGSourceDoc getRow(int rowIndex) {
            if (rowIndex < 0 || rowIndex >= rows.size())
                return null;
            return rows.get(rowIndex);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return 4;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            RAGSourceDoc doc = rows.get(rowIndex);
            switch (columnIndex) {
                case 0:
                    return doc.itemId;
                case 1: {
                    // Try to resolve the item name from the appCase
                    try {
                        int id = Integer.parseInt(doc.itemId);
                        App app = App.get();
                        if (app != null && app.appCase instanceof IPEDMultiSource) {
                            for (IPEDSource src : ((IPEDMultiSource) app.appCase).getAtomicSources()) {
                                if (id <= src.getLastId()) {
                                    IItemId iid = new ItemId(src.getSourceId(), id);
                                    iped.data.IItem item = app.appCase.getItemByItemId(iid);
                                    if (item != null)
                                        return item.getName();
                                }
                            }
                        }
                    } catch (Exception ignore) {
                    }
                    return doc.itemId;
                }
                case 2:
                    return String.format("%.3f", doc.score);
                case 3: {
                    try {
                        int id = Integer.parseInt(doc.itemId);
                        App app = App.get();
                        if (app != null && app.appCase instanceof IPEDMultiSource) {
                            for (IPEDSource src : ((IPEDMultiSource) app.appCase).getAtomicSources()) {
                                if (id <= src.getLastId()) {
                                    IItemId iid = new ItemId(src.getSourceId(), id);
                                    iped.data.IItem item = app.appCase.getItemByItemId(iid);
                                    if (item != null)
                                        return item.getPath();
                                }
                            }
                        }
                    } catch (Exception ignore) {
                    }
                    return "";
                }
                default:
                    return "";
            }
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return String.class;
        }
    }
}
