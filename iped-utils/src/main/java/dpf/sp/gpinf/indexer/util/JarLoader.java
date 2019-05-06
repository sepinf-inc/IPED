package dpf.sp.gpinf.indexer.util;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JarLoader {
    
   private static Logger LOGGER = LoggerFactory.getLogger(JarLoader.class);
   
   private static volatile Boolean javaFXPresent = null;

   @Deprecated
   public boolean loadJavaFX() {
	    if(javaFXPresent != null)
	        return javaFXPresent;
	    
        javaFXPresent = false;
        String javaVersion = System.getProperty("java.version"); //$NON-NLS-1$
        if (javaVersion.compareTo("1.8") >= 0) //$NON-NLS-1$
            javaFXPresent = true;
        else if (javaVersion.compareTo("1.7") > 0) { //$NON-NLS-1$
            String minor = javaVersion.substring(javaVersion.indexOf("_") + 1); //$NON-NLS-1$
            if (!javaVersion.startsWith("1.7") || Integer.valueOf(minor) >= 6) { //$NON-NLS-1$
                String fxJar = "jfxrt.jar"; //$NON-NLS-1$
                String javaLib = System.getProperty("java.home") + File.separator + "lib"; //$NON-NLS-1$ //$NON-NLS-2$
                if (new File(javaLib + File.separator + "ext" + File.separator + fxJar).exists()) { //$NON-NLS-1$
                    javaFXPresent = true;
                } else {
                    javaFXPresent = loadJar(new File(javaLib + File.separator + fxJar));
                }
            }
        }
        return javaFXPresent;
    }

	@Deprecated
    public boolean loadJar(File file) {
        if (!file.exists()) {
            return false;
        }
        try {
            URL jarUrl = file.toURI().toURL();
            ClassLoader sysloader = ClassLoader.getSystemClassLoader();
            Class<?> sysclass = URLClassLoader.class;
            Class<?>[] parameters = new Class[]{URL.class};
            Method method = sysclass.getDeclaredMethod("addURL", parameters); //$NON-NLS-1$
            method.setAccessible(true);
            method.invoke(sysloader, new Object[]{jarUrl});
            LOGGER.info(jarUrl.toString() + " loaded"); //$NON-NLS-1$
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

    }

    @Deprecated
    public boolean loadJarDir(File dir) {
        String[] jarNameList = dir.list();
        if (jarNameList != null) {
            for (int i = 0; i < jarNameList.length; i++) {
                File jar = new File(dir, jarNameList[i]);
                if (jar.getName().toLowerCase().endsWith(".jar") && !loadJar(jar)) { //$NON-NLS-1$
                    return false;
                }
            }
        }
        return true;
    }
}
