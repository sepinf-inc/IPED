package iped.app.ui.ai.view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

import iped.app.ui.ai.model.Conversation;

/**
 * Sidebar component responsible for displaying the list of conversations and allowing users to create, select, or delete conversations.
 * Refactored to act strictly as a Passive View (MVP Pattern), delegating logic to the injected listener.
 */
public class SidebarPanel extends JPanel {

    private JButton newChatButton;
    private JList<Conversation> conversationList;
    private DefaultListModel<Conversation> conversationListModel;
    
    private final Component parentFrame;
    private final SidebarListener listener;

    /**
     * Contract for the SidebarPanel's event listener, allowing external components (e.g., Controller) 
     * to react to user interactions without coupling the view to business logic or persistence.
     */
    public interface SidebarListener {
        void onConversationSelected(Conversation conversation);
        void onNewChatRequested();
        void onNewAgentChatRequested();
        void onDeleteRequested(Conversation conversation);
    }

    /**
     * Constructs the SidebarPanel with necessary dependencies and initializes the UI components.
     * @param parentFrame Parent component used for dialog positioning.
     * @param listener    External listener to handle sidebar events (selection, creation, deletion).
     */
    public SidebarPanel(Component parentFrame, SidebarListener listener) {
        this.parentFrame = parentFrame;
        this.listener = listener;
        
        configurePanelLayout();
        initComponents();
    }

    private void configurePanelLayout() {
        setMinimumSize(new Dimension(150, 0)); 
        setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5)); 
        setLayout(new BorderLayout(0, 5));
    }

    private void initComponents() {
        // Initializes the "New Chat" button with dropdown menu
        newChatButton = new JButton("+ New Chat");
        newChatButton.setFont(newChatButton.getFont().deriveFont(Font.BOLD));
        newChatButton.addActionListener(e -> {
            if (listener == null) return;
            JPopupMenu menu = new JPopupMenu();
            JMenuItem newChatItem = new JMenuItem("New Chat");
            newChatItem.addActionListener(ev -> listener.onNewChatRequested());
            menu.add(newChatItem);
            JMenuItem newAgentItem = new JMenuItem("New Agent Chat");
            newAgentItem.addActionListener(ev -> listener.onNewAgentChatRequested());
            menu.add(newAgentItem);
            menu.setPreferredSize(new Dimension(newChatButton.getWidth(), menu.getPreferredSize().height));
            menu.show(newChatButton, 0, newChatButton.getHeight());
        });
        add(newChatButton, BorderLayout.NORTH);

        // Initializes the conversation list with a custom cell renderer and mouse listener for selection and deletion
        conversationListModel = new DefaultListModel<>();
        conversationList = new JList<>(conversationListModel);
        conversationList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        setupCellRenderer();
        setupMouseListeners();

        JScrollPane scrollPane = new JScrollPane(conversationList);
        scrollPane.setBorder(BorderFactory.createEmptyBorder()); 
        add(scrollPane, BorderLayout.CENTER);
    }

    private void setupCellRenderer() {
        conversationList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JPanel rowPanel = new JPanel(new BorderLayout(8, 0));
            rowPanel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
            rowPanel.setOpaque(true);
            rowPanel.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());

            if (value instanceof Conversation) {
                Conversation conv = (Conversation) value;
                
                String displayText = conv.getTitle();
                if (conv.isAgentConversation()) {
                    displayText += " (Agent)";
                }
                JLabel textLabel = new JLabel(displayText);
                textLabel.setFont(list.getFont());
                textLabel.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
                
                JLabel removeLabel = new JLabel("X");
                removeLabel.setFont(list.getFont().deriveFont(Font.BOLD));
                removeLabel.setForeground(isSelected ? new Color(160, 0, 0) : Color.LIGHT_GRAY);
                
                rowPanel.add(textLabel, BorderLayout.CENTER);
                rowPanel.add(removeLabel, BorderLayout.EAST);
            }
            return rowPanel;
        });
    }

    private void setupMouseListeners() {
        conversationList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1) return;
                
                int index = conversationList.locationToIndex(e.getPoint());
                if (index < 0) return;
                
                Rectangle cellBounds = conversationList.getCellBounds(index, index);
                if (cellBounds == null || !cellBounds.contains(e.getPoint())) return;

                Conversation selected = conversationListModel.getElementAt(index);
                
                // Verificação de clique na Hotzone do botão 'X' (28 pixels à direita)
                if (e.getX() >= cellBounds.x + cellBounds.width - 28) {
                    promptDeleteConversation(selected);
                    return;
                }
                
                if (selected != null && listener != null) {
                    listener.onConversationSelected(selected);
                }
            }
        });
    }
    
    public JList<Conversation> getConversationList() {
        return conversationList;
    }

    public void setConversationList(JList<Conversation> conversationList) {
        this.conversationList = conversationList;
    }

    /**
     * Updates the conversation list UI based on the provided list of conversations.
     * @param conversations List of Conversation objects to display in the sidebar.
     */
    public void updateConversationsList(List<Conversation> conversations) {
        conversationListModel.clear();
        if (conversations != null) {
            for (Conversation conv : conversations) {
                conversationListModel.addElement(conv);
            }
        }
        conversationList.repaint();
    }

    public void setSelectedValue(Conversation conv, boolean shouldScroll) {
        conversationList.setSelectedValue(conv, shouldScroll);
    }

    private void promptDeleteConversation(Conversation conv) {
        int confirm = JOptionPane.showConfirmDialog(parentFrame,
            "Are you sure you want to delete this chat?\n\"" + conv.getTitle() + "\"",
            "Delete Chat",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
            
        if (confirm == JOptionPane.YES_OPTION && listener != null) {
            listener.onDeleteRequested(conv);
        }
    }
}