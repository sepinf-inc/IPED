package jep;

/**
 * Copyright (c) 2006-2019 JEP AUTHORS.
 *
 * This file is licensed under the the zlib/libpng License.
 *
 * This software is provided 'as-is', without any express or implied
 * warranty. In no event will the authors be held liable for any
 * damages arising from the use of this software.
 * 
 * Permission is granted to anyone to use this software for any
 * purpose, including commercial applications, and to alter it and
 * redistribute it freely, subject to the following restrictions:
 * 
 *     1. The origin of this software must not be misrepresented; you
 *     must not claim that you wrote the original software. If you use
 *     this software in a product, an acknowledgment in the product
 *     documentation would be appreciated but is not required.
 * 
 *     2. Altered source versions must be plainly marked as such, and
 *     must not be misrepresented as being the original software.
 * 
 *     3. This notice may not be removed or altered from any source
 *     distribution.
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * A singleton that searches for loaded classes from the JRE and the Java
 * classpath. This is the default ClassEnquirer that is used if no ClassEnquirer
 * is specified when constructing an Interpreter. ClassList is also used by the
 * command line <code>jep</code> script.
 * 
 * PS: This is a copy and paste of ClassList class from JEP with a fix for
 * https://github.com/ninia/jep/issues/323. To be removed when we upgrade to
 * fixed JEP (Luis Nassif).
 * 
 * @author Mike Johnson
 */
public class JEPClassFinder implements ClassEnquirer {

    private static JEPClassFinder inst;

    // storage for package, member classes
    private Map<String, List<String>> packageToClassMap = new HashMap<>();

    // storage for package, sub-packages based on classes found
    private Map<String, List<String>> packageToSubPackageMap = new HashMap<>();

    private JEPClassFinder() throws JepException {
        loadClassPath();
        loadPackages();
        loadClassList();

        for (String restrictedPkg : ClassEnquirer.RESTRICTED_PKG_NAMES) {
            packageToClassMap.remove(restrictedPkg);
            packageToSubPackageMap.remove(restrictedPkg);
        }
    }

    /**
     * load jar files from class path
     * 
     */
    private void loadClassPath() {
        StringTokenizer tok = new StringTokenizer(System.getProperty("java.class.path"),
                System.getProperty("path.separator"));

        Queue<String> queue = new LinkedList<>();
        Set<String> seen = new HashSet<>();

        while (tok.hasMoreTokens()) {
            String el = tok.nextToken();
            queue.add(el);
            seen.add(el);
        }

        while (!queue.isEmpty()) {
            String el = queue.remove();

            if (!el.toLowerCase().endsWith(".jar")) {
                // ignore filesystem classpath
                continue;
            }

            // make sure it exists
            File file = new File(el);
            if (!file.exists() || !file.canRead())
                continue;

            try (JarFile jfile = new JarFile(el, false)) {

                // add entries from manifest to check later
                Manifest manifest = jfile.getManifest();
                if (manifest != null) {
                    String classpath = manifest.getMainAttributes().getValue(Attributes.Name.CLASS_PATH);

                    if (classpath != null) {
                        String[] relativePaths = classpath.split(" ");

                        for (String relativePath : relativePaths) {
                            // fix for https://github.com/ninia/jep/issues/323
                            String path = relativePath;
                            if (file.getParent() != null) {
                                path = file.getParent() + File.separator + path;
                            }
                            if (!seen.contains(path)) {
                                queue.add(path);
                                seen.add(path);
                            }
                        }
                    }
                }

                Enumeration<JarEntry> entries = jfile.entries();
                while (entries.hasMoreElements()) {
                    String entry = entries.nextElement().getName();

                    if (!entry.toLowerCase().endsWith(".class")) {
                        // not a class file, so we don't care
                        continue;
                    }

                    // entry looks like:
                    // pkg/subpkg/.../ClassName.class
                    // blah.class
                    // jep/ClassList.class
                    int end = entry.lastIndexOf('/');
                    if (end < 0) {
                        // a class name without a package but inside a jar
                        continue;
                    }
                    String pname = entry.substring(0, end).replace('/', '.');

                    String cname = stripClassExt(entry.substring(end + 1));
                    if (!cname.contains("$")) {
                        addClass(pname, cname);
                    }
                }
            } catch (IOException e) {
                // debugging only
                e.printStackTrace();
            }
        }
    }

    /*
     * the jre will tell us about what jar files it has open. use that facility to
     * get a list of packages. then read the files ourselves since java won't share.
     */
    private void loadPackages() throws JepException {
        ClassLoader cl = this.getClass().getClassLoader();

        Package[] ps = Package.getPackages();
        for (Package p : ps) {
            String pname = p.getName().replace('.', '/');
            URL url = cl.getResource(pname);

            if (url == null || !url.getProtocol().equals("file"))
                continue;

            File dir = null;
            try {
                dir = new File(url.toURI());
            } catch (java.net.URISyntaxException e) {
                throw new JepException(e);
            }

            for (File classfile : dir.listFiles(new ClassFilenameFilter()))
                addClass(p.getName(), stripClassExt(classfile.getName()));
        }
    }

    // don't pass me nulls.
    // strips .class from a file name.
    private String stripClassExt(String name) {
        return name.substring(0, name.length() - 6);
    }

