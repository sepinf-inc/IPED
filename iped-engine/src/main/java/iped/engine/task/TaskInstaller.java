package iped.engine.task;

import java.util.List;

import iped.engine.config.ConfigurationManager;
import iped.engine.config.TaskInstallerConfig;
import iped.engine.core.Worker;

/**
 * Instancia e instala as tarefas de processamento em um Worker. A ordem de
 * execução das tarefas pelo Worker é definida pela sua ordem de instalação.
 *
 * A ordem de execução das tarefas é um parâmetro muito sensível que tem direto
 * impacto na corretude do processamento, por isso deve ser configurada com
 * cautela.
 */
public class TaskInstaller {

    public void installProcessingTasks(Worker worker) throws Exception {
        
        TaskInstallerConfig taskConfig = ConfigurationManager.get().findObject(TaskInstallerConfig.class);

        List<AbstractTask> tasks = taskConfig.getNewTaskInstances();
        worker.tasks.addAll(tasks);

        for (AbstractTask t : tasks) {
            t.setWorker(worker);
        }
    }

}
