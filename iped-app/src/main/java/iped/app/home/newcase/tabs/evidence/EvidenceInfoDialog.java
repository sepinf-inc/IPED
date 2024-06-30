package iped.app.home.newcase.tabs.evidence;/*
                                            * @created 27/09/2022
                                            * @project IPED
                                            * @author Thiago S. Figueiredo
                                            */

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import iped.app.home.newcase.model.Evidence;
import iped.app.home.style.StyleManager;
import iped.app.ui.Messages;

public class EvidenceInfoDialog extends JDialog {

    private Evidence evidence;
    private ArrayList<EvidenceListListener> evidenceListListener = new ArrayList<>();

    private JDialog evidenceDialog;
    private JButton okButton = new JButton("OK");
    private JLabel labelFileName = new JLabel();
    private JLabel labelPath = new JLabel();
    private JTextField textFieldAlias = new JTextField();
    private JTextField textFieldPassword = new JTextField();
    private JTextField textFieldTimeZone = new JTextField();
    private JTextField textFieldBlocksize = new JTextField();
    private JTextArea textAreaMaterial = new JTextArea();

    public EvidenceInfoDialog(Frame owner) {
        super(owner);
        evidenceDialog = this;

        evidenceDialog.setTitle(Messages.get("Home.Evidences.DialogTitle")); //$NON-NLS-1$
        evidenceDialog.setBounds(0, 0, 1024, 400);
        evidenceDialog.setLocationRelativeTo(null);

        JPanel fullPanel = new JPanel(new BorderLayout());
        Insets inset = StyleManager.getDefaultPanelInsets();
        fullPanel.setBorder(BorderFactory.createEmptyBorder(inset.top, inset.left, inset.bottom, inset.right));

        JPanel panel = new JPanel(new GridBagLayout());

        int line = 0;
        JLabel filename = new JLabel(Messages.get("Home.Evidences.Dialog.FileName"));
        panel.add(filename, getGridBagConstraints(0, line, 1, 1));
        panel.add(labelFileName, getGridBagConstraints(1, line, 2, 1));

        line++;
        panel.add(new JLabel(Messages.get("Home.Evidences.Dialog.Path")), getGridBagConstraints(0, line, 1, 1));
        panel.add(labelPath, getGridBagConstraints(1, line, 2, 1));

        line++;
        panel.add(new JLabel(Messages.get("Home.Evidences.Dialog.Alias")), getGridBagConstraints(0, line, 1, 1));
        panel.add(textFieldAlias, getGridBagConstraints(1, line, 2, 1));

        line++;
        panel.add(new JLabel(Messages.get("Home.Evidences.Dialog.EvidenceDesc")), getGridBagConstraints(0, line, 1, 1));
        textAreaMaterial.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        textAreaMaterial.setRows(3);
        panel.add(textAreaMaterial, getGridBagConstraints(1, line, 2, 1));

        line++;
        panel.add(new JLabel(Messages.get("Home.Evidences.Dialog.Password")), getGridBagConstraints(0, line, 1, 1));
        panel.add(textFieldPassword, getGridBagConstraints(1, line, 2, 1));

        line++;
        panel.add(new JLabel(Messages.get("Home.Evidences.Dialog.TimeZone")), getGridBagConstraints(0, line, 1, 1));
        panel.add(textFieldTimeZone, getGridBagConstraints(1, line, 2, 1));

        line++;
        panel.add(new JLabel(Messages.get("Home.Evidences.Dialog.BlockSize")), getGridBagConstraints(0, line, 1, 1));
        panel.add(textFieldBlocksize, getGridBagConstraints(1, line, 2, 1));

        fullPanel.add(panel, BorderLayout.CENTER);
        JPanel bPanel = new JPanel();
        bPanel.add(okButton);
        fullPanel.add(bPanel, BorderLayout.SOUTH);

        evidenceDialog.getContentPane().add(new JScrollPane(fullPanel));

        okButton.addActionListener(e -> {
            saveData();
            this.setVisible(false);
        });
    }

    private GridBagConstraints getGridBagConstraints(int x, int y, int width, int height) {
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        if (width > 1)
            c.weightx = 1.0;
        if (height > 1) {
            c.weighty = 1.0;
            c.fill = GridBagConstraints.BOTH;
        }
        c.gridx = x;
        c.gridy = y;
        c.gridwidth = width;
        c.gridheight = height;
        c.insets = new Insets(2, 10, 2, 10);
        return c;
    }

    public void showDialog(Evidence evidence) {
        this.evidence = evidence;
        labelFileName.setText(evidence.getFileName());
        labelPath.setText(evidence.getPath());
        textFieldAlias.setText(evidence.getAlias());
        textFieldPassword.setText(evidence.getPassword());
        textFieldTimeZone.setText(evidence.getTimezone());
        textFieldBlocksize.setText((evidence.getBlocksize() == null) ? "0" : evidence.getBlocksize().toString());
        textAreaMaterial.setText(evidence.getMaterial());
        this.setVisible(true);

    }

    private void saveData() {
        if (evidence == null)
            return;
        evidence.setFileName(labelFileName.getText());
        evidence.setPath(labelPath.getText());
        evidence.setAlias(textFieldAlias.getText());
        evidence.setPassword(textFieldPassword.getText());
        evidence.setTimezone(textFieldTimeZone.getText());
        evidence.setBlocksize(Integer.parseInt(textFieldBlocksize.getText()));
        evidence.setMaterial(textAreaMaterial.getText());
        evidenceListListener.forEach(e -> e.evidenceDataChange());
    }

    public void addListener(EvidenceListListener listener) {
        evidenceListListener.add(listener);
    }

}
