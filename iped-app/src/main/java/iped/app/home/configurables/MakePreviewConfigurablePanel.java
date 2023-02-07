package iped.app.home.configurables;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.Collection;
import java.util.Iterator;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import iped.app.home.MainFrame;
import iped.app.ui.controls.textarea.RegexTextPane;
import iped.engine.config.MakePreviewConfig;

public class MakePreviewConfigurablePanel extends ConfigurablePanel {
    MakePreviewConfig previewConfig;
    protected RegexTextPane textAreaSupportedMimes;
    protected RegexTextPane textAreaSupportedMimesWithLinks;

    protected MakePreviewConfigurablePanel(MakePreviewConfig configurable, MainFrame mainFrame) {
        super(configurable, mainFrame);
        this.previewConfig=configurable;
    }

    @Override
    public void createConfigurableGUI() {
        textAreaSupportedMimes = new RegexTextPane();
        textAreaSupportedMimes.setAutoscrolls(true);
        textAreaSupportedMimes.getDocument().addDocumentListener(this);
        JScrollPane txtAreaScroll = new JScrollPane();
        txtAreaScroll.setViewportView(textAreaSupportedMimes);
        txtAreaScroll.setAutoscrolls(true);
        this.setLayout(new BorderLayout());
        JPanel textPanel = new JPanel(new BorderLayout());
        textPanel.add(new JLabel("Supported Mimetypes:"),BorderLayout.NORTH);
        textPanel.add(txtAreaScroll,BorderLayout.CENTER);
        this.add(textPanel, BorderLayout.CENTER);
        Collection<String> col = (Collection<String>) previewConfig.getSupportedMimes();
        StringBuffer output = new StringBuffer(); 
        for (Iterator iterator = col.iterator(); iterator.hasNext();) {
            String string = (String) iterator.next();
            output.append(string);
            output.append("\n");
        }
        textAreaSupportedMimes.getDocument().removeDocumentListener(this);
        textAreaSupportedMimes.setText(output.toString());
        textAreaSupportedMimes.getDocument().addDocumentListener(this);

        
        textAreaSupportedMimesWithLinks = new RegexTextPane();
        textAreaSupportedMimesWithLinks.setAutoscrolls(true);
        textAreaSupportedMimesWithLinks.getDocument().addDocumentListener(this);
        JScrollPane txtAreaScroll2WithLinks= new JScrollPane();
        txtAreaScroll2WithLinks.setViewportView(textAreaSupportedMimesWithLinks);
        txtAreaScroll2WithLinks.setAutoscrolls(true);
        textPanel = new JPanel(new BorderLayout());
        textPanel.add(new JLabel("Supported Mimetypes with links:"),BorderLayout.NORTH);
        textPanel.add(txtAreaScroll2WithLinks,BorderLayout.CENTER);
        this.add(textPanel, BorderLayout.SOUTH);
        col = (Collection<String>) previewConfig.getSupportedMimesWithLinks();
        output = new StringBuffer(); 
        for (Iterator iterator = col.iterator(); iterator.hasNext();) {
            String string = (String) iterator.next();
            output.append(string);
            output.append("\n");
        }
        textAreaSupportedMimesWithLinks.getDocument().removeDocumentListener(this);
        textAreaSupportedMimesWithLinks.setText(output.toString());
        textAreaSupportedMimesWithLinks.getDocument().addDocumentListener(this);    
    }

    @Override
    public void applyChanges() throws ConfigurableValidationException {
        // TODO Auto-generated method stub
        
    }

    
}
