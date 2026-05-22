package iped.app.ui.ai.view;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.KeyAdapter;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseMotionAdapter;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;

import iped.app.ui.App;
import iped.app.ui.Messages;
import iped.data.IItem;
import iped.app.ui.ai.controller.AIAssistantController;

/**
 * AI Assistant floating panel UI layer for IPED.
 * <p>
 * This class acts strictly as the top-level Container (View) in the MVP/MVC architecture.
 * Responsibility is isolated entirely to layout arrangement and window lifecycle management,
 * completely decoupled from business, extraction, and network logic.
 * </p>
 */
public class AIAssistantPanel {

    private static final int HORIZONTAL_OFFSET = 30;
    private static final int VERTICAL_OFFSET = 120;
    private static final double HEIGHT_PERCENTAGE = 0.8;
    private static final int PANEL_WIDTH = 750;

    private JFrame frame;
    private JSplitPane splitPane;

    private HeaderPanel headerPanel;
    private SidebarPanel sidebarPanel;
    private ContextPanel contextPanel;
    private ChatAreaPanel chatAreaPanel;
    private JPanel tasksPanel;

    private final AIAssistantController controller;
    private boolean processing;

    private static AIAssistantPanel instance;

    /**
     * Singleton instance ensures only one floating panel manager exists at a time.
     */
    public static synchronized AIAssistantPanel getInstance() {
        if (instance == null) {
            instance = new AIAssistantPanel();
        }
        return instance;
    }

    /**
     * Private constructor initializing the Controller to achieve Inversion of Control (IoC).
     */
    private AIAssistantPanel() {
        this.controller = new AIAssistantController(this);
        createBaseFrame();
        this.controller.initialize();
    }

    private void createBaseFrame() {
        String title = "AI Assistant";
        try { 
            title = Messages.getString("AIAssistant.Title"); 
        } catch (Exception e) {}

        frame = new JFrame(title);
        frame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        frame.setResizable(true);

        // --- BEGIN GLOBAL BLOCKING WITH A "HOLE" (GLASS PANE) ---
        JPanel glassPane = new JPanel() {
            @Override
            public boolean contains(int x, int y) {
                // If HeaderPanel exists, check whether the click lands in its area
                if (headerPanel != null && headerPanel.isVisible()) {
                    // Convert glass pane coordinates to HeaderPanel coordinates
                    Point p = SwingUtilities.convertPoint(this, x, y, headerPanel);
                    // If HeaderPanel contains this point, return false (click passes through)
                    if (headerPanel.contains(p)) {
                        return false; 
                    }
                }
                // For the rest of the screen, the glass pane is solid and blocks clicks
                return super.contains(x, y);
            }
        };
        glassPane.setOpaque(false); 
        glassPane.addMouseListener(new MouseAdapter() {}); 
        glassPane.addMouseMotionListener(new MouseMotionAdapter() {}); 
        glassPane.addKeyListener(new KeyAdapter() {}); 
        glassPane.setFocusTraversalKeysEnabled(false); 
        frame.setGlassPane(glassPane);
        // --- END GLOBAL BLOCKING ---
    }

    /**
     * Assembles the layout using pre-configured passive components supplied by the controller.
     */
    public void assembleLayout(HeaderPanel header, SidebarPanel sidebar, ContextPanel context, ChatAreaPanel chatArea, JPanel tasks) {
        this.headerPanel = header;
        this.sidebarPanel = sidebar;
        this.contextPanel = context;
        this.chatAreaPanel = chatArea;
        this.tasksPanel = tasks;

        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        mainPanel.add(headerPanel, BorderLayout.NORTH);

        JPanel chatWorkspacePanel = new JPanel(new BorderLayout(5, 5));
        
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        centerPanel.add(contextPanel, BorderLayout.NORTH);
        centerPanel.add(chatAreaPanel, BorderLayout.CENTER);
        centerPanel.add(tasksPanel, BorderLayout.EAST);

        chatWorkspacePanel.add(centerPanel, BorderLayout.CENTER);

        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebarPanel, chatWorkspacePanel);
        splitPane.setContinuousLayout(true);
        splitPane.setDividerSize(5);
        splitPane.setBorder(null); 
        splitPane.setDividerLocation(220); 

        mainPanel.add(splitPane, BorderLayout.CENTER);

