package iped.app.ui.bookmarks;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.DocumentFilter;
import javax.swing.text.PlainDocument;

import iped.app.ui.App;
import iped.app.ui.BookmarkTree;
import iped.app.ui.IconManager;
import iped.app.ui.Messages;
import iped.engine.data.Bookmarks;

/**
 * Custom text field for entering bookmark names with hierarchy support.
 */
public class BookmarkNameField extends JPanel {
    private static final long serialVersionUID = 1L;

    private static final String ROOT_NAME = Messages.getString("BookmarksTreeModel.RootName");
    private static final Icon rootIcon = IconManager.getTreeIcon("bookmarks-root");
    
    private GhostTextField textField;
    private JLabel pathPreview;
    private String parentPath;
    
    public BookmarkNameField() {
        parentPath = null;
        setupUI();
    }
    
    private void setupUI() {
        setLayout(new BorderLayout(2, 0));

        textField = new GhostTextField(Messages.getString("BookmarksManager.NewBookmark.GhostText"));
        textField.setToolTipText(Messages.getString("BookmarksManager.NewBookmark.Tip"));
        PlainDocument doc = (PlainDocument) textField.getDocument();
        doc.setDocumentFilter(new SequenceFilter(Bookmarks.PATH_SEPARATOR_DISPLAY));

        pathPreview = new JLabel(" ");
        pathPreview.setToolTipText(Messages.getString("BookmarksManager.CopyName.Tip"));
        pathPreview.setBorder(BorderFactory.createEmptyBorder(3, 5, 3, 5));
                
        add(textField, BorderLayout.NORTH);
        add(pathPreview, BorderLayout.CENTER);
        
        // Keyboard shortcuts
        textField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && e.isControlDown()) {
                    insertSeparator();
                    e.consume();
                }
            }
        });
            
        // Mouse listener for bookmark name/path copy
        pathPreview.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                // Check for Ctrl + Left Click
                if (e.isControlDown() && javax.swing.SwingUtilities.isLeftMouseButton(e)) {
                    if (parentPath == null || parentPath.isEmpty() || Bookmarks.PATH_SEPARATOR.equals(parentPath))
                        return;

                    String textToCopy = "";

                    if (e.getClickCount() == 1) {
                        // Single Click: Copy bookmark name
                        textToCopy = BookmarkTree.getNameFromPath(parentPath);
                    }
                    else if (e.getClickCount() == 2) {
                        // Double Click: Copy formatted bookmark full path
                        textToCopy = BookmarkTree.displayPath(parentPath);
                    }

                    if (!textToCopy.isEmpty()) {
                        StringSelection selection = new StringSelection(textToCopy);
                        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                        clipboard.setContents(selection, null);
                    }
                }
            }
        });
    
        // Update preview as user types
        textField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { updatePreview(); }
            public void removeUpdate(DocumentEvent e) { updatePreview(); }
            public void changedUpdate(DocumentEvent e) { updatePreview(); }
        });
    }
    
    /**
     * Inserts the path separator into the text field (with spaces for better presentation)
     */
    private void insertSeparator() {
        String currentText = textField.getText();
        
        // Get selection bounds
        int selectionStart = textField.getSelectionStart();
        int selectionEnd = textField.getSelectionEnd();
        
        // Build new text
        String newText;
        int newCaretPos;
        
        if (selectionStart != selectionEnd) {
            // Text is selected - replace selection with separator
            newText = currentText.substring(0, selectionStart) + 
                    Bookmarks.PATH_SEPARATOR_DISPLAY_FORMATTED + 
                    currentText.substring(selectionEnd);
            newCaretPos = selectionStart + Bookmarks.PATH_SEPARATOR_DISPLAY_FORMATTED.length();
        } else {
            // No selection - insert at caret position
            int caretPos = textField.getCaretPosition();
            newText = currentText.substring(0, caretPos) + 
                    Bookmarks.PATH_SEPARATOR_DISPLAY_FORMATTED + 
                    currentText.substring(caretPos);
            newCaretPos = caretPos + Bookmarks.PATH_SEPARATOR_DISPLAY_FORMATTED.length();
        }
        
        textField.setText(newText);
        textField.setCaretPosition(newCaretPos);
        textField.requestFocus();
    }

    /**
     * Sets the parent path context for relative paths
     */
    public void setParentPath(String parent) {
        this.parentPath = parent;
        updatePreview();
    }
    
    /**
     * Updates the preview label to show the full path with context
     */
    private void updatePreview() {               
        // Determine the context where the new bookmark will be created
        // Check if context is root
        boolean rootContext = parentPath == null || parentPath.isEmpty() || Bookmarks.PATH_SEPARATOR_DISPLAY.equals(parentPath) || isAbsolutePath();
        
        // Minor label height adjustment
        int minHeight = rootIcon.getIconHeight();
        Dimension prefSize = pathPreview.getPreferredSize();
        pathPreview.setPreferredSize(new Dimension(prefSize.width, Math.max(prefSize.height, minHeight)));

        // Set current context preview
        if (rootContext) {
            pathPreview.setText(ROOT_NAME);
            pathPreview.setIcon(rootIcon);
        } else {
            pathPreview.setText(BookmarkTree.getNameFromPath(parentPath));
            pathPreview.setIcon(BookmarkIcon.getIcon(App.get().appCase.getMultiBookmarks().getBookmarkColor(parentPath)));
        }
    }
    
    /**
     * Gets the full bookmark path from the text field
     */
    public String getFullPath() {
        return textField.getText().trim();
    }
    
    /**
     * Sets the text of the text field
     */
    public void setText(String text) {
        textField.setText(text != null ? text : "");
        updatePreview();
    }
    
    /**
     * Gets the current text of the text field
     */
    public String getText() {
        return textField.getText().trim();
    }
    
    /**
     * Clears the text of the text field
     */
    public void clear() {
        textField.setText("");
        updatePreview();
    }
    
    /**
     * Checks if the text field is empty
     */
    public boolean isEmpty() {
        return textField.getText().trim().isEmpty();
    }
    
    /**
     * Checks if the text of the text field is is an absolute path
     */
    public boolean isAbsolutePath() {
        String text = textField.getText().trim();
        return text.startsWith(Bookmarks.PATH_SEPARATOR);
    }
    
    @Override
    public void requestFocus() {
        textField.requestFocus();
    }
    
    @Override
    public boolean requestFocusInWindow() {
        return textField.requestFocusInWindow();
    }
    
    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        textField.setEnabled(enabled);
    }
    
    /**
     * Adds a DocumentListener to the underlying text field
     */
    public void addDocumentListener(DocumentListener listener) {
        textField.getDocument().addDocumentListener(listener);
    }
    
    /**
     * Adds a FocusListener to the underlying text field
     */
    public void addFocusListener(java.awt.event.FocusListener listener) {
        textField.addFocusListener(listener);
    }
    
    /**
     * Selects all text in the text field
     */
    public void selectAll() {
        textField.selectAll();
    }
}

