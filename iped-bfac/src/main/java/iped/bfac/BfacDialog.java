package iped.bfac;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

import iped.bfac.api.BfacApiClient;
import iped.bfac.api.Category;
import iped.bfac.api.LoginResult;
import iped.bfac.api.Submission;
import iped.bfac.api.ValidationResult;
import iped.bfac.localization.Messages;
import iped.bfac.ui.BookmarkSelectionPanel;
import iped.bfac.ui.UploadProgressPanel;
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

    // Worker for session validation on dialog open
    private SwingWorker<ValidationResult, Void> sessionValidationWorker;

    // Prevents multiple connection-error dialogs when parallel API calls fail
    private boolean connectionErrorShown;

    // Login panel components
    private JLabel serverUrlLabel;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton loginButton;
    private JLabel loginStatusLabel;

    // Submission panel components
    private BookmarkSelectionPanel bookmarkSelectionPanel;
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

    // Progress panel
    private UploadProgressPanel uploadProgressPanel;

    private BfacDialog(JFrame parent) {
        super(parent, Messages.getString("BfacDialog.Title"), false); // Non-modal
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
        checkStoredSessionOnOpen();
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
                Messages.getString("BfacDialog.UploadInProgressConfirm"),
                Messages.getString("BfacDialog.UploadInProgressTitle"),
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
            Messages.getString("BfacDialog.AppCloseUploadInProgressConfirm"),
            Messages.getString("BfacDialog.UploadInProgressTitle"),
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
        if (bookmarkSelectionPanel != null) {
            bookmarkSelectionPanel.setMultiBookmarks(
                    ipedSource != null ? ipedSource.getMultiBookmarks() : null);
        }
    }

    /**
     * Pre-fills username from stored config during construction.
     */
    private void initFromStoredCredentials() {
        String storedUsername = apiClient.getStoredUsername();
        if (storedUsername != null && !storedUsername.isEmpty()) {
            usernameField.setText(storedUsername);
        }
    }

    /**
     * Checks stored credentials and validates the session with the backend when opening the dialog.
     */
    private void checkStoredSessionOnOpen() {
        String storedUsername = apiClient.getStoredUsername();
        if (storedUsername != null && !storedUsername.isEmpty()) {
            usernameField.setText(storedUsername);
        }

        if (apiClient.hasStoredCredentials()) {
            apiClient.reloadStoredCredentials();
            userInfoLabel.setText(Messages.getString("BfacDialog.LoggedInAs",
                    storedUsername != null ? storedUsername : Messages.getString("BfacDialog.DefaultUser")));
            cardLayout.show(cardPanel, CARD_SUBMISSION);
            validateSessionInBackground();
        } else {
            cardLayout.show(cardPanel, CARD_LOGIN);
        }
    }

    /**
     * Clears credentials and returns to the login panel after session expiration (401).
     * @param showModalMessage if true, shows a warning dialog (e.g. during upload)
     */
    private void showLoginAfterSessionExpired(boolean showModalMessage) {
        apiClient.logout();

        String storedUsername = apiClient.getStoredUsername();
        if (storedUsername != null && !storedUsername.isEmpty()) {
            usernameField.setText(storedUsername);
        }

        if (showModalMessage) {
            JOptionPane.showMessageDialog(
                    this,
                    Messages.getString("BfacDialog.SessionExpired"),
                    Messages.getString("BfacDialog.SessionExpiredTitle"),
                    JOptionPane.WARNING_MESSAGE);
        }

        loginStatusLabel.setForeground(Color.ORANGE);
        loginStatusLabel.setText(Messages.getString("BfacDialog.SessionExpiredLoginAgain"));
        cardLayout.show(cardPanel, CARD_LOGIN);
    }

    private void showLoginAfterSessionExpired() {
        showLoginAfterSessionExpired(false);
    }

    /**
     * Shows a user-friendly connection error and closes the dialog after the user dismisses it.
     * @param detailMessage technical detail (e.g. exception message), may be null
     */
    private void showBackendConnectionErrorAndClose(String detailMessage) {
        if (connectionErrorShown) {
            return;
        }
        connectionErrorShown = true;

        StringBuilder message = new StringBuilder(Messages.getString("BfacDialog.BackendConnectionError"));
        message.append("\n\n").append(Messages.getString("BfacDialog.BackendUrl", apiClient.getBaseUrl()));
        if (detailMessage != null && !detailMessage.isEmpty()) {
            message.append("\n\n").append(Messages.getString("BfacDialog.ConnectionDetails", detailMessage));
        }

        JOptionPane.showMessageDialog(
                this,
                message.toString(),
                Messages.getString("BfacDialog.BackendConnectionErrorTitle"),
                JOptionPane.ERROR_MESSAGE);
        closeAndCleanup();
    }

    /**
     * Validates the current session with the server in a background thread.
     * If the session is invalid (401), returns to the login panel.
     */
    private void validateSessionInBackground() {
        if (sessionValidationWorker != null && !sessionValidationWorker.isDone()) {
            return;
        }

        sessionValidationWorker = new SwingWorker<ValidationResult, Void>() {
            @Override
            protected ValidationResult doInBackground() {
                return apiClient.validateSession();
            }

            @Override
            protected void done() {
                try {
                    ValidationResult result = get();
                    if (result.isValid()) {
                        loadCategoriesInBackground();
                        loadOpenSubmissionsInBackground();
                    } else if (result.getStatusCode() == 401) {
                        showLoginAfterSessionExpired();
                    } else if (result.getStatusCode() == -1 || apiClient.wasLastCallConnectionError()) {
                        showBackendConnectionErrorAndClose(result.getMessage());
                    } else {
                        loadCategoriesInBackground();
                        loadOpenSubmissionsInBackground();
                    }
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    showBackendConnectionErrorAndClose(cause.getMessage());
                }
            }
        };
        sessionValidationWorker.execute();
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
                    if (apiClient.wasLastCallUnauthorized()) {
                        showLoginAfterSessionExpired();
                        return;
                    }
                    if (apiClient.wasLastCallConnectionError()) {
                        showBackendConnectionErrorAndClose(null);
                        return;
                    }
                    List<Category> categories = get();
                    categoryComboBox.removeAllItems();
                    for (Category category : categories) {
                        categoryComboBox.addItem(category);
                    }
                } catch (Exception e) {
                    if (apiClient.wasLastCallConnectionError()) {
                        Throwable cause = e.getCause() != null ? e.getCause() : e;
                        showBackendConnectionErrorAndClose(cause.getMessage());
                    }
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
                    if (apiClient.wasLastCallUnauthorized()) {
                        showLoginAfterSessionExpired();
                        return;
                    }
                    if (apiClient.wasLastCallConnectionError()) {
                        showBackendConnectionErrorAndClose(null);
                        return;
                    }
                    List<Submission> submissions = get();
                    existingSubmissionComboBox.removeAllItems();
                    for (Submission submission : submissions) {
                        existingSubmissionComboBox.addItem(submission);
                    }
                    if (submissions.isEmpty()) {
                        // If no open submissions, switch to new submission mode
                        newSubmissionRadio.setSelected(true);
                        onSubmissionModeChanged();
                    } else {
                        syncCategoryWithSelectedSubmission();
                    }
                } catch (Exception e) {
                    if (apiClient.wasLastCallConnectionError()) {
                        Throwable cause = e.getCause() != null ? e.getCause() : e;
                        showBackendConnectionErrorAndClose(cause.getMessage());
                    }
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
            createSubmissionButton.setText(Messages.getString("BfacDialog.CreateSubmissionAndUpload"));
        } else {
            createSubmissionButton.setText(Messages.getString("BfacDialog.AddToSubmissionAndUpload"));
            syncCategoryWithSelectedSubmission();
        }
    }

    private void syncCategoryWithSelectedSubmission() {
        Submission selectedSubmission = (Submission) existingSubmissionComboBox.getSelectedItem();
        if (selectedSubmission == null) {
            return;
        }

        String submissionCategoryName = selectedSubmission.getCategoryName();
        if (submissionCategoryName == null || submissionCategoryName.isEmpty()) {
            return;
        }

        for (int i = 0; i < categoryComboBox.getItemCount(); i++) {
            Category category = categoryComboBox.getItemAt(i);
            if (category != null && submissionCategoryName.equals(category.getName())) {
                categoryComboBox.setSelectedIndex(i);
                break;
            }
        }
    }

    private void initComponents() {
        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);

        cardPanel.add(createLoginPanel(), CARD_LOGIN);
        cardPanel.add(createSubmissionPanel(), CARD_SUBMISSION);

        uploadProgressPanel = new UploadProgressPanel();
        uploadProgressPanel.setListener(new UploadProgressPanel.Listener() {
            @Override
            public void onCancel() {
                BfacDialog.this.onCancel();
            }

            @Override
            public void onClose() {
                BfacDialog.this.onClose();
            }
        });
        cardPanel.add(uploadProgressPanel, CARD_PROGRESS);

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
        JLabel titleLabel = new JLabel(Messages.getString("BfacDialog.LoginTitle"), SwingConstants.CENTER);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(0, 0, 20, 0);
        panel.add(titleLabel, gbc);

        gbc.gridwidth = 1;
        gbc.insets = new Insets(5, 5, 5, 5);

        // Server URL (read-only, from config)
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.EAST;
        panel.add(new JLabel(Messages.getString("BfacDialog.ServerUrl")), gbc);

        serverUrlLabel = new JLabel(apiClient.getBaseUrl());
        serverUrlLabel.setFont(new Font("Monospaced", Font.PLAIN, 12));
        serverUrlLabel.setForeground(Color.DARK_GRAY);
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(serverUrlLabel, gbc);

        // Username
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.anchor = GridBagConstraints.EAST;
        panel.add(new JLabel(Messages.getString("BfacDialog.Username")), gbc);

        usernameField = new JTextField(25);
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(usernameField, gbc);

        // Password
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.anchor = GridBagConstraints.EAST;
        panel.add(new JLabel(Messages.getString("BfacDialog.Password")), gbc);

        passwordField = new JPasswordField(25);
        passwordField.addActionListener(e -> onLogin()); // Allow ENTER key to submit
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(passwordField, gbc);

        // Login button
        loginButton = new JButton(Messages.getString("BfacDialog.Login"));
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
        userInfoLabel = new JLabel(Messages.getString("BfacDialog.LoggedInAs", Messages.getString("BfacDialog.DefaultUser")));
        userInfoLabel.setFont(new Font("SansSerif", Font.ITALIC, 12));
        topPanel.add(userInfoLabel, BorderLayout.WEST);

        logoutButton = new JButton(Messages.getString("BfacDialog.Logout"));
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
            Messages.getString("BfacDialog.SelectBookmarks"),
            TitledBorder.LEFT,
            TitledBorder.TOP));

        String[] demoLabels = {
            Messages.getString("BfacDialog.DemoBookmark.MalwareSamples"),
            Messages.getString("BfacDialog.DemoBookmark.SuspiciousFiles"),
            Messages.getString("BfacDialog.DemoBookmark.DocumentsToAnalyze"),
            Messages.getString("BfacDialog.DemoBookmark.EncryptedFiles"),
            Messages.getString("BfacDialog.DemoBookmark.UnknownExecutables")
        };
        bookmarkSelectionPanel = new BookmarkSelectionPanel(demoLabels);
        bookmarkPanel.add(bookmarkSelectionPanel, BorderLayout.CENTER);

        centerPanel.add(bookmarkPanel);
        centerPanel.add(Box.createVerticalStrut(10));

        // Submission details
        JPanel detailsPanel = new JPanel(new GridBagLayout());
        detailsPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            Messages.getString("BfacDialog.Submission"),
            TitledBorder.LEFT,
            TitledBorder.TOP));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Radio buttons for new/existing submission
        newSubmissionRadio = new JRadioButton(Messages.getString("BfacDialog.CreateNewSubmission"));
        newSubmissionRadio.setSelected(true);
        existingSubmissionRadio = new JRadioButton(Messages.getString("BfacDialog.UseExistingSubmission"));

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
        existingSubmissionComboBox.addActionListener(e -> syncCategoryWithSelectedSubmission());
        existingPanel.add(existingSubmissionComboBox, BorderLayout.CENTER);

        refreshSubmissionsButton = new JButton("\u21BB"); // Clockwise open circle arrow (refresh)
        refreshSubmissionsButton.setToolTipText(Messages.getString("BfacDialog.RefreshSubmissions.tooltip"));
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
        detailsPanel.add(new JLabel(Messages.getString("BfacDialog.Name")), gbc);

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
        detailsPanel.add(new JLabel(Messages.getString("BfacDialog.Category")), gbc);

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
        detailsPanel.add(new JLabel(Messages.getString("BfacDialog.Comment")), gbc);

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
        uploadFilesCheckBox = new JCheckBox(Messages.getString("BfacDialog.UploadFiles"), true);
        detailsPanel.add(uploadFilesCheckBox, gbc);

        centerPanel.add(detailsPanel);

        panel.add(centerPanel, BorderLayout.CENTER);

        // Bottom panel with create button
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        createSubmissionButton = new JButton(Messages.getString("BfacDialog.CreateSubmissionAndUpload"));
        createSubmissionButton.addActionListener(e -> onCreateSubmission());
        bottomPanel.add(createSubmissionButton);
        panel.add(bottomPanel, BorderLayout.SOUTH);

        return panel;
    }

    // Event handlers

    private void onLogin() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (username.isEmpty() || password.isEmpty()) {
            loginStatusLabel.setForeground(Color.RED);
            loginStatusLabel.setText(Messages.getString("BfacDialog.EnterCredentials"));
            return;
        }

        // Disable UI during login
        loginButton.setEnabled(false);
        usernameField.setEnabled(false);
        passwordField.setEnabled(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        loginStatusLabel.setForeground(Color.BLUE);
        loginStatusLabel.setText(Messages.getString("BfacDialog.LoggingIn"));

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

                        userInfoLabel.setText(Messages.getString("BfacDialog.LoggedInAs", username));
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
                    loginStatusLabel.setText(Messages.getString("BfacDialog.Error", e.getMessage()));
                } finally {
                    // Re-enable UI
                    loginButton.setEnabled(true);
                    usernameField.setEnabled(true);
                    passwordField.setEnabled(true);
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
        List<String> selectedBookmarks = bookmarkSelectionPanel.getCheckedBookmarks();

        if (selectedBookmarks.isEmpty()) {
            javax.swing.JOptionPane.showMessageDialog(this,
                Messages.getString("BfacDialog.NoBookmarksSelected"),
                Messages.getString("BfacDialog.NoBookmarksSelectedTitle"),
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
                    Messages.getString("BfacDialog.NameRequired"),
                    Messages.getString("BfacDialog.NameRequiredTitle"),
                    javax.swing.JOptionPane.WARNING_MESSAGE);
                return;
            }

            Category selectedCategory = (Category) categoryComboBox.getSelectedItem();
            if (selectedCategory == null) {
                javax.swing.JOptionPane.showMessageDialog(this,
                    Messages.getString("BfacDialog.CategoryRequired"),
                    Messages.getString("BfacDialog.CategoryRequiredTitle"),
                    javax.swing.JOptionPane.WARNING_MESSAGE);
                return;
            }
            categoryName = selectedCategory.getName();
            comment = submissionCommentArea.getText().trim();
        } else {
            Submission selectedSubmission = (Submission) existingSubmissionComboBox.getSelectedItem();
            if (selectedSubmission == null) {
                javax.swing.JOptionPane.showMessageDialog(this,
                    Messages.getString("BfacDialog.SubmissionRequired"),
                    Messages.getString("BfacDialog.SubmissionRequiredTitle"),
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
        uploadProgressPanel.reset();
        uploadProgressPanel.startTracking();

        // Create callback for worker communication
        SubmissionWorker.SubmissionCallback callback = new SubmissionWorker.SubmissionCallback() {
            @Override
            public void onLogMessage(String message) {
                uploadProgressPanel.appendLog(message);
            }

            @Override
            public void onComplete(boolean success) {
                uploadProgressPanel.stopTracking();
                uploadProgressPanel.setCancelEnabled(false);
                uploadProgressPanel.setCloseEnabled(true);
                currentWorker = null;
            }

            @Override
            public void onAuthenticationError() {
                javax.swing.SwingUtilities.invokeLater(() -> showLoginAfterSessionExpired(true));
            }
        };

        // Get max concurrent uploads from config
        int maxConcurrentUploads = 5; // default
        iped.engine.config.BFACClientConfig bfacClientConfig = iped.engine.config.ConfigurationManager.get()
                .findObject(iped.engine.config.BFACClientConfig.class);
        if (bfacClientConfig != null) {
            maxConcurrentUploads = bfacClientConfig.getMaxConcurrentUploads();
        }

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
            uploadFilesCheckBox.isSelected(),
            maxConcurrentUploads
        );

        // Listen for progress updates
        currentWorker.addPropertyChangeListener(evt -> {
            if ("progress".equals(evt.getPropertyName())) {
                uploadProgressPanel.setProgress((Integer) evt.getNewValue());
            } else if ("uploadBytes".equals(evt.getPropertyName())) {
                long[] bytes = (long[]) evt.getNewValue();
                if (bytes != null && bytes.length == 2) {
                    uploadProgressPanel.setUploadBytes(bytes[0], bytes[1]);
                }
            }
        });

        currentWorker.execute();
    }

    private void onCancel() {
        // Cancel the current worker if running
        if (currentWorker != null && !currentWorker.isDone()) {
            currentWorker.cancelOperation();
            uploadProgressPanel.appendLog(Messages.getString("BfacDialog.CancellingOperation"));
            return; // Let the worker finish and update UI
        }

        // Return to submission panel
        uploadProgressPanel.stopTracking();
        uploadProgressPanel.reset();
        cardLayout.show(cardPanel, CARD_SUBMISSION);
    }

    private void onClose() {
        // Return to submission panel
        uploadProgressPanel.stopTracking();
        uploadProgressPanel.reset();
        cardLayout.show(cardPanel, CARD_SUBMISSION);

        // Clear form
        submissionNameField.setText("");
        submissionCommentArea.setText("");
        bookmarkSelectionPanel.clearSelection();
        uploadFilesCheckBox.setSelected(false);
    }

    /**
     * Updates the bookmark list with bookmarks from the IPED case.
     * @param bookmarks Set of bookmark names from the case
     */
    public void setBookmarks(Set<String> bookmarks) {
        if (ipedSource != null) {
            bookmarkSelectionPanel.setMultiBookmarks(ipedSource.getMultiBookmarks());
        }
        bookmarkSelectionPanel.setBookmarks(bookmarks);
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
