package iped.app.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.app.ai.AIAssistantService;
import iped.app.ai.AIAssistantContext;
import iped.data.IItemId;
import iped.engine.config.AIAssistantConfig;

/**
 * AI Assistant floating panel for IPED
 * Provides AI-powered analysis features for digital forensics
 * 
 * Features:
 * - Chat interface for questions about selected items
 * - Quick tasks: chat analysis, document summarization, pattern detection, classification
 * - Context-aware: uses selected items and search query
 * - Local/intranet only - no data leaves your network
 */
public class AIAssistantPanel {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AIAssistantPanel.class);
    
    private static final int HORIZONTAL_OFFSET = 30;
    private static final int VERTICAL_OFFSET = 120;
    private static final double HEIGHT_PERCENTAGE = 0.8;
    
    private JDialog dialog;
    private JTextArea chatArea;
    private JTextArea inputArea;
    private JButton sendButton;
    private JPanel taskButtonsPanel;
    private JLabel statusLabel;
    private JProgressBar progressBar;
    
    private List<ChatMessage> chatHistory = new ArrayList<>();
    private AIAssistantService aiService;
    private AIAssistantContext currentContext;
    
    private static AIAssistantPanel instance;
    
    private int panelWidth;
    
    /**
     * Gets singleton instance
     */
    public static AIAssistantPanel getInstance() {
        if (instance == null) {
            instance = new AIAssistantPanel();
        }
        return instance;
    }
    
    /**
     * Private constructor - use getInstance()
     */
    private AIAssistantPanel() {
        aiService = new AIAssistantService();
        
        if (!aiService.isAvailable()) {
            LOGGER.warn("AI Assistant is not available - check configuration");
        }
        
        // Get panel width from config
        AIAssistantConfig config = aiService.getConfig();
        panelWidth = config != null ? config.getPanelWidth() : 450;
        
        createUI();
        currentContext = new AIAssistantContext();
    }
    
    /**
     * Creates the floating dialog UI
     */
    private void createUI() {
        dialog = new JDialog(App.get(), Messages.getString("AIAssistant.Title"), false);
        dialog.setUndecorated(false);
        dialog.setResizable(true);
        
        // Main panel
        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        // Header with status
        JPanel headerPanel = createHeaderPanel();
        mainPanel.add(headerPanel, BorderLayout.NORTH);
        
        // Chat area
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setFont(new Font("SansSerif", Font.PLAIN, 12));
        JScrollPane chatScroll = new JScrollPane(chatArea);
        chatScroll.setPreferredSize(new Dimension(panelWidth, 300));
        
        // Quick task buttons
        taskButtonsPanel = createTaskButtonsPanel();
        
        // Input area
        JPanel inputPanel = createInputPanel();
        
        // Progress bar (initially hidden)
        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        
        // Combine components
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        centerPanel.add(chatScroll, BorderLayout.CENTER);
        centerPanel.add(taskButtonsPanel, BorderLayout.SOUTH);
        
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        bottomPanel.add(progressBar, BorderLayout.NORTH);
        bottomPanel.add(inputPanel, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        
        dialog.getContentPane().add(mainPanel);
        dialog.pack();
        
        // Position dialog
        positionDialog();
        
        // Auto-hide on focus lost (if configured)
        dialog.addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowLostFocus(WindowEvent e) {
                AIAssistantConfig config = aiService.getConfig();
                if (config != null && config.isAutoHideOnFocusLost()) {
                    dialog.setVisible(false);
                }
            }
        });
        
        // Add welcome message
        if (aiService.isAvailable()) {
            addMessage("System", "AI Assistant ready. Select items and ask questions or use quick tasks below.");
        } else {
            addMessage("System", "AI Assistant is not configured. Check AIAssistantConfig.txt and ensure a local AI service is running.");
        }
    }
    
    /**
     * Creates header panel with title and status
     */
    private JPanel createHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout());
        
        // Title
        JLabel titleLabel = new JLabel(Messages.getString("AIAssistant.Title"));
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        
        // Status indicator
        statusLabel = new JLabel();
        updateStatusLabel();
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        
        // Assemble header
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.add(titleLabel, BorderLayout.NORTH);
        leftPanel.add(statusLabel, BorderLayout.SOUTH);
        
        headerPanel.add(leftPanel, BorderLayout.WEST);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
        
        return headerPanel;
    }
    
    /**
     * Updates the status label
     */
    private void updateStatusLabel() {
        if (statusLabel == null) return;
        
        if (aiService.isAvailable()) {
            AIAssistantConfig config = aiService.getConfig();
            statusLabel.setText(String.format("● %s - %s", 
                config.getProvider(), config.getModelName()));
            statusLabel.setForeground(new Color(68, 108, 179));
        } else {
            statusLabel.setText("● Not Available");
            statusLabel.setForeground(new Color(242, 121, 53));
        }
    }
    
    /**
     * Creates the quick task buttons panel
     */
    private JPanel createTaskButtonsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(
            Messages.getString("AIAssistant.QuickTasks")));
        
        // Quick task buttons
        addTaskButton(panel, Messages.getString("AIAssistant.IdentifyChatSubject"), 
            e -> executeChatSubjectIdentification());
        addTaskButton(panel, Messages.getString("AIAssistant.SummarizeDocument"), 
            e -> executeSummarizeDocument());
        addTaskButton(panel, Messages.getString("AIAssistant.FindPatterns"), 
            e -> executeFindPatterns());
        addTaskButton(panel, Messages.getString("AIAssistant.ClassifyContent"), 
            e -> executeClassifyContent());
        
        // Add test connection button
        panel.add(Box.createVerticalStrut(10));
        JButton testButton = new JButton("Test Connection");
        testButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        testButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));
        testButton.setFont(new Font("SansSerif", Font.PLAIN, 10));
        testButton.addActionListener(e -> testConnection());
        panel.add(testButton);
        
        return panel;
    }
    
    /**
     * Adds a task button to the panel
     */
    private void addTaskButton(JPanel panel, String text, ActionListener action) {
        JButton button = new JButton(text);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        button.addActionListener(action);
        panel.add(button);
        panel.add(Box.createVerticalStrut(5));
    }
    
    /**
     * Creates input panel with text area and send button
     */
    private JPanel createInputPanel() {
        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
        
        inputArea = new JTextArea(3, 20);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        
        // Enter to send, Shift+Enter for new line
        inputArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isShiftDown()) {
                    e.consume();
                    sendMessage();
                }
            }
        });
        
        JScrollPane inputScroll = new JScrollPane(inputArea);
        
        sendButton = new JButton(Messages.getString("AIAssistant.Send"));
        sendButton.addActionListener(e -> sendMessage());
        
        inputPanel.add(inputScroll, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        
        return inputPanel;
    }
    
    /**
     * Positions dialog on screen
     * Dialog appears at right side of screen with fixed offsets
     */
    private void positionDialog() {
        // Get screen dimensions
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice gd = ge.getDefaultScreenDevice();
        GraphicsConfiguration gc = gd.getDefaultConfiguration();
        Rectangle screenBounds = gc.getBounds();
        
        // Calculate dialog height
        int dialogHeight = (int) (screenBounds.height * HEIGHT_PERCENTAGE);
        dialog.setSize(dialog.getWidth(), dialogHeight);
        
        // Position dialog
        int x = screenBounds.x + screenBounds.width - dialog.getWidth() - HORIZONTAL_OFFSET;
        int y = screenBounds.y + VERTICAL_OFFSET;
        
        // Ensure dialog fits on screen
        if (y + dialog.getHeight() > screenBounds.y + screenBounds.height) {
            y = screenBounds.y + screenBounds.height - dialog.getHeight();
        }
        
        dialog.setLocation(x, y);
    }
    
    /**
     * Toggles panel visibility
     */
    public void toggleVisibility() {
        if (!aiService.isAvailable()) {
            JOptionPane.showMessageDialog(App.get(), 
                "AI Assistant is not available.\n" +
                "Please check AIAssistantConfig.txt and ensure a local AI service is running.\n\n" +
                aiService.getStatusInfo(),
                "AI Assistant Not Available",
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        if (dialog.isVisible()) {
            dialog.setVisible(false);
        } else {
            // Update context when opening
            updateContext();
            dialog.setVisible(true);
            inputArea.requestFocusInWindow();
        }
    }
    
    /**
     * Updates the current context with selected items
     */
    private void updateContext() {
        currentContext = getContext();
    }
    
    /**
     * Sends user message to AI
     */
    private void sendMessage() {
        String userMessage = inputArea.getText().trim();
        if (userMessage.isEmpty()) return;
        
        if (!aiService.isAvailable()) {
            addMessage("Error", "AI service is not available");
            return;
        }
        
        // Add user message to chat
        addMessage("User", userMessage);
        inputArea.setText("");
        
        // Update context
        updateContext();
        
        // Show progress
        setProcessing(true);
        
        // Process in background
        new Thread(() -> {
            try {
                String response = aiService.sendMessage(userMessage, currentContext);
                SwingUtilities.invokeLater(() -> {
                    addMessage("AI", response);
                    setProcessing(false);
                });
            } catch (Exception e) {
                LOGGER.error("Error sending message to AI", e);
                SwingUtilities.invokeLater(() -> {
                    addMessage("Error", "Failed to get response: " + e.getMessage());
                    setProcessing(false);
                });
            }
        }).start();
    }
    
    /**
     * Adds a message to chat display
     */
    private void addMessage(String sender, String message) {
        ChatMessage chatMessage = new ChatMessage(sender, message);
        chatHistory.add(chatMessage);
        
        StringBuilder sb = new StringBuilder();
        for (ChatMessage msg : chatHistory) {
            sb.append("[").append(msg.sender).append("]\n");
            sb.append(msg.message).append("\n\n");
        }
        chatArea.setText(sb.toString());
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }
    
    /**
     * Gets current context (selected items, search query)
     */
    private AIAssistantContext getContext() {
        AIAssistantContext context = new AIAssistantContext();
        
        try {
            // Get selected items
            App app = App.get();
            int[] selectedRows = app.resultsTable.getSelectedRows();
            for (int row : selectedRows) {
                int modelRow = app.resultsTable.convertRowIndexToModel(row);
                IItemId itemId = app.ipedResult.getItem(modelRow);
                context.addSelectedItem(itemId);
            }
            
            // Add current search query
            if (app.queryComboBox.getSelectedItem() != null) {
                context.setSearchQuery(app.queryComboBox.getSelectedItem().toString());
            }
            
        } catch (Exception e) {
            LOGGER.error("Error building context", e);
        }
        
        return context;
    }
    
    /**
     * Executes chat subject identification
     */
    private void executeChatSubjectIdentification() {
        updateContext();
        
        if (currentContext.getSelectedItems().isEmpty()) {
            addMessage("System", "Please select chat messages to analyze");
            return;
        }
        
        addMessage("System", "Analyzing chat subjects...");
        setProcessing(true);
        
        new Thread(() -> {
            try {
                String result = aiService.identifyChatSubject(currentContext);
                SwingUtilities.invokeLater(() -> {
                    addMessage("AI", result);
                    setProcessing(false);
                });
            } catch (Exception e) {
                LOGGER.error("Error identifying chat subject", e);
                SwingUtilities.invokeLater(() -> {
                    addMessage("Error", "Analysis failed: " + e.getMessage());
                    setProcessing(false);
                });
            }
        }).start();
    }
    
    /**
     * Executes document summarization
     */
    private void executeSummarizeDocument() {
        updateContext();
        
        if (currentContext.getSelectedItems().isEmpty()) {
            addMessage("System", "Please select documents to summarize");
            return;
        }
        
        addMessage("System", "Summarizing documents...");
        setProcessing(true);
        
        new Thread(() -> {
            try {
                String result = aiService.summarizeDocument(currentContext);
                SwingUtilities.invokeLater(() -> {
                    addMessage("AI", result);
                    setProcessing(false);
                });
            } catch (Exception e) {
                LOGGER.error("Error summarizing document", e);
                SwingUtilities.invokeLater(() -> {
                    addMessage("Error", "Summarization failed: " + e.getMessage());
                    setProcessing(false);
                });
            }
        }).start();
    }
    
    /**
     * Executes pattern finding
     */
    private void executeFindPatterns() {
        updateContext();
        
        if (currentContext.getSelectedItems().isEmpty()) {
            addMessage("System", "Please select items to analyze for patterns");
            return;
        }
        
        addMessage("System", "Finding patterns...");
        setProcessing(true);
        
        new Thread(() -> {
            try {
                String result = aiService.findPatterns(currentContext);
                SwingUtilities.invokeLater(() -> {
                    addMessage("AI", result);
                    setProcessing(false);
                });
            } catch (Exception e) {
                LOGGER.error("Error finding patterns", e);
                SwingUtilities.invokeLater(() -> {
                    addMessage("Error", "Pattern analysis failed: " + e.getMessage());
                    setProcessing(false);
                });
            }
        }).start();
    }
    
    /**
     * Executes content classification
     */
    private void executeClassifyContent() {
        updateContext();
        
        if (currentContext.getSelectedItems().isEmpty()) {
            addMessage("System", "Please select items to classify");
            return;
        }
        
        addMessage("System", "Classifying content...");
        setProcessing(true);
        
        new Thread(() -> {
            try {
                String result = aiService.classifyContent(currentContext);
                SwingUtilities.invokeLater(() -> {
                    addMessage("AI", result);
                    setProcessing(false);
                });
            } catch (Exception e) {
                LOGGER.error("Error classifying content", e);
                SwingUtilities.invokeLater(() -> {
                    addMessage("Error", "Classification failed: " + e.getMessage());
                    setProcessing(false);
                });
            }
        }).start();
    }
    
    /**
     * Tests connection to AI service
     */
    private void testConnection() {
        addMessage("System", "Testing connection to AI service...");
        setProcessing(true);
        
        new Thread(() -> {
            boolean success = aiService.testConnection();
            SwingUtilities.invokeLater(() -> {
                if (success) {
                    addMessage("System", "Connection successful!");
                } else {
                    addMessage("System", "Connection failed. Check logs for details.\n\n" +
                              aiService.getStatusInfo());
                }
                setProcessing(false);
            });
        }).start();
    }
    
    /**
     * Sets UI processing state
     */
    private void setProcessing(boolean processing) {
        progressBar.setVisible(processing);
        sendButton.setEnabled(!processing);
        inputArea.setEnabled(!processing);
        
        if (processing) {
            dialog.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        } else {
            dialog.setCursor(Cursor.getDefaultCursor());
        }
    }
    
    /**
     * Simple chat message class
     */
    private static class ChatMessage {
        String sender;
        String message;
        long timestamp;
        
        ChatMessage(String sender, String message) {
            this.sender = sender;
            this.message = message;
            this.timestamp = System.currentTimeMillis();
        }
    }
}