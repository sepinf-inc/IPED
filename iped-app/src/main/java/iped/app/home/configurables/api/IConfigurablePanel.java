package iped.app.home.configurables.api;

import java.awt.Component;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public interface IConfigurablePanel {
    /**
     * Internal UI components initialization method
     */
    void createConfigurableGUI();

    /**
     * Returns a reference to the main UI component. Used to install on App UI.
     */
    Component getPanel();

    /**
     * Checks if UI has changed the configurable.
     */
    boolean hasChanged();

    /**
     * Effectivelly saves the changes done in configurable object.
     */
    void applyChanges() throws ConfigurableValidationException;;

    /**
     * Register a listener to configurable object change.
     */
    void fireChangeListener(ChangeEvent changeEvent);

    /**
     * Register a listener to configurable object change.
     */
    void addChangeListener(ChangeListener changeListener);

}