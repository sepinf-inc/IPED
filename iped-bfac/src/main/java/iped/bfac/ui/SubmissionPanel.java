package iped.bfac.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

import iped.bfac.api.Category;
import iped.bfac.api.Submission;
import iped.bfac.localization.Messages;
import iped.data.IIPEDSource;
import iped.data.IMultiBookmarks;

/**
 * Submission card for BFAC: bookmarks, submission form, and create/upload action.
 */
public class SubmissionPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    public static class SubmissionInput {
        public boolean newSubmission;
        public int submissionId;
        public String name;
        public String categoryName;
        public String comment;
        public Set<String> bookmarks;
        public boolean uploadFiles;
    }

    public interface Listener {
        void onLogout();
        void onRefreshSubmissions();
        void onCreateSubmission(SubmissionInput input);
    }

    private final BookmarkSelectionPanel bookmarkSelectionPanel;
    private final JRadioButton newSubmissionRadio;
    private final JRadioButton existingSubmissionRadio;
    private final JComboBox<Submission> existingSubmissionComboBox;
    private final JButton refreshSubmissionsButton;
    private final JTextField submissionNameField;
    private final JTextArea submissionCommentArea;
    private final JComboBox<Category> categoryComboBox;
    private final JCheckBox uploadFilesCheckBox;
    private final JButton createSubmissionButton;
    private final JLabel userInfoLabel;

    private Listener listener;

    public SubmissionPanel() {
        super(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel topPanel = new JPanel(new BorderLayout());
        userInfoLabel = new JLabel(Messages.getString("BfacDialog.LoggedInAs",
                Messages.getString("BfacDialog.DefaultUser")));
        userInfoLabel.setFont(new Font("SansSerif", Font.ITALIC, 12));
        topPanel.add(userInfoLabel, BorderLayout.WEST);

        JButton logoutButton = new JButton(Messages.getString("BfacDialog.Logout"));
        logoutButton.addActionListener(e -> {
            if (listener != null) {
                listener.onLogout();
            }
        });
        topPanel.add(logoutButton, BorderLayout.EAST);
        add(topPanel, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));

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

        JPanel detailsPanel = new JPanel(new GridBagLayout());
        detailsPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                Messages.getString("BfacDialog.Submission"),
                TitledBorder.LEFT,
                TitledBorder.TOP));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

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

        JPanel existingPanel = new JPanel(new BorderLayout(5, 0));
        existingSubmissionComboBox = new JComboBox<>();
        existingSubmissionComboBox.setEnabled(false);
        existingSubmissionComboBox.addActionListener(e -> syncCategoryWithSelectedSubmission());
        existingPanel.add(existingSubmissionComboBox, BorderLayout.CENTER);

        refreshSubmissionsButton = new JButton("\u21BB");
        refreshSubmissionsButton.setToolTipText(Messages.getString("BfacDialog.RefreshSubmissions.tooltip"));
        refreshSubmissionsButton.setEnabled(false);
        refreshSubmissionsButton.addActionListener(e -> {
            if (listener != null) {
                listener.onRefreshSubmissions();
            }
        });
        existingPanel.add(refreshSubmissionsButton, BorderLayout.EAST);

        gbc.gridy = 2;
        gbc.insets = new Insets(0, 25, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        detailsPanel.add(existingPanel, gbc);
        gbc.weightx = 0;

        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.gridwidth = 1;

        gbc.gridx = 0;
        gbc.gridy = 3;
        detailsPanel.add(new JLabel(Messages.getString("BfacDialog.Name")), gbc);

        submissionNameField = new JTextField(30);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        detailsPanel.add(submissionNameField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        detailsPanel.add(new JLabel(Messages.getString("BfacDialog.Category")), gbc);

        categoryComboBox = new JComboBox<>();
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        detailsPanel.add(categoryComboBox, gbc);

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

        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weighty = 0;
        gbc.anchor = GridBagConstraints.WEST;
        uploadFilesCheckBox = new JCheckBox(Messages.getString("BfacDialog.UploadFiles"), true);
        detailsPanel.add(uploadFilesCheckBox, gbc);

        centerPanel.add(detailsPanel);
        add(centerPanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        createSubmissionButton = new JButton(Messages.getString("BfacDialog.CreateSubmissionAndUpload"));
        createSubmissionButton.addActionListener(e -> onCreateSubmission());
        bottomPanel.add(createSubmissionButton);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setLoggedInUser(String username) {
        userInfoLabel.setText(Messages.getString("BfacDialog.LoggedInAs", username));
    }

    public void setCategories(List<Category> categories) {
        categoryComboBox.removeAllItems();
        for (Category category : categories) {
            categoryComboBox.addItem(category);
        }
    }

    public void setSubmissions(List<Submission> submissions) {
        existingSubmissionComboBox.removeAllItems();
        for (Submission submission : submissions) {
            existingSubmissionComboBox.addItem(submission);
        }
        if (submissions.isEmpty()) {
            newSubmissionRadio.setSelected(true);
            onSubmissionModeChanged();
        } else {
            syncCategoryWithSelectedSubmission();
        }
    }

    public void setSubmissionsLoading(boolean loading) {
        refreshSubmissionsButton.setEnabled(!loading && existingSubmissionRadio.isSelected());
        existingSubmissionComboBox.setEnabled(!loading && existingSubmissionRadio.isSelected());
    }

    public void setIPEDSource(IIPEDSource ipedSource) {
        bookmarkSelectionPanel.setMultiBookmarks(
                ipedSource != null ? ipedSource.getMultiBookmarks() : null);
    }

    public void setBookmarks(Set<String> bookmarks) {
        bookmarkSelectionPanel.setBookmarks(bookmarks);
    }

    public void setMultiBookmarks(IMultiBookmarks multiBookmarks) {
        bookmarkSelectionPanel.setMultiBookmarks(multiBookmarks);
    }

    public void resetForm() {
        submissionNameField.setText("");
        submissionCommentArea.setText("");
        bookmarkSelectionPanel.clearSelection();
        uploadFilesCheckBox.setSelected(false);
    }

    private void onSubmissionModeChanged() {
        boolean isNewSubmission = newSubmissionRadio.isSelected();

        existingSubmissionComboBox.setEnabled(!isNewSubmission);
        refreshSubmissionsButton.setEnabled(!isNewSubmission);
        submissionNameField.setEnabled(isNewSubmission);
        categoryComboBox.setEnabled(isNewSubmission);
        submissionCommentArea.setEnabled(isNewSubmission);

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

    private void onCreateSubmission() {
        List<String> selectedBookmarks = bookmarkSelectionPanel.getCheckedBookmarks();

        if (selectedBookmarks.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    Messages.getString("BfacDialog.NoBookmarksSelected"),
                    Messages.getString("BfacDialog.NoBookmarksSelectedTitle"),
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        SubmissionInput input = new SubmissionInput();
        input.bookmarks = new HashSet<>(selectedBookmarks);
        input.uploadFiles = uploadFilesCheckBox.isSelected();
        input.newSubmission = newSubmissionRadio.isSelected();

        if (input.newSubmission) {
            input.name = submissionNameField.getText().trim();
            if (input.name.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        Messages.getString("BfacDialog.NameRequired"),
                        Messages.getString("BfacDialog.NameRequiredTitle"),
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            Category selectedCategory = (Category) categoryComboBox.getSelectedItem();
            if (selectedCategory == null) {
                JOptionPane.showMessageDialog(this,
                        Messages.getString("BfacDialog.CategoryRequired"),
                        Messages.getString("BfacDialog.CategoryRequiredTitle"),
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            input.categoryName = selectedCategory.getName();
            input.comment = submissionCommentArea.getText().trim();
        } else {
            Submission selectedSubmission = (Submission) existingSubmissionComboBox.getSelectedItem();
            if (selectedSubmission == null) {
                JOptionPane.showMessageDialog(this,
                        Messages.getString("BfacDialog.SubmissionRequired"),
                        Messages.getString("BfacDialog.SubmissionRequiredTitle"),
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            input.submissionId = selectedSubmission.getId();
            input.name = selectedSubmission.getName();
            input.categoryName = selectedSubmission.getCategoryName();
            input.comment = null;
        }

        if (listener != null) {
            listener.onCreateSubmission(input);
        }
    }
}
