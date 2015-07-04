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
           
		tasks.add(new TempFileTask());
		tasks.add(new HashTask());
		tasks.add(new SignatureTask());
		tasks.add(new SetTypeTask());
		tasks.add(new SetCategoryTask());
		//tarefas que ignoram itens após categorização para incluir categoria de ignorados no csv
		tasks.add(new KFFTask());
		tasks.add(new LedKFFTask());
		tasks.add(new DuplicateTask());
		
		tasks.add(new ParsingTask());
		tasks.add(new ExportFileTask());
		tasks.add(new MakePreviewTask());
		tasks.add(new VideoThumbTask());
		tasks.add(new HTMLReportTask());
		//Carving precisa ficar apos exportação (devido a rename p/ hash de subitens, que são referenciaos por seus filhos carved)
		//e antes da indexação (pois pode setar propriedade hasChildren nos itens)
		tasks.add(new CarveTask());
		tasks.add(new IndexTask());
		tasks.add(new ExportCSVTask());
		
	}
	
	
}
