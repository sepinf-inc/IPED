package br.gov.pf.labld.cases;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.TitledBorder;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.desktop.Messages;

public class CaseOptionsPanel extends JPanel implements ComponentListener {

    private static final long serialVersionUID = -8590852801053774717L;

    private CaseManagement caseManagement;

    private FormPanel formPanel;

    private OptionsHelper helper;

    private List<String> booleanOptions = new ArrayList<>();

    private JComboBox<String> profileCombo;

    public CaseOptionsPanel(CaseManagement caseManagement) {
        super();
        this.caseManagement = caseManagement;
        createGUI();
    }

    public void createGUI() {
        TitledBorder titledBorder = BorderFactory.createTitledBorder(Messages.getString("Case.Options"));
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(20, 10, 10, 10), titledBorder));

        formPanel = new FormPanel();

        createBooleanOptionGUI("enableLedDie", Messages.getString("Case.enableLedDie"),
                Messages.getString("Case.enableLedDieHelp"));
        createBooleanOptionGUI("exportFileProps", Messages.getString("Case.exportFileProps"),
                Messages.getString("Case.exportFilePropsHelp"));
        createBooleanOptionGUI("enableLanguageDetect", Messages.getString("Case.enableLanguageDetect"),
                Messages.getString("Case.enableLanguageDetectHelp"));
        createBooleanOptionGUI("enableRegexSearch", Messages.getString("Case.enableRegexSearch"),
                Messages.getString("Case.enableRegexSearchHelp"));
        createBooleanOptionGUI("enableNamedEntityRecogniton", Messages.getString("Case.enableNamedEntityRecogniton"),
                Messages.getString("Case.enableNamedEntityRecognitonHelp"));
        createBooleanOptionGUI("enableGraphGeneration", Messages.getString("Case.enableGraphGeneration"),
                Messages.getString("Case.enableGraphGenerationHelp"));
        createBooleanOptionGUI("indexUnknownFiles", Messages.getString("Case.indexUnknownFiles"),
                Messages.getString("Case.indexUnknownFilesHelp"));
        createBooleanOptionGUI("addUnallocated", Messages.getString("Case.addUnallocated"),
                Messages.getString("Case.addUnallocatedHelp"));
        createBooleanOptionGUI("enableOCR", Messages.getString("Case.enableOCR"),
                Messages.getString("Case.enableOCRHelp"));

        createProfileComboBox();

        loadOptions();

        JButton cancelBtn = new JButton(new CancelAction());
        cancelBtn.setText(Messages.getString("Case.Cancel"));
        formPanel.addButton(cancelBtn);

        JButton saveBtn = new JButton(new SaveAction());
        saveBtn.setText(Messages.getString("Case.SaveCase"));
        formPanel.addButton(saveBtn);
        formPanel.layoutForm();

        JScrollPane scrollPane = new JScrollPane(formPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, BorderLayout.CENTER);

        this.addComponentListener(this);
    }

    private void createProfileComboBox() {
        String help = Messages.getString("Case.ProfileHelp");
        JLabel jLabel = new JLabel(Messages.getString("Case.Profile"));
        jLabel.setToolTipText(help);
        jLabel.setHorizontalTextPosition(JLabel.LEFT);

        ImageIcon imageIcon = createImageIcon("information.png", help);
        jLabel.setIcon(imageIcon);

        String[] items = new String[] { "default", "blind", "fastmode", "forensic", "linkanalysis", "pedo", "triage" };
        profileCombo = new JComboBox<>(items);

        profileCombo.setSelectedItem(caseManagement.getProfile());

        formPanel.addItem("profile", jLabel, profileCombo);
    }

    private void createBooleanOptionGUI(String name, String label, String help) {
        JLabel jLabel = new JLabel(label);
        jLabel.setToolTipText(help);
        jLabel.setHorizontalTextPosition(JLabel.LEFT);

        ImageIcon imageIcon = createImageIcon("information.png", help);
        jLabel.setIcon(imageIcon);

        formPanel.addBooleanItem(name, jLabel);
        booleanOptions.add(name);
    }

    public void loadOptions() {
        helper = new OptionsHelper(Configuration.CONFIG_FILE);

        for (String itemName : booleanOptions) {
            formPanel.setBooleanItemValue(itemName, helper.getBoolean(itemName));
        }

        profileCombo.setSelectedItem(caseManagement.getProfile());
    }

    public void saveOptions() {
        Map<String, Object> props = new HashMap<>();
        for (String itemName : booleanOptions) {
            props.put(itemName, formPanel.getBooleanItemValue(itemName));
        }
        try {
            File opts = OptionsHelper.writeToFile(Configuration.CONFIG_FILE, props);
            String msg = Messages.getString("Case.OptionsSaved", opts.getAbsolutePath());
            JOptionPane.showMessageDialog(null, msg);
        } catch (IOException e) {
            String msg = Messages.getString("Case.OptionsSaveError", e.getLocalizedMessage());
            JOptionPane.showMessageDialog(null, msg);
        }
        this.caseManagement.setProfile(formPanel.getItemText("profile"));
    }

    protected ImageIcon createImageIcon(String path, String description) {
        URL imgURL = getClass().getResource(path);
        return new ImageIcon(imgURL, description);
    }

    public static String getPanelName() {
        return CaseOptionsPanel.class.getSimpleName();
    }

    private class CancelAction extends AbstractAction {

        private static final long serialVersionUID = 1491610258957389509L;

        @Override
        public void actionPerformed(ActionEvent e) {
            loadOptions();
        }

    }

    private class SaveAction extends AbstractAction {

        private static final long serialVersionUID = -4461459354785144611L;

        @Override
        public void actionPerformed(ActionEvent e) {
            saveOptions();
        }

    }

    @Override
    public void componentResized(ComponentEvent e) {

    }

    @Override
    public void componentMoved(ComponentEvent e) {

    }

    @Override
    public void componentShown(ComponentEvent e) {
        loadOptions();
    }

    @Override
    public void componentHidden(ComponentEvent e) {

    }

}
