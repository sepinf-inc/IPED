package dpf.sp.gpinf.indexer.process.task;

import gpinf.dev.data.EvidenceFile;

import java.io.File;
import java.util.Properties;

import dpf.sp.gpinf.indexer.process.Worker;

/**
 * Classe que representa uma tarefa de procesamento (assinatura, hash, carving, indexaÃ§Ã£o, etc).
 * Cada Worker cria, inicializa e usa suas instÃ¢ncias das tarefas, assim normalmente hÃ¡ vÃ¡rias
 * instÃ¢ncias de uma mesma tarefa.
 * Caso a tarefa produza um novo item (subitem de zip ou carving), ele deve ser processado pelo
 * Worker {processNewItem()}
 * A tarefa recebe 01 item por vez para processar.
 * 
 */
public abstract class AbstractTask {
	
	/**
	 * Worker que executarÃ¡ esta tarefa.
	 */
	protected Worker worker;
	
	public AbstractTask nextTask;
	
	/**
	 * Construtor padrÃ£o
	 */
	public AbstractTask(){
	}
	
	/**
	 * Construtor recebendo um worker.
	 * 
	 * @param worker O worker que executarÃ¡ esta tarefa
	 */
	public AbstractTask(Worker worker){
		this.worker = worker;
	}
	
	/**
	 * MÃ©todo de inicializaÃ§Ã£o da tarefa. Chamado em cada instÃ¢ncia da tarefa pelo
	 * Worker no qual ela estÃ¡ instalada.
	 * @param confProps ParÃ¢metros obtidos do arquivo de configuraÃ§Ã£o principal
	 * @param confDir DiretÃ³rio que pode conter um arquivo avanÃ§ado de configuraÃ§Ã£o da tarefa
	 * @throws Exception Se ocorreu erro durante a inicializaÃ§Ã£o
	 */
	abstract public void init(final Properties confParams, File confDir) throws Exception;
	
	/**
	 * MÃ©todo que realiza o processamento do item pela tarefa.
	 * @param evidence Item a ser processado.
	 */
	abstract public void process(EvidenceFile evidence) throws Exception;
	
	/**
	 * MÃ©todo que realiza o processamento do item pela tarefa e o envia para
	 * a prÃ³xima tarefa.
	 * @param evidence Item a ser processado.
	 */
	public void processAndSendToNextTask(EvidenceFile evidence) throws Exception{
		process(evidence);
		if(nextTask != null)
			nextTask.processAndSendToNextTask(evidence);
	}
	
	/**
	 * MÃ©todo chamado ao final do processamento em cada tarefa instanciada.
	 * Pode conter cÃ³digo de finalizaÃ§Ã£o da tarefa e liberaÃ§Ã£o de recursos.
	 */
	abstract public void finish() throws Exception;

}
