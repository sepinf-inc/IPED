package iped.app.ui.ai.view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;

import iped.app.ui.ai.model.ContextFileEntry;
import iped.data.IItem;

/**
 * Encapsulated component responsible for displaying the interface for files added to the AI context.
 * Applies SRP and acts strictly as a Passive View, delegating all state changes to the ContextListener.
 */
public class ContextPanel extends JPanel {

    private static final int CONTEXT_VISIBLE_ITEMS = 5;
    private static final int CONTEXT_REMOVE_HOTZONE_PX = 28;
    private static final int PANEL_WIDTH = 750;

    private JList<Object> contextList;
    private DefaultListModel<Object> contextListModel;
    private JLabel contextTitleLabel;
    private JLabel contextEmptyLabel;
    private JLabel chatModeLabel;
    private JButton clearContextButton;

    private final ContextListener listener;
    private boolean isContextEditLocked = false;

    /**
     * Contract for the ContextPanel's event listener, allowing external components (e.g., Controller)
     * to intercept user interactions such as clearing the context or removing an individual file.
     */
    public interface ContextListener {
        void onClearContextRequested();
        void onRemoveFileRequested(IItem item);
    }

    /**
     * Internal utility class responsible for rendering the capacity overflow line.
     */
    private static final class ContextSummaryRow {
        private final String text;

