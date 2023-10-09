package iped.engine.task.leappbridge;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

import org.apache.tika.metadata.Metadata;

import iped.engine.config.Configuration;
import iped.engine.config.TaskInstallerConfig;
import iped.engine.util.PathPatternMap;
import iped.properties.BasicProps;
import jep.Jep;
import jep.python.PyCallable;

public class ALeappPluginsManager {

    Boolean initialized = false;
    public Jep jep;
    PathPatternMap<LeapArtifactsPlugin> pathPatternsMap = new PathPatternMap<LeapArtifactsPlugin>();
    HashMap<String, LeapArtifactsPlugin> plugins = new HashMap<String, LeapArtifactsPlugin>();

    public LeapArtifactsPlugin detect(InputStream stream, Metadata metadata) throws IOException {
        String path = metadata.get(BasicProps.PATH);
        LeapArtifactsPlugin artifacts = pathPatternsMap.getPatternMatch(path);

        return artifacts;
    }

    public Collection<LeapArtifactsPlugin> getPlugins() {
        return plugins.values();
    }

    public boolean hasPatternMatch(String file, LeapArtifactsPlugin plugin) {
        for (String pattern : plugin.patterns) {
            if (pattern.startsWith("*/")) {
                pattern = "*" + pattern;
            }
            if (FileSystems.getDefault().getPathMatcher("glob:" + pattern).matches(Path.of(file))) {
                return true;
            }
        }
        return false;

    }

    synchronized public void init(Jep pjep) {
        this.jep = pjep;

        synchronized (initialized) {
            if (initialized) {
                return;
            } else {
                jep.eval("import sys");

                if (jep == null) {
                    return;
                }

                File pythonParsersFolder = new File(Configuration.getInstance().configPath,
                        TaskInstallerConfig.SCRIPT_BASE);
                File aleappPath = new File(pythonParsersFolder, "ALEAPP");
                File scriptsPath = new File(aleappPath, "scripts");
                File artifactsPath = new File(scriptsPath, "artifacts");
                if (artifactsPath.exists()) {
                    jep.eval("sys.path.append('" + aleappPath.getAbsolutePath().replace("\\", "\\\\") + "')");
                    jep.eval("sys.path.append('" + scriptsPath.getAbsolutePath().replace("\\", "\\\\") + "')");
                    jep.eval("sys.path.append('" + artifactsPath.getAbsolutePath().replace("\\", "\\\\") + "')");
                    File[] scripts = artifactsPath.listFiles();
                    if (scripts != null) {
                        for (File file : scripts) {
                            if (!file.isFile()) {
                                continue;
                            }
                            loadArtifacts(file);
                        }
                    }

                }

                initialized = true;
            }
        }

    }

    private void loadArtifacts(File scriptFile) {
        try {

            String fileName = scriptFile.getName();
            String moduleName = fileName.substring(0, fileName.lastIndexOf("."));
            jep.eval("from " + moduleName + " import __artifacts__ as artifacts");

            HashMap lartifacts = (HashMap) jep.getValue("artifacts");

            for (Iterator<Entry<String, Collection>> iterator = lartifacts.entrySet().iterator(); iterator.hasNext();) {
                Entry<String, Collection> e = (Entry<String, Collection>) iterator.next();
                Collection c = e.getValue();

                PyCallable p = (PyCallable) c.toArray()[2];
                String methodName = (String) p.getAttr("__name__");

                LeapArtifactsPlugin plugin = new LeapArtifactsPlugin();
                plugin.setModuleName(moduleName);
                plugin.setMethodName(methodName);
                plugin.setName((String) c.toArray()[0]);

                Object o = c.toArray()[1];
                if (o instanceof String) {
                    pathPatternsMap.put((String) o, plugin);
                    plugin.addPattern((String) o);
                    plugins.put(plugin.moduleName, plugin);
                } else {
                    Collection<String> pathPatterns = (Collection<String>) e.getValue().toArray()[1];
                    for (String pathPattern : pathPatterns) {
                        pathPatternsMap.put(pathPattern, plugin);
                        plugins.put(plugin.moduleName, plugin);
                        plugin.addPattern(pathPattern);
                    }
                }

            }


        } catch (Exception e) {
            if (e.getMessage().contains("artifacts")) {
                // Ignores as the file does not have an __artifacts__ declared variable, meaning
                // it is not
                // a plugin.
            } else {
                throw e; // otherwise, rethrows the exception
            }

        }

    }

    public LeapArtifactsPlugin getPlugin(String pluginName) {
        return plugins.get(pluginName);
    }

}
