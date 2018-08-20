package dpf.sp.gpinf.carver;

import java.io.File;
import java.util.Properties;

import dpf.sp.gpinf.carver.api.CarverConfiguration;
import dpf.sp.gpinf.carver.api.CarverConfigurationException;


public class CarverConfigurationFactory {
    static CarverConfiguration carverConfig = null;
    static String CARVE_CONFIG_XML = "CarverConfig.xml"; //$NON-NLS-1$

    static public CarverConfiguration getCarverConfiguration(File confDir) throws CarverConfigurationException {
        try {
            if (carverConfig == null) {
                Class<?> classe = Thread.currentThread().getContextClassLoader().loadClass("dpf.sp.gpinf.carver.XMLCarverConfiguration");
                carverConfig = (CarverConfiguration) classe.newInstance();
                File confFile = new File(confDir, CARVE_CONFIG_XML);
                Properties props = new Properties();
                props.setProperty("XML_CONFIG_FILE", confFile.getAbsolutePath());
                props.setProperty("XML_CONFIG_DIR", confDir.getAbsolutePath());
                carverConfig.init(props);
            }
            return carverConfig;

        } catch (Exception e) {
            throw new CarverConfigurationException(e);
        }
    }

}
