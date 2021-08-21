package dpf.sp.gpinf.indexer.config;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

public class TaskInstallerConfig implements Configurable<List<String>> {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private static final String CONFIG_XML = "TaskInstaller.xml"; //$NON-NLS-1$
    public static final String SCRIPT_BASE = "conf/scripts"; //$NON-NLS-1$

    private List<String> xmls = new ArrayList<>();

    public List<AbstractTask> getNewTaskInstances() {
        Map<String, AbstractTask> tasks = new LinkedHashMap<>();
        for (String xml : xmls) {
            try {
                loadTasks(xml, tasks);
            } catch (InstantiationException | IllegalAccessException | ClassNotFoundException
                    | ParserConfigurationException | SAXException | IOException e) {
                throw new RuntimeException(e);
            }
        }
        return tasks.values().stream().collect(Collectors.toList());
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
        byte[] bytes = Files.readAllBytes(resource);
        this.xmls.add(new String(bytes, StandardCharsets.UTF_8));
    }

    private void loadTasks(String xml, Map<String, AbstractTask> tasks) throws InstantiationException,
            IllegalAccessException, IOException, ClassNotFoundException, ParserConfigurationException, SAXException {

        DocumentBuilder dombuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document dom = dombuilder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        NodeList list = dom.getElementsByTagName("task"); //$NON-NLS-1$
        for (int i = 0; i < list.getLength(); i++) {
            Node node = list.item(i);
            Node attr = node.getAttributes().getNamedItem("class"); //$NON-NLS-1$
            if (attr != null) {
                String className = attr.getNodeValue();
                tasks.putIfAbsent(className, (AbstractTask) Class.forName(className).newInstance());
            }
            attr = node.getAttributes().getNamedItem("script"); //$NON-NLS-1$
            if (attr != null) {
                String scriptName = attr.getNodeValue();
                File scriptDir = new File(Configuration.getInstance().appRoot, SCRIPT_BASE);
                tasks.putIfAbsent(scriptName, getScriptTask(scriptDir, scriptName));
            }
        }
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
    public List<String> getConfiguration() {
        return xmls;
    }

    @Override
    public void setConfiguration(List<String> config) {
        this.xmls = config;
    }

}
