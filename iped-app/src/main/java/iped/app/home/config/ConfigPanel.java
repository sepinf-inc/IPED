package iped.app.home.config;

/*
 * @created 07/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */

import iped.app.home.DefaultPanel;
import iped.app.home.MainFrame;
import iped.app.home.MainFrameCardsNames;
import iped.app.home.style.StyleManager;

import javax.swing.*;
import java.awt.*;

/**
 * Her we'll manage IPED configuration
 * the main config file is LocalConfig.txt
 */
public class ConfigPanel extends DefaultPanel {

    public ConfigPanel(MainFrame mainFrame) {
        super(mainFrame);
    }

    /**
     * Prepare everything to be displayed
     */
    protected void createAndShowGUI(){
        this.setLayout( new BoxLayout( this, BoxLayout.Y_AXIS ) );
        this.add(createTitlePanel());
        this.add(createFormPanel());
        this.add(Box.createVerticalGlue());
        this.add(createButtonsPanel());
    }

    /**
     * Create a new JPanel instance containing the Page Title
     * @return - JPanel containing the Page Title
     */
    private JPanel createTitlePanel(){
        JPanel panelTitle = new JPanel();
        panelTitle.setBackground(Color.white);
        JLabel labelTitle = new JLabel("Configurações de ambiente");
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

        int labelsWidth = 1;
        double labelsWeightx = 0;
        double inputsWeightx = 1.0;
        int linha = 0;

        panelForm.add(new JLabel("Idioma:"), getGridBagConstraints(0, linha, labelsWidth, labelsWeightx));
        panelForm.add(new JComboBox<>(), getGridBagConstraints(labelsWidth+1, linha, 2, inputsWeightx));

        linha++;
        panelForm.add(new JLabel("N de Threads:"), getGridBagConstraints(0, linha, labelsWidth, labelsWeightx));
        panelForm.add(new JTextField(), getGridBagConstraints(labelsWidth+1, linha, 2, inputsWeightx));

        linha++;
        panelForm.add(new JLabel("Diretório para temporários:"), getGridBagConstraints(0, linha, labelsWidth, labelsWeightx));
        panelForm.add(new JTextField(), getGridBagConstraints(labelsWidth+1, linha, 2, inputsWeightx));

        linha++;
        panelForm.add(new JLabel("Banco de dados de Hash:"), getGridBagConstraints(0, linha, labelsWidth, labelsWeightx));
        panelForm.add(new JTextField(), getGridBagConstraints(labelsWidth+1, linha, 2, inputsWeightx));

        linha++;
        panelForm.add(new JLabel("JAR do SleuthKit:"), getGridBagConstraints(0, linha, labelsWidth, labelsWeightx));
        panelForm.add(new JTextField(), getGridBagConstraints(labelsWidth+1, linha, 2, inputsWeightx));

        linha++;
        panelForm.add(new JLabel("Executável do MPlayer:"), getGridBagConstraints(0, linha, labelsWidth, labelsWeightx));
        panelForm.add(new JTextField(), getGridBagConstraints(labelsWidth+1, linha, 2, inputsWeightx));

        linha++;
        panelForm.add(new JLabel("Diretório de Plugins opcionais:"), getGridBagConstraints(0, linha, labelsWidth, labelsWeightx));
        panelForm.add(new JTextField(), getGridBagConstraints(labelsWidth+1, linha, 2, inputsWeightx));

        linha++;
        panelForm.add(new JLabel("Diretório do RegRipper:"), getGridBagConstraints(0, linha, labelsWidth, labelsWeightx));
        panelForm.add(new JTextField(), getGridBagConstraints(labelsWidth+1, linha, 2, inputsWeightx));

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
        c.insets = new Insets(2, 0,2, 0);
        return c;
    }

    /**
     * A JPanel containing "Save" and "Cancel" buttons
     * @return JPanel - a new JPanel instance containing the bottom page Button
     */
    private JPanel createButtonsPanel() {
        JPanel panelButtons = new JPanel();
        panelButtons.setBackground(Color.white);
        JButton buttonSave = new JButton("Salvar");
        buttonSave.addActionListener( e -> mainFrame.showPanel(MainFrameCardsNames.HOME));
        JButton buttonCancel = new JButton("Cancelar");
        buttonCancel.addActionListener( e -> mainFrame.showPanel(MainFrameCardsNames.HOME));
        panelButtons.add(buttonSave);
        panelButtons.add(buttonCancel);
        return panelButtons;
    }

}
