package iped.app.home.processmanager;

/*
 * @created 27/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */

import iped.app.home.DefaultPanel;
import iped.app.home.MainFrame;
import iped.app.home.style.StyleManager;

import javax.swing.*;
import java.awt.*;

public class ProcessManager extends DefaultPanel {


    private final String START_PROCESS = "startprocess";

    public ProcessManager(MainFrame mainFrame) {
        super(mainFrame);
    }

    @Override
    protected void createAndShowGUI() {
        this.setLayout( new BoxLayout( this, BoxLayout.PAGE_AXIS ) );
        this.add(createTitlePanel());
        this.add(createFormPanel());
    }

    private JPanel createTitlePanel(){
        JPanel panelTitle = new JPanel();
        panelTitle.setBackground(Color.white);
        JLabel labelTitle = new JLabel("Aguarde seu processamento esta sendo iniciado");
        labelTitle.setFont(StyleManager.getPageTitleFont());
        panelTitle.add(labelTitle);
        return panelTitle;
    }

    private JPanel createFormPanel(){
        JPanel panelForm = new JPanel(new CardLayout());
        panelForm.add(new StartingPanel(mainFrame), START_PROCESS);

        return panelForm;
    }

    public void showPanel(String cardName){
        ((CardLayout) this.getLayout()).show(this, cardName);
    }

}
