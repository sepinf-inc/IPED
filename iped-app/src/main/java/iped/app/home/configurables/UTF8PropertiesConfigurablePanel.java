package iped.app.home.configurables;

import java.awt.*;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.JTextComponent;

import iped.app.home.MainFrame;
import iped.configuration.Configurable;
import iped.engine.localization.Messages;
import iped.utils.UTF8Properties;

public class UTF8PropertiesConfigurablePanel extends ConfigurablePanel implements ChangeListener {
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
                    try{
                        int ivalue = Integer.parseInt(value.toString().trim());
                        JSpinner spinner = new JSpinner();
                        spinner.setValue(ivalue);
                        spinner.addChangeListener(this);
                        c=spinner;
                    }catch (Exception e) {
                        // TODO: handle exception
                    }
                    if(c==null) {
                        try{
                            boolean bvalue = value.toString().trim().equals("true");
                            if(!bvalue) {
                                if(value.toString().trim().equals("false")) {
                                    bvalue=false;
                                }else {
                                    throw new Exception();
                                }
                            }
                            JCheckBox cb = new JCheckBox();
                            cb.setSelected(bvalue);
                            cb.addChangeListener(this);
                            c=cb;
                        }catch (Exception e) {
                        }
                    }
                    if(c==null) {
                        JTextField textField = new JTextField( (value != null) ? value.toString() : "" );
                        textField.getDocument().addDocumentListener(this);
                        c=textField;
                    }
                }
                contentPanel.add(c, getGridBagConstraints(1, currentLine, 1, 1));
                String tooltipKey = configurable.getClass().getName()+"."+propertie+Messages.TOOLTIP_SUFFIX;
                String toolTipText = Messages.getString(tooltipKey ,config.getComments(propertie));
                if(toolTipText!=null) {
                    c.setToolTipText(toolTipText);
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
            }
        }
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        changed = true;        
    }

}
