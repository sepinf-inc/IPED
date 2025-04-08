package iped.app.ui.columns;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;

import iped.app.ui.App;
import iped.app.ui.Messages;
import iped.app.ui.ResultTableModel;
import iped.app.ui.columns.ColumnsManager.CheckBoxState;
import iped.engine.task.index.IndexItem;
import iped.engine.util.Util;
import iped.localization.LocalizedProperties;
import iped.utils.StringUtil;

public class ColumnsSelectUI extends ColumnsManagerUI {

    private static final String SELECTED_PROPERTIES_FILENAME = "data/selectedProps.dat";

    private static ColumnsSelectUI instance;

    private static volatile boolean okButtonClicked;

    protected JCheckBox toggleSelectUnselectAllCheckBox = new JCheckBox(Messages.getString("ColumnsManager.ToggleSelectAll"));
    protected JButton selectVisibleButton = new JButton(Messages.getString("ColumnsManager.SelectVisible"));
    protected JButton okButton = new JButton("OK");

    protected Map<String, CheckBoxState> allCheckBoxesState = new HashMap<>();

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
        okButtonClicked = false;

        dialog.setTitle(Messages.getString("ReportDialog.PropertiesDialogTitle"));

        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                dispose();
            }
        });

        dialog.getContentPane().remove(panel);
        autoManage.removeActionListener(this);

        toggleSelectUnselectAllCheckBox.setAlignmentX(0);
        toggleSelectUnselectAllCheckBox.setBorder(BorderFactory.createEmptyBorder(6, 3, 0, 0));
        toggleSelectUnselectAllCheckBox.addActionListener(this);

        Box topPanel = Box.createVerticalBox();
        topPanel.add(showColsLabel);
        topPanel.add(combo);
        topPanel.add(textFieldNameFilter);
        topPanel.add(toggleSelectUnselectAllCheckBox);
        selectVisibleButton.addActionListener(this);
        okButton.addActionListener(this);

        panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(scrollList, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(okButton, BorderLayout.EAST);
        if (showCheckVisibleColsButton()) {
            bottomPanel.add(selectVisibleButton, BorderLayout.WEST);
        }
        panel.add(bottomPanel, BorderLayout.SOUTH);

        dialog.getContentPane().add(panel);
        dialog.setLocationRelativeTo(App.get());

        initializeAllCheckBoxesState();
        Set<String> loadedSelectedProperties = loadSavedFields(getPropertiesFile());
        if (loadedSelectedProperties.isEmpty()) {
            loadedSelectedProperties = getDefaultProperties();
        }
        checkOnlySelectedProperties(loadedSelectedProperties);
    }

    protected boolean showCheckVisibleColsButton() {
        return true;
    }

    private static File getModuleDir() {
        return App.get().appCase.getAtomicSources().get(0).getModuleDir();
    }

    protected File getPropertiesFile() {
        return new File(getModuleDir(), SELECTED_PROPERTIES_FILENAME);
    }

    protected Set<String> getDefaultProperties() {
        return Set.of(ResultTableModel.BOOKMARK_COL, IndexItem.NAME, IndexItem.EXT, IndexItem.TYPE, IndexItem.LENGTH, IndexItem.DELETED, 
                IndexItem.CATEGORY, IndexItem.CREATED, IndexItem.MODIFIED, IndexItem.ACCESSED, IndexItem.CHANGED, IndexItem.HASH, IndexItem.PATH);
    }

    public static ArrayList<String> loadSavedFields() {
        File propsFile = new File(getModuleDir(), SELECTED_PROPERTIES_FILENAME);
        return new ArrayList<String>(loadSavedFields(propsFile));
    }

    public static Set<String> loadSavedFields(File propsFile) {
        if (propsFile.exists()) {
            try {
                return (Set<String>) Util.readObject(propsFile.getAbsolutePath());
            } catch (ClassNotFoundException | IOException e) {
                e.printStackTrace();
            }
        }
        return new TreeSet<>();
    }

    protected void saveSelectedProps(File propsFile) {
        try {
            Set<String> reportPropsSet = new TreeSet<>();
            reportPropsSet.addAll(getSelectedProperties());
            if (reportPropsSet.size() > 0) {
                Util.writeObject(reportPropsSet, propsFile.getAbsolutePath());
            }
        } catch (Exception e1) {
            e1.printStackTrace();
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource().equals(selectVisibleButton)) {
            checkOnlySelectedProperties(ColumnsManager.getInstance().getVisibleColumns());
            updatePanelList();
        } else if (e.getSource().equals(toggleSelectUnselectAllCheckBox)) {
            checkProperties(getListedFieldNames(), toggleSelectUnselectAllCheckBox.isSelected());
            updatePanelList();
        } else if (e.getSource().equals(combo)) {
            updatePanelList();
        } else if (e.getSource().equals(okButton)) {
            saveSelectedProps(getPropertiesFile());
            okButtonClicked = true;
            dispose();
        } else if (e.getSource() instanceof JCheckBox) {
            JCheckBox source = (JCheckBox) e.getSource();
            String nonLocalizedText = LocalizedProperties.getNonLocalizedField(source.getText());
            boolean isSelected = source.isSelected();
            allCheckBoxesState.put(nonLocalizedText, new CheckBoxState(isSelected));
            updateToggleCheckBox();
        }

        if (getSelectedProperties().size() == 0) {
            okButton.setEnabled(false);
        } else {
            okButton.setEnabled(true);
        }
    }

    private void updateToggleCheckBox() {
        for (Component c : listPanel.getComponents()) {
            if (c instanceof JCheckBox && !((JCheckBox) c).isSelected()) {
                toggleSelectUnselectAllCheckBox.setSelected(false);
                return;
            }
        }
        toggleSelectUnselectAllCheckBox.setSelected(true);
    }

    private Set<String> getListedFieldNames() {
        TreeSet<String> result = new TreeSet<>();
        for (Component c : listPanel.getComponents()) {
            if (c instanceof JCheckBox) {
                String localizedField = ((JCheckBox) c).getText();
                result.add(LocalizedProperties.getNonLocalizedField(localizedField));
            }
        }
        return result;
    }

    // Updates according to the allCheckBoxesStates list
    @Override
    protected void updatePanelList() {
        listPanel.removeAll();
        List<String> fieldNames = Arrays.asList(columnsManager.fieldGroups[combo.getSelectedIndex()]);
        fieldNames = fieldNames.stream().map(f -> LocalizedProperties.getLocalizedField(f)).collect(Collectors.toList());
        Collections.sort(fieldNames, StringUtil.getIgnoreCaseComparator());
        String filter = textFieldNameFilter.getText().trim().toLowerCase();
        boolean allChecked = true;
        for (String fieldName : fieldNames) {
            if (filter.isEmpty() || fieldName.toLowerCase().indexOf(filter) >= 0) {
                JCheckBox check = new JCheckBox();
                check.setText(fieldName);
                CheckBoxState checkBoxState = allCheckBoxesState.get(LocalizedProperties.getNonLocalizedField(fieldName));
                if (checkBoxState != null) {
                    check.setSelected(checkBoxState.isSelected);
                    check.setEnabled(checkBoxState.isEnabled);
                }
                if (!check.isSelected()) {
                    allChecked = false;
                }
                check.addActionListener(this);
                listPanel.add(check);
            }
        }
        toggleSelectUnselectAllCheckBox.setSelected(allChecked);
        dialog.revalidate();
        dialog.repaint();
    }

    private void initializeAllCheckBoxesState() {
        for (int i = 0; i < ColumnsManager.getInstance().fieldGroups.length; i++) {
            List<String> fieldNames = Arrays.asList(ColumnsManager.getInstance().fieldGroups[i]);
            fieldNames.forEach(f -> allCheckBoxesState.putIfAbsent(LocalizedProperties.getNonLocalizedField(f), new CheckBoxState(false)));
        }
    }

    public List<String> getSelectedProperties() {
        List<String> selectedProperties = new ArrayList<>();
        for (Map.Entry<String, CheckBoxState> hmEntry : allCheckBoxesState.entrySet()) {
            String fieldName = hmEntry.getKey();
            Boolean isCheckBoxSelected = hmEntry.getValue().isSelected;
            if (isCheckBoxSelected) {
                selectedProperties.add(LocalizedProperties.getNonLocalizedField(fieldName));
            }
        }
        return selectedProperties;
    }

    protected void checkOnlySelectedProperties(Set<String> props) {
        for (Map.Entry<String, CheckBoxState> hmEntry : allCheckBoxesState.entrySet()) {
            String nonLocalizedKey = LocalizedProperties.getNonLocalizedField(hmEntry.getKey());
            if (props.contains(nonLocalizedKey)) {
                allCheckBoxesState.put(nonLocalizedKey, new CheckBoxState(true));
            } else {
                allCheckBoxesState.put(nonLocalizedKey, new CheckBoxState(false));
            }
        }
    }

    protected void checkProperties(Set<String> props, boolean check) {
        for (Map.Entry<String, CheckBoxState> hmEntry : allCheckBoxesState.entrySet()) {
            String nonLocalizedKey = LocalizedProperties.getNonLocalizedField(hmEntry.getKey());
            if (props.contains(nonLocalizedKey)) {
                allCheckBoxesState.put(nonLocalizedKey, new CheckBoxState(check));
            }
        }
    }


}
