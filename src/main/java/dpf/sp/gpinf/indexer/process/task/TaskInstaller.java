package dpf.sp.gpinf.indexer.process.task;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import dpf.sp.gpinf.indexer.process.Worker;
import dpf.sp.gpinf.indexer.util.IPEDException;

/**
 * Instancia e instala as tarefas de processamento em um Worker. A ordem de execução das tarefas
 * pelo Worker é definida pela sua ordem de instalação.
 *
 * A ordem de execução das tarefas é um parâmetro muito sensível que tem direto impacto na corretude
 * do processamento, por isso deve ser configurada com cautela.
 */
public class TaskInstaller {
    
  private static final String TASKS_CONFIG_XML = "conf/TaskInstaller.xml";
  private static final String SCRIPT_BASE = "conf/scripts";
  
  private File scriptDir;

  public void installProcessingTasks(Worker worker) throws Exception {
      
    scriptDir = new File(worker.output, SCRIPT_BASE);
    List<AbstractTask> tasks = worker.tasks;
    File taskConfig = new File(worker.output, TASKS_CONFIG_XML);
    
    loadTasks(taskConfig, tasks);
    
    for(AbstractTask t : tasks) {
        t.setWorker(worker);
    }
  }
  
  private List<AbstractTask> loadTasks(File file, List<AbstractTask> tasks) throws InstantiationException, IllegalAccessException, IOException, ClassNotFoundException{
      
      String classPrefix = "task class=\"";
      String scriptPrefix = "task script=\"";
      List<String> lines = Files.readAllLines(file.toPath());
      for(String line : lines) {
          int i = line.indexOf(classPrefix);
          if(i > -1) {
              String className = line.substring(i + classPrefix.length(), line.indexOf("\"></task>"));
              tasks.add((AbstractTask)Class.forName(className).newInstance());
          }
          i = line.indexOf(scriptPrefix);
          if(i > -1) {
              String scriptName = line.substring(i + scriptPrefix.length(), line.indexOf("\"></task>"));
              tasks.add(getScriptTask(scriptName));
          }
      }
      return tasks;
  }
  
  private ScriptTask getScriptTask(String name){
      File script = new File(scriptDir, name);
      if(!script.exists())
          throw new IPEDException("Script File not found: " + script.getAbsolutePath());
      
      return new ScriptTask(script);
  }

}
