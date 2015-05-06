package dpf.sp.gpinf.indexer.process.task;

import gpinf.dev.data.CaseData;
import gpinf.dev.data.EvidenceFile;

import java.io.File;
import java.util.Date;
import java.util.Properties;

import org.apache.tika.exception.TikaException;

import dpf.sp.gpinf.indexer.io.TimeoutException;
import dpf.sp.gpinf.indexer.process.ItemProducer;
import dpf.sp.gpinf.indexer.process.Statistics;
import dpf.sp.gpinf.indexer.process.Worker;

/**
 * Classe que representa uma tarefa de procesamento (assinatura, hash, carving, indexação, etc).
 * 
 * Cada Worker possui suas próprias instâncias das tarefas, assim normalmente há várias
 * instancias de uma mesma tarefa.
 * 
 * Caso a tarefa produza um novo item (subitem de zip ou carving), ele deve ser processado pelo
 * Worker @see dpf.sp.gpinf.indexer.process.Worker#processNewItem()
 * A tarefa recebe 01 item por vez para processar.
 * 
 */
public abstract class AbstractTask {
	
	/**
	 * Worker que executará esta tarefa.
	 */
	protected Worker worker;
	
	/**
	 * Estatísticas que podem ser atualizadas pela tarefa.
	 */
	protected Statistics stats;
	
	/**
	 * Diretório de saída do processamento. A tarefa pode criar um subdiretório
	 * contendo dados resultantes do seu processamento.
	 */
	protected File output;
	
	/**
	 * Representa o caso atual. As diferentes instâncias das tarefas podem
	 * armazenar objetos compartilhados no mapa objectMap do caso.
	 */
	protected CaseData caseData;
	
	/**
	 * Próxima tarefa que será executada no pipeline.
	 */
	protected AbstractTask nextTask;
	
	/**
	 * Define a próxima tarefa no pipeline.
	 * @param nextTask próxima tarefa
	 */
	public void setNextTask(AbstractTask nextTask) {
		this.nextTask = nextTask;
	}

	/**
	 * Construtor recebendo um worker.
	 * 
	 * @param worker O worker que executará esta tarefa
	 */
	public AbstractTask(Worker worker){
		this.worker = worker;
		if(worker != null){
			this.stats = worker.stats;
			this.caseData = worker.caseData;
			this.output = worker.output;
		}
	}
	
	/**
	 * Método de inicialização da tarefa. Chamado em cada instância da tarefa pelo
	 * Worker no qual ela está instalada.
	 * @param confParams Parâmetros obtidos do arquivo de configuração principal
	 * @param confDir Diretório que pode conter um arquivo avançado de configuração da tarefa
	 * @throws Exception Se ocorreu erro durante a inicialização
	 */
	abstract public void init(final Properties confParams, File confDir) throws Exception;
	
	/**
	 * Método chamado ao final do processamento em cada tarefa instanciada.
	 * Pode conter código de finalização da tarefa e liberação de recursos.
	 * 
	 * @throws Exception Caso ocorra erro inesperado.
	 */
	abstract public void finish() throws Exception;
	
	/**
	 * Realiza o processamento do item pela tarefa.
	 * @param evidence Item a ser processado.
	 * @throws Exception Caso ocorra erro inesperado.
	 */
	abstract protected void process(EvidenceFile evidence) throws Exception;
	
	/**
	 * Realiza o processamento do item na tarefa e o envia para
	 * a próxima tarefa.
	 * @param evidence Item a ser processado.
	 * @throws Exception Caso ocorra erro inesperado.
	 */
	public final void processAndSendToNextTask(EvidenceFile evidence) throws Exception{
		
		if(this == worker.firstTask && !evidence.isQueueEnd())
			worker.itensBeingProcessed++;
		
		AbstractTask prevTask = worker.runningTask; 
		worker.runningTask = this;
		EvidenceFile prevEvidence = worker.evidence;
		if(!evidence.isQueueEnd())
			worker.evidence = evidence;
		
		if(!evidence.isToIgnore() || processIgnoredItem()){
			processMonitorTimeout(evidence);
		}
		sendToNextTask(evidence);
		
		worker.runningTask = prevTask;
		worker.evidence = prevEvidence;
		
		// ESTATISTICAS
		if((nextTask == null) && !evidence.isQueueEnd()){
		    evidence.dispose();
			stats.incProcessed();
			if ((!evidence.isSubItem() && !evidence.isCarved()) || ItemProducer.indexerReport) {
				stats.incActiveProcessed();
				Long len = evidence.getLength();
				if(len == null)
					len = 0L;
				stats.addVolume(len);
			}
			worker.itensBeingProcessed--;
		}
	}
	
	/**
	 * Envia o item para a próxima tarefa que será executada.
	 * 
	 * @param evidence Item a ser processado
	 * @throws Exception Caso ocorra erro inesperado.
	 */
	protected void sendToNextTask(EvidenceFile evidence) throws Exception{
		if(nextTask != null)
			nextTask.processAndSendToNextTask(evidence);
	}
	
	/**
	 * Processa o item monitorando timeout durante parsing. Caso ocorra timeout, 
	 * o item é reprocessado na tarefa com um parser seguro, sem risco de timeout.
	 * 
	 * @param evidence Item a ser procesado
	 * @throws Exception Se ocorrer erro inesperado.
	 */
	private void processMonitorTimeout(EvidenceFile evidence) throws Exception{
		try {
			this.process(evidence);
			
		} catch (TimeoutException e) {
			System.out.println(new Date() + "\t[ALERT]\t" + worker.getName() + " TIMEOUT ao processar " + evidence.getPath() + " (" + evidence.getLength() + "bytes)\t" + e);
			stats.incTimeouts();
			evidence.setTimeOut(true);
			processMonitorTimeout(evidence);

		}catch (Throwable t) {
			//Ignora arquivos recuperados e corrompidos
			if(t.getCause() instanceof TikaException && evidence.isCarved()){
				stats.incCorruptCarveIgnored();
				//System.out.println(new Date() + "\t[AVISO]\t" + this.getName() + " " + "Ignorando arquivo recuperado corrompido " + evidence.getPath() + " (" + length + "bytes)\t" + t.getCause());
				evidence.setToIgnore(true);
				
			}else
				throw t;
			
		}
	}
	
	/**
	 * Indica se itens ignorados, como KFF ignorable, devem ser processados pela tarefa ou não.
	 * O valor padrão é false, assim itens ignorados não são processados pelas tarefas seguintes.
	 * Tarefas específicas podem sobrescrever esse comportamento.
	 * 
	 * @return se a tarefa deve processar um item ignorado.
	 */
	protected boolean processIgnoredItem(){
		return false;
	}

}
