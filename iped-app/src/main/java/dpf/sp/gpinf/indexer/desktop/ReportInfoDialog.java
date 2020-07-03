package dpf.sp.gpinf.indexer.desktop;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.filechooser.FileFilter;

import gpinf.dev.data.ReportInfo;

public class ReportInfoDialog extends JDialog implements ActionListener{
    
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    
    private ReportInfo reportInfo = new ReportInfo();
    
    JDialog infoDialog;
    JButton infoButton = new JButton(Messages.getString("ReportDialog.LoadButton")); //$NON-NLS-1$
    JButton okButton = new JButton("OK"); //$NON-NLS-1$
    JTextField rNumber = new JTextField();
    JTextField rDate = new JTextField();
    JTextField rTitle = new JTextField();
    JTextField rExaminer = new JTextField();
    JTextField rCaseNumber = new JTextField();
    JTextField rRequestForm = new JTextField();
    JTextField rRequestDate = new JTextField();
    JTextField rRequester = new JTextField();
    JTextField rLabNumber = new JTextField();
    JTextField rLabDate = new JTextField();
    JTextArea rEvidences = new JTextArea();
    
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
        return c;
    }
    
    public ReportInfoDialog(JDialog owner) {
        super(owner);
        
        infoDialog = this;
        infoDialog.setTitle(Messages.getString("ReportDialog.CaseInfo")); //$NON-NLS-1$
        infoDialog.setBounds(0, 0, 500, 550);
        infoDialog.setLocationRelativeTo(null);

        JPanel fullPanel = new JPanel(new BorderLayout());
        fullPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        JPanel panel = new JPanel(new GridBagLayout());
        
        JLabel loadFile = new JLabel();
        loadFile.setText(Messages.getString("ReportDialog.LoadInfo"));
        panel.add(loadFile, getGridBagConstraints(0, 0, 2, 1));
        panel.add(infoButton, getGridBagConstraints(2, 0, 1, 1));

        JLabel num = new JLabel(Messages.getString("ReportDialog.ReportNum")); //$NON-NLS-1$
        panel.add(num, getGridBagConstraints(0, 1, 1, 1));
        panel.add(rNumber, getGridBagConstraints(1, 1, 2, 1));

        JLabel date = new JLabel(Messages.getString("ReportDialog.ReportDate")); //$NON-NLS-1$
        panel.add(date, getGridBagConstraints(0, 2, 1, 1));
        panel.add(rDate, getGridBagConstraints(1, 2, 2, 1));

        JLabel title = new JLabel(Messages.getString("ReportDialog.ReportTitle")); //$NON-NLS-1$
        panel.add(title, getGridBagConstraints(0, 3, 1, 1));
        panel.add(rTitle, getGridBagConstraints(1, 3, 2, 1));

        JLabel examiner = new JLabel(Messages.getString("ReportDialog.Examiner")); //$NON-NLS-1$
        panel.add(examiner, getGridBagConstraints(0, 4, 1, 1));
        panel.add(rExaminer, getGridBagConstraints(1, 4, 2, 1));

        JLabel ipl = new JLabel(Messages.getString("ReportDialog.Investigation")); //$NON-NLS-1$
        panel.add(ipl, getGridBagConstraints(0, 5, 1, 1));
        panel.add(rCaseNumber, getGridBagConstraints(1, 5, 2, 1));

        JLabel request = new JLabel(Messages.getString("ReportDialog.Request")); //$NON-NLS-1$
        panel.add(request, getGridBagConstraints(0, 6, 1, 1));
        panel.add(rRequestForm, getGridBagConstraints(1, 6, 2, 1));
        
        JLabel requestDate = new JLabel(Messages.getString("ReportDialog.RequestDate")); //$NON-NLS-1$
        panel.add(requestDate, getGridBagConstraints(0, 7, 1, 1));
        panel.add(rRequestDate, getGridBagConstraints(1, 7, 2, 1));

        JLabel requester = new JLabel(Messages.getString("ReportDialog.Requester")); //$NON-NLS-1$
        panel.add(requester, getGridBagConstraints(0, 8, 1, 1));
        panel.add(rRequester, getGridBagConstraints(1, 8, 2, 1));

        JLabel record = new JLabel(Messages.getString("ReportDialog.Record")); //$NON-NLS-1$
        panel.add(record, getGridBagConstraints(0, 9, 1, 1));
        panel.add(rLabNumber, getGridBagConstraints(1, 9, 2, 1));

        JLabel recordDate = new JLabel(Messages.getString("ReportDialog.RecordDate")); //$NON-NLS-1$
        panel.add(recordDate, getGridBagConstraints(0, 10, 1, 1));
        panel.add(rLabDate, getGridBagConstraints(1, 10, 2, 1));

        JLabel evidences = new JLabel(Messages.getString("ReportDialog.Evidences")); //$NON-NLS-1$
        panel.add(evidences, getGridBagConstraints(0, 11, 1, 1));
        panel.add(rEvidences, getGridBagConstraints(1, 11, 2, 2));
        rEvidences.setLineWrap(true);
        rEvidences.setWrapStyleWord(true);
        
        fullPanel.add(panel, BorderLayout.CENTER);
        JPanel bPanel = new JPanel(new BorderLayout());
        bPanel.add(okButton, BorderLayout.EAST);
        fullPanel.add(bPanel, BorderLayout.SOUTH);

        infoDialog.getContentPane().add(fullPanel);
        
        infoButton.addActionListener(this);
        okButton.addActionListener(this);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        
        if (e.getSource() == infoButton) {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setCurrentDirectory(App.get().appCase.getCaseDir());
            fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            fileChooser.setFileFilter(new InfoFileFilter());
            if (fileChooser.showOpenDialog(App.get()) == JFileChooser.APPROVE_OPTION) {
                File infoFile = fileChooser.getSelectedFile();
                try {
                    reportInfo = new ReportInfo();
                    if(infoFile.getName().endsWith(".json"))
                        reportInfo.readJsonInfoFile(infoFile);
                    else if(infoFile.getName().endsWith(".asap"))
                        reportInfo.readAsapInfoFile(infoFile);
                    
                    populateTextFields(reportInfo);
                    
                } catch (IOException e1) {
                    e1.printStackTrace();
                    JOptionPane.showMessageDialog(null, "Error loading case info file: " + e1.toString()); //$NON-NLS-1$
                }
            }
            
        }
        
        if(e.getSource() == okButton) {
            loadTextFields();
            this.setVisible(false);
        }
        
    }
    
    private class InfoFileFilter extends FileFilter {
        @Override
        public boolean accept(File f) {
            return  f.isDirectory() || 
                    f.getName().toLowerCase().endsWith(".asap") || //$NON-NLS-1$
                    f.getName().toLowerCase().endsWith(".json");
        }

        @Override
        public String getDescription() {
            return "*.json;*.asap"; //$NON-NLS-1$
        }
    }
    
    private void populateTextFields(ReportInfo info) {
        rNumber.setText(info.reportNumber);
        rDate.setText(info.reportDate);
        rTitle.setText(info.reportTitle);
        rExaminer.setText(info.getExaminersText());
        rCaseNumber.setText(info.caseNumber);
        rRequestForm.setText(info.requestForm);
        rRequestDate.setText(info.requestDate);
        rRequester.setText(info.requester);
        rLabNumber.setText(info.labCaseNumber);
        rLabDate.setText(info.labCaseDate);
        rEvidences.setText(info.getEvidenceDescHtml());
    }
    
    private void loadTextFields() {
        reportInfo.reportNumber = rNumber.getText().trim();
        reportInfo.reportDate = rDate.getText().trim();
        reportInfo.reportTitle = rTitle.getText().trim();
        reportInfo.fillExaminersFromText(rExaminer.getText().trim());
        reportInfo.caseNumber = rCaseNumber.getText().trim();
        reportInfo.requestForm = rRequestForm.getText().trim();
        reportInfo.requestDate = rRequestDate.getText().trim();
        reportInfo.requester = rRequester.getText().trim();
        reportInfo.labCaseNumber = rLabNumber.getText().trim();
        reportInfo.labCaseDate = rLabDate.getText().trim();
        reportInfo.fillEvidenceFromText(rEvidences.getText().trim());
    }
    
    public ReportInfo getReportInfo() {
        return reportInfo;
    }
}
