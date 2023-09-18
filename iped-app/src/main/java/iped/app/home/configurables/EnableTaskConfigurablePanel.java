package iped.app.home.configurables;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;

import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SpringLayout;

import iped.app.home.MainFrame;
import iped.engine.config.EnableTaskProperty;
import iped.utils.UTF8Properties;

public class EnableTaskConfigurablePanel extends ConfigurablePanel {

    private JTextField textField;

    protected EnableTaskConfigurablePanel(EnableTaskProperty configurable, MainFrame mainFrame) {
        super(configurable, mainFrame);
    }

    @Override
    public void createConfigurableGUI() {
        UTF8Properties config = (UTF8Properties) configurable.getConfiguration();
        if(config!=null) {
            Set<Entry<Object, Object>> es = config.entrySet();
            for (Iterator<Entry<Object, Object>> iterator = es.iterator(); iterator.hasNext();) {
                Entry<Object, Object> e = iterator.next();
                if(e.getKey().equals(((EnableTaskProperty)configurable).getPropertyName())) {
                    JLabel label = new JLabel(e.getKey() +":");
                    layout.putConstraint(SpringLayout.NORTH, label, 15, SpringLayout.NORTH, this);
                    this.add(label);
                    Object o=e.getValue();
                    textField = new JTextField();
                    if(o!=null) {
                        textField.setText(o.toString());                    
                    }
                    textField.getDocument().addDocumentListener(this);
                    layout.putConstraint(SpringLayout.VERTICAL_CENTER, textField, 0, SpringLayout.VERTICAL_CENTER, label);
                    layout.putConstraint(SpringLayout.WEST, textField, 5, SpringLayout.EAST, label);
                    this.add(textField);
                }
            }
        }

    }

    @Override
    public void applyChanges() {
        UTF8Properties config = (UTF8Properties) configurable.getConfiguration();
        if(config!=null) {
            Set<Entry<Object, Object>> es = config.entrySet();
            for (Iterator<Entry<Object, Object>> iterator = es.iterator(); iterator.hasNext();) {
                Entry<Object, Object> e = iterator.next();
                if(e.getKey().equals(((EnableTaskProperty)configurable).getPropertyName())) {
                    config.setProperty(e.getKey().toString(), textField.getText());                
                }
            }
        }
    }

}
