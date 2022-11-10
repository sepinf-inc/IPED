package iped.app.home.newcase.tabs.process;

import javax.swing.SpringLayout;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import iped.app.home.DefaultPanel;
import iped.app.home.MainFrame;
import iped.configuration.Configurable;
import iped.utils.UTF8Properties;

/**
 * @created 10/11/2022
 * @project IPED
 * @author Patrick Dalla Bernardina
 */

public abstract class ConfigurablePanel extends DefaultPanel implements DocumentListener{
    Configurable<?> configurable;
    SpringLayout layout;
    protected boolean changed=false;

    protected ConfigurablePanel(Configurable<?> configurable, MainFrame mainFrame) {
        super(mainFrame);
        this.configurable = configurable;
    }

    /**
     * Factory method to instantiate an ConfigurablePanel suitable to the configurable object
     * @param configurable - the configurable object that the created ConfigurablePanel will handle.
     * @param mainFram - the main frame of the panel.
     */
    static ConfigurablePanel createConfigurablePanel(Configurable<?> configurable, MainFrame mainFrame) {
        Object config = configurable.getConfiguration();
        ConfigurablePanel result=null;
        
        if(config instanceof UTF8Properties) {
            result = new UTF8PropertiesConfigurablePanel((Configurable<UTF8Properties>)configurable, mainFrame);
        }else if(config instanceof String) {
            result = new TextConfigurablePanel((Configurable<String>)configurable, mainFrame);
        }else {
            result = new BeanConfigurablePanel((Configurable<?>)configurable, mainFrame);
        }
        
        return result;
    }

    /**
     * Creates the UI objects of the panel.
     * Every editable UI must install "this" object as a DocumentListener to keep track of changes 
     */
    abstract public void createConfigurableGUI();

    /**
     * Applies the changes made on UI objects to the underlying configurable object
     */
    abstract public void applyChanges();

    @Override
    protected void createAndShowGUI() {
        layout = new SpringLayout();
        
        this.setLayout(layout);
    }
    
    @Override
    public void insertUpdate(DocumentEvent e) {
        changed=true;
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        changed=true;
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        changed=true;
    }
    
    public boolean hasChanged() {
        return changed;
    }
}
