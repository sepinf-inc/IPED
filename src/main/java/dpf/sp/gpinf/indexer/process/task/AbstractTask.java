package dpf.sp.gpinf.indexer.process.task;

import gpinf.dev.data.EvidenceFile;

import java.io.File;
import java.util.Properties;

import dpf.sp.gpinf.indexer.process.Worker;

/*
 * Classe que representa uma tarefa de procesamento (assinatura, hash, carving, indexação, etc).
 * Cada Worker cria, inicializa e usa suas instâncias das tarefas, assim normalmente há várias
 * instâncias de uma mesma tarefa.
 * Caso a tarefa produza um novo item (subitem de zip ou carving), ele deve ser processado pelo
 * Worker {processNewItem()}
 * A tarefa recebe 01 item por vez para processar.
 * 
 */
public abstract class AbstractTask {
	
	/*
	 * Worker que executará esta tarefa.
	 */
	protected Worker worker;
	
	/*
	 * Construtor padrão
	 */
	public AbstractTask(){
	}
	
	/*
	 * Constrt
	 */
	public AbstractTask(Worker worker){
		this.worker = worker;
	}
	
	/*
	 * Método de inicialização da tarefa. Chamado em cada instância da tarefa pelo
	 * Worker no qual ela está instalada.
	 * @param confProps Parâmetros obtidos do arquivo de configuração principal
	 * @param confDir Diretório que pode conter um arquivo avançado de configuração da tarefa
	 */
	abstract public void init(final Properties confProps, File confDir) throws Exception;
	
	/*
	 * Método que realiza o processamento do item pela tarefa.
	 * @param evidence Item a ser processado.
	 */
	abstract public void process(EvidenceFile evidence) throws Exception;
	
	/*
	 * Método chamado ao final do processamento em cada tarefa instanciada.
	 * Pode conter código de finalização da tarefa e liberação de recursos.
	 */
	abstract public void finish() throws Exception;

}
