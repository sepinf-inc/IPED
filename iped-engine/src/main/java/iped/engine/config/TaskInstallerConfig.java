package iped.engine.config;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.Files;
import java.nio.file.Path;
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

import iped.configuration.Configurable;
import iped.engine.task.AbstractTask;
import iped.engine.task.PythonTask;
import iped.engine.task.ScriptTask;
import iped.exception.IPEDException;

public class TaskInstallerConfig implements Configurable<String> {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private static final String CONFIG_XML = "TaskInstaller.xml"; //$NON-NLS-1$
    public static final String SCRIPT_BASE = "scripts/tasks"; //$NON-NLS-1$

    private String xml;

    public List<AbstractTask> getNewTaskInstances() {
        Map<String, AbstractTask> tasks = new LinkedHashMap<>();
        try {
            loadTasks(xml, tasks);
        } catch (Exception e) {
            throw new RuntimeException(e);
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
        this.xml = new String(bytes, StandardCharsets.UTF_8);
    }

	private void loadTasks(String xml, Map<String, AbstractTask> tasks) throws InstantiationException,
			IllegalAccessException, IOException, ClassNotFoundException, ParserConfigurationException, SAXException,
			IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {

        DocumentBuilder dombuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document dom = dombuilder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        NodeList list = dom.getElementsByTagName("task"); //$NON-NLS-1$
        for (int i = 0; i < list.getLength(); i++) {
            Node node = list.item(i);
            Node attr = node.getAttributes().getNamedItem("class"); //$NON-NLS-1$
            if (attr != null) {
                String className = attr.getNodeValue();
                tasks.putIfAbsent(className, (AbstractTask) Class.forName(className).getDeclaredConstructor().newInstance());
            }
            attr = node.getAttributes().getNamedItem("script"); //$NON-NLS-1$
            if (attr != null) {
                String scriptName = attr.getNodeValue();
                File scriptDir = new File(Configuration.getInstance().configPath, SCRIPT_BASE);
                File script = new File(scriptDir, scriptName);
                if (!script.exists()) {
                    scriptDir = new File(Configuration.getInstance().appRoot, SCRIPT_BASE);
                    script = new File(scriptDir, scriptName);
                    if (!script.exists()) {
                        throw new IPEDException("Script File not found: " + script.getAbsolutePath()); //$NON-NLS-1$
                    }
                }
                tasks.putIfAbsent(scriptName, getScriptTask(script));
            }
        }
    }

    private AbstractTask getScriptTask(File script) {
        if (script.getName().endsWith(".py"))
            return new PythonTask(script);
        else
            return new ScriptTask(script);
    }

    @Override
    public String getConfiguration() {
        return xml;
    }

    @Override
    public void setConfiguration(String config) {
        this.xml = config;
    }

}
