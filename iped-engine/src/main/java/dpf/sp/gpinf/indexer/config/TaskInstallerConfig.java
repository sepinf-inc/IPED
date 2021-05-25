package dpf.sp.gpinf.indexer.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.DirectoryStream.Filter;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.process.task.AbstractTask;
import dpf.sp.gpinf.indexer.process.task.PythonTask;
import dpf.sp.gpinf.indexer.process.task.ScriptTask;
import dpf.sp.gpinf.indexer.util.IPEDException;
import macee.core.Configurable;

public class TaskInstallerConfig implements Configurable<List<Path>> {

    private static final String CONFIG_XML = "TaskInstaller.xml"; //$NON-NLS-1$
    public static final String SCRIPT_BASE = "conf/scripts"; //$NON-NLS-1$

    private List<Path> resources = new ArrayList<>();

    public List<AbstractTask> getNewTaskInstances() {
        List<AbstractTask> tasks = new ArrayList<>();
        for (Path path : resources) {
            try {
                loadTasks(path.toFile(), tasks);
            } catch (InstantiationException | IllegalAccessException | ClassNotFoundException
                    | ParserConfigurationException | SAXException | IOException e) {
                throw new RuntimeException(e);
            }
        }
        return tasks;
    }

    @Override
    public Filter<Path> getResourceLookupFilter() {
        return new Filter<Path>() {
            @Override
            public boolean accept(Path entry) throws IOException {
                return entry.endsWith(CONFIG_XML);
            }
        };
    }

    @Override
    public void processConfig(Path resource) throws IOException {
        this.resources.add(resource);
    }

    private List<AbstractTask> loadTasks(File file, List<AbstractTask> tasks) throws InstantiationException,
            IllegalAccessException, IOException, ClassNotFoundException, ParserConfigurationException, SAXException {

        DocumentBuilder dombuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document dom = dombuilder.parse(file);
        NodeList list = dom.getElementsByTagName("task"); //$NON-NLS-1$
        for (int i = 0; i < list.getLength(); i++) {
            Node node = list.item(i);
            Node attr = node.getAttributes().getNamedItem("class"); //$NON-NLS-1$
            if (attr != null) {
                String className = attr.getNodeValue();
                tasks.add((AbstractTask) Class.forName(className).newInstance());
            }
            attr = node.getAttributes().getNamedItem("script"); //$NON-NLS-1$
            if (attr != null) {
                String scriptName = attr.getNodeValue();
                File scriptDir = new File(Configuration.getInstance().appRoot, SCRIPT_BASE);
                tasks.add(getScriptTask(scriptDir, scriptName));
            }
        }

        return tasks;
    }

    private AbstractTask getScriptTask(File scriptDir, String name) {
        File script = new File(scriptDir, name);
        if (!script.exists())
            throw new IPEDException("Script File not found: " + script.getAbsolutePath()); //$NON-NLS-1$

        if (name.endsWith(".py"))
            return new PythonTask(script);
        else
            return new ScriptTask(script);
    }

    @Override
    public List<Path> getConfiguration() {
        return resources;
    }

    @Override
    public void setConfiguration(List<Path> config) {
        this.resources = config;
    }

}
