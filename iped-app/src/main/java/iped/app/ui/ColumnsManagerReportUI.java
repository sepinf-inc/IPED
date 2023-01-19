package iped.app.ui;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import iped.engine.task.index.IndexItem;
import iped.localization.LocalizedProperties;
import iped.utils.StringUtil;

public class ColumnsManagerReportUI extends ColumnsManagerUI {
    private ColumnsManagerUI columnsManagerUI;
    private static ColumnsManagerReportUI instance;

    private static final String[] basicReportProps = { IndexItem.NAME, IndexItem.PATH, IndexItem.TYPE, IndexItem.LENGTH, IndexItem.CREATED,
        IndexItem.MODIFIED, IndexItem.ACCESSED, IndexItem.DELETED, IndexItem.CARVED, IndexItem.HASH, IndexItem.ID_IN_SOURCE };

    public static ColumnsManagerReportUI getInstance() {
        if (instance == null)
            instance = new ColumnsManagerReportUI();
        return instance;
    }

    @Override
    public void dispose() {
        dialog.setVisible(false);
        columnsManager = null;
        columnsManagerUI = null;
        instance = null;
        selectOnlyBasicProperties();
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

        // initial selected properties are the basics
        selectOnlyBasicProperties();

        updatePanelList();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource().equals(combo)) {
            updatePanelList();
        } else {
            JCheckBox source = (JCheckBox) e.getSource();
            String text = source.getText();
            boolean isSelected = source.isSelected();
            columnsManager.updateCol(text, isSelected);
            columnsManager.saveReportColumns();
        }
    }

    @Override
    protected void updatePanelList() {
        listPanel.removeAll();
        List<String> fieldNames = Arrays.asList(columnsManager.fieldGroups[combo.getSelectedIndex()]);
        fieldNames = fieldNames.stream().map(f -> LocalizedProperties.getLocalizedField(f)).collect(Collectors.toList());
        Collections.sort(fieldNames, StringUtil.getIgnoreCaseComparator());
        String filter = textFieldNameFilter.getText().trim().toLowerCase();
        for (String fieldName : fieldNames) {
            if (filter.isEmpty() || fieldName.toLowerCase().indexOf(filter) >= 0) {
                JCheckBox check = new JCheckBox();
                check.setText(fieldName);
                JCheckBox previousCheckBox = columnsCheckBoxes.get(LocalizedProperties.getNonLocalizedField(fieldName));
                if (previousCheckBox != null && previousCheckBox.isSelected())
                    check.setSelected(true);
                check.addActionListener(this);
                listPanel.add(check);
                columnsCheckBoxes.put(LocalizedProperties.getNonLocalizedField(fieldName), check);
            }
        }
        dialog.revalidate();
        dialog.repaint();
    }

    private void selectOnlyBasicProperties() {
        for (Map.Entry<String, JCheckBox> hmEntry : columnsCheckBoxes.entrySet()) {
            JCheckBox check = hmEntry.getValue();
            String key = hmEntry.getKey();
            if (Arrays.asList(basicReportProps).contains(key)) {
                check.setSelected(true);
            } else {
                check.setSelected(false);
            }
        }
    }

}
