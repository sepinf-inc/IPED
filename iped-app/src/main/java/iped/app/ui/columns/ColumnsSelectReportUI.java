package iped.app.ui.columns;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JOptionPane;

import iped.app.ui.App;
import iped.app.ui.Messages;
import iped.app.ui.columns.ColumnsManager.CheckBoxState;
import iped.engine.task.index.IndexItem;
import iped.localization.LocalizedProperties;
import iped.properties.BasicProps;

public class ColumnsSelectReportUI extends ColumnsSelectUI {
    private static ColumnsSelectReportUI instance;
    protected JButton clearButton = new JButton(Messages.getString("ColumnsManager.ClearButton"));

    private final int PROPERTIES_LIMIT_NUM = 100; 

    private static final String[] basicReportProps = { IndexItem.NAME, IndexItem.PATH, IndexItem.TYPE, IndexItem.LENGTH, IndexItem.CREATED,
        IndexItem.MODIFIED, IndexItem.ACCESSED, IndexItem.DELETED, IndexItem.CARVED, IndexItem.HASH, IndexItem.ID_IN_SOURCE };

    public static ColumnsSelectReportUI getInstance() {
        if (instance == null)
            instance = new ColumnsSelectReportUI();
        return instance;
    }

    @Override
    public void dispose() {
        super.dispose();
        dialog.setVisible(false);
        instance = null;
    }

    protected ColumnsSelectReportUI() {
        super();
        saveFileName = ColumnsManager.SELECTED_REPORT_PROPERTIES_FILENAME;

        dialog.getContentPane().remove(panel);

        Box topPanel = Box.createVerticalBox();
        topPanel.add(showColsLabel);
        topPanel.add(combo);
        topPanel.add(textFieldNameFilter);

        panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(scrollList, BorderLayout.CENTER);

        clearButton.addActionListener(this);
        JPanel leftBottomPanel = new JPanel(new BorderLayout());
        leftBottomPanel.add(selectVisibleButton, BorderLayout.WEST);
        leftBottomPanel.add(clearButton, BorderLayout.EAST);
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(leftBottomPanel, BorderLayout.WEST);
        bottomPanel.add(okButton, BorderLayout.EAST);

        panel.add(bottomPanel, BorderLayout.SOUTH);

        dialog.getContentPane().add(panel);
        dialog.setLocationRelativeTo(App.get());

        loadedSelectedProperties = ColumnsManager.loadSelectedFields(saveFileName);
        if (loadedSelectedProperties != null) {
            columnsManager.enableOnlySelectedProperties(loadedSelectedProperties);
        } else {
            columnsManager.enableOnlySelectedProperties(Arrays.asList(basicReportProps));
        }
        disableRequiredProperties();
        updatePanelList();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        super.actionPerformed(e);
        if (e.getSource().equals(clearButton)) {
            columnsManager.disableAllProperties();
            disableRequiredProperties();
            updatePanelList();
        } else if (e.getSource() instanceof JCheckBox || e.getSource().equals(selectVisibleButton)) {
            if (columnsManager.getSelectedProperties().size() > PROPERTIES_LIMIT_NUM) {
                if (e.getSource() instanceof JCheckBox) {
                    JCheckBox source = (JCheckBox) e.getSource();
                    String nonLocalizedText = LocalizedProperties.getNonLocalizedField(source.getText());
                    JOptionPane.showMessageDialog(dialog, Messages.getString("ColumnsManager.LimitReachedMessage", PROPERTIES_LIMIT_NUM),
                        Messages.getString("ColumnsManager.LimitReachedTitle"), JOptionPane.ERROR_MESSAGE);
                    columnsManager.allCheckBoxesState.put(nonLocalizedText, new CheckBoxState(false));
                } else {
                    JOptionPane.showMessageDialog(dialog, Messages.getString("ColumnsManager.LimitReachedVisibleMessage", PROPERTIES_LIMIT_NUM),
                        Messages.getString("ColumnsManager.LimitReachedTitle"), JOptionPane.ERROR_MESSAGE);
                    columnsManager.enableOnlySelectedProperties(
                        columnsManager.colState.visibleFields.stream().limit(PROPERTIES_LIMIT_NUM).collect(Collectors.toList()));
                }
                updatePanelList();
            }
        }
    }

    public void disableRequiredProperties() {
        columnsManager.allCheckBoxesState.put(IndexItem.ID_IN_SOURCE, new CheckBoxState(true, false));
        columnsManager.allCheckBoxesState.put(BasicProps.PATH, new CheckBoxState(true, false));
    }
}
