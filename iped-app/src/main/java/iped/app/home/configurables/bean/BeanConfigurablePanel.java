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
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SpringLayout;

import org.apache.commons.lang3.tuple.Pair;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import iped.app.home.MainFrame;
import iped.app.home.configurables.ConfigurablePanel;
import iped.app.ui.controls.textarea.RegexTextPane;
import iped.configuration.Configurable;

public class BeanConfigurablePanel extends ConfigurablePanel {
    private ArrayList<Component> comps;
    private JLabel largestLabel;
    private JLabel lastLabel;
    private int max;

    public BeanConfigurablePanel(Configurable<?> configurable, MainFrame mainFrame) {
        super(configurable, mainFrame);
    }

    /**
     * Creates the suitable ui input to manipulate the specific property
     * 
     * @param config
     *            - the instance of the bean which property will be manipulated by
     *            the input
     * @param pd
     *            - the property descriptor of the property for which will be
     *            created the ui input
     */
    private JComponent createInputForProperty(Object config, PropertyDescriptor pd) {
        Object o = null;
        try {
            o = pd.getReadMethod().invoke(config);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return createInputForObject(o);
    }

    /**
     * Creates an layout the UI fields for the bean manipulation
     * 
     * @param config
     *            - the instance of the bean which property will be manipulated by
     *            the input
     */
    public void createBeanGUI(Object config) throws IntrospectionException {
        if (config instanceof Collection<?>) {
            Collection<?> col = (Collection<?>) config;
            for (Iterator iterator = col.iterator(); iterator.hasNext();) {
                Object object = (Object) iterator.next();
                createBeanGUI(object);
            }

            return;
        }

        property: for (PropertyDescriptor pd : Introspector.getBeanInfo(config.getClass()).getPropertyDescriptors()) {
            if (pd.getName().equals("class")) {
                continue property;
            }
            String localizedName = iped.engine.localization.Messages.getString(configurable.getClass().getName() + "." + pd.getDisplayName(), pd.getDisplayName());
            JLabel label = new JLabel(localizedName + ":");
            int width = pd.getDisplayName().length();
            if (max < width) {
                max = width;
                largestLabel = label;
            }
            comps.add(label);
            if (lastLabel != null) {
                layout.putConstraint(SpringLayout.NORTH, label, 15, SpringLayout.SOUTH, lastLabel);
            }

            JComponent uiField = createInputForProperty(config, pd);

            layout.putConstraint(SpringLayout.VERTICAL_CENTER, uiField, 0, SpringLayout.VERTICAL_CENTER, label);
            comps.add(uiField);
            lastLabel = label;
        }
    }

    public void addCreatedObjects() {
        for (Iterator iterator = comps.iterator(); iterator.hasNext();) {
            Component component = (Component) iterator.next();
            if (component != largestLabel) {
                if (component instanceof JLabel) {
                    layout.putConstraint(SpringLayout.EAST, component, 0, SpringLayout.EAST, largestLabel);
                } else {
                    layout.putConstraint(SpringLayout.WEST, component, 5, SpringLayout.EAST, largestLabel);
                }
            }
            this.add(component);
        }
    }

    private JComponent createInputForObject(Object o) {
        JComponent uiField = null;
        if (o != null) {
            if (o instanceof Boolean) {
                JCheckBox checkField = new JCheckBox();
                checkField.setText(o.toString());
                checkField.addChangeListener(e -> {
                    changed = true;
                });
                uiField = checkField;
            } else {
                uiField = createInputForString(o.toString());
            }
        }
        return uiField;
    }

    public JComponent createInputForString(String strConfig) {
        boolean isJson = false;
        if (strConfig.trim().startsWith("{")) {
            JSONParser parser = new JSONParser();
            try {
                parser.parse(strConfig);
                isJson = true;
            } catch (ParseException e) {
            }
        }
        JComponent uiField = null;

        if (isJson) {
            RegexTextPane textArea = new RegexTextPane();
            textArea.setAutoscrolls(true);
            textArea.setText(strConfig);
            textArea.getDocument().addDocumentListener(this);
            JScrollPane txtAreaScroll = new JScrollPane();
            txtAreaScroll.setViewportView(textArea);
            txtAreaScroll.setAutoscrolls(true);
            uiField = txtAreaScroll;
            layout.putConstraint(SpringLayout.SOUTH, uiField, 0, SpringLayout.SOUTH, this);
            layout.putConstraint(SpringLayout.EAST, uiField, 0, SpringLayout.EAST, this);
        } else {
            JTextField textField = new JTextField();
            textField.setText(strConfig);
            textField.getDocument().addDocumentListener(this);
            uiField = textField;
        }
        return uiField;
    }

    public void createObjectGUI(Object o, String strLabel) {
        JLabel label = new JLabel(strLabel + ":");
        int width = strLabel.length();
        if (max < width) {
            max = width;
            largestLabel = label;
        }
        comps.add(label);
        if (lastLabel != null) {
            layout.putConstraint(SpringLayout.NORTH, label, 15, SpringLayout.SOUTH, lastLabel);
        }
        lastLabel = label;

        JComponent uiField = createInputForObject(o);
        layout.putConstraint(SpringLayout.NORTH, uiField, 0, SpringLayout.NORTH, label);
        comps.add(uiField);
    }

    public void createConfigurableGUI() {
        try {
            lastLabel = null;
            comps = new ArrayList<Component>();
            largestLabel = null;
            max = 0;

            Object config = configurable.getConfiguration();
            if (config != null) {
                if (config instanceof Pair<?, ?>) {
                    createObjectGUI(((Pair<?, ?>) config).getLeft(), "Left");
                    createObjectGUI(((Pair<?, ?>) config).getRight(), "Right");
                } else {
                    createBeanGUI(config);
                }
                addCreatedObjects();
            } else {
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