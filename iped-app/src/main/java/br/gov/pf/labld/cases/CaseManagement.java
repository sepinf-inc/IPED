package br.gov.pf.labld.cases;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.HeadlessException;
import java.awt.Insets;

import javax.swing.JFrame;
import javax.swing.JPanel;

import dpf.sp.gpinf.indexer.IpedVersion;

public class CaseManagement extends JFrame {

    private static final long serialVersionUID = 7347564605923739292L;

    private JPanel mainPanel;

    private NewCasePanel newCasePanel;

    private OpenCasePanel openCasePanel;

    private String profile;

    public CaseManagement() throws HeadlessException {
        super(IpedVersion.APP_NAME);
        createGUI();
    }

    public void createGUI() {
        mainPanel = new JPanel(new CardLayout());

        newCasePanel = new NewCasePanel(this);
        openCasePanel = new OpenCasePanel(this);
        CaseOptionsPanel caseOptionsPanel = new CaseOptionsPanel(this);

        mainPanel.add(newCasePanel, NewCasePanel.getPanelName());
        mainPanel.add(openCasePanel, OpenCasePanel.getPanelName());
        mainPanel.add(caseOptionsPanel, CaseOptionsPanel.getPanelName());

        JPanel sidePanel = new JPanel(new GridBagLayout());

        GridBagConstraints c = new GridBagConstraints();

        CaseMenuPanel menuPanel = new CaseMenuPanel(this);

        c.anchor = GridBagConstraints.LINE_START;
        c.insets = new Insets(0, 15, 0, 15);
        sidePanel.add(menuPanel, c);

        Container contentPane = getContentPane();
        contentPane.add(sidePanel, BorderLayout.WEST);
        contentPane.add(mainPanel, BorderLayout.CENTER);
    }

    private static void createAndShowGUI() {
        CaseManagement caseManagement = new CaseManagement();

        caseManagement.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        caseManagement.setSize(500, 500);
        caseManagement.setLocationRelativeTo(null);
        caseManagement.setExtendedState(JFrame.MAXIMIZED_BOTH);
        caseManagement.pack();
        caseManagement.setVisible(true);
    }

    public void showOpenCasePanel() {
        showPanel(OpenCasePanel.getPanelName());
    }

    public void showNewCasePanel() {
        showPanel(NewCasePanel.getPanelName());
    }

    public void showCaseOptionsPanel() {
        showPanel(CaseOptionsPanel.getPanelName());
    }

    public void showPanel(String panelName) {
        CardLayout cl = (CardLayout) (mainPanel.getLayout());
        cl.show(mainPanel, panelName);
    }

    protected NewCasePanel getNewCasePanel() {
        return newCasePanel;
    }

    protected OpenCasePanel getOpenCasePanel() {
        return openCasePanel;
    }

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        if (profile.equals("default")) {
            this.profile = null;
        } else {
            this.profile = profile;
        }
    }

    public static void main(String[] args) {

        String locale = System.getProperty("iped-locale");
        if (locale == null) {
            System.setProperty("iped-locale", "pt-BR");
        }

        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                createAndShowGUI();
            }
        });
    }

}
