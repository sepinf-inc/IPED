package iped.app.home.configurables;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.JTextComponent;

import iped.app.home.MainFrame;
import iped.configuration.Configurable;
import iped.engine.config.IPropertiesConfigurable;
import iped.engine.localization.Messages;
import iped.utils.UTF8Properties;

public class UTF8PropertiesConfigurablePanel extends ConfigurablePanel implements ItemListener {
    TreeMap<Object, JComponent> textFieldList = new TreeMap<Object, JComponent>();

    public UTF8PropertiesConfigurablePanel(Configurable<UTF8Properties> configurable, MainFrame mainFrame) {
        super(configurable, mainFrame);
    }

    public void createConfigurableGUI() {
        JPanel contentPanel = new JPanel(new GridBagLayout());
        contentPanel.setBackground(Color.white);
        UTF8Properties config = (UTF8Properties) configurable.getConfiguration();
        int currentLine = 0;
        if(config!=null) {
            for(Object propertie : config.orderedKeySet()){
                Object value = config.get(propertie);
                JComponent c = null;
                String localizedName = Messages.getString(configurable.getClass().getName()+"."+propertie, propertie.toString());

                //create label
                contentPanel.add(new JLabel(localizedName +":"), getGridBagConstraints(0, currentLine, 1, 0));
                //create input
                if(value!=null) {
                    Type t = getFieldType(configurable, propertie.toString());
                    if(t!=null) {
                        if(t.getTypeName().equals("int") || t.getTypeName().equals("long")) {
                            try {
                                boolean hasDefaultValue = hasDefaultValue(configurable, propertie.toString());
                                JSpinner spinner;
                                if(!hasDefaultValue) {
                                    spinner = new JSpinner();
                                    int ivalue = Integer.parseInt(value.toString().trim());
                                    spinner.setValue(ivalue);
                                }else {
                                    spinner = new JSpinner();                                 
                                    AutoCalcSpinnerModel model = new AutoCalcSpinnerModel(spinner);
                                    spinner.setModel(model);
                                    if(!value.toString().equals("auto")) {
                                        int ivalue = Integer.parseInt(value.toString().trim());
                                        spinner.setValue(ivalue);
                                    }else {
                                        spinner.setValue(0);
                                    }
                                }
                                spinner.getModel().addChangeListener(new ChangeListener() {
                                    @Override
                                    public void stateChanged(ChangeEvent e) {
                                        changed = true;
                                        fireChangeListener(e);
                                    }
                                });
                                c=spinner;
                            }catch(NumberFormatException ne) {                                
                            }
                        }
                        if (t.getTypeName().equals("boolean") || t.getTypeName().equals("java.lang.Boolean")
                                || (t.getTypeName().equals("java.lang.String")
                                        && (value.toString().trim().equals("true")
                                                || value.toString().trim().equals("false")))) {
                            boolean bvalue = value.toString().trim().equals("true");
                            JCheckBox cb = new JCheckBox();
                            cb.setSelected(bvalue);
                            cb.addItemListener(this);
                            c=cb;
                        }
                    } else {
                        if (value.toString().trim().equals("true") || value.toString().trim().equals("false")) {
                            boolean bvalue = value.toString().trim().equals("true");
                            JCheckBox cb = new JCheckBox();
                            cb.setSelected(bvalue);
                            cb.addItemListener(this);
                            c = cb;
                        }
                    }
                    if (c == null) {
                        if (value == null) {
                            JTextField textField = new JTextField((value != null) ? value.toString() : "");
                            textField.getDocument().addDocumentListener(this);
                            c = textField;
                        } else {
                            if (value.toString().length() > 100) {
                                JTextArea textArea = new JTextArea((value != null) ? value.toString() : "");
                                textArea.getDocument().addDocumentListener(this);
                                textArea.setLineWrap(true);
                                Border border = BorderFactory.createLineBorder(Color.BLACK);
                                textArea.setBorder(border);
                                c = textArea;
                            } else {
                                JTextField textField = new JTextField((value != null) ? value.toString() : "");
                                textField.getDocument().addDocumentListener(this);
                                c = textField;
                            }
                        }
                    }
                }
                contentPanel.add(c, getGridBagConstraints(1, currentLine, 1, 1));
                String tooltipKey = configurable.getClass().getName()+"."+propertie+Messages.TOOLTIP_SUFFIX;
                try {
                    String toolTipText = Messages.getString(tooltipKey, config.getComments(propertie));
                    if (toolTipText != null) {
                        c.setToolTipText(toolTipText);
                    }
                } catch (Exception e) {
                    // tooltip does not exists in resources
                }
                //
                textFieldList.put(propertie, c);
                currentLine++;
            }
        }else {
            contentPanel.add(new JLabel("No content!!"), getGridBagConstraints(0, currentLine, 1, 1));
        }

        JScrollPane contentScrollPanel = new JScrollPane();
        contentScrollPanel.setViewportView(contentPanel);
        contentScrollPanel.setAutoscrolls(true);
        this.setLayout(new BorderLayout());
        this.add(contentScrollPanel,BorderLayout.CENTER);
    }

