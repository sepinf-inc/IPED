package iped.app.ui.ai.controller;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import iped.app.ui.App;
import iped.app.ui.Messages;
import iped.app.ui.ai.AIChatCoordinator;
import iped.app.ui.ai.backend.AIBackendClient;
import iped.app.ui.ai.backend.AIBackendConfig;
import iped.app.ui.ai.context.AIContextManager;
import iped.app.ui.ai.context.ContextChangeEvent;
import iped.app.ui.ai.context.ContextChangeListener;
import iped.app.ui.ai.context.ConversationManager;
import iped.app.ui.ai.model.AIChatMessage;
import iped.app.ui.ai.model.Conversation;
import iped.app.ui.ai.util.ConversationPersistence;
import iped.app.ui.ai.view.AIAssistantPanel;
import iped.app.ui.ai.view.ChatAreaPanel;
import iped.app.ui.ai.view.ContextPanel;
import iped.app.ui.ai.view.HeaderPanel;
import iped.app.ui.ai.view.SidebarPanel;
import iped.data.IItem;
import iped.data.IItemId;
import iped.engine.search.IPEDSearcher;
import iped.engine.search.MultiSearchResult;

/**
 * Main controller for the AI Assistant feature, responsible for orchestrating interactions between the UI and the AI backend.
 */
public class AIAssistantController {

    private final AIAssistantPanel mainView;
    private SidebarPanel sidebarView;
    private ChatAreaPanel chatAreaView;
    private ContextPanel contextView;
    private HeaderPanel headerView;
    private JPanel tasksView;

    private AIChatCoordinator coordinator;
    private final ConversationManager conversationManager;
    private final AIContextManager contextManager;

    private boolean isSwitchingChats = false;

    /**
        * Constructor that injects only the main view, keeping things decoupled.
        * @param mainView The main layout container.
     */
    public AIAssistantController(AIAssistantPanel mainView) {
        this.mainView = mainView;
        this.conversationManager = ConversationManager.getInstance();
        this.contextManager = AIContextManager.getInstance();
    }

    /**
        * Entry point called by the main view to assemble the UI and logic.
     */
    public void initialize() {
        // 1. Initialize essential dependencies
        ensureChatServiceInitialized();

        // 2. Instantiate sub-panels and inject listeners
        initViews();

        // 3. Configure global domain observers
        setupStateObservers();

        // 4. Hand off the constructed views so the main panel can layout
        mainView.assembleLayout(headerView, sidebarView, contextView, chatAreaView, tasksView);

        // 5. Render initial state (messages, lists, context, etc.)
        sidebarView.updateConversationsList(conversationManager.getConversations());
        contextView.updateContextData(contextManager.getContextEntriesForUI());
        refreshChatArea();
    }

