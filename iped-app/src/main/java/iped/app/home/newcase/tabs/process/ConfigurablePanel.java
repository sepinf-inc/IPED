package iped.app.home.newcase.tabs.process;

import java.awt.Component;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;

import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SpringLayout;

import iped.app.home.DefaultPanel;
import iped.app.home.MainFrame;
import iped.app.ui.controls.textarea.RegexTextPane;
import iped.configuration.Configurable;
import iped.utils.UTF8Properties;

public class ConfigurablePanel extends DefaultPanel{
    Configurable<?> configurable;
    SpringLayout layout;

    public ConfigurablePanel(Configurable<?> configurable, MainFrame mainFrame) {
        super(mainFrame);
        this.configurable = configurable;
        Object config = configurable.getConfiguration();
        if(config instanceof UTF8Properties) {
            createPropertiesGUI();
        }else if(config instanceof String) {
            createStringGUI();
        }else {
            createBeanGUI();
        }
        // TODO Auto-generated constructor stub
    }

    private void createStringGUI() {
        RegexTextPane textArea = new RegexTextPane();
        textArea.setAutoscrolls(true);
        textArea.setText(configurable.getConfiguration().toString());
        JScrollPane txtAreaScroll = new JScrollPane();
        txtAreaScroll.setViewportView(textArea);
        txtAreaScroll.setAutoscrolls(true);        
        this.add(txtAreaScroll);
    }

    @Override
    protected void createAndShowGUI() {
        layout = new SpringLayout();

        this.setLayout(layout);
        
    }

    protected void createPropertiesGUI() {
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
                comps.add(textField);
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
    
    protected void createBeanGUI() {
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
                    JTextField textField = new JTextField();
                    if(o!=null) {
                        textField.setText(o.toString());                    
                    }
                    if(lastLabel!=null) {
                        layout.putConstraint(SpringLayout.NORTH, textField, 15, SpringLayout.SOUTH, lastLabel);
                        layout.putConstraint(SpringLayout.NORTH, textField, 15, SpringLayout.SOUTH, lastLabel);
                    }
                    comps.add(textField);
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

}
