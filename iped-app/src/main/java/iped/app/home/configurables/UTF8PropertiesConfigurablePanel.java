package iped.app.home.configurables;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.Map.Entry;

import javax.swing.*;

import iped.app.home.MainFrame;
import iped.configuration.Configurable;
import iped.utils.UTF8Properties;

public class UTF8PropertiesConfigurablePanel extends ConfigurablePanel {
    HashMap<Object, JTextField> textFieldList = new HashMap<Object, JTextField>();

    public UTF8PropertiesConfigurablePanel(Configurable<UTF8Properties> configurable, MainFrame mainFrame) {
        super(configurable, mainFrame);
    }

    public void createConfigurableGUI() {
        JPanel contentPanel = new JPanel(new GridBagLayout());
        contentPanel.setBackground(Color.white);
        UTF8Properties config = (UTF8Properties) configurable.getConfiguration();
        int currentLine = 0;
        if(config!=null) {
            for(Entry<Object, Object> propertie : config.entrySet()){
                //create label
                contentPanel.add(new JLabel(propertie.getKey() +":"), getGridBagConstraints(0, currentLine, 1, 0));
                //create input
                JTextField textField = new JTextField( (propertie.getValue() != null) ? propertie.getValue().toString() : "" );
                textField.getDocument().addDocumentListener(this);
                contentPanel.add(textField, getGridBagConstraints(1, currentLine, 1, 1));
                //
                textFieldList.put(propertie.getKey(), textField);
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
