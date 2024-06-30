package iped.app.home.newcase.tabs.evidence;/*
                                            * @created 10/12/2022
                                            * @project IPED
                                            * @author Thiago S. Figueiredo
                                            */

import java.awt.Frame;
import java.awt.Insets;
import java.util.ArrayList;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;

import iped.app.home.newcase.model.Evidence;
import iped.app.home.style.StyleManager;
import iped.app.ui.Messages;
import oshi.hardware.HWDiskStore;

public class DiskSelecionDialog extends JDialog {

    private ArrayList<EvidenceListListener> evidenceListListener = new ArrayList<>();
    private JDialog diskDialog;
    private JButton okButton = new JButton(Messages.get("Home.Evidences.DisksDialog.Select"));
    private JTable tableDiskSelection;
    private DisksTableModel tableModel;
    private ArrayList<Evidence> evidencesList;

    public DiskSelecionDialog(Frame owner, ArrayList<Evidence> evidencesList) {
        super(owner);
        this.evidencesList = evidencesList;
        diskDialog = this;

        diskDialog.setTitle(Messages.get("Home.Evidences.DisksDialog.Title")); //$NON-NLS-1$
        diskDialog.setBounds(0, 0, 1024, 400);
        diskDialog.setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.PAGE_AXIS));
        Insets inset = StyleManager.getDefaultPanelInsets();
        mainPanel.setBorder(BorderFactory.createEmptyBorder(inset.top, inset.left, inset.bottom, inset.right));

        tableModel = new DisksTableModel(evidencesList);
        tableDiskSelection = new JTable(tableModel);
        tableDiskSelection.setFillsViewportHeight(true);
        tableDiskSelection.setRowHeight(30);

        JScrollPane scroll = new JScrollPane();
        scroll.setViewportView(tableDiskSelection);
        mainPanel.add(scroll);

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(okButton);
        mainPanel.add(buttonPanel);

        diskDialog.getContentPane().add(mainPanel);

        okButton.addActionListener(e -> {
            addDiskToEvidenceList();
            this.setVisible(false);
        });
    }

    private void addDiskToEvidenceList() {
        for (int rowIndex : tableDiskSelection.getSelectedRows()) {
            HWDiskStore disk = ((DisksTableModel) tableDiskSelection.getModel()).getDiskAt(rowIndex);
            Evidence evidence = new Evidence();
            evidence.setFileName(disk.getModel());
            evidence.setPath(disk.getName());
            evidencesList.add(evidence);
        }
        evidenceListListener.forEach(e -> e.evidenceDataChange());
    }

    public void showDialog() {
        this.setVisible(true);
    }

    public void addListener(EvidenceListListener listener) {
        evidenceListListener.add(listener);
    }

}
