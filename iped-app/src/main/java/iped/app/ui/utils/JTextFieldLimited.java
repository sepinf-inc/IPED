package iped.app.ui.utils;

import javax.swing.JTextField;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;

public class JTextFieldLimited extends JTextField {

    private static final long serialVersionUID = -4312467193947299492L;

    public void setLimit(int limit) {
        setDocument(new PlainDocument() {
            private static final long serialVersionUID = -672527266979603682L;

            public void insertString(int offset, String str, AttributeSet attr) throws BadLocationException {
                if (str != null && (getLength() + str.length()) <= limit) {
                    super.insertString(offset, str, attr);
                }
            }
        });
    }
}
