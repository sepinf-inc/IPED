package iped.app.home.configurables;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Iterator;

import javax.swing.AbstractSpinnerModel;
import javax.swing.JFormattedTextField;
import javax.swing.JFormattedTextField.AbstractFormatter;
import javax.swing.JFormattedTextField.AbstractFormatterFactory;
import javax.swing.JSpinner;
import javax.swing.JSpinner.DefaultEditor;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class AutoCalcSpinnerModel extends AbstractSpinnerModel {
    int value;
    JSpinner spinner;
    ArrayList<ChangeListener> listeners = new ArrayList<ChangeListener>();
    private boolean isInternalChage;

    public AutoCalcSpinnerModel(JSpinner spinner) {
        this.spinner = spinner;
        DefaultEditor c = new DefaultEditor(spinner);
        c.getTextField().setEditable(true);
        c.getTextField().setEnabled(true);
        c.setEnabled(true);
        c.getTextField().setFormatterFactory(new AbstractFormatterFactory() {
            @Override
            public AbstractFormatter getFormatter(JFormattedTextField tf) {
                // TODO Auto-generated method stub
                return new AbstractFormatter() {
                    @Override
                    public String valueToString(Object value) throws ParseException {
                        return value.toString();
                    }

                    @Override
                    public Object stringToValue(String text) throws ParseException {
                        if (text.equals("0") || text.trim().equals("")) {
                            return "auto";
                        }
                        return text;
                    }
                };
            }
        });
        // spinner.setEditor();
        spinner.setEditor(c);
    }

    @Override
    public Object getValue() {
        if (value == 0) {
            return "auto";
        } else {
            return value;
        }
    }

    @Override
    public void setValue(Object value) {
        boolean valid = true;
        if (value.toString().equals("auto")) {
            this.value = 0;
        } else {
            if (value instanceof String) {
                try {
                    this.value = Integer.parseInt((String) value);
                } catch (Exception e) {
                    valid = false;
                }
            } else {
                this.value = (Integer) value;
            }
        }
        if (!isInternalChage) {
            DefaultEditor c = (DefaultEditor) spinner.getEditor();
            isInternalChage = true;
            c.getTextField().setValue(getValue());
            isInternalChage = false;
            if (valid) {
                for (Iterator iterator = listeners.iterator(); iterator.hasNext();) {
                    ChangeListener changeListener = (ChangeListener) iterator.next();
                    changeListener.stateChanged(new ChangeEvent(spinner));
                }
            }
        }
    }

    @Override
    public Object getNextValue() {
        return value + 1;
    }

    @Override
    public Object getPreviousValue() {
        if (value > 0) {
            return value - 1;
        }
        return 0;
    }

    @Override
    public void addChangeListener(ChangeListener l) {
        listeners.add(l);
    }

    @Override
    public void removeChangeListener(ChangeListener l) {
        listeners.remove(l);
    }

}
