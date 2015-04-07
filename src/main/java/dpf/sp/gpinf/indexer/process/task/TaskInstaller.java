package dpf.sp.gpinf.indexer.process.task;

import java.util.List;

import dpf.sp.gpinf.indexer.process.Worker;

/**
 * Instancia e instala as tarefas de processamento em um Worker.
 * A ordem de execução das tarefas pelo Worker é definida pela sua ordem
 * de instalação.
 * 
 * A ordem de execução das tarefas é um parâmetro muito sensível que tem
 * direto impacto na corretude do processamento, por isso deve ser configurada
 * com cautela.
 */
public class TaskInstaller {

	public void installProcessingTasks(Worker worker) throws Exception{
		
		List<AbstractTask> tasks = worker.tasks;
                
		tasks.add(new HashTask(worker));
		tasks.add(new SignatureTask(worker));
		tasks.add(new SetTypeTask(worker));
		tasks.add(new SetCategoryTask(worker));
		tasks.add(new KFFTask(worker));
		tasks.add(new LedKFFTask(worker));
		tasks.add(new ExportCSVTask(worker));
		tasks.add(new DuplicateTask(worker));
		tasks.add(new ExpandContainerTask(worker));
		tasks.add(new ExportFileTask(worker));
		//Carving precisa ficar apos exportação (devido a rename que muda a referencia)
		//e antes da indexação (pois pode setar propriedade hasChildren no pai)
		tasks.add(new CarveTask(worker));
		tasks.add(new IndexTask(worker));
		
	}
	
	
}