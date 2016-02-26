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
        
		tasks.add(new IgnoreHardLinkTask(worker));
		tasks.add(new TempFileTask(worker));
		tasks.add(new HashTask(worker));
		//Nova tarefa IgnoreHardLinkTask após HashTask para armazenar hash dos arquivos
		//tasks.add(new IgnoreHardLinkTask(worker));
		
		tasks.add(new SignatureTask(worker));
		tasks.add(new SetTypeTask(worker));
		tasks.add(new SetCategoryTask(worker));
		//tarefas que ignoram itens após categorização para incluir categoria de ignorados no csv
		tasks.add(new KFFTask(worker));
		tasks.add(new LedKFFTask(worker));
		tasks.add(new DuplicateTask(worker));
		
		tasks.add(new ParsingTask(worker));
		tasks.add(new ExportFileTask(worker));
		tasks.add(new MakePreviewTask(worker));
		tasks.add(new ImageThumbTask(worker));
		tasks.add(new VideoThumbTask(worker));
		tasks.add(new DIETask(worker));
		tasks.add(new HTMLReportTask(worker));
		//Carving precisa ficar apos exportação (devido a rename p/ hash de subitens, que são referenciaos por seus filhos carved)
		//e antes da indexação (pois pode setar propriedade hasChildren nos itens)
		tasks.add(new CarveTask(worker));
		tasks.add(new IndexTask(worker));
		tasks.add(new ExportCSVTask(worker));
		
	}
	
	
}
