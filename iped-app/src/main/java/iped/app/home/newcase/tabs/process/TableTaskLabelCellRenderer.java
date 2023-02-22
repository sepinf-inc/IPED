package iped.app.home.newcase.tabs.process;

import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Image;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;

import iped.app.ui.App;
import iped.engine.task.AbstractTask;
import iped.engine.task.PythonTask;
import iped.utils.IconUtil;

public class TableTaskLabelCellRenderer extends DefaultTableCellRenderer {
    private ImageIcon dragIcon;
    private static final String resPath = '/' + App.class.getPackageName().replace('.', '/') + '/';

    public TableTaskLabelCellRenderer() {
        super();
        Image img = (new ImageIcon(IconUtil.class.getResource(resPath + "cursor-hand-icon.png"))).getImage();
        Image newimg = img.getScaledInstance( 16, 16,  java.awt.Image.SCALE_SMOOTH ) ;
        this.dragIcon = new ImageIcon(newimg);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        AbstractTask currentTask = (AbstractTask) value;
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new FlowLayout(FlowLayout.LEFT));
        JLabel nameLabel = new JLabel();
        String localizedName = iped.engine.localization.Messages.getString(currentTask.getClass().getName(), currentTask.getName());
        nameLabel.setText(localizedName);
        panel.add(nameLabel, BorderLayout.WEST);
        
        if(currentTask instanceof PythonTask) {

            JButton taskStartDragButton = new JButton(dragIcon);
            taskStartDragButton.setVerticalAlignment(SwingConstants.CENTER);

            taskStartDragButton.addActionListener( e -> {
                Robot robot;
                try {
                    //simulate mouse click with shift pressed to start dragging
                    robot = new Robot();
                    Point point = panel.getLocationOnScreen(); //rect is my custom view
                    robot.mouseMove(point.x,point.y);
                    robot.mousePress(InputEvent.BUTTON1_MASK);
                } catch (AWTException e1) {
                    e1.printStackTrace();
                }            
            });
            taskStartDragButton.setPreferredSize(new Dimension(20,25));
            taskStartDragButton.setOpaque(false);
            taskStartDragButton.setContentAreaFilled(false);
            taskStartDragButton.setBorderPainted(false);

            panel.add(taskStartDragButton,BorderLayout.WEST);
        }        
        
        return panel;
        
    }


}
