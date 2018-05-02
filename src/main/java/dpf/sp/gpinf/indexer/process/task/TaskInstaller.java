package dpf.sp.gpinf.indexer.process.task;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import dpf.sp.gpinf.indexer.process.Worker;

/**
 * Instancia e instala as tarefas de processamento em um Worker. A ordem de execução das tarefas
 * pelo Worker é definida pela sua ordem de instalação.
 *
 * A ordem de execução das tarefas é um parâmetro muito sensível que tem direto impacto na corretude
 * do processamento, por isso deve ser configurada com cautela.
 */
public class TaskInstaller {
    
  private static final String TASKS_CONFIG_XML = "conf/TaskInstaller.xml";

  public void installProcessingTasks(Worker worker) throws Exception {
      
    List<AbstractTask> tasks = worker.tasks;
    File taskConfig = new File(worker.output, TASKS_CONFIG_XML);
    
    loadTasks(taskConfig, tasks);
    
    for(AbstractTask t : tasks) {
        t.setWorker(worker);
    }
  }
  
  private List<AbstractTask> loadTasks(File file, List<AbstractTask> tasks) throws InstantiationException, IllegalAccessException, ClassNotFoundException, IOException{
      List<String> lines = Files.readAllLines(file.toPath());
      List<String> names = new ArrayList<>();
      String prefix = "task class=\"";
      for(String line : lines) {
          int i = line.indexOf(prefix);
          if(i > -1)
              names.add(line.substring(i + prefix.length(), line.indexOf("\"></task>")));
      }
      for(String className : names)
          tasks.add((AbstractTask)Class.forName(className).newInstance());
      
      return tasks;
  }

}