        private ContextSummaryRow(String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    /**
     * Constructs the ContextPanel as a passive UI component.
     * @param listener The external controller listening to UI interaction events.
     */
    public ContextPanel(ContextListener listener) {
        this.listener = listener;
        
        configureLayout();
        initComponents();
    }

    private void configureLayout() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), ""),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)));

        JPanel topPanel = new JPanel(new BorderLayout());
        contextTitleLabel = new JLabel("Added Context (0 files)");
        contextTitleLabel.setFont(contextTitleLabel.getFont().deriveFont(Font.BOLD));
        topPanel.add(contextTitleLabel, BorderLayout.WEST);

        chatModeLabel = new JLabel("Modo Chat Resumido ativo");
        chatModeLabel.setForeground(new Color(200, 100, 0));
        chatModeLabel.setFont(chatModeLabel.getFont().deriveFont(Font.BOLD, 10f));
        chatModeLabel.setVisible(false);
        topPanel.add(chatModeLabel, BorderLayout.EAST);

        add(topPanel, BorderLayout.NORTH);
    }

    private void initComponents() {
        contextListModel = new DefaultListModel<>();
        contextList = new JList<>(contextListModel);
        contextList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        contextList.setVisibleRowCount(CONTEXT_VISIBLE_ITEMS);
        contextList.setBackground(new Color(255, 255, 240));

        setupCellRenderer();
        setupMouseListener();

        JScrollPane contextScroll = new JScrollPane(contextList);
        contextScroll.setPreferredSize(new Dimension(PANEL_WIDTH - 10, 80));

        contextEmptyLabel = new JLabel("No files added to context.");
        contextEmptyLabel.setForeground(Color.GRAY);
        contextEmptyLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));
        contextEmptyLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel listContainer = new JPanel(new BorderLayout());
        listContainer.add(contextScroll, BorderLayout.CENTER);
        listContainer.add(contextEmptyLabel, BorderLayout.NORTH);

        clearContextButton = new JButton("Clear");
        clearContextButton.setMargin(new Insets(0, 5, 0, 5));
        clearContextButton.setEnabled(false);
        clearContextButton.addActionListener(e -> {
            if (!isContextEditLocked && listener != null) {
                listener.onClearContextRequested();
            }
        });

        JPanel actionPanel = new JPanel(new BorderLayout());
        actionPanel.add(clearContextButton, BorderLayout.NORTH);

        add(listContainer, BorderLayout.CENTER);
        add(actionPanel, BorderLayout.EAST);
    }

    public void setSummarizedMode(boolean summarized) {
        if (chatModeLabel != null) {
            chatModeLabel.setVisible(summarized);
        }
    }

    /**
     * Enables or disables direct context-edit actions in this panel for the
     * currently displayed conversation.
     *
     * This is a UI-only edit lock: it prevents the user from removing items or
     * clearing the context from the panel once the conversation already contains
     * an assistant reply or an in-flight draft.
     *
     * It does NOT prevent the controller from creating a new conversation and
     * programmatically populating its context (auto-fork flow).
    */
    public void setContextEditLocked(boolean locked) {
        this.isContextEditLocked = locked;
        clearContextButton.setEnabled(!locked && contextListModel.getSize() > 0);
        
        String currentTitle = contextTitleLabel.getText();
        if (currentTitle != null) {
            String baseTitle = currentTitle.replace(" [LOCKED]", "");
            String lockText = locked ? " [LOCKED]" : "";
            contextTitleLabel.setText(baseTitle + lockText);
        }
        
        contextList.repaint();
        repaint();
    }

    private void setupCellRenderer() {
        contextList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            if (value instanceof ContextSummaryRow) {
                JLabel label = (JLabel) new DefaultListCellRenderer()
                        .getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                ContextSummaryRow summary = (ContextSummaryRow) value;
                label.setText(summary.toString());
                label.setForeground(Color.DARK_GRAY);
                label.setFont(label.getFont().deriveFont(Font.ITALIC));
                label.setToolTipText(summary.toString());
                return label;
            }

            if (value instanceof ContextFileEntry) {
                return createContextEntryCell(list, (ContextFileEntry) value, isSelected);
            }

            JLabel label = (JLabel) new DefaultListCellRenderer()
                    .getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            label.setText(String.valueOf(value));
            return label;
        });
    }

    private JComponent createContextEntryCell(JList<?> list, ContextFileEntry entry, boolean isSelected) {
        JPanel rowPanel = new JPanel(new BorderLayout(8, 0));
        rowPanel.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        rowPanel.setOpaque(true);
        rowPanel.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());

        JLabel textLabel = new JLabel();
        textLabel.setOpaque(false);
        textLabel.setFont(list.getFont());
        textLabel.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());

        // Hide the per-item remove "X" while direct context editing is disabled.
        JLabel removeLabel = new JLabel(isContextEditLocked ? "" : "X");
        removeLabel.setOpaque(false);
        removeLabel.setFont(list.getFont().deriveFont(Font.BOLD));
        removeLabel.setForeground(new Color(160, 0, 0));

        if (entry.isValidForContext()) {
            textLabel.setText(entry.getFileName());
            rowPanel.setToolTipText(entry.getFullPath());
        } else {
            String reason = entry.getValidationReason() != null ? entry.getValidationReason() : "Rejected item.";
            textLabel.setText(entry.getFileName() + " - " + reason);
            rowPanel.setToolTipText(reason + " Path: " + entry.getFullPath());
            textLabel.setForeground(isSelected ? list.getSelectionForeground() : new Color(180, 0, 0));
        }

        rowPanel.add(textLabel, BorderLayout.CENTER);
        rowPanel.add(removeLabel, BorderLayout.EAST);
        return rowPanel;
    }

    private void setupMouseListener() {
        contextList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // Ignore direct remove clicks while context editing is disabled for this conversation view
                if (isContextEditLocked) return;

                if (e.getButton() != MouseEvent.BUTTON1) return;

                int index = contextList.locationToIndex(e.getPoint());
                if (index < 0 || index >= contextListModel.size()) return;

                Rectangle cellBounds = contextList.getCellBounds(index, index);
                if (cellBounds == null || !cellBounds.contains(e.getPoint())) return;

                Object value = contextListModel.getElementAt(index);
                if (!(value instanceof ContextFileEntry)) return;

                if (e.getX() < cellBounds.x + cellBounds.width - CONTEXT_REMOVE_HOTZONE_PX) return;

                if (listener != null) {
                    listener.onRemoveFileRequested(((ContextFileEntry) value).getItem());
                }
            }
        });
    }

    /**
     * Synchronizes the file UI components with the state data supplied by the controller.
     * @param entries The reactive UI list entries representing current domain facts.
     */
    public void updateContextData(List<ContextFileEntry> entries) {
        contextListModel.clear();

        if (entries == null || entries.isEmpty()) {
            contextEmptyLabel.setVisible(true);
            contextList.setVisible(false);
            clearContextButton.setEnabled(false);
            contextTitleLabel.setText("Added Context (0 files)");
        } else {
            contextEmptyLabel.setVisible(false);
            contextList.setVisible(true);
            // Disable "Clear Context" only for the current conversation's panel-level edit flow
            clearContextButton.setEnabled(!isContextEditLocked);

            int validCount = 0;
            for (ContextFileEntry entry : entries) {
                if (entry.isValidForContext()) {
                    validCount++;
                }
            }
            int invalidCount = entries.size() - validCount;

            int visibleCount = Math.min(CONTEXT_VISIBLE_ITEMS, entries.size());
            for (int i = 0; i < visibleCount; i++) {
                contextListModel.addElement(entries.get(i));
            }

            if (entries.size() > CONTEXT_VISIBLE_ITEMS) {
                int hiddenCount = entries.size() - CONTEXT_VISIBLE_ITEMS;
                String summaryText = "+ " + hiddenCount + " more items (" + validCount + " valid, " + invalidCount + " rejected)";
                contextListModel.addElement(new ContextSummaryRow(summaryText));
            }

            String lockText = isContextEditLocked ? " [LOCKED]" : "";
            if (invalidCount > 0) {
                contextTitleLabel.setText("Added Context (" + validCount + " valid, " + invalidCount + " rejected)" + lockText);
            } else {
                contextTitleLabel.setText("Added Context (" + validCount + " valid)" + lockText);
            }
        }

        repaint();
    }
}