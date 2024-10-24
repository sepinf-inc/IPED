package iped.app.home.configurables;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import iped.app.home.MainFrame;
import iped.app.home.configurables.api.IConfigurablePanel;
import iped.app.home.configurables.api.IConfigurablePanelFactory;
import iped.app.home.configurables.bean.BeanConfigurablePanel;
import iped.configuration.Configurable;
import iped.engine.config.CategoryConfig;
import iped.engine.config.CategoryToExpandConfig;
import iped.engine.config.ExternalParsersConfig;
import iped.engine.config.MakePreviewConfig;
import iped.engine.config.ParsersConfig;
import iped.engine.config.RegexTaskConfig;
import iped.engine.task.AbstractTask;
import iped.engine.task.ExportFileTask;
import iped.engine.task.carver.XMLCarverConfiguration;
import iped.utils.UTF8Properties;

public class ConfigurablePanelFactory implements IConfigurablePanelFactory {

    private static IConfigurablePanelFactory singleton;

    /**
     * Hard coded factory to instantiate IConfigurablePanel suitable to the
     * configurable object.
     * 
     * @param configurable
     *            - the configurable object that the created ConfigurablePanel will
     *            handle.
     * @param mainFrame
     *            - the main frame of the panel.
     */
    @Override
    public IConfigurablePanel createConfigurablePanel(AbstractTask task, Configurable<?> configurable, MainFrame mainFrame) {
        Object config = configurable.getConfiguration();
        ConfigurablePanel result = null;

        if (config instanceof UTF8Properties) {
            result = new UTF8PropertiesConfigurablePanel((Configurable<UTF8Properties>) configurable, mainFrame);
        } else if (configurable instanceof ParsersConfig) {
            result = new ParsersConfigurablePanel((ParsersConfig) configurable, mainFrame);
        } else if (configurable instanceof ExternalParsersConfig) {
            result = new ParsersConfigurablePanel((ExternalParsersConfig) configurable, mainFrame);
        } else if (configurable instanceof CategoryConfig) {
            result = new SetCategoryConfigurablePanel((CategoryConfig) configurable, mainFrame);
        } else if (config instanceof String) {
            /* try to see if it is a json object */
            boolean isJson = false;
            String strConfig = (String) config;
            if (strConfig.trim().startsWith("{")) {
                JSONParser parser = new JSONParser();
                try {
                    parser.parse(strConfig);
                    isJson = true;
                } catch (ParseException e) {
                }
            }

            if (isJson) {
                result = new JSONConfigurablePanel((Configurable<String>) configurable, mainFrame);
            } else {
                /* try to see if it is a xml object */
                try {
                    if (strConfig.trim().startsWith("<?xml")) {
                        result = new XMLConfigurablePanel((Configurable<String>) configurable, mainFrame);
                    }
                } finally {
                    if (result == null) {
                        result = new TextConfigurablePanel((Configurable<String>) configurable, mainFrame);
                    }
                }
            }
        } else if (configurable instanceof CategoryToExpandConfig) {
            result = new CategorySetConfigPanel((CategoryToExpandConfig) configurable, mainFrame);
        } else if (configurable instanceof MakePreviewConfig) {
            result = new MakePreviewConfigurablePanel((MakePreviewConfig) configurable, mainFrame);
        } else if (config instanceof XMLCarverConfiguration) {
            result = new XMLCarverConfigurablePanel((Configurable<XMLCarverConfiguration>) configurable, mainFrame);
        } else if (configurable.getClass().equals(RegexTaskConfig.class)) {
            result = new RegexConfigurablePanel((Configurable<?>) configurable, mainFrame);
        } else if (config instanceof Collection<?>) {
            if (task instanceof ExportFileTask && config instanceof Set<?>) {
                result = new CategorySetConfigPanel((Configurable<Set<String>>) configurable, mainFrame);
            } else {
                Type type;
                try {
                    type = configurable.getClass().getMethod("getConfiguration").getGenericReturnType();
                    if (type instanceof ParameterizedType) {
                        ParameterizedType ptype = (ParameterizedType) type;
                        Type[] typeArguments = ptype.getActualTypeArguments();
                        if (typeArguments[0].getTypeName().equals(String.class.getCanonicalName())) {
                            result = new StringSetConfigurablePanel((Configurable<HashSet<String>>) configurable, mainFrame);
                        }
                    }
                } catch (NoSuchMethodException | SecurityException e) {
                    e.printStackTrace();
                }
            }
        }
        if (result == null) {
            result = new BeanConfigurablePanel((Configurable<?>) configurable, mainFrame);
        }

        return result;
    }

    public static IConfigurablePanelFactory getInstance() {
        if (singleton == null) {
            singleton = new ConfigurablePanelFactory();
        }
        return singleton;
    }

}