class GhostTextField extends JTextField {
    private final String ghostText;

    public GhostTextField(String ghostText) {
        this.ghostText = ghostText;
        this.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                repaint();
            }
            @Override
            public void focusLost(FocusEvent e) {
                repaint();
            }
        });
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // If the field only contains spaces, make it empty
        if (!getText().isEmpty() && getText().trim().isEmpty())
            setText("");

        // If the field is empty, draw the ghost text
        if (getText().isEmpty()) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Color.GRAY);
            g2.setFont(getFont().deriveFont(Font.ITALIC));

            // Adjust the coordinates to match the current font's baseline
            Insets insets = getInsets();
            FontMetrics fm = g2.getFontMetrics();
            int x = insets.left;
            int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            
            g2.drawString(ghostText, x, y);
            g2.dispose();
        }
    }
}

class SequenceFilter extends DocumentFilter {
    private final String sequence;

    public SequenceFilter(String sequence) {
        this.sequence = sequence;
    }

    @Override
    public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
        handleDeletion(fb, offset, length);
    }

    @Override
    public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
        // If length > 0, it's a deletion/overwrite. Handle it.
        if (length > 0) {
            handleDeletion(fb, offset, length);
            // After deletion, insert the new text at the new adjusted position
            fb.insertString(offset, text, attrs); 
        } else {
            super.replace(fb, offset, length, text, attrs);
        }
    }

    private void handleDeletion(FilterBypass fb, int offset, int length) throws BadLocationException {
        Document doc = fb.getDocument();
        String fullText = doc.getText(0, doc.getLength());
        
        int finalOffset = offset;
        int finalLength = length;

        // Find all occurrences of the target sequence
        int index = fullText.indexOf(sequence);
        while (index != -1) {
            int targetStart = index;
            int targetEnd = index + sequence.length();
            int deletionEnd = offset + length;

            // Check for overlap: does the deletion range touch the target?
            if (offset < targetEnd && deletionEnd > targetStart) {
                // Expand the range to encompass the full target
                finalOffset = Math.min(finalOffset, targetStart);
                int newEnd = Math.max(deletionEnd, targetEnd);
                finalLength = newEnd - finalOffset;
            }
            index = fullText.indexOf(sequence, index + 1);
        }

        super.remove(fb, finalOffset, finalLength);
    }
}