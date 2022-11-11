package iped.app.home.configurables;

import java.awt.Component;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.Iterator;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SpringLayout;

import org.apache.commons.lang3.tuple.Pair;

import iped.app.home.MainFrame;
import iped.configuration.Configurable;

public class BeanConfigurablePanel extends ConfigurablePanel{
    private ArrayList<Component> comps;
    private JLabel largestLabel;
    private JLabel lastLabel;
    private int max;

    protected BeanConfigurablePanel(Configurable<?> configurable, MainFrame mainFrame) {
        super(configurable, mainFrame);
    }

    public void createBeanGUI(Object config) throws IntrospectionException {
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