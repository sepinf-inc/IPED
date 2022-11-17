package iped.app.home.configurables;

import java.awt.BorderLayout;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;

import org.apache.commons.lang3.tuple.Pair;

import iped.app.home.MainFrame;
import iped.configuration.Configurable;
import iped.engine.config.RegexTaskConfig.RegexEntry;

public class RegexConfigurablePanel extends ConfigurablePanel{
    protected JTextPane textArea;

    protected RegexConfigurablePanel(Configurable<?> configurable, MainFrame mainFrame) {
        super(configurable, mainFrame);
    }

    @Override
    public void applyChanges() {
        Pair<Boolean, List<RegexEntry>> config = (Pair<Boolean, List<RegexEntry>>) configurable.getConfiguration();
        List<RegexEntry> list = (List<RegexEntry>) config.getValue();
    }

    @Override
    public void createConfigurableGUI() {
        JCheckBox checkField = new JCheckBox();
        Boolean b = ((Pair<Boolean,?>)configurable.getConfiguration()).getKey();
        checkField.setText("Format matches.");
        checkField.addChangeListener(e->{
            changed=true;
        });
        this.setLayout(new BorderLayout());
        this.add(checkField, BorderLayout.NORTH);
        
        textArea = new JTextPane();
        textArea.setAutoscrolls(true);
        JScrollPane txtAreaScroll = new JScrollPane();
        txtAreaScroll.setViewportView(textArea);
        txtAreaScroll.setAutoscrolls(true);
        this.add(txtAreaScroll,BorderLayout.CENTER);

        Collection<RegexEntry> col = (Collection<RegexEntry>) ((Pair)configurable.getConfiguration()).getValue();
        StringBuffer output = new StringBuffer(); 
        for (Iterator iterator = col.iterator(); iterator.hasNext();) {
            RegexEntry o = (RegexEntry) iterator.next();
            output.append(o.toString());
            output.append("\n");
        }
        textArea.setText(output.toString());
        textArea.getDocument().addDocumentListener(this);
    }

}