        frame.getContentPane().add(mainPanel);
        frame.pack();
        positionDialog();
    }

    /**
     * Entry point for external IPED actions adding items to context.
     * Delegates behavior directly to the controller.
     */
    public void startNewConversationWithCurrentContext(List<IItem> pendingItems) {
        if (controller != null) {
            controller.startNewConversationWithCurrentContext(pendingItems);
        }
    }

    public boolean isProcessing() {
        return processing;
    }

    /**
     * Toggles the processing state visual cues across subcomponents and locks the UI.
     */
    public void setProcessing(boolean processing) {
        this.processing = processing;
        if (chatAreaPanel != null) {
            chatAreaPanel.setProcessing(processing);
        }
        
        // Enable or disable the transparent shield
        frame.getGlassPane().setVisible(processing);
        
        if (processing) {
            // Steal focus for the shield to prevent keyboard interaction with buttons
            frame.getGlassPane().requestFocusInWindow();
        } else {
            // When the AI finishes responding, restore focus to the text input
            if (chatAreaPanel != null) {
                chatAreaPanel.requestFocusToInput();
            }
        }

        frame.setCursor(processing ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
    }

    /**
     * Performs visual shifting of the JSplitPane divider to hide or show the sidebar.
     */
    public void toggleSidebar() {
        if (sidebarPanel == null || splitPane == null) {
            return;
        }
        if (sidebarPanel.isVisible()) {
            sidebarPanel.setVisible(false);
            splitPane.setDividerLocation(0);
            splitPane.setDividerSize(0);
        } else {
            sidebarPanel.setVisible(true);
            splitPane.setDividerSize(5);
            splitPane.setDividerLocation(220);
        }
    }

    private void positionDialog() {
        Rectangle screenBounds = resolvePreferredScreenBounds();

        int height = (int) (screenBounds.height * HEIGHT_PERCENTAGE);
        frame.setSize(PANEL_WIDTH + 150, height);

        int x = screenBounds.x + screenBounds.width - frame.getWidth() - HORIZONTAL_OFFSET;
        int y = screenBounds.y + VERTICAL_OFFSET;

        if (y + frame.getHeight() > screenBounds.y + screenBounds.height) {
            y = screenBounds.y + screenBounds.height - frame.getHeight();
        }

        frame.setLocation(x, y);
    }

    private Rectangle resolvePreferredScreenBounds() {
        Window owner = App.get();
        if (owner != null) {
            GraphicsConfiguration ownerGc = owner.getGraphicsConfiguration();
            if (ownerGc != null) {
                return ownerGc.getBounds();
            }
        }

        return GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration().getBounds();
    }

    private Rectangle getVirtualScreenBounds() {
        Rectangle virtualBounds = new Rectangle();
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            virtualBounds = virtualBounds.union(device.getDefaultConfiguration().getBounds());
        }
        return virtualBounds;
    }

    private void ensureVisibleOnScreen() {
        Rectangle virtualBounds = getVirtualScreenBounds();
        Rectangle currentBounds = frame.getBounds();
        if (!virtualBounds.intersects(currentBounds)) {
            positionDialog();
        }
    }

    /**
     * Handles the asynchronous visibility initialization of the frame window.
     */
    public void showFrame() {
        Runnable action = () -> {
            ensureVisibleOnScreen();
            
            if (frame.getExtendedState() == JFrame.ICONIFIED) {
                frame.setExtendedState(JFrame.NORMAL);
            }
            
            if (!frame.isVisible()) {
                frame.setVisible(true);
            }
            
            frame.toFront();
            frame.requestFocus();
            
            if (chatAreaPanel != null) {
                chatAreaPanel.requestFocusToInput();
            }
        };

        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
        }
    }

    /**
     * Alternates the frame state between hidden and visible.
     */
    public void toggleVisibility() {
        SwingUtilities.invokeLater(() -> {
            if (frame.isVisible()) {
                frame.setVisible(false);
            } else {
                showFrame();
            }
        });
    }

    public JFrame getFrame() {
        return frame;
    }

    public HeaderPanel getHeaderPanel() {
        return headerPanel;
    }

    public SidebarPanel getSidebarPanel() {
        return sidebarPanel;
    }

    public ContextPanel getContextPanel() {
        return contextPanel;
    }

    public ChatAreaPanel getChatAreaPanel() {
        return chatAreaPanel;
    }

    public JPanel getTasksPanel() {
        return tasksPanel;
    }
}