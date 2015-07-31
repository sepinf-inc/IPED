package dpf.sp.gpinf.indexer.process.task;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.process.Worker;
import gpinf.dev.data.EvidenceFile;

/**
 * Análise de assinatura utilizando biblioteca Apache Tika.
 */
public class SignatureTask extends AbstractTask {
	private static Logger LOGGER = LoggerFactory.getLogger(SignatureTask.class); 
	
	public static boolean processFileSignatures = true;
	
	public SignatureTask(Worker worker){
		super(worker);
	}
	
	public void process(EvidenceFile evidence){
			
		MediaType type = evidence.getMediaType();
		if (type == null) {
			Metadata metadata = new Metadata();
			metadata.set(Metadata.RESOURCE_NAME_KEY, evidence.getName());
			try {
				if(processFileSignatures){
					TikaInputStream tis = null;
					try {
						tis = evidence.getTikaStream();
						type = worker.detector.detect(tis, metadata).getBaseType();
						
					} catch (IOException e) {
						LOGGER.warn("{} Detecção de tipo abortada: {} ({} bytes)\t\t{}", Thread.currentThread().getName(), evidence.getPath(), 
								evidence.getLength(), e.toString());						
					}
				}
				
				//Caso seja item office07 cifrado e tenha extensão específica, refina o tipo
				if(type != null && type.toString().equals("application/x-tika-ooxml-protected")
					&& "docx xlsx pptx".contains(evidence.getExt().toLowerCase())){
						type = MediaType.application("x-tika-ooxml-protected-" + evidence.getExt().toLowerCase());
				}
				
				if (type == null)
					type = worker.detector.detect(null, metadata).getBaseType();
					

			} catch (Exception e) {
				type = MediaType.OCTET_STREAM;
				
				LOGGER.warn("{} Detecção de tipo abortada: {} ({} bytes)\t\t{}", Thread.currentThread().getName(), 
						evidence.getPath(), evidence.getLength(), e.toString());
			}
			evidence.setMediaType(type);
		}
	}

	@Override
	public void init(Properties confProps, File confDir) throws Exception {
		String value = confProps.getProperty("processFileSignatures");
		if (value != null)
			value = value.trim();
		if (value != null && !value.isEmpty())
			processFileSignatures = Boolean.valueOf(value);
	}

	@Override
	public void finish() throws Exception {
		// TODO Auto-generated method stub
		
	}

}
