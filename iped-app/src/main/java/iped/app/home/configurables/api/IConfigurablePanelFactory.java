package iped.app.home.configurables.api;

import iped.app.home.MainFrame;
import iped.configuration.Configurable;
import iped.engine.task.AbstractTask;

public interface IConfigurablePanelFactory {

    /**
     * Factory method to instantiate an ConfigurablePanel suitable to the
     * configurable object
     * 
     * @param configurable
     *            - the configurable object that the created ConfigurablePanel will
     *            handle.
     * @param mainFrame
     *            - the main frame of the panel.
     */
    public IConfigurablePanel createConfigurablePanel(AbstractTask task, Configurable<?> configurable, MainFrame mainFrame);

}
