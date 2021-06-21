package dpf.sp.gpinf.indexer.ui.controls;

import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

import javax.swing.JTextField;

public class HintTextField extends JTextField {
    private static final long serialVersionUID = 6618073426383888695L;
    private final String hint;

    public HintTextField(String hint) {
        super();
        this.hint = hint;
        addFocusListener(new FocusListener() {
            public void focusLost(FocusEvent e) {
                String text = getText().trim();
                if (text.isEmpty() || text.equals(hint)) {
                    setText(hint);
                    setForeground(getDisabledTextColor());
                }
            }

            public void focusGained(FocusEvent e) {
                String text = getText().trim();
                if (text.equals(hint))
                    setText("");
                setForeground(null);
            }
        });
        setText(hint);
        setForeground(getDisabledTextColor());
    }

    public void updateUI() {
        super.updateUI();
        String text = getText().trim();
        setForeground(text.equals(hint) ? getDisabledTextColor() : null);
    }
}
