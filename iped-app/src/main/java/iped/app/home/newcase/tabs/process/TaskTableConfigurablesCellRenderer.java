package iped.app.home.newcase.tabs.process;

import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.List;

import javax.swing.JButton;
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
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
            int row, int column) {
        return createColumnOptionPanel((AbstractTask) value);
    }

    /**
     * Create a JPanel to change the tasks properties
     */
    private JPanel createColumnOptionPanel(AbstractTask task){
        int count = 0;//counts the number of non EnableTaskProperty configurables

        List<Configurable<?>> configurables = task.getConfigurables();

        for (Configurable<?> value : configurables) {
            if (!(value instanceof EnableTaskProperty)
                    && !(value instanceof HashDBLookupConfig)) {
                count++;
            }
        }

        if(count>0) {
            JPanel panel = new JPanel();
            panel.setLayout( new GridBagLayout() );
            panel.setBackground(Color.WHITE);

            GridBagConstraints gbc = new GridBagConstraints();
            JButton taskOptionButton = new JButton("...");

            taskOptionButton.addActionListener( e -> new TaskConfigDialog(configurationManager, task, mainFrame).setVisible(true));

            taskOptionButton.setVerticalAlignment(SwingConstants.CENTER);
            panel.add(taskOptionButton, gbc);
            return panel;
        }else {
            if(task instanceof IScriptTask) {
                JPanel panel = new JPanel();
                panel.setLayout( new GridBagLayout() );
                panel.setBackground(Color.WHITE);

                GridBagConstraints gbc = new GridBagConstraints();
                JButton taskOptionButton = new JButton("...");

                taskOptionButton.addActionListener( e -> new TaskConfigDialog(configurationManager, task, mainFrame).setVisible(true));

                taskOptionButton.setVerticalAlignment(SwingConstants.CENTER);
                panel.add(taskOptionButton, gbc);
                return panel;
            }else {
                return new JPanel();
            }
        }
    }
}
