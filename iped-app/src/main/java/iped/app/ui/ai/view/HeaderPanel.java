package iped.app.ui.ai.view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionListener;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Modular panel for the application header.
 * Encapsulates the title, sidebar toggle button, and backend status label.
 */
public class HeaderPanel extends JPanel {

    private final JLabel statusLabel;

    public HeaderPanel(String titleText, ActionListener toggleSidebarListener) {
        
        // Initialize the base JPanel with BorderLayout
        super(new BorderLayout());

        // Sidebar toggle button
        JButton toggleSidebarBtn = new JButton("☰");
        toggleSidebarBtn.setMargin(new Insets(2, 6, 2, 6));
        toggleSidebarBtn.setFocusPainted(false);
        toggleSidebarBtn.setToolTipText("Toggle Sidebar");
        
        // Execute the action injected by the main class
        toggleSidebarBtn.addActionListener(toggleSidebarListener);

        // Title label
        JLabel titleLabel = new JLabel(titleText);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 14));

        // Title area (button + text)
        JPanel titleArea = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        titleArea.add(toggleSidebarBtn);
        titleArea.add(titleLabel);

        // Initialize status label
        statusLabel = new JLabel("● Connected to local backend server");
        statusLabel.setForeground(new Color(0, 150, 0)); // Green for active

        // Group title and status on the left side
        JPanel leftPanel = new JPanel(new BorderLayout(0, 5));
        leftPanel.add(titleArea, BorderLayout.NORTH);
        leftPanel.add(statusLabel, BorderLayout.SOUTH);

        // Add components to the main panel (this)
        add(leftPanel, BorderLayout.WEST);
        setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));
    }

    /**
     * Allows external controllers to update the backend status visual state.
     */
    public void updateStatus(String text, Color color) {
        statusLabel.setText(text);
        statusLabel.setForeground(color);
    }
}