    private void initViews() {
        // Initialize sidebar (injecting click-handling logic)
        this.sidebarView = new SidebarPanel(mainView.getFrame(), new SidebarPanel.SidebarListener() {
            @Override
            public void onNewChatRequested() {
                startNewChat();
            }

            @Override
            public void onDeleteRequested(Conversation conversation) {
                deleteChat(conversation);
            }

            @Override
            public void onConversationSelected(Conversation conversation) {
                loadConversation(conversation);
            }
        });

        // Initialize header
        String title = "AI Assistant";
        try { title = Messages.getString("AIAssistant.Title"); } catch (Exception e) {}
        this.headerView = new HeaderPanel(title, e -> mainView.toggleSidebar());

        // Initialize context panel with injected behavior (listener)
        this.contextView = new ContextPanel(new ContextPanel.ContextListener() {
            @Override
            public void onClearContextRequested() {
                contextManager.clearContext();
            }

            @Override
            public void onRemoveFileRequested(IItem item) {
                contextManager.removeContextFile(item);
            }
        });

        // Initialize chat area
        String sendText = "Send";
        try { sendText = Messages.getString("AIAssistant.Send"); } catch (Exception e) {}
        this.chatAreaView = new ChatAreaPanel(750, sendText);
        
        // Wire chat area send events to the controller
        this.chatAreaView.getSendButton().addActionListener(e -> handleSendAction());
        this.chatAreaView.getInputArea().addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isShiftDown()) {
                    e.consume();
                    handleSendAction();
                }
            }
        });

        // Delegate token click handling back to IPED (open file)
        this.chatAreaView.installTextPaneClickListener(
            (hash, chunkId) -> navigateToItem(hash, chunkId),
            this::refreshChatArea
        );

        // Initialize right-side quick tasks panel
        this.tasksView = createTasksPanel();
    }

    private void setupStateObservers() {
        this.contextManager.addContextChangeListener(new ContextChangeListener() {
            @Override
            public void contextChanged(ContextChangeEvent event) {
                // 1. Inject new data into the passive view for rendering on the correct thread
                SwingUtilities.invokeLater(() -> {
                    if (contextView != null) {
                        contextView.updateContextData(contextManager.getContextEntriesForUI());
                    }
                });

                // 2. Domain persistence logic
                if (isSwitchingChats) return; // Abort if the system is switching chats
                
                Conversation activeConv = conversationManager.getActiveConversation();
                if (activeConv != null) {
                    List<Integer> currentIds = contextManager.getContextFiles().stream()
                            .map(IItem::getId)
                            .collect(Collectors.toList());
                    
                    activeConv.setContextIds(currentIds);
                    activeConv.updateLastModified();
                    
                    final Conversation convToSave = activeConv;
                    new Thread(() -> ConversationPersistence.saveConversation(convToSave)).start();
                }
            }
        });
    }

    private boolean ensureChatServiceInitialized() {
        if (coordinator != null) {
            return true;
        }
        try {
            coordinator = new AIChatCoordinator(new AIBackendClient(AIBackendConfig.loadFromSystemProperties()));
            return true;
        } catch (Throwable t) {
            addMessage("System Error", "Failed to initialize AI backend: " + t.getMessage(), "error");
            t.printStackTrace();
            return false;
        }
    }

    // ========================================================================
    // SIDEBAR BUSINESS LOGIC
    // ========================================================================

    private void startNewChat() {
        conversationManager.startNewConversation();
        clearChatScreenAndMemory();
        contextManager.clearContext();
        sidebarView.updateConversationsList(conversationManager.getConversations());
        refreshChatArea();
        
        addMessage("System", "Started a new conversation session.", "system");
        chatAreaView.getInputArea().requestFocusInWindow();
    }

    private void deleteChat(Conversation conv) {
        ConversationPersistence.deleteConversation(conv.getId());
        conversationManager.removeConversation(conv);
        
        Conversation active = conversationManager.getActiveConversation();
        boolean isActiveDeleted = (active == null || active.getId().equals(conv.getId()));
        
        if (isActiveDeleted) {
            List<Conversation> remaining = conversationManager.getConversations();
            if (!remaining.isEmpty()) {
                conversationManager.setActiveConversation(remaining.get(0));
                loadConversation(remaining.get(0));
            } else {
                conversationManager.setActiveConversation(null);
                clearChatScreenAndMemory();
                contextManager.clearContext();
                refreshChatArea();
            }
        }
        
        sidebarView.updateConversationsList(conversationManager.getConversations());
    }

    private void loadConversation(Conversation conv) {
        isSwitchingChats = true;
        conversationManager.setActiveConversation(conv);
        
        if (coordinator != null) {
            coordinator.loadHistoricalContext(conv.getChatHashes(), conv.getContextIds(), conv.getMessages());
        }
        
        contextManager.clearContext();
        
        new Thread(() -> {
            List<IItem> restoredItems = new ArrayList<>();
            try {
                if (conv.getChatHashes() != null && !conv.getChatHashes().isEmpty()) {
                    for (String hash : conv.getChatHashes()) {
                        try {
                            IPEDSearcher searcher = new IPEDSearcher(App.get().appCase, "hash:" + hash);
                            MultiSearchResult result = searcher.multiSearch();
                            if (result != null && result.getLength() > 0) {
                                IItemId qualifiedItemId = result.getItem(0);
                                IItem item = App.get().appCase.getItemByItemId(qualifiedItemId);
                                if (item != null) restoredItems.add(item);
                            }
                        } catch (Exception e) {
                            System.err.println("Could not restore context item hash: " + hash);
                        }
                    }
                } else if (conv.getContextIds() != null && !conv.getContextIds().isEmpty()) {
                    for (Integer itemId : conv.getContextIds()) {
                        try {
                            IPEDSearcher searcher = new IPEDSearcher(App.get().appCase, "id:" + itemId);
                            MultiSearchResult result = searcher.multiSearch();
                            if (result != null && result.getLength() > 0) {
                                IItemId qualifiedItemId = result.getItem(0);
                                IItem item = App.get().appCase.getItemByItemId(qualifiedItemId);
                                if (item != null) restoredItems.add(item);
                            }
                        } catch (Exception e) {
                            System.err.println("Could not restore context item ID: " + itemId);
                        }
                    }
                }
            } finally {
                SwingUtilities.invokeLater(() -> {
                    try {
                        if (!restoredItems.isEmpty()) {
                            Conversation currentActive = conversationManager.getActiveConversation();
                            if (currentActive == null || !currentActive.getId().equals(conv.getId())) {
                                return;
                            }
                            List<Integer> freshIds = restoredItems.stream()
                                    .map(IItem::getId).collect(Collectors.toList());
                            
                            if (coordinator != null) {
                                coordinator.loadHistoricalContext(conv.getChatHashes(), freshIds, conv.getMessages());
                            }
                            contextManager.addContextFiles(restoredItems);
                        }
                    } finally {
                        isSwitchingChats = false;
                    }
                });
            }
        }).start();
        
        if (chatAreaView != null) chatAreaView.forceDiscardStreaming(); 
        
        refreshChatArea();
        sidebarView.setSelectedValue(conv, true);
    }

    public void startNewConversationWithCurrentContext(List<IItem> pendingItems) {
        Conversation newConversation = conversationManager.startNewConversation();

        List<Integer> contextIds = new ArrayList<>();
        for (IItem item : contextManager.getContextFiles()) {
            if (item != null && !contextIds.contains(item.getId())) {
                contextIds.add(item.getId());
            }
        }

        if (pendingItems != null) {
            for (IItem item : pendingItems) {
                if (item != null && !contextIds.contains(item.getId())) {
                    contextIds.add(item.getId());
                }
            }
        }

        newConversation.setContextIds(contextIds);
        newConversation.setChatHashes(new ArrayList<>());
        newConversation.setMessages(new ArrayList<>());
        newConversation.updateLastModified();

        if (pendingItems != null) {
            contextManager.addContextFiles(pendingItems);
        }

        if (coordinator != null) coordinator.clearHistory();

        sidebarView.updateConversationsList(conversationManager.getConversations());
        refreshChatArea();
        sidebarView.setSelectedValue(newConversation, true);
        mainView.showFrame();
    }

    // ========================================================================
    // CHAT / NETWORK BUSINESS LOGIC
    // ========================================================================

    private void handleSendAction() {
        String text = chatAreaView.getInputArea().getText().trim();
        if (text.isEmpty()) return;

        if (!ensureChatServiceInitialized()) return;

        if (conversationManager.getActiveConversation() == null) {
            conversationManager.startNewConversation();
            sidebarView.updateConversationsList(conversationManager.getConversations());
        }

        addMessage("You", text, "user");
        chatAreaView.getInputArea().setText("");
        mainView.setProcessing(true);
        
        AIChatMessage assistantDraft = AIChatMessage.create("Assistant", "", "assistant");
        chatAreaView.startMessageStreaming(assistantDraft);
        
        coordinator.askQuestion(
            text, 
            (token) -> SwingUtilities.invokeLater(() -> chatAreaView.enqueueStreamingToken(token)),
            () -> SwingUtilities.invokeLater(() -> {
                chatAreaView.pruneStreaming(() -> {
                    if (!assistantDraft.getContent().isEmpty()) {
                        conversationManager.addMessageToActive(assistantDraft);
                        sidebarView.updateConversationsList(conversationManager.getConversations());
                    }
                    mainView.setProcessing(false);
                });
            }),
            (errorMessage) -> SwingUtilities.invokeLater(() -> {
                chatAreaView.forceDiscardStreaming();
                addMessage("System Error", errorMessage, "error");
                mainView.setProcessing(false);
            })
        );
    }

    private void addMessage(String sender, String message, String type) {
        AIChatMessage chatMessage = AIChatMessage.create(sender, message, type);
        conversationManager.addMessageToActive(chatMessage);
        refreshChatArea();
    }

    private void refreshChatArea() {
        if (chatAreaView != null) {
            List<AIChatMessage> renderableMessages = new ArrayList<>();
            Conversation activeConv = conversationManager.getActiveConversation();
            
            boolean hasMessages = false;
            
            if (activeConv != null) {
                renderableMessages.addAll(activeConv.getMessages());
                hasMessages = activeConv.hasAssistantReply();
            }
            
            if (chatAreaView.getCurrentDraftMessage() != null) {
                renderableMessages.add(chatAreaView.getCurrentDraftMessage());
                hasMessages = true; // if there's a draft we already lock
            }
            
            chatAreaView.renderHistoricalMessages(renderableMessages);
            
            // Bussines rule: blocks context editing if assistant has replied or there's a draft
            if (contextView != null) {
                contextView.setLocked(hasMessages);
            }
        }
    }

    private void clearChatScreenAndMemory() {
        if (coordinator != null) coordinator.clearHistory();
        if (chatAreaView != null) chatAreaView.clearChatScreen();
    }

    // ========================================================================
    // NAVIGATION AND HELPER UTILITIES
    // ========================================================================

    private void navigateToItem(String hash, String chunkId) {
        if (hash == null || hash.isEmpty() || App.get() == null || App.get().appCase == null) return;

        new Thread(() -> {
            try {
                IPEDSearcher searcher = new IPEDSearcher(App.get().appCase, "hash:" + hash);
                MultiSearchResult result = searcher.multiSearch();
                if (result == null || result.getLength() == 0) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(mainView.getFrame(),
                            "Item not found for hash: " + hash, "Not found", JOptionPane.INFORMATION_MESSAGE));
                    return;
                }

                IItemId itemId = result.getItem(0);
                int luceneId = App.get().appCase.getLuceneId(itemId);
                
                if (chunkId != null && !chunkId.isEmpty()) {
                    try {
                        App.get().getViewerController().getHtmlLinkViewer().setElementIDToScroll(chunkId);
                    } catch (Exception e) {}
                }
                
                iped.app.ui.FileProcessor fp = new iped.app.ui.FileProcessor(luceneId, true);
                fp.execute();
                
                SwingUtilities.invokeLater(() -> selectItemInResultsTable(luceneId));

            } catch (Exception ex) {
                ex.printStackTrace();
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(mainView.getFrame(),
                        "Error opening item: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE));
            }
        }).start();
    }

    private void selectItemInResultsTable(int luceneId) {
        if (App.get() == null || App.get().getResults() == null || App.get().getResultsTable() == null) return;
        
        for (int i = 0; i < App.get().getResults().getLength(); i++) {
            try {
                IItemId item = App.get().getResults().getItem(i);
                if (App.get().appCase.getLuceneId(item) == luceneId) {
                    int viewIndex = App.get().getResultsTable().convertRowIndexToView(i);
                    App.get().getResultsTable().setRowSelectionInterval(viewIndex, viewIndex);
                    java.awt.Rectangle cellRect = App.get().getResultsTable().getCellRect(viewIndex, 0, false);
                    App.get().getResultsTable().scrollRectToVisible(cellRect);
                    break;
                }
            } catch (Exception e) {}
        }
    }

    private JPanel createTasksPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder("Quick Actions"));

        Map<String, String> taskPrompts = new java.util.HashMap<>();
        taskPrompts.put("Summarize", "Resuma o arquivo fornecido.");
        taskPrompts.put("Find Patterns", "Encontre padrões no arquivo fornecido.");

        String[] tasks = {"Summarize", "Find Patterns"};
        for (String task : tasks) {
            JButton btn = new JButton(task);
            btn.setAlignmentX(Component.CENTER_ALIGNMENT);
            btn.setMaximumSize(new Dimension(200, 30));
            btn.addActionListener(e -> {
                chatAreaView.getInputArea().setText(taskPrompts.get(task));
                handleSendAction();
            });
            panel.add(btn);
            panel.add(Box.createVerticalStrut(5));
        }
        
        return panel;
    }
}