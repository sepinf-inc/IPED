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
                String text = HintTextField.super.getText().trim();
                if (text.isEmpty() || text.equals(hint)) {
                    HintTextField.super.setText(hint);
                    setForeground(getDisabledTextColor());
                }
            }

            public void focusGained(FocusEvent e) {
                String text = HintTextField.super.getText().trim();
                if (text.equals(hint))
                    HintTextField.super.setText("");
                setForeground(null);
            }
        });
        setText("");
    }

    @Override
    public void updateUI() {
        super.updateUI();
        String text = super.getText();
        setForeground(text.equals(hint) ? getDisabledTextColor() : null);
    }

    public void setText(String text) {
        if (text == null || text.isEmpty())
            text = hint;
        super.setText(text);
        setForeground(text.equals(hint) ? getDisabledTextColor() : null);
    }

    public String getText() {
        String text = super.getText();
        return text.equals(hint) ? "" : text;
    }
}
