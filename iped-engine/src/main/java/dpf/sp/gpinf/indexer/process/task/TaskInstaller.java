package dpf.sp.gpinf.indexer.process.task;

import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.process.Worker;
import dpf.sp.gpinf.indexer.util.IPEDException;

/**
 * Instancia e instala as tarefas de processamento em um Worker. A ordem de
 * execução das tarefas pelo Worker é definida pela sua ordem de instalação.
 *
 * A ordem de execução das tarefas é um parâmetro muito sensível que tem direto
 * impacto na corretude do processamento, por isso deve ser configurada com
 * cautela.
 */
public class TaskInstaller {

    private static final String TASKS_CONFIG_XML = "conf/TaskInstaller.xml"; //$NON-NLS-1$
    public static final String SCRIPT_BASE = "conf/scripts"; //$NON-NLS-1$

    private File scriptDir;

    public void installProcessingTasks(Worker worker) throws Exception {

        scriptDir = new File(worker.output, SCRIPT_BASE);
        List<AbstractTask> tasks = worker.tasks;
        File taskConfig = new File(worker.output, TASKS_CONFIG_XML);

        loadTasks(taskConfig, tasks);

        for (AbstractTask t : tasks) {
            t.setWorker(worker);
        }
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
                tasks.add(getScriptTask(scriptName));
            }
        }

        return tasks;
    }

    private AbstractTask getScriptTask(String name) {
        File script = new File(scriptDir, name);
        if (!script.exists())
            throw new IPEDException("Script File not found: " + script.getAbsolutePath()); //$NON-NLS-1$

        if (name.endsWith(".py"))
            return new PythonTask(script);
        else
            return new ScriptTask(script);
    }

}
