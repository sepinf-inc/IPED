package iped.app.home.configurables;

import java.awt.Component;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.Iterator;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SpringLayout;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentEvent.ElementChange;
import javax.swing.event.DocumentEvent.EventType;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.AbstractDocument.DefaultDocumentEvent;

import iped.app.home.MainFrame;
import iped.configuration.Configurable;

public class BeanConfigurablePanel extends ConfigurablePanel{
    protected BeanConfigurablePanel(Configurable<?> configurable, MainFrame mainFrame) {
        super(configurable, mainFrame);
    }

    public void createConfigurableGUI() {
        try {
            Component lastLabel = null;
            ArrayList<Component> comps = new ArrayList<Component>();
            JLabel largestLabel = null;
            int max=0;

            Object config = configurable.getConfiguration();
            if(config!=null) {
                property:for (PropertyDescriptor pd : Introspector.getBeanInfo(config.getClass()).getPropertyDescriptors()) {
                    if(pd.getName().equals("class")) {
                        continue property;
                    }
                    JLabel label = new JLabel(pd.getDisplayName()+":");
                    int width = pd.getDisplayName().length();
                    if(max<width) {
                        max=width;
                        largestLabel=label;
                    }
                    comps.add(label);
                    if(lastLabel!=null) {
                        layout.putConstraint(SpringLayout.NORTH, label, 15, SpringLayout.SOUTH, lastLabel);
                    }

                    Object o=null;
                    try {
                        o = pd.getReadMethod().invoke(config);
                    }catch (Exception e) {
                        e.printStackTrace();
                    }
                    
                    JComponent uiField = null;
                    if(o!=null) {
                        if(o instanceof Boolean) {
                            JCheckBox checkField = new JCheckBox();
                            checkField.setText(o.toString());
                            checkField.addChangeListener(e->{
                                changed=true;
                            });
                            uiField = checkField;
                        }else {
                            JTextField textField = new JTextField();
                            textField.setText(o.toString());
                            textField.getDocument().addDocumentListener(this);
                            uiField = textField;
                        }
                    }

                    layout.putConstraint(SpringLayout.VERTICAL_CENTER, uiField, 0, SpringLayout.VERTICAL_CENTER, label);
                    comps.add(uiField);
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
        } catch (IllegalArgumentException | IntrospectionException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Override
    public void applyChanges() {
        // TODO Auto-generated method stub
        
    }

}