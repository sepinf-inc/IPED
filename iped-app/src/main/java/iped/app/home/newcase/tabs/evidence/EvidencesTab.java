package iped.app.home.newcase.tabs.evidence;

/*
 * @created 09/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */

import iped.app.home.DefaultPanel;
import iped.app.home.MainFrame;
import iped.app.home.newcase.NewCaseContainerPanel;
import iped.app.home.style.StyleManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;

/**
 * A page to manage the evidences to be processed
 */
public class EvidencesTab extends DefaultPanel {

    private JTable jtableEvidences;
    private EvidencesTableModel evidencesTableModel;

    public EvidencesTab(MainFrame mainFrame) {
        super(mainFrame);
    }

    /**
     * Prepare everything to be displayed
     */
    protected void createAndShowGUI() {
        this.setLayout( new BorderLayout() );
        setBorder(new EmptyBorder(10,10,10,10));
        this.add(createTitlePanel(), BorderLayout.NORTH);
        this.add(createFormPanel(), BorderLayout.CENTER);
        this.add(createNavigationButtonsPanel(), BorderLayout.SOUTH);
    }

    /**
     * Create a new JPanel instance containing the Page Title
     * @return - JPanel containing the Page Title
     */
    private JPanel createTitlePanel(){
        JPanel panelTitle = new JPanel();
        panelTitle.setBackground(Color.white);
        JLabel labelTitle = new JLabel("Evidências");
        labelTitle.setFont(StyleManager.getPageTitleFont());
        panelTitle.add(labelTitle);
        return panelTitle;
    }

    /**
     * Create a new JPanel instance containing all inputs
     * @return JPanel - A JPanel containing all data input form itens
     */
    private JPanel createFormPanel(){
        JPanel panelForm = new JPanel();
        panelForm.setLayout(new BoxLayout( panelForm, BoxLayout.PAGE_AXIS ));
        panelForm.setBackground(Color.white);
        setupTableButtonsPanel(panelForm);
        panelForm.add( Box.createRigidArea( new Dimension(10, 10) ) );
        setupEvidenceTables(panelForm);
        return panelForm;
    }

    /**
     * Create and setup a JPanel containing all buttons to add Evidences to Table
     * @param panel - JPanel containing JButtons
     */
    private void setupTableButtonsPanel(JPanel panel){
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout( buttonPanel, BoxLayout.LINE_AXIS ));
        buttonPanel.setBackground(Color.white);
        buttonPanel.add( new JButton("Adicionar Pasta") );
        buttonPanel.add( new JButton("Adicionar arquivo") );
        buttonPanel.add( new JButton("Adicionar imagens recursivamente") );
        buttonPanel.add( new JButton("Adicionar disco") );
        panel.add(buttonPanel);
    }

    /**
     * Create and setup a JTable do manage all Evidences to be processed
     * @param panel - A JPanel to add JTable
     */
    private void setupEvidenceTables(JPanel panel){
        ArrayList<Evidence> evidencesList = new ArrayList<>();
        Evidence evidence = new Evidence();
        evidence.setFileName("File1.txt");
        evidence.setPath("/home/download");
        evidencesList.add(evidence);
        evidence = (Evidence) evidence.clone();
        evidence.setFileName("File2.iso");
        evidencesList.add(evidence);
        evidence = (Evidence) evidence.clone();
        evidence.setFileName("File3.iso");
        evidencesList.add(evidence);
        evidence = (Evidence) evidence.clone();
        evidence.setFileName("File4.iso");
        evidencesList.add(evidence);

        evidencesTableModel = new EvidencesTableModel(evidencesList);
        jtableEvidences = new JTable();
        setupTableLayout();
        panel.add( new JScrollPane(jtableEvidences));
    }

    /**
     * Adjusts the JTable layout
     */
    private void setupTableLayout(){
        jtableEvidences.setFillsViewportHeight(true);
        jtableEvidences.setRowHeight(30);
        jtableEvidences.setModel(evidencesTableModel);
        jtableEvidences.getColumn( jtableEvidences.getColumnName(3)).setCellRenderer( new TableEvidenceOptionsCellRenderer() );
        jtableEvidences.getColumn( jtableEvidences.getColumnName(3)).setCellEditor( new TableEvidenceOptionsCellEditor(new JCheckBox()) );
        jtableEvidences.getColumn( jtableEvidences.getColumnName(3)).setMaxWidth(70);
    }

    /**
     * A JPanel containing "Back" and "Next" buttons
     * @return JPanel - a new JPanel instance containing the bottom page Button
     */
    private JPanel createNavigationButtonsPanel() {
        JPanel panelButtons = new JPanel();
        panelButtons.setBackground(Color.white);
        JButton buttoCancel = new JButton("Voltar");
        buttoCancel.addActionListener( e -> NewCaseContainerPanel.getInstance().goToPreviousTab());
        JButton buttonNext = new JButton("Próximo");
        buttonNext.addActionListener( e -> NewCaseContainerPanel.getInstance().goToNextTab());
        panelButtons.add(buttoCancel);
        panelButtons.add(buttonNext);
        return panelButtons;
    }

}
