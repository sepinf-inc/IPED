package iped.app.home.configurables.bean;

import java.awt.Component;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SpringLayout;

import org.apache.commons.lang3.tuple.Pair;

import iped.app.home.MainFrame;
import iped.app.home.configurables.ConfigurablePanel;
import iped.configuration.Configurable;

public class BeanConfigurablePanel extends ConfigurablePanel{
    private ArrayList<Component> comps;
    private JLabel largestLabel;
    private JLabel lastLabel;
    private int max;

    public BeanConfigurablePanel(Configurable<?> configurable, MainFrame mainFrame) {
        super(configurable, mainFrame);
    }

    /**
     * Creates the suitable ui input to manipulate the specific property
     * @param config - the instance of the bean which property will be manipulated by
     *                  the input 
     * @param pd - the property descriptor of the property for which will be created the
     *              ui input  
     */
    private JComponent createInputForProperty(Object config, PropertyDescriptor pd) {
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
        return uiField;
    }
    
    /**
     * Creates an layout the UI fields for the bean manipulation
     * @param config - the instance of the bean which property will be manipulated by
     *                  the input 
     */
    public void createBeanGUI(Object config) throws IntrospectionException {
        if(config instanceof Collection<?>) {
            Collection<?> col= (Collection<?>) config;
            for (Iterator iterator = col.iterator(); iterator.hasNext();) {
                Object object = (Object) iterator.next();
                createBeanGUI(object);
            }

            return;
        }

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

            JComponent uiField = createInputForProperty(config, pd);


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
    }

    public void createConfigurableGUI() {
        try {
            lastLabel = null;
            comps = new ArrayList<Component>();
            largestLabel = null;
            max=0;

            Object config = configurable.getConfiguration();
            if(config!=null) {
                if(config instanceof Pair<?, ?>) {
                    createBeanGUI(((Pair<?, ?>)config).getLeft());
                    createBeanGUI(((Pair<?, ?>)config).getRight());
                }else {
                    createBeanGUI(config);
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