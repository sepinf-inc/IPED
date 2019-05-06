package dpf.sp.gpinf.indexer.util;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.StringTokenizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public final class CustomLoader {
    
    private static Logger LOGGER;

    private static ClassLoader loader = null;

    private CustomLoader() {}
    
    public static boolean isFromCustomLoader(String[] args) {
        return args != null && args.length > 0 && args[args.length - 1].equals("--customLoader"); //$NON-NLS-1$
    }
    
    public static String[] getCustomLoaderArgs(String mainClassName, String[] args, File logFile) {
        String[] newArgs = new String[args.length + 3];
        System.arraycopy(args, 0, newArgs, 1, args.length);
        newArgs[0] = mainClassName;
        newArgs[newArgs.length - 2] = logFile.getAbsolutePath();
        newArgs[newArgs.length - 1] = "--customLoader"; //$NON-NLS-1$
        return newArgs;
    }
    
    public static String getLogPathFromCustomArgs(String[] args) {
        return args[args.length - 2];
    }
    
    public static String[] clearCustomLoaderArgs(String[] args) {
        String[] newArgs = new String[args.length - 2];
        System.arraycopy(args, 0, newArgs, 0, args.length - 2);
        return newArgs;
    }

    public static void run(String[] arguments, List<File> extensionJars) throws Exception {
        
        LOGGER = LoggerFactory.getLogger(CustomLoader.class);

        // get the name of the class to be loaded from the argument list
        String className = arguments[0];
        String[] args = new String[arguments.length - 1];
        System.arraycopy( arguments, 1, args, 0, args.length );

        // load the class with the customized class loader and
        // invoke the main method
        if ( className != null ) {
            ClassLoader cl = getCustomLoader(extensionJars);
            Thread.currentThread().setContextClassLoader(cl);
            Class<?> c = cl.loadClass( className );
            Method m = c.getMethod( "main", new Class[] { String[].class } ); //$NON-NLS-1$
            m.invoke( null, new Object[] { args } );
        }
    }

    private static synchronized ClassLoader getCustomLoader(List<File> extensionJars) {
        if ( loader == null ) {

            ArrayList<URL> vec = new ArrayList<URL>();
            
            String classpath = System.getProperty( "java.class.path" ); //$NON-NLS-1$
            addUrls(vec, classpath, File.pathSeparator);
            
            for(File f : extensionJars)
                if(f != null) //$NON-NLS-1$
                    try {
                        vec.add(f.toURI().toURL());
                        LOGGER.info("Adding to classpath: " + f.getCanonicalPath()); //$NON-NLS-1$
                        
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
            
            // copy urls to array
            URL[] urls = new URL[vec.size()];
            vec.toArray(urls);

            // instantiate class loader
            loader = new CustomURLClassLoader( urls );
        }

        return loader;
    }

    private static void addUrls(ArrayList<URL> urls, String data, String delimiter) {
        StringTokenizer tokens = new StringTokenizer( data, delimiter );
        while ( tokens.hasMoreTokens() ) {
            addUrl( urls, tokens.nextToken() );
        }
    }

    private static void addUrl(ArrayList<URL> urls, String singlePath) {
        try {
            urls.add(new File(singlePath).toURI().toURL());
            
        } catch ( MalformedURLException e ) {
            e.printStackTrace();
        }
    }

    /**
     * A customized class loader which is used to load classes and resources
     * from a search path of user-defined URLs.
     */
    public static final class CustomURLClassLoader extends URLClassLoader {

        public CustomURLClassLoader( URL[] urls ) {
            super( urls );
        }

        @Override
        protected Class<?> findClass( String name ) throws ClassNotFoundException {
            throw new ClassNotFoundException( name );
        }

        @Override
        protected synchronized Class<?> loadClass( String name, boolean resolve )
            throws ClassNotFoundException
        {
            Class<?> c = findLoadedClass( name );
            if ( c == null ) {
                try {
                    c = super.findClass( name );
                } catch ( ClassNotFoundException e ) {
                    return super.loadClass( name, resolve );
                }
            }
            if ( resolve ) {
                resolveClass( c );
            }
            return c;
        }
    }
}
