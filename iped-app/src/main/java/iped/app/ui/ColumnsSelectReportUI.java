package iped.app.ui;

import java.util.Arrays;
import java.awt.BorderLayout;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JPanel;

import iped.engine.task.index.IndexItem;

public class ColumnsSelectReportUI extends ColumnsSelectUI {
    private static ColumnsSelectReportUI instance;

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
        selectVisibleButton.addActionListener(this);
        combo.addActionListener(this);

        panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(scrollList, BorderLayout.CENTER);

        okButton.addActionListener(this);
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
        updatePanelList();
    }
}
