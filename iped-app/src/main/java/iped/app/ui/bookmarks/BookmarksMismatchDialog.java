package iped.app.ui.bookmarks;

import javax.swing.*;
import javax.swing.border.EmptyBorder;

import iped.app.ui.Messages;
import iped.data.IBookmarks;
import iped.data.IIPEDSource;
import iped.engine.data.IPEDSource;

import java.awt.*;
import java.util.List;

public class BookmarksMismatchDialog extends JDialog {

    public BookmarksMismatchDialog(JDialog parent, String bookmarkName, List<IPEDSource> sources) {
        super(parent, ModalityType.APPLICATION_MODAL);
        setTitle(Messages.getString("BookmarksManager.Mismatch.Title"));
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);

        setLayout(new BorderLayout());
        
        // Header panel
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        headerPanel.setBorder(new EmptyBorder(10, 10, 0, 10));
        JLabel headerLabel = new JLabel(Messages.getString("BookmarksManager.Bookmark") + ": " + bookmarkName);
        headerPanel.add(headerLabel);
        add(headerPanel, BorderLayout.NORTH);
        
        // Container for all case panels
        JPanel mainList = new JPanel();
        mainList.setLayout(new BoxLayout(mainList, BoxLayout.Y_AXIS));
        mainList.setBorder(new EmptyBorder(10, 10, 10, 10));
        // mainList.add(Box.createVerticalStrut(10));

        for (IIPEDSource source : sources) {
            if (!(source instanceof IPEDSource))
                continue;

            IBookmarks bookmarks = source.getBookmarks();

            // Get bookmark ID for the bookmark in this case
            int bookmarkId = bookmarks.getBookmarkId(bookmarkName);
            if (bookmarkId == -1) {
                continue; // the bookmark does not exist in this case
            }

            IPEDSource ipedCase = (IPEDSource) source;
            int caseId = ipedCase.getSourceId() + 1;
            String casePath = ipedCase.getCaseDir().getAbsolutePath();
            String caseComments = bookmarks.getBookmarkComment(bookmarkId);
            String caseQuery = bookmarks.getBookmarkQuery(bookmarkId);

            // Case Panel
            JPanel casePanel = new JPanel(new BorderLayout(5, 5));
            casePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            JPanel infoPanel = new JPanel(new GridLayout(2, 1));

            // Case ID Label
            infoPanel.add(new JLabel(Messages.getString("App.Case") + ": " + caseId));
            
            // Case Path TextField
            JTextField pathTextField = new JTextField(casePath);
            pathTextField.setToolTipText(Messages.getString("BookmarksManager.NewBookmark.Tip"));
            pathTextField.setEditable(false);
            // pathTextField.setBorder(null);
            pathTextField.setFont(new Font("Monospaced", Font.PLAIN, 11));
            infoPanel.add(pathTextField);

            // Comments TextArea
            JTextArea commentsArea = new JTextArea(caseComments != null ? caseComments : "");
            commentsArea.setRows(2);
            commentsArea.setLineWrap(true);
            commentsArea.setWrapStyleWord(true);
            commentsArea.setEditable(false);
            commentsArea.setToolTipText(Messages.getString("BookmarksManager.CommentsTooltip")); //$NON-NLS-1$
            JScrollPane commentsScroll = new JScrollPane(commentsArea);
            commentsScroll.setPreferredSize(new Dimension(300, 50));

            // Query TextArea
            JTextArea queryArea = new JTextArea(caseQuery != null ? caseQuery : "");
            queryArea.setRows(2);
            queryArea.setLineWrap(true);
            queryArea.setWrapStyleWord(true);
            queryArea.setEditable(false);
            queryArea.setToolTipText(Messages.getString("BookmarksManager.QueryTooltip")); //$NON-NLS-1$
            JScrollPane queryScroll = new JScrollPane(queryArea);
            queryScroll.setPreferredSize(new Dimension(300, 50));
            queryArea.setForeground(Color.RED);

            casePanel.add(infoPanel, BorderLayout.NORTH);
            casePanel.add(commentsScroll, BorderLayout.CENTER);
            casePanel.add(queryScroll, BorderLayout.SOUTH);
            mainList.add(casePanel);
            mainList.add(Box.createVerticalStrut(15));
        }

        JScrollPane mainScroll = new JScrollPane(mainList);
        mainScroll.getVerticalScrollBar().setUnitIncrement(16);
        
        add(mainScroll, BorderLayout.CENTER);
        
        JButton closeBtn = new JButton(Messages.getString("Ok"));
        closeBtn.addActionListener(e -> dispose());
        JPanel btnPanel = new JPanel();
        btnPanel.add(closeBtn);
        add(btnPanel, BorderLayout.SOUTH);

        pack();
        setSize(getWidth() + 20, 510);
        setLocationRelativeTo(parent);
    }
}
