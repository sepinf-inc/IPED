package iped.app.home.newcase.tabs.process;

import javax.swing.JScrollPane;

import iped.app.home.MainFrame;
import iped.app.ui.controls.textarea.RegexTextPane;
import iped.configuration.Configurable;

public class TextConfigurablePanel extends ConfigurablePanel{

    protected TextConfigurablePanel(Configurable<String> configurable, MainFrame mainFrame) {
        super(configurable, mainFrame);
    }

    public void createConfigurableGUI() {
        RegexTextPane textArea = new RegexTextPane();
        textArea.setAutoscrolls(true);
        textArea.setText(configurable.getConfiguration().toString());
        JScrollPane txtAreaScroll = new JScrollPane();
        txtAreaScroll.setViewportView(textArea);
        txtAreaScroll.setAutoscrolls(true);        
        this.add(txtAreaScroll);
    }

    @Override
    public void applyChanges() {
        // TODO Auto-generated method stub
        
    }

}
