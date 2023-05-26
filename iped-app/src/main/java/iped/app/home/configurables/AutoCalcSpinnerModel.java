package iped.app.home.configurables;

import java.awt.Component;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyVetoException;
import java.beans.VetoableChangeListener;
import java.util.ArrayList;
import java.util.Iterator;

import javax.swing.AbstractSpinnerModel;
import javax.swing.JDialog;
import javax.swing.JSpinner;
import javax.swing.JSpinner.DefaultEditor;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class AutoCalcSpinnerModel extends AbstractSpinnerModel {
    int value;
    JSpinner spinner;
    ArrayList<ChangeListener> listeners = new ArrayList<ChangeListener>(); 
    
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

        for (Iterator iterator = listeners.iterator(); iterator.hasNext();) {
            ChangeListener changeListener = (ChangeListener) iterator.next();
            changeListener.stateChanged(new ChangeEvent(spinner));
        }
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
        listeners.add(l);
    }

    @Override
    public void removeChangeListener(ChangeListener l) {
        listeners.remove(l);
    }

}
