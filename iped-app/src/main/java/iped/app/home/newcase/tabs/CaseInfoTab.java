package iped.app.home.newcase.tabs;/*
 * @created 08/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */

import iped.app.home.DefaultPanel;
import iped.app.home.MainFrame;
import iped.app.home.newcase.NewCaseContainerPanel;
import iped.app.home.style.StyleManager;

import javax.swing.*;
import java.awt.*;

/**
 * Case info TAB
 */
public class CaseInfoTab extends DefaultPanel {

    public CaseInfoTab(MainFrame mainFrame) {
        super(mainFrame);
    }

    /**
     * Prepare everything to be displayed
     */
    protected void createAndShowGUI(){
        this.setLayout( new BorderLayout() );
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
        JLabel labelTitle = new JLabel("Informações do caso");
        labelTitle.setFont(StyleManager.getPageTitleFont());
        panelTitle.add(labelTitle);
        return panelTitle;
    }

    /**
     * Create a new JPanel instance containing all inputs
     * @return JPanel - A JPanel containing all data input form itens
     */
    private JPanel createFormPanel(){
        JPanel panelForm = new JPanel(new GridBagLayout());
        panelForm.setBackground(Color.white);

        int column1 = 0;
        int column2 = 1;

        int column1Width = 1;
        int column2width = 2;

        double noWeightx = 0;
        double fullWeightx = 1.0;

        int currentLine = 0;

        panelForm.add(new JLabel("Número do caso:"), getGridBagConstraints(column1, currentLine, column1Width, noWeightx));
        panelForm.add(new JTextField(), getGridBagConstraints(column2, currentLine, column2width, fullWeightx));

        currentLine++;
        panelForm.add(new JLabel("Nome do caso:"), getGridBagConstraints(column1, currentLine, column1Width, noWeightx));
        panelForm.add(new JTextField(), getGridBagConstraints(column2, currentLine, column2width, fullWeightx));

        currentLine++;
        panelForm.add(new JLabel("Investigado(s):"), getGridBagConstraints(column1, currentLine, column1Width, noWeightx));
        panelForm.add(new JTextField(), getGridBagConstraints(column2, currentLine, column2width, fullWeightx));

        currentLine++;
        panelForm.add(new JLabel("Data da solicitação:"), getGridBagConstraints(column1, currentLine, column1Width, noWeightx));
        panelForm.add(new JTextField(), getGridBagConstraints(column2, currentLine, column2width, fullWeightx));

        currentLine++;
        panelForm.add(new JLabel("Demandante:"), getGridBagConstraints(column1, currentLine, column1Width, noWeightx));
        panelForm.add(new JTextField(), getGridBagConstraints(column2, currentLine, column2width, fullWeightx));

        currentLine++;
        panelForm.add(new JLabel("Organização:"), getGridBagConstraints(column1, currentLine, column1Width, noWeightx));
        panelForm.add(new JTextField(), getGridBagConstraints(column2, currentLine, column2width, fullWeightx));

        currentLine++;
        panelForm.add(new JLabel("Examinador:"), getGridBagConstraints(column1, currentLine, column1Width, noWeightx));
        panelForm.add(new JTextField(), getGridBagConstraints(column2, currentLine, column2width, fullWeightx));

        currentLine++;
        panelForm.add(new JLabel("Contato:"), getGridBagConstraints(column1, currentLine, column1Width, noWeightx));
        panelForm.add(new JTextField(), getGridBagConstraints(column2, currentLine, column2width, fullWeightx));

        currentLine++;
        panelForm.add(new JLabel("Notas:"), getGridBagConstraints(column1, currentLine, column1Width, noWeightx));
        JTextArea textAreaNotas = new JTextArea();
        textAreaNotas.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        textAreaNotas.setRows(5);
        panelForm.add(textAreaNotas, getGridBagConstraints(column2, currentLine, column2width, fullWeightx));

        currentLine++;
        GridBagConstraints c = getGridBagConstraints(column1, currentLine, column1Width+column2width, fullWeightx);
        c.insets = new Insets(10,10,10,10);
        panelForm.add(new JSeparator(), c);

        currentLine++;
        panelForm.add(new JLabel("Pasta do caso:"), getGridBagConstraints(column1, currentLine, column1Width, noWeightx));
        JPanel panelCaseFolder = new JPanel();
        panelCaseFolder.setLayout(new BoxLayout(panelCaseFolder, BoxLayout.LINE_AXIS));
        panelCaseFolder.setBackground(Color.white);
        panelCaseFolder.add(new JTextField());
        panelCaseFolder.add(new JButton("..."));
        panelForm.add(panelCaseFolder, getGridBagConstraints(column2, currentLine, column2width, fullWeightx));

        currentLine++;
        panelForm.add(new Checkbox("A pasta do caso esta em um SSD?"), getGridBagConstraints(column2, currentLine, column2width, fullWeightx));

        return panelForm;

    }

    /**
     * Create a new GridBagConstraints to be used on this page Form
     * @param tableColumnIndex - The index number of table column
     * @param tableLineIndex - The index number of table line
     * @param cellWidth - The table Cell Width
     * @param weightX - Cell Weight X
     * @return GridBagConstraints - a new GridBagConstraints instance containing all parameter passed
     */
    private GridBagConstraints getGridBagConstraints(int tableColumnIndex, int tableLineIndex, int cellWidth, double weightX) {
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = weightX;
        c.gridx = tableColumnIndex;
        c.gridy = tableLineIndex;
        c.gridwidth = cellWidth;
        c.gridheight = 1;
        c.insets = new Insets(2,10,2,10);
        return c;
    }

    /**
     * A JPanel containing "Cancel" and "Next" buttons
     * @return JPanel - a new JPanel instance containing the bottom page Button
     */
    private JPanel createNavigationButtonsPanel() {
        JPanel panelButtons = new JPanel();
        panelButtons.setBackground(Color.white);
        JButton buttoCancel = new JButton("Cancelar");
        buttoCancel.addActionListener( e -> NewCaseContainerPanel.getInstance().goHome());
        JButton buttonNext = new JButton("Próximo");
        buttonNext.addActionListener( e -> NewCaseContainerPanel.getInstance().goToNextTab() );
        panelButtons.add(buttoCancel);
        panelButtons.add(buttonNext);
        return panelButtons;
    }



}
