package iped.app.ui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.BorderLayout;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableColumn;

import iped.app.ui.controls.HintTextField;
import iped.localization.LocalizedProperties;
import iped.utils.StringUtil;


public class ColumnsManagerUI implements ActionListener {
    protected JCheckBox autoManage = new JCheckBox(Messages.getString("ColumnsManager.AutoManageCols")); //$NON-NLS-1$
    protected JComboBox<Object> combo;
    protected JLabel showColsLabel = new JLabel(Messages.getString("ColumnsManager.ShowCols")); //$NON-NLS-1$
    protected HintTextField textFieldNameFilter;
    protected JDialog dialog = new JDialog(App.get());
    protected final JPanel listPanel;
    protected JPanel panel = new JPanel(new BorderLayout());

    protected static ColumnsManager columnsManager;
    private static ColumnsManagerUI instance;

    protected static Map<String, JCheckBox> columnsCheckBoxes;

    public static ColumnsManagerUI getInstance() {
        if (instance == null)
            instance = new ColumnsManagerUI();
        return instance;
    }

    public void dispose() {
        dialog.setVisible(false);
        columnsManager = null;
        instance = null;
    }

    public void setVisible() {
        columnsManager.updateDinamicFields();
        updatePanelList();
        dialog.setVisible(true);
        combo.requestFocus();
    }

    protected ColumnsManagerUI() {
        columnsManager = ColumnsManager.getInstance();
        columnsCheckBoxes = new HashMap<>();

        dialog.setBounds(new Rectangle(400, 400));
        dialog.setTitle(Messages.getString("ColumnsManager.Title")); //$NON-NLS-1$

        showColsLabel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        showColsLabel.setAlignmentX(0);

        listPanel = new JPanel() {
            private static final long serialVersionUID = -4882872614411133375L;
            
            @Override
            public void updateUI() {
                super.updateUI();
                Color c = UIManager.getColor("List.background");
                if (c != null)
                    setBackground(new Color(c.getRGB()));
            }
        };
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));

        combo = new JComboBox<Object>(ColumnsManager.groupNames);
        combo.setAlignmentX(0);

        autoManage.setSelected(columnsManager.isAutoManageCols());
        autoManage.setAlignmentX(0);
        autoManage.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        autoManage.addActionListener(this);

        textFieldNameFilter = new HintTextField(Messages.getString("ColumnsManager.Filter"));
        textFieldNameFilter.setAlignmentX(0);
        textFieldNameFilter.getDocument().addDocumentListener(new DocumentListener() {
            public void removeUpdate(DocumentEvent e) {
                changedUpdate(e);
            }

            public void insertUpdate(DocumentEvent e) {
                changedUpdate(e);
            }

            public void changedUpdate(DocumentEvent e) {
                if (textFieldNameFilter.isFocusOwner()) {
                    updatePanelList();
                }
            }
        });

        Box topPanel = Box.createVerticalBox();
        topPanel.add(autoManage);
        topPanel.add(showColsLabel);
        topPanel.add(combo);
        topPanel.add(textFieldNameFilter);
        combo.addActionListener(this);

        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        panel.add(topPanel, BorderLayout.NORTH);

        JScrollPane scrollList = new JScrollPane(listPanel);
        scrollList.getVerticalScrollBar().setUnitIncrement(10);
        panel.add(scrollList, BorderLayout.CENTER);

        dialog.getContentPane().add(panel);
        dialog.setLocationRelativeTo(App.get());

        updatePanelList();
    }

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
                if (columnsManager.colState.visibleFields.contains(LocalizedProperties.getNonLocalizedField(fieldName)))
                    check.setSelected(true);
                check.addActionListener(this);
                listPanel.add(check);
                columnsCheckBoxes.put(LocalizedProperties.getNonLocalizedField(fieldName), check);
            }
        }
        dialog.revalidate();
        dialog.repaint();
    }


    @Override
    public void actionPerformed(ActionEvent e) {

        if (e.getSource().equals(autoManage))
            columnsManager.setAutoManageCols(autoManage.isSelected());
        else if (e.getSource().equals(combo)) {
            updatePanelList();
        } else {
            JCheckBox source = (JCheckBox) e.getSource();
            String text = source.getText();
            boolean isSelected = source.isSelected();
            columnsManager.updateCol(text, isSelected);
            updateGUICol(source.getText(), isSelected);
        }
    }

    private void updateGUICol(String colName, boolean insert) {
        colName = LocalizedProperties.getNonLocalizedField(colName);
        Map<String, Integer> lastWidths = columnsManager.lastWidths;
        int modelIdx = columnsManager.lastModelIdx;
        if (insert) {
            TableColumn tc = new TableColumn(modelIdx);
            if (lastWidths.containsKey(colName))
                tc.setPreferredWidth(lastWidths.get(colName));
            else
                tc.setPreferredWidth(150);
            App.get().resultsTable.addColumn(tc);
            setColumnRenderer(tc);
        } else {
            int viewIdx = App.get().resultsTable.convertColumnIndexToView(modelIdx);
            if (viewIdx > -1) {
                TableColumn col = App.get().resultsTable.getColumnModel().getColumn(viewIdx);
                App.get().resultsTable.removeColumn(col);
            }
        }
    }

    public void setColumnRenderer(TableColumn tc) {
        if (ResultTableModel.SCORE_COL.equals(columnsManager.getLoadedFields().get(tc.getModelIndex() - ResultTableModel.fixedCols.length)))
            tc.setCellRenderer(new ProgressCellRenderer());
    }

    public List<String> getSelectedProperties() {
        List<String> selectedColumns = new ArrayList<>();
        for (Map.Entry<String, JCheckBox> hmEntry : columnsCheckBoxes.entrySet()) {
            JCheckBox check = hmEntry.getValue();
            if (check.isSelected()) {
                selectedColumns.add(LocalizedProperties.getNonLocalizedField(check.getText()));
            }
        }
        return selectedColumns;
    }
}
