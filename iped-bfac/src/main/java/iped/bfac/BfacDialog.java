package iped.bfac;

import java.awt.CardLayout;
import java.awt.Frame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.Set;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

import iped.bfac.api.BfacApiClient;
import iped.bfac.api.Category;
import iped.bfac.api.Submission;
import iped.bfac.localization.Messages;
import iped.bfac.ui.LoginPanel;
import iped.bfac.ui.SubmissionPanel;
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

    private static BfacDialog instance;
    private JFrame parentFrame;

    private CardLayout cardLayout;
    private JPanel cardPanel;

    private BfacApiClient apiClient;
    private BfacBackendSync backendSync;
    private IIPEDSource ipedSource;

    private SubmissionWorker currentWorker;
    private boolean connectionErrorShown;

    private LoginPanel loginPanel;
    private SubmissionPanel submissionPanel;
    private UploadProgressPanel uploadProgressPanel;

    private BfacDialog(JFrame parent) {
        super(parent, Messages.getString("BfacDialog.Title"), false);
        this.parentFrame = parent;
        this.apiClient = new BfacApiClient();
        initComponents();
        setSize(600, 550);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleWindowClosing();
            }
        });
    }

    public static synchronized BfacDialog getInstance(JFrame parent) {
        if (instance == null || !instance.isDisplayable()) {
            instance = new BfacDialog(parent);
        }
        return instance;
    }

    public void showDialog() {
        if (parentFrame != null && parentFrame.getState() == Frame.ICONIFIED) {
            parentFrame.setState(Frame.NORMAL);
        }
        checkStoredSessionOnOpen();
        setVisible(true);
        toFront();
        requestFocus();
    }

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
        } else {
            closeAndCleanup();
        }
    }

    public boolean isUploadInProgress() {
        return currentWorker != null && !currentWorker.isDone();
    }

    public static boolean hasActiveUpload() {
        return instance != null && instance.isUploadInProgress();
    }

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

    private void closeAndCleanup() {
        instance = null;
        dispose();
    }

    public void setIPEDSource(IIPEDSource ipedSource) {
        this.ipedSource = ipedSource;
        if (submissionPanel != null) {
            submissionPanel.setIPEDSource(ipedSource);
        }
    }

    public void setBookmarks(Set<String> bookmarks) {
        if (submissionPanel != null) {
            if (ipedSource != null) {
                submissionPanel.setMultiBookmarks(ipedSource.getMultiBookmarks());
            }
            submissionPanel.setBookmarks(bookmarks);
        }
    }

    private void checkStoredSessionOnOpen() {
        String storedUsername = apiClient.getStoredUsername();
        if (storedUsername != null && !storedUsername.isEmpty()) {
            loginPanel.setUsername(storedUsername);
        }

        if (apiClient.hasStoredCredentials()) {
            apiClient.reloadStoredCredentials();
            submissionPanel.setLoggedInUser(storedUsername != null ? storedUsername
                    : Messages.getString("BfacDialog.DefaultUser"));
            cardLayout.show(cardPanel, CARD_SUBMISSION);
            backendSync.validateSession();
        } else {
            cardLayout.show(cardPanel, CARD_LOGIN);
        }
    }

    private void showLoginAfterSessionExpired(boolean showModalMessage) {
        apiClient.logout();

        String storedUsername = apiClient.getStoredUsername();
        if (storedUsername != null && !storedUsername.isEmpty()) {
            loginPanel.setUsername(storedUsername);
        }

        if (showModalMessage) {
            JOptionPane.showMessageDialog(
                    this,
                    Messages.getString("BfacDialog.SessionExpired"),
                    Messages.getString("BfacDialog.SessionExpiredTitle"),
                    JOptionPane.WARNING_MESSAGE);
        }

        loginPanel.showSessionExpiredMessage();
        cardLayout.show(cardPanel, CARD_LOGIN);
    }

    private void showLoginAfterSessionExpired() {
        showLoginAfterSessionExpired(false);
    }

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

    private void onLoginSuccess(String username) {
        submissionPanel.setLoggedInUser(username);
        backendSync.loadCategories();
        backendSync.loadOpenSubmissions();
        cardLayout.show(cardPanel, CARD_SUBMISSION);
    }

    private void onLogout() {
        apiClient.logout();
        loginPanel.clearPassword();
        loginPanel.resetStatus();
        cardLayout.show(cardPanel, CARD_LOGIN);
    }

    private void onRefreshSubmissions() {
        backendSync.loadOpenSubmissions();
    }

    private void onCreateSubmission(SubmissionPanel.SubmissionInput input) {
        cardLayout.show(cardPanel, CARD_PROGRESS);
        uploadProgressPanel.reset();
        uploadProgressPanel.startTracking();

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

        int maxConcurrentUploads = 5;
        iped.engine.config.BFACClientConfig bfacClientConfig = iped.engine.config.ConfigurationManager.get()
                .findObject(iped.engine.config.BFACClientConfig.class);
        if (bfacClientConfig != null) {
            maxConcurrentUploads = bfacClientConfig.getMaxConcurrentUploads();
        }

        currentWorker = new SubmissionWorker(
            apiClient,
            ipedSource,
            callback,
            input.newSubmission,
            input.submissionId,
            input.name,
            input.comment,
            input.categoryName,
            input.bookmarks,
            input.uploadFiles,
            maxConcurrentUploads
        );

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
        if (currentWorker != null && !currentWorker.isDone()) {
            currentWorker.cancelOperation();
            uploadProgressPanel.appendLog(Messages.getString("BfacDialog.CancellingOperation"));
            return;
        }

        uploadProgressPanel.stopTracking();
        uploadProgressPanel.reset();
        cardLayout.show(cardPanel, CARD_SUBMISSION);
    }

    private void onClose() {
        uploadProgressPanel.stopTracking();
        uploadProgressPanel.reset();
        cardLayout.show(cardPanel, CARD_SUBMISSION);
        submissionPanel.resetForm();
    }

    private void initComponents() {
        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);

        loginPanel = new LoginPanel(apiClient);
        loginPanel.setServerUrl(apiClient.getBaseUrl());
        loginPanel.setListener(this::onLoginSuccess);

        submissionPanel = new SubmissionPanel();
        submissionPanel.setListener(new SubmissionPanel.Listener() {
            @Override
            public void onLogout() {
                BfacDialog.this.onLogout();
            }

            @Override
            public void onRefreshSubmissions() {
                BfacDialog.this.onRefreshSubmissions();
            }

            @Override
            public void onCreateSubmission(SubmissionPanel.SubmissionInput input) {
                BfacDialog.this.onCreateSubmission(input);
            }
        });

        backendSync = new BfacBackendSync(apiClient);
        backendSync.setListener(new BfacBackendSync.Listener() {
            @Override
            public void onSessionValid() {
                // no-op; data loads are triggered from validateSession
            }

            @Override
            public void onSessionExpired() {
                showLoginAfterSessionExpired();
            }

            @Override
            public void onConnectionError(String detailMessage) {
                showBackendConnectionErrorAndClose(detailMessage);
            }

            @Override
            public void onCategoriesLoaded(List<Category> categories) {
                submissionPanel.setCategories(categories);
            }

            @Override
            public void onSubmissionsLoadStarted() {
                submissionPanel.setSubmissionsLoading(true);
            }

            @Override
            public void onSubmissionsLoadFinished() {
                submissionPanel.setSubmissionsLoading(false);
            }

            @Override
            public void onSubmissionsLoaded(List<Submission> submissions) {
                submissionPanel.setSubmissions(submissions);
            }
        });

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

        cardPanel.add(loginPanel, CARD_LOGIN);
        cardPanel.add(submissionPanel, CARD_SUBMISSION);
        cardPanel.add(uploadProgressPanel, CARD_PROGRESS);

        getContentPane().add(cardPanel);
        cardLayout.show(cardPanel, CARD_LOGIN);
    }

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
