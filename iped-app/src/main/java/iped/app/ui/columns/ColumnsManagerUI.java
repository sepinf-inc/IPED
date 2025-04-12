package iped.app.ui.columns;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dialog.ModalityType;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableColumn;

import iped.app.ui.App;
import iped.app.ui.Messages;
import iped.app.ui.ProgressCellRenderer;
import iped.app.ui.ResultTableModel;
import iped.app.ui.columns.ColumnsManager.ColumnState;
import iped.app.ui.controls.HintTextField;
import iped.engine.util.Util;
import iped.localization.LocalizedProperties;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.utils.StringUtil;
import iped.viewers.util.ProgressDialog;

public class ColumnsManagerUI implements ActionListener {

    protected static ColumnsManager columnsManager;
    private static ColumnsManagerUI instance;

    protected JCheckBox autoManage = new JCheckBox(Messages.getString("ColumnsManager.AutoManageCols")); //$NON-NLS-1$
    protected JComboBox<Object> combo;
    protected JScrollPane scrollList;
    protected JLabel showColsLabel = new JLabel(Messages.getString("ColumnsManager.ShowCols")); //$NON-NLS-1$
    protected HintTextField textFieldNameFilter;
    protected JDialog dialog = new JDialog(App.get());
    protected final JPanel listPanel;
    protected JPanel panel = new JPanel(new BorderLayout());

    protected int firstColsToPin = 10;

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
    
    public void setPinnedColumns(int firstColsToPin) {
        this.firstColsToPin = firstColsToPin;
    }

    public int getPinnedColumns() {
        return this.firstColsToPin;
    }

    protected ColumnsManagerUI() {
        columnsManager = ColumnsManager.getInstance();
        dialog.setModal(true);

        dialog.setBounds(new Rectangle(400, 400));
        dialog.setTitle(Messages.getString("ColumnsManager.Title"));

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

        scrollList = new JScrollPane(listPanel);
        scrollList.getVerticalScrollBar().setUnitIncrement(10);
        panel.add(scrollList, BorderLayout.CENTER);

        dialog.getContentPane().add(panel);
        dialog.setLocationRelativeTo(App.get());
    }

    public void moveTimelineColumns(int newPos) {
        String[] timeFields = { BasicProps.TIMESTAMP, BasicProps.TIME_EVENT };
        for (int i = 0; i < App.get().getResultsTable().getColumnCount(); i++) {
            TableColumn col = App.get().getResultsTable().getColumnModel().getColumn(i);
            String colName = col.getHeaderValue().toString();
            for (int k = 0; k < timeFields.length; k++) {
                if (colName.equalsIgnoreCase(timeFields[k])) {
                    if (!columnsManager.colState.visibleFields.contains(timeFields[k])) {
                        updateGUICol(colName, true);
                    }
                    App.get().getResultsTable().moveColumn(i, newPos);
                    if (newPos > i) {
                        i--;
                    } else {
                        newPos++;
                    }
                    timeFields[k] = null;
                }
            }
        }
    }

