package iped.app.home.newcase.tabs.process;

import java.awt.Component;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.Map.Entry;

import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SpringLayout;

import iped.app.home.MainFrame;
import iped.configuration.Configurable;
import iped.utils.UTF8Properties;

public class UTF8PropertiesConfigurablePanel extends ConfigurablePanel {
    HashMap<Object, JTextField> textFields = new HashMap<Object, JTextField>(); 

    public UTF8PropertiesConfigurablePanel(Configurable<UTF8Properties> configurable, MainFrame mainFrame) {
        super(configurable, mainFrame);
    }

    public void createConfigurableGUI() {
        Component lastLabel = null;
        ArrayList<Component> comps = new ArrayList<Component>();
        JLabel largestLabel = null;
        int max=0;
        
        UTF8Properties config = (UTF8Properties) configurable.getConfiguration();
        if(config!=null) {
            Set<Entry<Object, Object>> es = config.entrySet();
            for (Iterator<Entry<Object, Object>> iterator = es.iterator(); iterator.hasNext();) {
                Entry<Object, Object> e = iterator.next();                    
                JLabel label = new JLabel(e.getKey() +":");
                int width = e.getKey().toString().length();
                if(max<width) {
                    max=width;
                    largestLabel=label;
                }
                comps.add(label);
                if(lastLabel!=null) {
                    layout.putConstraint(SpringLayout.NORTH, label, 15, SpringLayout.SOUTH, lastLabel);
                }
                Object o=e.getValue();
                JTextField textField = new JTextField();
                if(o!=null) {
                    textField.setText(o.toString());                    
                }
                if(lastLabel!=null) {
                    layout.putConstraint(SpringLayout.NORTH, textField, 15, SpringLayout.SOUTH, lastLabel);
                    layout.putConstraint(SpringLayout.NORTH, textField, 15, SpringLayout.SOUTH, lastLabel);
                }
                textField.getDocument().addDocumentListener(this);
                comps.add(textField);
                textFields.put(e.getKey(), textField);
                lastLabel=label;
            }
            for (Iterator iterator = comps.iterator(); iterator.hasNext();) {
                Component component = (Component) iterator.next();
                if(component!=largestLabel) {
                    if(component instanceof JLabel) {
                        layout.putConstraint(SpringLayout.EAST, component, 0, SpringLayout.EAST, largestLabel);
                    }else {
                        layout.putConstraint(SpringLayout.WEST, component, 5,SpringLayout.EAST, largestLabel);
                    }
                }
                this.add(component);
            }
        }else {
            System.out.print("null");
        }
    }

    @Override
    public void applyChanges() {
        UTF8Properties config = (UTF8Properties) configurable.getConfiguration();
        if(config!=null) {
            Set<Entry<Object, Object>> es = config.entrySet();
            for (Iterator<Entry<Object, Object>> iterator = es.iterator(); iterator.hasNext();) {
                Entry<Object, Object> e = iterator.next();
                JTextField textField = textFields.get(e.getKey());
                config.setProperty(e.getKey().toString(), textField.getText());                
            }
        }
    }

}
