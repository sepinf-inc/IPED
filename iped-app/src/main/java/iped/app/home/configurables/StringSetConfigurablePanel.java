package iped.app.home.configurables;

import java.util.Collection;
import java.util.Iterator;

import iped.app.home.MainFrame;
import iped.configuration.Configurable;

public class StringSetConfigurablePanel extends TextConfigurablePanel {

    private static final long serialVersionUID = 6101850157753514466L;

    protected StringSetConfigurablePanel(Configurable<?> configurable, MainFrame mainFrame) {
        super(configurable, mainFrame);
    }

    @Override
    public void createConfigurableGUI() {
        super.createConfigurableGUI();
        Collection<String> col = (Collection<String>) configurable.getConfiguration();
        StringBuffer output = new StringBuffer(); 
        for (Iterator iterator = col.iterator(); iterator.hasNext();) {
            String string = (String) iterator.next();
            output.append(string);
            output.append("\n");
        }
        textArea.setText(output.toString());
        textArea.getDocument().addDocumentListener(this);
    }

    @Override
    public void applyChanges() {
        Collection<String> col =(Collection<String>) configurable.getConfiguration();
        col.removeAll(col);

        String content = textArea.getText();
        for (String line : content.split("\n")) { //$NON-NLS-1$
            line = line.trim();
            if (line.startsWith("#") || line.isEmpty()) { //$NON-NLS-1$
                continue;
            }
            col.add(line);
        }
    }

}
