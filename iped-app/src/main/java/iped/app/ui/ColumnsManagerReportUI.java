package iped.app.ui;

import java.awt.event.ActionEvent;
import java.awt.BorderLayout;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

public class ColumnsManagerReportUI extends ColumnsManagerUI {
    private ColumnsManagerUI columnsManagerUI;
    private static ColumnsManagerReportUI instance;

    public static ColumnsManagerReportUI getInstance() {
        if (instance == null)
            instance = new ColumnsManagerReportUI();
        return instance;
    }

    protected ColumnsManagerReportUI() {
        super();
        dialog.getContentPane().remove(panel);
        columnsManagerUI = ColumnsManagerUI.getInstance();
        autoManage.removeActionListener(columnsManagerUI);

        Box topPanel = Box.createVerticalBox();
        topPanel.add(showColsLabel);
        topPanel.add(combo);
        topPanel.add(textFieldNameFilter);
        combo.addActionListener(this);

        panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        panel.add(topPanel, BorderLayout.NORTH);

        JScrollPane scrollList = new JScrollPane(listPanel);
        scrollList.getVerticalScrollBar().setUnitIncrement(10);
        panel.add(scrollList, BorderLayout.CENTER);

        dialog.getContentPane().add(panel);
        dialog.setLocationRelativeTo(App.get());

        updateList();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource().equals(combo)) {
            updateList();
        } else {
            JCheckBox source = (JCheckBox) e.getSource();
            String text = source.getText();
            boolean isSelected = source.isSelected();
            columnsManager.updateCol(text, isSelected);
            // updateGUICol(source.getText(), isSelected);
        }
    }
    
}
