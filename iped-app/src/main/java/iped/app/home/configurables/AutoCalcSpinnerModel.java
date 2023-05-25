package iped.app.home.configurables;

import java.awt.Component;

import javax.swing.AbstractSpinnerModel;
import javax.swing.JSpinner;
import javax.swing.JSpinner.DefaultEditor;
import javax.swing.event.ChangeListener;

public class AutoCalcSpinnerModel extends AbstractSpinnerModel {
    int value;
    JSpinner spinner;
    
    public AutoCalcSpinnerModel(JSpinner spinner) {
        this.spinner = spinner;
    }
    
    @Override
    public Object getValue() {
        if(value==0) {
            return "auto";
        }else {
            return value;
        }
    }

    @Override
    public void setValue(Object value) {
        if(value.toString().equals("auto")) {
            this.value = 0;
        }else {
            this.value = (Integer)value;
        }
        DefaultEditor c = (DefaultEditor) spinner.getEditor();
        c.getTextField().setValue(getValue());
    }

    @Override
    public Object getNextValue() {
        return value+1;
    }

    @Override
    public Object getPreviousValue() {
        if(value>0) {
            return value-1;
        }
        return 0;
    }

    @Override
    public void addChangeListener(ChangeListener l) {
    }

    @Override
    public void removeChangeListener(ChangeListener l) {
    }

}
