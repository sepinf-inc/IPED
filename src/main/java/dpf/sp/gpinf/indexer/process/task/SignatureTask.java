package dpf.sp.gpinf.indexer.process.task;

import gpinf.dev.data.EvidenceFile;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Properties;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

import dpf.sp.gpinf.indexer.process.Worker;

/**
 * Análise de assinatura utilizando biblioteca Apache Tika.
 */
public class SignatureTask extends AbstractTask {
	
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
						System.out.println(new Date() + "\t[ALERTA]\t" + Thread.currentThread().getName() + " Detecção do tipo abortada: "
								+ evidence.getPath() + " (" + evidence.getLength() + " bytes)\t\t" + e.toString());
						
					}
				}
				
				if (type == null 
						//Caso seja item office07 cifrado e tenha extensão específica, realiza nova detecção para refinar o tipo
						|| (type.toString().equals("application/x-tika-ooxml-protected")
						&& "docx xlsx pptx".contains(evidence.getExt().toLowerCase()))){
					
					if(type != null)
						evidence.setEncrypted(true);
					type = worker.detector.detect(null, metadata).getBaseType();
				}
					

			} catch (Exception e) {
				type = MediaType.OCTET_STREAM;
				
				System.out.println(new Date() + "\t[ALERTA]\t" + Thread.currentThread().getName() + " Detecção do tipo abortada: "
				+ evidence.getPath() + " (" + evidence.getLength() + " bytes)\t\t" + e.toString());
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
