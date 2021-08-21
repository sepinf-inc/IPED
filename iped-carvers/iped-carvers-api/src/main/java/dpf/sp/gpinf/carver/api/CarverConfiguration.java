package dpf.sp.gpinf.carver.api;

import java.io.File;
import java.util.HashMap;

import org.apache.tika.mime.MediaType;
import org.arabidopsis.ahocorasick.AhoCorasick;

public interface CarverConfiguration {
    /* returns the configured carverTypes */
    public CarverType[] getCarverTypes();

    /* Verify if a mediaType is configured to be processed */
    public boolean isToProcess(MediaType mediaType);

    /* Verify if a mediaType is configured to be processed */
    public boolean isToNotProcess(MediaType mediaType);

    /* Verify if a mediaType is configured to be carved */
    public boolean isToCarve(MediaType mediaType);

    /* Configures the listener passed as parameter */
    public void configListener(CarvedItemListener cil) throws CarverConfigurationException;

    /* Returns the populated state machine tree */
    public AhoCorasick getPopulatedTree();

    /* Returns the populated state machine tree */
    public HashMap<CarverType, Carver> getRegisteredCarvers();

    public Carver createCarverFromJSName(File scriptFile);

    public boolean isToIgnoreCorrupted();

}
