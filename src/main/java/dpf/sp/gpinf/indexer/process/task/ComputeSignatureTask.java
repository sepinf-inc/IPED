package dpf.sp.gpinf.indexer.process.task;

import gpinf.dev.data.EvidenceFile;

import java.io.IOException;
import java.util.Date;

import org.apache.tika.Tika;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.process.Worker;

/*
 * ANALISE DE ASSINATURA
 */
public class ComputeSignatureTask extends AbstractTask {
	
	private Worker worker;
	
	public ComputeSignatureTask(Worker worker){
		this.worker = worker;
	}

	public void process(EvidenceFile evidence){
			
		MediaType type = evidence.getMediaType();
		if (type == null) {
			Metadata metadata = new Metadata();
			metadata.set(Metadata.RESOURCE_NAME_KEY, evidence.getName());
			try {
				if(Configuration.processFileSignatures){
					TikaInputStream tis = null;
					try {
						tis = evidence.getTikaStream();
						type = worker.detector.detect(tis, metadata).getBaseType();
						
					} catch (IOException e) {
						System.out.println(new Date() + "\t[ALERTA]\t" + Thread.currentThread().getName() + " Detecção do tipo abortada: "
								+ evidence.getPath() + " (" + evidence.getLength() + " bytes)\t\t" + e.toString());
						
					}finally{
						if(tis != null)
							tis.close();
					}
				}
				
				if (type == null)
					type = worker.detector.detect(null, metadata).getBaseType();

			} catch (Exception e) {
				type = MediaType.OCTET_STREAM;
				
				System.out.println(new Date() + "\t[ALERTA]\t" + Thread.currentThread().getName() + " Detecção do tipo abortada: "
				+ evidence.getPath() + " (" + evidence.getLength() + " bytes)\t\t" + e.toString());
			}
			evidence.setMediaType(type);
		}
	}
}
