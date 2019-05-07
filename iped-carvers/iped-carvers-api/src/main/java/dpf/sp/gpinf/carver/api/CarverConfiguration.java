package dpf.sp.gpinf.carver.api;

import org.apache.tika.mime.MediaType;
import org.arabidopsis.ahocorasick.AhoCorasick;

import iped3.configuration.ConfigurationDirectory;

import java.io.File;
import java.util.HashMap;
import java.util.Properties;

public interface CarverConfiguration {
    /* returns the configured carverTypes */
    public CarverType[] getCarverTypes();

    /* Verify if a mediaType is configured to be processed */
    public boolean isToProcess(MediaType mediaType);

    /* Verify if a mediaType is configured to be processed */
    public boolean isToNotProcess(MediaType mediaType);

    /* Verify if a mediaType is configured to be carved */
    public boolean isToCarve(MediaType mediaType);

    /* Configures the Task passed as parameter */
    public void configTask(File confDir, CarvedItemListener cil) throws CarverConfigurationException;

    /* Returns the populated state machine tree */
    public AhoCorasick getPopulatedTree();

    /* Returns the populated state machine tree */
    public HashMap<CarverType, Carver> getRegisteredCarvers();

    /* initializes the configuration with the parameters in the Properties object */
    public void init(ConfigurationDirectory localConfig, Properties props) throws CarverConfigurationException;

    /* */
    public Carver createCarverFromJSName(String scriptName);

    public boolean isToIgnoreCorrupted();

}
