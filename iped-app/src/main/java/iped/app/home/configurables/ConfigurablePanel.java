package iped.app.home.configurables;

import java.awt.Component;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyVetoException;
import java.beans.VetoableChangeListener;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.SpringLayout;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import iped.app.home.DefaultPanel;
import iped.app.home.MainFrame;
import iped.app.home.configurables.api.ConfigurableValidationException;
import iped.app.home.configurables.api.IConfigurablePanel;
import iped.configuration.Configurable;

/**
 * @created 10/11/2022
 * @project IPED
 * @author Patrick Dalla Bernardina
 */

public abstract class ConfigurablePanel extends DefaultPanel implements DocumentListener, IConfigurablePanel, VetoableChangeListener {
    protected Configurable<?> configurable;
    protected SpringLayout layout;
    protected boolean changed = false;

    List<ChangeListener> changeListeners = new ArrayList<ChangeListener>();

    protected ConfigurablePanel(Configurable<?> configurable, MainFrame mainFrame) {
        super(mainFrame);
        this.configurable = configurable;
    }

    /**
     * Creates the UI objects of the panel. Every editable UI must install "this"
     * object as a DocumentListener to keep track of changes
     */
    abstract public void createConfigurableGUI();

    /**
     * Applies the changes made on UI objects to the underlying configurable object
     */
    abstract public void applyChanges() throws ConfigurableValidationException;

    @Override
    protected void createAndShowGUI() {
        layout = new SpringLayout();

        this.setLayout(layout);
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        changedUpdate(e);
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        changedUpdate(e);
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        changed = true;
        fireChangeListener(new ChangeEvent(e.getDocument()));
    }

    public boolean hasChanged() {
        return changed;
    }

    public Configurable<?> getConfigurable() {
        return configurable;
    }

    public void setConfigurable(Configurable<?> configurable) {
        this.configurable = configurable;
    }

    public void fireChangeListener(ChangeEvent e) {
        for (Iterator iterator = changeListeners.iterator(); iterator.hasNext();) {
            ChangeListener changeListener = (ChangeListener) iterator.next();
            changeListener.stateChanged(e);
        }
    }

    public void addChangeListener(ChangeListener changeListener) {
        changeListeners.add(changeListener);
    }

    public void removeChangeListener(ChangeListener changeListener) {
        changeListeners.remove(changeListener);
    }

    @Override
    public Component getPanel() {
        return this;
    }

    public void vetoableChange(PropertyChangeEvent e) throws PropertyVetoException {
        changed = true;
        fireChangeListener(new ChangeEvent(e.getSource()));
    }
}
