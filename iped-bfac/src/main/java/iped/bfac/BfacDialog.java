package iped.bfac;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

import java.awt.Frame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JOptionPane;

import iped.bfac.api.BfacApiClient;
import iped.bfac.api.Category;
import iped.bfac.api.LoginResult;
import iped.bfac.api.Submission;
import iped.bfac.api.ValidationResult;
import iped.data.IIPEDSource;

/**
 * Main dialog for BFAC integration in IPED.
 * Allows users to login, select bookmarks, create submissions and upload files/hashes.
 */
public class BfacDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private static final String CARD_LOGIN = "login";
    private static final String CARD_SUBMISSION = "submission";
    private static final String CARD_PROGRESS = "progress";

    // Singleton instance
    private static BfacDialog instance;
    private JFrame parentFrame;

    private CardLayout cardLayout;
    private JPanel cardPanel;

    // API client
    private BfacApiClient apiClient;

    // IPED source for getting items
    private IIPEDSource ipedSource;

    // Worker for background submission
    private SubmissionWorker currentWorker;

    // Login panel components
    private JTextField serverUrlField;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton loginButton;
    private JLabel loginStatusLabel;

    // Submission panel components
    private JList<String> bookmarkList;
    private DefaultListModel<String> bookmarkListModel;
    private JRadioButton newSubmissionRadio;
    private JRadioButton existingSubmissionRadio;
    private JComboBox<Submission> existingSubmissionComboBox;
    private JButton refreshSubmissionsButton;
    private JTextField submissionNameField;
    private JTextArea submissionCommentArea;
    private JComboBox<Category> categoryComboBox;
    private JCheckBox uploadFilesCheckBox;
    private JButton createSubmissionButton;
    private JButton logoutButton;
    private JLabel userInfoLabel;
    private JPanel newSubmissionFieldsPanel;

    // Progress panel components
    private JProgressBar progressBar;
    private JTextArea progressLogArea;
    private JButton cancelButton;
    private JButton closeButton;

    private BfacDialog(JFrame parent) {
        super(parent, "BFAC - Base Federal de Arquivos Conhecidos", false); // Non-modal
        this.parentFrame = parent;
        this.apiClient = new BfacApiClient();
        initComponents();
        initFromStoredCredentials();
        setSize(600, 550);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        // Handle window closing
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleWindowClosing();
            }
        });
    }

    /**
     * Gets or creates the singleton instance of BfacDialog.
     * If the dialog already exists, it will be restored and brought to focus.
     * @param parent The parent frame
     * @return The singleton BfacDialog instance
     */
    public static synchronized BfacDialog getInstance(JFrame parent) {
        if (instance == null || !instance.isDisplayable()) {
            instance = new BfacDialog(parent);
        }
        return instance;
    }

    /**
     * Shows the dialog, restoring it if minimized and bringing it to focus.
     */
    public void showDialog() {
        // Restore parent frame if iconified
        if (parentFrame != null && parentFrame.getState() == Frame.ICONIFIED) {
            parentFrame.setState(Frame.NORMAL);
        }
        setVisible(true);
        toFront();
        requestFocus();
    }

    /**
     * Handles the window closing event.
     * If an upload is in progress, asks the user for confirmation.
     */
    private void handleWindowClosing() {
        if (isUploadInProgress()) {
            int result = JOptionPane.showConfirmDialog(
                this,
                "An upload is currently in progress. If you close this window, the upload will be interrupted.\n\n" +
                "Do you want to close anyway?",
                "Upload in Progress",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            );

            if (result == JOptionPane.YES_OPTION) {
                if (currentWorker != null) {
                    currentWorker.cancelOperation();
                }
                closeAndCleanup();
            }
            // If NO, do nothing - keep the dialog open
        } else {
            closeAndCleanup();
        }
    }

    /**
     * Checks if an upload operation is currently in progress.
     * @return true if upload is in progress
     */
    public boolean isUploadInProgress() {
        return currentWorker != null && !currentWorker.isDone();
    }

    /**
     * Static method to check if there's an active BFAC upload in progress.
     * Can be called from outside to check before closing the application.
     * @return true if an upload is in progress
     */
    public static boolean hasActiveUpload() {
        return instance != null && instance.isUploadInProgress();
    }

    /**
     * Static method to confirm application close when upload is in progress.
     * Shows a confirmation dialog if there's an active upload.
     * @param parent The parent component for the dialog
     * @return true if the application can be closed, false if user cancelled
     */
    public static boolean confirmApplicationClose(java.awt.Component parent) {
        if (!hasActiveUpload()) {
            return true;
        }

        int result = JOptionPane.showConfirmDialog(
            parent,
            "BFAC: An upload is currently in progress. If you close the application, the upload will be interrupted.\n\n" +
            "Do you want to close anyway?",
            "Upload in Progress",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );

        if (result == JOptionPane.YES_OPTION) {
            if (instance != null && instance.currentWorker != null) {
                instance.currentWorker.cancelOperation();
            }
            return true;
        }
        return false;
    }

    /**
     * Closes the dialog and cleans up resources.
     */
    private void closeAndCleanup() {
        instance = null;
        dispose();
    }

    /**
     * Sets the IPED source for accessing items and bookmarks.
     * @param ipedSource The IPED source
     */
    public void setIPEDSource(IIPEDSource ipedSource) {
        this.ipedSource = ipedSource;
    }

    /**
     * Initialize dialog state from stored credentials if available.
     */
    private void initFromStoredCredentials() {
        // Pre-fill username if stored
        String storedUsername = apiClient.getStoredUsername();
        if (storedUsername != null && !storedUsername.isEmpty()) {
            usernameField.setText(storedUsername);
        }

        // If already authenticated, validate session with server
        if (apiClient.isAuthenticated()) {
            userInfoLabel.setText("Logged in as: " + (storedUsername != null ? storedUsername : "user"));
            cardLayout.show(cardPanel, CARD_SUBMISSION);

            // Validate session in background
            validateSessionInBackground(storedUsername);
        }
    }

    /**
     * Validates the current session with the server in a background thread.
     * If the session is invalid (401), returns to the login panel.
     * @param storedUsername The stored username for display purposes
     */
    private void validateSessionInBackground(String storedUsername) {
        new SwingWorker<ValidationResult, Void>() {
            @Override
            protected ValidationResult doInBackground() {
                return apiClient.validateSession();
            }

            @Override
            protected void done() {
                try {
                    ValidationResult result = get();
                    if (result.isValid()) {
                        // Session is valid, load data
                        loadCategoriesInBackground();
                        loadOpenSubmissionsInBackground();
                    } else if (result.getStatusCode() == 401) {
                        // Session expired, return to login
                        loginStatusLabel.setForeground(Color.ORANGE);
                        loginStatusLabel.setText("Session expired. Please login again.");
                        cardLayout.show(cardPanel, CARD_LOGIN);
                    } else {
                        // Other error (e.g., connection error), stay on submission panel
                        // but still try to load data (might work with cached token)
                        loadCategoriesInBackground();
                        loadOpenSubmissionsInBackground();
                    }
                } catch (Exception e) {
                    // On error, stay on submission panel and try to load data
                    loadCategoriesInBackground();
                    loadOpenSubmissionsInBackground();
                }
            }
        }.execute();
    }

    /**
     * Loads categories from the API in a background thread.
     */
    private void loadCategoriesInBackground() {
        new SwingWorker<List<Category>, Void>() {
            @Override
            protected List<Category> doInBackground() {
                return apiClient.getCategories();
            }

            @Override
            protected void done() {
                try {
                    List<Category> categories = get();
                    categoryComboBox.removeAllItems();
                    for (Category category : categories) {
                        categoryComboBox.addItem(category);
                    }
                } catch (Exception e) {
                    // Keep default categories if loading fails
                }
            }
        }.execute();
    }

    /**
     * Loads open submissions from the API in a background thread.
     */
    private void loadOpenSubmissionsInBackground() {
        refreshSubmissionsButton.setEnabled(false);
        existingSubmissionComboBox.setEnabled(false);

        new SwingWorker<List<Submission>, Void>() {
            @Override
            protected List<Submission> doInBackground() {
                return apiClient.getOpenSubmissions();
            }

            @Override
            protected void done() {
                try {
                    List<Submission> submissions = get();
                    existingSubmissionComboBox.removeAllItems();
                    for (Submission submission : submissions) {
                        existingSubmissionComboBox.addItem(submission);
                    }
                    if (submissions.isEmpty()) {
                        // If no open submissions, switch to new submission mode
                        newSubmissionRadio.setSelected(true);
                        onSubmissionModeChanged();
                    }
                } catch (Exception e) {
                    // Keep empty if loading fails
                } finally {
                    refreshSubmissionsButton.setEnabled(true);
                    existingSubmissionComboBox.setEnabled(existingSubmissionRadio.isSelected());
                }
            }
        }.execute();
    }

    /**
     * Called when the submission mode (new/existing) is changed.
     */
    private void onSubmissionModeChanged() {
        boolean isNewSubmission = newSubmissionRadio.isSelected();

        // Enable/disable fields based on mode
        existingSubmissionComboBox.setEnabled(!isNewSubmission);
        refreshSubmissionsButton.setEnabled(!isNewSubmission);
        submissionNameField.setEnabled(isNewSubmission);
        categoryComboBox.setEnabled(isNewSubmission);
        submissionCommentArea.setEnabled(isNewSubmission);

        // Update button text
        if (isNewSubmission) {
            createSubmissionButton.setText("Create Submission & Upload");
        } else {
            createSubmissionButton.setText("Add to Submission & Upload");
        }
    }

    private void initComponents() {
        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);

        cardPanel.add(createLoginPanel(), CARD_LOGIN);
        cardPanel.add(createSubmissionPanel(), CARD_SUBMISSION);
        cardPanel.add(createProgressPanel(), CARD_PROGRESS);

        getContentPane().add(cardPanel);

        // Start with login panel
        cardLayout.show(cardPanel, CARD_LOGIN);
    }

    private JPanel createLoginPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Title
        JLabel titleLabel = new JLabel("Login BFAC", SwingConstants.CENTER);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(0, 0, 20, 0);
        panel.add(titleLabel, gbc);

        gbc.gridwidth = 1;
        gbc.insets = new Insets(5, 5, 5, 5);

        // Server URL
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.EAST;
        panel.add(new JLabel("Server URL:"), gbc);

        serverUrlField = new JTextField("http://localhost:8000/", 25);
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(serverUrlField, gbc);

        // Username
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.anchor = GridBagConstraints.EAST;
        panel.add(new JLabel("Username:"), gbc);

        usernameField = new JTextField(25);
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(usernameField, gbc);

        // Password
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.anchor = GridBagConstraints.EAST;
        panel.add(new JLabel("Password:"), gbc);

        passwordField = new JPasswordField(25);
        passwordField.addActionListener(e -> onLogin()); // Allow ENTER key to submit
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(passwordField, gbc);

        // Login button
        loginButton = new JButton("Login");
        loginButton.addActionListener(e -> onLogin());
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(20, 5, 5, 5);
        panel.add(loginButton, gbc);

        // Status label
        loginStatusLabel = new JLabel(" ");
        loginStatusLabel.setForeground(Color.RED);
        gbc.gridy = 5;
        gbc.insets = new Insets(10, 5, 5, 5);
        panel.add(loginStatusLabel, gbc);

        return panel;
    }

    private JPanel createSubmissionPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Top panel with user info and logout
        JPanel topPanel = new JPanel(new BorderLayout());
        userInfoLabel = new JLabel("Logged in as: user@example.com");
        userInfoLabel.setFont(new Font("SansSerif", Font.ITALIC, 12));
        topPanel.add(userInfoLabel, BorderLayout.WEST);

        logoutButton = new JButton("Logout");
        logoutButton.addActionListener(e -> onLogout());
        topPanel.add(logoutButton, BorderLayout.EAST);
        panel.add(topPanel, BorderLayout.NORTH);

        // Center panel with bookmark selection and submission details
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));

        // Bookmark selection
        JPanel bookmarkPanel = new JPanel(new BorderLayout(5, 5));
        bookmarkPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            "Select Bookmarks",
            TitledBorder.LEFT,
            TitledBorder.TOP));

        bookmarkListModel = new DefaultListModel<>();
        // Mock bookmarks for demonstration
        bookmarkListModel.addElement("Malware Samples");
        bookmarkListModel.addElement("Suspicious Files");
        bookmarkListModel.addElement("Documents to Analyze");
        bookmarkListModel.addElement("Encrypted Files");
        bookmarkListModel.addElement("Unknown Executables");

        bookmarkList = new JList<>(bookmarkListModel);
        bookmarkList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        bookmarkList.setVisibleRowCount(6);

        JScrollPane bookmarkScrollPane = new JScrollPane(bookmarkList);
        bookmarkScrollPane.setPreferredSize(new Dimension(400, 120));
        bookmarkPanel.add(bookmarkScrollPane, BorderLayout.CENTER);

        JLabel bookmarkHintLabel = new JLabel("Hold Ctrl to select multiple bookmarks");
        bookmarkHintLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));
        bookmarkPanel.add(bookmarkHintLabel, BorderLayout.SOUTH);

        centerPanel.add(bookmarkPanel);
        centerPanel.add(Box.createVerticalStrut(10));

        // Submission details
        JPanel detailsPanel = new JPanel(new GridBagLayout());
        detailsPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            "Submission",
            TitledBorder.LEFT,
            TitledBorder.TOP));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Radio buttons for new/existing submission
        newSubmissionRadio = new JRadioButton("Create new submission");
        newSubmissionRadio.setSelected(true);
        existingSubmissionRadio = new JRadioButton("Use existing submission");

        ButtonGroup submissionModeGroup = new ButtonGroup();
        submissionModeGroup.add(newSubmissionRadio);
        submissionModeGroup.add(existingSubmissionRadio);

        newSubmissionRadio.addActionListener(e -> onSubmissionModeChanged());
        existingSubmissionRadio.addActionListener(e -> onSubmissionModeChanged());

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        detailsPanel.add(newSubmissionRadio, gbc);

        gbc.gridy = 1;
        detailsPanel.add(existingSubmissionRadio, gbc);

        // Existing submission selection panel
        JPanel existingPanel = new JPanel(new BorderLayout(5, 0));
        existingSubmissionComboBox = new JComboBox<>();
        existingSubmissionComboBox.setEnabled(false);
        existingPanel.add(existingSubmissionComboBox, BorderLayout.CENTER);

        refreshSubmissionsButton = new JButton("↻");
        refreshSubmissionsButton.setToolTipText("Refresh submissions list");
        refreshSubmissionsButton.setEnabled(false);
        refreshSubmissionsButton.addActionListener(e -> loadOpenSubmissionsInBackground());
        existingPanel.add(refreshSubmissionsButton, BorderLayout.EAST);

        gbc.gridy = 2;
        gbc.insets = new Insets(0, 25, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        detailsPanel.add(existingPanel, gbc);
        gbc.weightx = 0;

        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.gridwidth = 1;

        // Submission name
        gbc.gridx = 0;
        gbc.gridy = 3;
        detailsPanel.add(new JLabel("Name:"), gbc);

        submissionNameField = new JTextField(30);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        detailsPanel.add(submissionNameField, gbc);

        // Category
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        detailsPanel.add(new JLabel("Category:"), gbc);

        categoryComboBox = new JComboBox<>();
        // Categories will be loaded from API after login
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        detailsPanel.add(categoryComboBox, gbc);

        // Comment
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        detailsPanel.add(new JLabel("Comment:"), gbc);

        submissionCommentArea = new JTextArea(2, 30);
        submissionCommentArea.setLineWrap(true);
        submissionCommentArea.setWrapStyleWord(true);
        JScrollPane commentScrollPane = new JScrollPane(submissionCommentArea);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;
        detailsPanel.add(commentScrollPane, gbc);

        // Upload files checkbox
        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weighty = 0;
        gbc.anchor = GridBagConstraints.WEST;
        uploadFilesCheckBox = new JCheckBox("Upload files (not just hashes)");
        detailsPanel.add(uploadFilesCheckBox, gbc);

        centerPanel.add(detailsPanel);

        panel.add(centerPanel, BorderLayout.CENTER);

        // Bottom panel with create button
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        createSubmissionButton = new JButton("Create Submission & Upload");
        createSubmissionButton.addActionListener(e -> onCreateSubmission());
        bottomPanel.add(createSubmissionButton);
        panel.add(bottomPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createProgressPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));

        // Title
        JLabel titleLabel = new JLabel("Upload Progress", SwingConstants.CENTER);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        panel.add(titleLabel, BorderLayout.NORTH);

        // Center panel with progress bar and log
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));

        // Progress bar
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setString("0%");
        progressBar.setPreferredSize(new Dimension(500, 25));
        progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));
        centerPanel.add(progressBar);
        centerPanel.add(Box.createVerticalStrut(15));

        // Progress log
        progressLogArea = new JTextArea(12, 50);
        progressLogArea.setEditable(false);
        progressLogArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane logScrollPane = new JScrollPane(progressLogArea);
        logScrollPane.setBorder(BorderFactory.createTitledBorder("Log"));
        centerPanel.add(logScrollPane);

        panel.add(centerPanel, BorderLayout.CENTER);

        // Bottom panel with buttons
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> onCancel());
        bottomPanel.add(cancelButton);

        closeButton = new JButton("Close");
        closeButton.setEnabled(false);
        closeButton.addActionListener(e -> onClose());
        bottomPanel.add(closeButton);

        panel.add(bottomPanel, BorderLayout.SOUTH);

        return panel;
    }

    // Event handlers

    private void onLogin() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        String serverUrl = serverUrlField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            loginStatusLabel.setForeground(Color.RED);
            loginStatusLabel.setText("Please enter username and password");
            return;
        }

        // Update server URL if changed
        if (!serverUrl.isEmpty() && !serverUrl.equals(apiClient.getBaseUrl())) {
            apiClient.setBaseUrl(serverUrl);
        }

        // Disable UI during login
        loginButton.setEnabled(false);
        usernameField.setEnabled(false);
        passwordField.setEnabled(false);
        serverUrlField.setEnabled(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        loginStatusLabel.setForeground(Color.BLUE);
        loginStatusLabel.setText("Logging in...");

        // Perform login in background thread
        new SwingWorker<LoginResult, Void>() {
            @Override
            protected LoginResult doInBackground() {
                return apiClient.login(username, password);
            }

            @Override
            protected void done() {
                try {
                    LoginResult result = get();
                    if (result.isSuccess()) {
                        // Clear password from memory
                        passwordField.setText("");

                        userInfoLabel.setText("Logged in as: " + username);
                        loginStatusLabel.setText(" ");

                        // Load categories and open submissions from API
                        loadCategoriesInBackground();
                        loadOpenSubmissionsInBackground();

                        cardLayout.show(cardPanel, CARD_SUBMISSION);
                    } else {
                        loginStatusLabel.setForeground(Color.RED);
                        loginStatusLabel.setText(result.getMessage());
                    }
                } catch (Exception e) {
                    loginStatusLabel.setForeground(Color.RED);
                    loginStatusLabel.setText("Error: " + e.getMessage());
                } finally {
                    // Re-enable UI
                    loginButton.setEnabled(true);
                    usernameField.setEnabled(true);
                    passwordField.setEnabled(true);
                    serverUrlField.setEnabled(true);
                    setCursor(Cursor.getDefaultCursor());
                }
            }
        }.execute();
    }

    private void onLogout() {
        // Clear credentials from API client and config
        apiClient.logout();

        // Clear UI
        passwordField.setText("");
        loginStatusLabel.setText(" ");
        loginStatusLabel.setForeground(Color.RED);
        cardLayout.show(cardPanel, CARD_LOGIN);
    }

    private void onCreateSubmission() {
        List<String> selectedBookmarks = bookmarkList.getSelectedValuesList();

        if (selectedBookmarks.isEmpty()) {
            javax.swing.JOptionPane.showMessageDialog(this,
                "Please select at least one bookmark.",
                "No Bookmarks Selected",
                javax.swing.JOptionPane.WARNING_MESSAGE);
            return;
        }

        boolean isNewSubmission = newSubmissionRadio.isSelected();
        int submissionId = -1;
        String submissionName;
        String categoryName;
        String comment;

        if (isNewSubmission) {
            submissionName = submissionNameField.getText().trim();
            if (submissionName.isEmpty()) {
                javax.swing.JOptionPane.showMessageDialog(this,
                    "Please enter a submission name.",
                    "Name Required",
                    javax.swing.JOptionPane.WARNING_MESSAGE);
                return;
            }

            Category selectedCategory = (Category) categoryComboBox.getSelectedItem();
            if (selectedCategory == null) {
                javax.swing.JOptionPane.showMessageDialog(this,
                    "Please select a category.",
                    "Category Required",
                    javax.swing.JOptionPane.WARNING_MESSAGE);
                return;
            }
            categoryName = selectedCategory.getName();
            comment = submissionCommentArea.getText().trim();
        } else {
            Submission selectedSubmission = (Submission) existingSubmissionComboBox.getSelectedItem();
            if (selectedSubmission == null) {
                javax.swing.JOptionPane.showMessageDialog(this,
                    "Please select an existing submission.",
                    "Submission Required",
                    javax.swing.JOptionPane.WARNING_MESSAGE);
                return;
            }
            submissionId = selectedSubmission.getId();
            submissionName = selectedSubmission.getName();
            categoryName = selectedSubmission.getCategoryName();
            comment = null;
        }

        // Switch to progress panel
        cardLayout.show(cardPanel, CARD_PROGRESS);
        progressLogArea.setText("");
        progressBar.setValue(0);
        progressBar.setString("0%");
        cancelButton.setEnabled(true);
        closeButton.setEnabled(false);

        // Create callback for worker communication
        SubmissionWorker.SubmissionCallback callback = new SubmissionWorker.SubmissionCallback() {
            @Override
            public void onLogMessage(String message) {
                appendLog(message);
            }

            @Override
            public void onComplete(boolean success) {
                cancelButton.setEnabled(false);
                closeButton.setEnabled(true);
                currentWorker = null;
            }

            @Override
            public void onAuthenticationError() {
                // Clear credentials and return to login screen
                apiClient.logout();
                javax.swing.SwingUtilities.invokeLater(() -> {
                    javax.swing.JOptionPane.showMessageDialog(
                        BfacDialog.this,
                        "Your session has expired. Please log in again.",
                        "Session Expired",
                        javax.swing.JOptionPane.WARNING_MESSAGE
                    );
                    cardLayout.show(cardPanel, CARD_LOGIN);
                    loginStatusLabel.setText("Session expired. Please log in again.");
                    loginStatusLabel.setForeground(java.awt.Color.RED);
                });
            }
        };

        // Start the submission worker
        currentWorker = new SubmissionWorker(
            apiClient,
            ipedSource,
            callback,
            isNewSubmission,
            submissionId,
            submissionName,
            comment,
            categoryName,
            new java.util.HashSet<>(selectedBookmarks),
            uploadFilesCheckBox.isSelected()
        );

        // Listen for progress updates
        currentWorker.addPropertyChangeListener(evt -> {
            if ("progress".equals(evt.getPropertyName())) {
                int progress = (Integer) evt.getNewValue();
                progressBar.setValue(progress);
                progressBar.setString(progress + "%");
            }
        });

        currentWorker.execute();
    }

    private void appendLog(String message) {
        progressLogArea.append(message + "\n");
        progressLogArea.setCaretPosition(progressLogArea.getDocument().getLength());
    }

    private void onCancel() {
        // Cancel the current worker if running
        if (currentWorker != null && !currentWorker.isDone()) {
            currentWorker.cancelOperation();
            appendLog("Cancelling operation...");
            return; // Let the worker finish and update UI
        }

        // Return to submission panel
        cardLayout.show(cardPanel, CARD_SUBMISSION);
        progressBar.setValue(0);
        progressBar.setString("0%");
        cancelButton.setEnabled(true);
        closeButton.setEnabled(false);
    }

    private void onClose() {
        // Return to submission panel
        cardLayout.show(cardPanel, CARD_SUBMISSION);
        progressBar.setValue(0);
        progressBar.setString("0%");
        cancelButton.setEnabled(true);
        closeButton.setEnabled(false);

        // Clear form
        submissionNameField.setText("");
        submissionCommentArea.setText("");
        bookmarkList.clearSelection();
        uploadFilesCheckBox.setSelected(false);
    }

    /**
     * Updates the bookmark list with bookmarks from the IPED case.
     * @param bookmarks Set of bookmark names from the case
     */
    public void setBookmarks(Set<String> bookmarks) {
        bookmarkListModel.clear();
        for (String bookmark : bookmarks) {
            bookmarkListModel.addElement(bookmark);
        }
    }

    // Main method for testing the dialog standalone
    public static void main(String[] args) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame();
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            BfacDialog dialog = new BfacDialog(frame);
            dialog.setVisible(true);

            System.exit(0);
        });
    }
}
