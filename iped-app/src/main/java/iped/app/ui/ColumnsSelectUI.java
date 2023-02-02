package iped.app.ui;

import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import iped.localization.LocalizedProperties;
import iped.utils.StringUtil;

public class ColumnsSelectUI extends ColumnsManagerUI {
    private ColumnsManagerUI columnsManagerUI;
    private static ColumnsSelectUI instance;
    private JButton okButton = new JButton("OK");
    ArrayList<String> loadedSelectedProperties;
    private static boolean okButtonClicked;
    protected String saveFileName;

    public static ColumnsSelectUI getInstance() {
        if (instance == null)
            instance = new ColumnsSelectUI();
        return instance;
    }

    @Override
    public void dispose() {
        super.dispose();
        dialog.setVisible(false);
        instance = null;
    }

    public static boolean getOkButtonClicked() {
        return okButtonClicked;
    }

    protected ColumnsSelectUI() {
        super();
        saveFileName = ColumnsManager.SELECTED_PROPERTIES_FILENAME;
        okButtonClicked = false;
        columnsManagerUI = ColumnsManagerUI.getInstance();

        dialog.setTitle(Messages.getString("ReportDialog.PropertiesDialogTitle"));

        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                dispose();
            }
        });

        dialog.getContentPane().remove(panel);
        autoManage.removeActionListener(columnsManagerUI);

        Box topPanel = Box.createVerticalBox();
        topPanel.add(showColsLabel);
        topPanel.add(combo);
        topPanel.add(textFieldNameFilter);
        okButton.addActionListener(this);
        combo.addActionListener(this);

        panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        panel.add(topPanel, BorderLayout.NORTH);

        JScrollPane scrollList = new JScrollPane(listPanel);
        scrollList.getVerticalScrollBar().setUnitIncrement(10);
        panel.add(scrollList, BorderLayout.CENTER);

        JPanel bPanel = new JPanel(new BorderLayout());
        bPanel.add(okButton, BorderLayout.EAST);
        panel.add(bPanel, BorderLayout.SOUTH);

        dialog.getContentPane().add(panel);
        dialog.setLocationRelativeTo(App.get());

        loadedSelectedProperties = ColumnsManager.loadSelectedFields(saveFileName);
        if (loadedSelectedProperties != null) {
            columnsManager.enableOnlySelectedProperties(loadedSelectedProperties);
        } else {
            columnsManager.enableOnlySelectedProperties(new ArrayList<String>(Arrays.asList(ResultTableModel.fields)));
        }
        updatePanelList();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource().equals(combo)) {
            updatePanelList();
        } else if (e.getSource().equals(okButton)) {
            columnsManager.saveSelectedProps(saveFileName);
            okButtonClicked = true;
            dispose();
        } else {
            JCheckBox source = (JCheckBox) e.getSource();
            String nonLocalizedText = LocalizedProperties.getNonLocalizedField(source.getText());
            boolean isSelected = source.isSelected();
            if (columnsManager.getSelectedProperties().size() == 1 && !isSelected) {
                updatePanelList();
                return;
            }
            columnsManager.allCheckBoxesState.put(nonLocalizedText, isSelected);
        }
    }

    // Updates according to the allCheckBoxesStates list
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
                Boolean checkBoxState = columnsManager.allCheckBoxesState.get(LocalizedProperties.getNonLocalizedField(fieldName));
                if (checkBoxState != null && checkBoxState == true)
                    check.setSelected(true);
                check.addActionListener(this);
                listPanel.add(check);
            }
        }
        dialog.revalidate();
        dialog.repaint();
    }
}