    private Type getFieldType(Configurable<?> configurable, String propertyName) {
        try {
            Field f = configurable.getClass().getField(propertyName);
            return f.getType();
        } catch (NoSuchFieldException | SecurityException e) {
        }
        String accessName = propertyName.substring(0,1).toUpperCase()+propertyName.substring(1);
        try {
            Method m = configurable.getClass().getMethod("get"+accessName, null);
            return m.getGenericReturnType();
        } catch (NoSuchMethodException | SecurityException e) {
        }
        try {
            Method m = configurable.getClass().getMethod("is"+accessName, null);
            return m.getGenericReturnType();
        } catch (NoSuchMethodException | SecurityException e) {
        }
        try {
            Method m = configurable.getClass().getMethod("isTo"+accessName, null);
            return m.getGenericReturnType();
        } catch (NoSuchMethodException | SecurityException e) {
        }
        
        return null;
    }

    private Class getType(Configurable<?> configurable, String propertyName) {
        try {
            Field f = configurable.getClass().getField(propertyName);
            return f.getType();
        } catch (NoSuchFieldException | SecurityException e) {
        }
        String accessName = propertyName.substring(0,1).toUpperCase()+propertyName.substring(1);
        try {
            Method m = configurable.getClass().getMethod("get"+accessName, null);
            return m.getReturnType();
        } catch (NoSuchMethodException | SecurityException e) {
        }
        try {
            Method m = configurable.getClass().getMethod("is"+accessName, null);
            return m.getReturnType();
        } catch (NoSuchMethodException | SecurityException e) {
        }
        try {
            Method m = configurable.getClass().getMethod("isTo"+accessName, null);
            return m.getReturnType();
        } catch (NoSuchMethodException | SecurityException e) {
        }
        return null;
    }

    private boolean hasDefaultValue(Configurable<?> configurable, String propertyName) {
        String accessName = propertyName.substring(0,1).toUpperCase()+propertyName.substring(1);
        try {
            Method m = configurable.getClass().getMethod("getDefault"+accessName, null);
            return true;
        } catch (NoSuchMethodException | SecurityException e) {
        }
        return false;
    }

    private GridBagConstraints getGridBagConstraints(int tableColumnIndex, int tableLineIndex, int cellWidth, double weightX) {
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = weightX;
        c.gridx = tableColumnIndex;
        c.gridy = tableLineIndex;
        c.gridwidth = cellWidth;
        c.gridheight = 1;
        c.insets = new Insets(2, 10,2, 10);
        return c;
    }

    @Override
    public void applyChanges() {
        UTF8Properties config = (UTF8Properties) configurable.getConfiguration();
        if(config!=null) {
            Set<Entry<Object, Object>> es = config.entrySet();
            for (Iterator<Entry<Object, Object>> iterator = es.iterator(); iterator.hasNext();) {
                Entry<Object, Object> e = iterator.next();
                JComponent c = textFieldList.get(e.getKey());
                if(c instanceof JTextComponent) {
                    JTextComponent textField = (JTextComponent) c;
                    config.setProperty(e.getKey().toString(), textField.getText());
                }
                if(c instanceof JCheckBox) {
                    JCheckBox cb = (JCheckBox) c;
                    config.setProperty(e.getKey().toString(), Boolean.toString(cb.isSelected()));
                }
                if(c instanceof JSpinner) {
                    JSpinner spinner = (JSpinner) c;
                    config.setProperty(e.getKey().toString(), spinner.getValue().toString());
                }
            }
            ((IPropertiesConfigurable)configurable).processProperties(config);
        }
    }

    @Override
    public void itemStateChanged(ItemEvent e) {
        changed = true;
        fireChangeListener(new ChangeEvent(e.getSource()));
    }

}
