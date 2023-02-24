package iped.app.home.configurables;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;

import javax.swing.*;

import iped.app.home.MainFrame;
import iped.configuration.Configurable;
import iped.engine.localization.Messages;
import iped.utils.UTF8Properties;

public class UTF8PropertiesConfigurablePanel extends ConfigurablePanel {
    TreeMap<Object, JTextField> textFieldList = new TreeMap<Object, JTextField>();

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
                String localizedName = Messages.getString(configurable.getClass().getName()+"."+propertie, propertie.toString());
                //create label
                contentPanel.add(new JLabel(localizedName +":"), getGridBagConstraints(0, currentLine, 1, 0));
                //create input
                JTextField textField = new JTextField( (value != null) ? value.toString() : "" );
                textField.getDocument().addDocumentListener(this);
                contentPanel.add(textField, getGridBagConstraints(1, currentLine, 1, 1));
                String tooltipKey = configurable.getClass().getName()+"."+propertie+Messages.TOOLTIP_SUFFIX;
                String toolTipText = Messages.getString(tooltipKey ,config.getComments(propertie));
                if(toolTipText!=null) {
                    textField.setToolTipText(toolTipText);
                }
                //
                textFieldList.put(propertie, textField);
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
                JTextField textField = textFieldList.get(e.getKey());
                config.setProperty(e.getKey().toString(), textField.getText());
            }
        }
    }

}
