package iped.app.home.configurables.popups;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTree;

import org.w3c.dom.Document;

import iped.app.home.configurables.ParsersConfigurablePanel;

public class ExternalParserPopup extends JPopupMenu implements ActionListener {
    JMenuItem createExternalParser;
    ParsersConfigurablePanel configPanel;
    JTree parsersTree;
    Document doc;

    public ExternalParserPopup(ParsersConfigurablePanel configPanel) {
        this.configPanel = configPanel;
        this.parsersTree = configPanel.getParsersTree();
        this.doc = configPanel.getDocument();
        createExternalParser = new JMenuItem("Create external parser");
        createExternalParser.addActionListener(this);
        add(createExternalParser);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == createExternalParser) {
            configPanel.startExternalParserCreation();
        }
    }
}
