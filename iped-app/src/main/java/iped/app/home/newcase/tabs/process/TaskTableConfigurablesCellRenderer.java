package iped.app.home.newcase.tabs.process;

import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.TableCellRenderer;

import iped.app.home.MainFrame;
import iped.configuration.Configurable;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.EnableTaskProperty;
import iped.engine.config.HashDBLookupConfig;
import iped.engine.task.AbstractTask;
import iped.engine.task.IScriptTask;

public class TaskTableConfigurablesCellRenderer implements TableCellRenderer {
    ConfigurationManager configurationManager;
    MainFrame mainFrame;
    
    public TaskTableConfigurablesCellRenderer(ConfigurationManager configurationManager, MainFrame mainFrame) {
        this.configurationManager = configurationManager;
        this.mainFrame = mainFrame;        
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object cellValue, boolean isSelected, boolean hasFocus,
            int row, int column) {

        AbstractTask task = (AbstractTask) cellValue;
        int count = 0; // counts the number of non EnableTaskProperty configurables
        Exception configurableLoadException = null;
        
        List<Configurable<?>> configurables = null;
        try {
            configurables = task.getConfigurables();
        }catch (Exception e) {
            configurableLoadException = e;
        }

        if(configurables!=null) {
            for (Configurable<?> value : configurables) {
                if (!(value instanceof EnableTaskProperty)
                        && !(value instanceof HashDBLookupConfig)) {
                    count++;
                }
            }
        }

        JPanel panel = new JPanel();
        panel.setBackground(TableCellRendererUtil.getBackground(table, row, isSelected));

        if (count > 0 || task instanceof IScriptTask) {
            panel.setLayout( new GridBagLayout() );

            GridBagConstraints gbc = new GridBagConstraints();
            JButton taskOptionButton = new JButton("...");
            if(configurableLoadException!=null) {
                taskOptionButton.setBackground(Color.RED);
                String errorMsg = configurableLoadException.getMessage();
                if (errorMsg == null)
                    errorMsg = configurableLoadException.getClass().getCanonicalName();
                String finalErrorMsg = errorMsg;
                taskOptionButton.addActionListener(
                        e -> JOptionPane.showMessageDialog(panel, "Error loading configurables: " + finalErrorMsg));
            }else {
                taskOptionButton.addActionListener( e -> new TaskConfigDialog(ConfigurationManager.get(), task, mainFrame).setVisible(true));
            }
            taskOptionButton.setVerticalAlignment(SwingConstants.CENTER);

            panel.add(taskOptionButton, gbc);
        }
        return panel;
    }

}