    /*
     * The jre keeps a list of classes in the lib folder. We don't have a better way
     * to figure out what's in the java package, so this is my little hack.
     */
    private void loadClassList() throws JepException {
        String version = System.getProperty("java.version");

        /*
         * The thread's context ClassLoader is useful if resources have a different
         * ClassLoader than classes (e.g. tomcat), while the Jep.class ClassLoader is
         * useful if running inside an OSGi container as a Bundle (e.g. eclipse).
         */
        ClassLoader[] classloadersToTry = new ClassLoader[] { Thread.currentThread().getContextClassLoader(),
                Jep.class.getClassLoader() };
        String rsc = "jep/classlist_";
        if (version.startsWith("1.7")) {
            rsc += "7";
        } else if (version.startsWith("1.8")) {
            rsc += "8";
        } else if (version.startsWith("9.")) {
            rsc += "9";
        } else if (version.startsWith("10.")) {
            rsc += "10";
        } else {
            rsc += "11";
        }
        rsc += ".txt";

        InputStream in = null;
        BufferedReader reader = null;
        ClassLoader cl = null;
        int i = 0;
        try {
            while (in == null && i < classloadersToTry.length) {
                cl = classloadersToTry[i];
                in = cl.getResourceAsStream(rsc);
                i++;
            }

            if (in == null) {
                throw new JepException("ClassList couldn't find resource " + rsc);
            }

            reader = new BufferedReader(new InputStreamReader(in));

            String line = "";
            while ((line = reader.readLine()) != null) {
                // ignore any class with $
                if (line.indexOf('$') > -1)
                    continue;

                // lines in the file look like: java/lang/String
                // split on /
                String[] parts = line.split("\\/");
                StringBuilder pname = new StringBuilder();
                String cname = parts[parts.length - 1];

                for (i = 0; i < parts.length - 1; i++) {
                    pname.append(parts[i]);
                    if (i < parts.length - 2)
                        pname.append(".");
                }

                addClass(pname.toString(), cname);
            }
        } catch (IOException e) {
            throw new JepException(e);
        } finally {
            try {
                if (reader != null)
                    reader.close();
            } catch (IOException ee) {
                // ignore
            }
        }
    }

    // add a class with given package name
    private void addClass(String pname, String cname) {
        List<String> el = packageToClassMap.get(pname);
        if (el == null) {
            el = new ArrayList<>();
            packageToClassMap.put(pname, el);
        }

        // convert to style we need in C code
        String fqname = pname + "." + cname;

        // unlikely, but don't add a class twice
        if (!el.contains(fqname)) {
            el.add(fqname);
        }

        // now figure out any sub-packages based on the package name
        int dotIdx = pname.indexOf(".");
        while (dotIdx > -1) {
            String pkgStart = pname.substring(0, dotIdx);
            int nextDot = pname.indexOf(".", dotIdx + 1);
            String subPkg = null;
            if (nextDot > -1) {
                subPkg = pname.substring(dotIdx + 1, nextDot);
            } else {
                subPkg = pname.substring(dotIdx + 1);
            }
            List<String> pl = packageToSubPackageMap.get(pkgStart);
            if (pl == null) {
                pl = new ArrayList<>();
                packageToSubPackageMap.put(pkgStart, pl);
            }
            if (!pl.contains(subPkg)) {
                pl.add(subPkg);
            }
            dotIdx = nextDot;
        }
    }

    /**
     * get classnames in package
     * 
     * @param pkg
     *            a <code>String</code> value
     * @return <code>String[]</code> array of class names
     */
    @Override
    public String[] getClassNames(String pkg) {
        List<String> classes = packageToClassMap.get(pkg);
        if (classes == null) {
            return new String[0];
        }

        String[] ret = new String[classes.size()];
        classes.toArray(ret);
        return ret;
    }

    @Override
    public String[] getSubPackages(String p) {
        List<String> el = packageToSubPackageMap.get(p);
        if (el == null) {
            return new String[0];
        }

        return el.toArray(new String[0]);
    }

    /**
     * Checks if the String is known to the ClassList as an available package
     * 
     * @param s
     *            a <code>String</code> to check
     * @return if the String is considered a Java package
     */
    @Override
    public boolean isJavaPackage(String s) {
        return (packageToClassMap.containsKey(s)) || (packageToSubPackageMap.containsKey(s));
    }

    /**
     * get ClassList instance
     * 
     * @return <code>ClassList</code> instance
     * @throws JepException
     *             if an error occurs
     */
    public static synchronized JEPClassFinder getInstance() throws JepException {
        if (JEPClassFinder.inst == null)
            JEPClassFinder.inst = new JEPClassFinder();
        return JEPClassFinder.inst;
    }

    /**
     * for testing only
     * 
     * @param argv
     *            command line arguments
     * @throws Throwable
     *             if an error occurs
     */
    public static void main(String argv[]) throws Throwable {
        if (argv.length > 0) {
            for (String arg : argv) {
                for (String c : JEPClassFinder.getInstance().getClassNames(arg))
                    System.out.println(c);
            }
        } else {
            for (String c : JEPClassFinder.getInstance().getClassNames("java.lang"))
                System.out.println(c);

            // test loadPackages
            for (String c : JEPClassFinder.getInstance().getClassNames("jep"))
                System.out.println(c);
        }
    }
}

class ClassFilenameFilter implements java.io.FilenameFilter {
    @Override
    public boolean accept(File dir, String name) {
        return (name != null && name.toLowerCase().endsWith(".class"));
    }
}