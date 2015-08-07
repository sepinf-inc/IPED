package dpf.sp.gpinf.indexer.process.task;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.process.Worker;
import gpinf.dev.data.EvidenceFile;

/**
 * Tarefa para geração de arquivos temporários para os itens antes do processamento.
 * Caso indexTemp esteja em disco SSD e a imagem esteja compactada (e01), pode aumentar 
 * consideravelmente a performance pois os itens deixam de ser descompactados múltiplas 
 * vezes pela libewf, a qual não é thread safe e sincroniza descompactações concorrentes,
 * subaproveitando máquinas multiprocessadas.
 * 
 * @author Nassif
 *
 */
public class TempFileTask extends AbstractTask{
	
	private static Logger LOGGER = LoggerFactory.getLogger(TempFileTask.class);
	private static int MAX_TEMPFILE_LEN = 1024 * 1024 * 1024;
	private boolean indexTempOnSSD = false;

	public TempFileTask(Worker worker) {
		super(worker);
	}

	@Override
	public void init(Properties confParams, File confDir) throws Exception {
		String value = confParams.getProperty("indexTempOnSSD");
		if (value != null)
			value = value.trim();
		if (value != null && !value.isEmpty())
			indexTempOnSSD = Boolean.valueOf(value);
		
	}

	@Override
	public void finish() throws Exception {
		// TODO Auto-generated method stub
	}

	@Override
	protected void process(EvidenceFile evidence) throws Exception {
		
		Long len = evidence.getLength();
		if(indexTempOnSSD && len != null && len <= MAX_TEMPFILE_LEN && evidence.getPath().toLowerCase().contains(".e01/vol_vol"))
			try{
				evidence.getTempFile();
			}catch(IOException e){
				LOGGER.warn("{} Erro ao acessar {} {}", Thread.currentThread().getName(), evidence.getPath(), e.toString());
			}
		
	}

}