    public void updateDinamicCols() {
        if (!columnsManager.isAutoManageCols())
            return;

        if (App.get().getResults().getLength() == App.get().appCase.getTotalItems()
                || App.get().getResults().getLength() == 0)
            return;

        final ProgressDialog progress = new ProgressDialog(App.get(), null, false, 100, ModalityType.TOOLKIT_MODAL);
        progress.setNote(Messages.getString("ColumnsManager.LoadingCols")); //$NON-NLS-1$

        new Thread() {
            public void run() {
                final Set<String> usedCols = columnsManager.getUsedCols(progress);

                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        updateDinamicCols(usedCols);
                        progress.close();
                    }
                });
            }
        }.start();
    }

    private void updateDinamicCols(Set<String> dinamicFields) {

        if (dinamicFields == null)
            return;

        Set<String> colNamesToPin = new HashSet<>();
        for (int i = 2; i < Math.min(firstColsToPin, App.get().getResultsTable().getColumnCount()); i++) {
            colNamesToPin.add(
                    App.get().getResultsTable().getColumnModel().getColumn(i).getHeaderValue().toString().toLowerCase());
        }

        for (String field : (List<String>) columnsManager.colState.visibleFields.clone()) {
            if (!dinamicFields.contains(field) && !colNamesToPin.contains(field.toLowerCase())
                    && !field.equals(BasicProps.LENGTH)) // length header changes to listed items total size info
            {
                updateGUICol(field, false);
            }
        }

        int newColStart = App.get().getResultsTable().getColumnCount();

        for (String field : dinamicFields) {
            if (!columnsManager.colState.visibleFields.contains(field))
                updateGUICol(field, true);
        }

        // move important new cols to front
        int newPosEmail = firstColsToPin;
        int newPosOther = firstColsToPin;
        for (int i = newColStart; i < App.get().getResultsTable().getColumnCount(); i++) {
            TableColumn col = App.get().getResultsTable().getColumnModel().getColumn(i);
            String colName = col.getHeaderValue().toString();
            if (colName.startsWith(ExtraProperties.MESSAGE_PREFIX)
                    || colName.startsWith(ExtraProperties.COMMUNICATION_PREFIX)) {
                App.get().getResultsTable().moveColumn(i, newPosEmail++);
                newPosOther++;
            } else if (colName.toLowerCase().startsWith(ExtraProperties.UFED_META_PREFIX)) {
                App.get().getResultsTable().moveColumn(i, newPosOther++);
            }
        }

        // move important old cols to front
        int lastOldCol = newColStart - 1 + newPosOther - firstColsToPin;
        newPosEmail = firstColsToPin;
        for (int i = newPosOther; i <= lastOldCol; i++) {
            TableColumn col = App.get().getResultsTable().getColumnModel().getColumn(i);
            String colName = col.getHeaderValue().toString();
            if (colName.startsWith(ExtraProperties.MESSAGE_PREFIX)
                    || colName.startsWith(ExtraProperties.COMMUNICATION_PREFIX)) {
                App.get().getResultsTable().moveColumn(i, newPosEmail++);
            }
        }
    }

    public void resetToLastLayout() {
        File cols = columnsManager.getColStateFile();
        try {
            ColumnState lastState = (ColumnState) Util.readObject(cols.getAbsolutePath());
            resetColumns(lastState.visibleFields, lastState.initialWidths);

        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }

    public void resetToDefaultLayout() {
        // resetColumns(Arrays.asList(defaultFields).stream().map(f ->
        // BasicProps.getLocalizedField(f))
        // .collect(Collectors.toList()), defaultWidths);
        resetColumns(Arrays.asList(ColumnsManager.defaultFields), ColumnsManager.defaultWidths);
    }

    public void resetColumns(List<String> newCols, List<Integer> widths) {
        for (String field : (List<String>) columnsManager.colState.visibleFields.clone())
            if (!newCols.contains(field) && !field.equals(ResultTableModel.SCORE_COL)
                    && !field.equals(ResultTableModel.BOOKMARK_COL))
                updateGUICol(field, false);

        for (String field : newCols) {
            if (!columnsManager.colState.visibleFields.contains(field))
                updateGUICol(field, true);
        }

        int newPos = 2;
        for (String col : newCols) {
            col = LocalizedProperties.getLocalizedField(col);
            for (int i = 0; i < App.get().getResultsTable().getColumnModel().getColumnCount(); i++) {
                TableColumn tc = App.get().getResultsTable().getColumnModel().getColumn(i);
                if (tc.getHeaderValue() instanceof String && ((String) tc.getHeaderValue())
                        .startsWith(col.substring(0, 1).toUpperCase() + col.substring(1))) {
                    App.get().getResultsTable().moveColumn(i, newPos++);
                }
            }
        }

        int j = 0;
        for (int i = 0; i < App.get().getResultsTable().getColumnModel().getColumnCount(); i++) {
            TableColumn tc = App.get().getResultsTable().getColumnModel().getColumn(i);
            if (tc.getModelIndex() >= ResultTableModel.fixedCols.length && j < widths.size()) {
                tc.setPreferredWidth(widths.get(j++));
            }
        }
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
        } else if (e.getSource() instanceof JCheckBox) {
            JCheckBox source = (JCheckBox) e.getSource();
            String text = source.getText();
            boolean isSelected = source.isSelected();
            updateGUICol(text, isSelected);
        }
    }

    Map<String, Integer> lastWidths = new HashMap<>();
    // updates GUI by adding or removing columns from the table
    private void updateGUICol(String colName, boolean insert) {
        colName = LocalizedProperties.getNonLocalizedField(colName);
        int modelIdx = columnsManager.loadedFields.indexOf(colName);
        if (insert) {
            columnsManager.colState.visibleFields.add(colName);
            if (modelIdx == -1) {
                columnsManager.loadedFields.add(colName);
                App.get().resultsModel.updateCols();
                modelIdx = ResultTableModel.fixedCols.length + columnsManager.loadedFields.size() - 1;
            } else
                modelIdx += ResultTableModel.fixedCols.length;

            TableColumn tc = new TableColumn(modelIdx);
            if (lastWidths.containsKey(colName))
                tc.setPreferredWidth(lastWidths.get(colName));
            else
                tc.setPreferredWidth(150);
            App.get().getResultsTable().addColumn(tc);
            setColumnRenderer(tc);
        } else {
            columnsManager.colState.visibleFields.remove(colName);
            modelIdx += ResultTableModel.fixedCols.length;
            int viewIdx = App.get().getResultsTable().convertColumnIndexToView(modelIdx);
            if (viewIdx > -1) {
                TableColumn col = App.get().getResultsTable().getColumnModel().getColumn(viewIdx);
                lastWidths.put(colName, col.getWidth());
                App.get().getResultsTable().removeColumn(col);
            }
        }
    }

    public void setColumnRenderer(TableColumn tc) {
        if (ResultTableModel.SCORE_COL.equals(columnsManager.getLoadedFields().get(tc.getModelIndex() - ResultTableModel.fixedCols.length)))
            tc.setCellRenderer(new ProgressCellRenderer());
    }

}
