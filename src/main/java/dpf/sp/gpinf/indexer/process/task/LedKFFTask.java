package dpf.sp.gpinf.indexer.process.task;

import gpinf.dev.data.EvidenceFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Properties;

import dpf.sp.gpinf.indexer.IndexFiles;
import dpf.sp.gpinf.indexer.parsers.util.LedHashes;
import dpf.sp.gpinf.indexer.process.Worker;
import dpf.sp.gpinf.indexer.util.HashValue;
import dpf.sp.gpinf.indexer.util.Log;

/**
 * Tarefa de consulta a base de hashes do LED. Pode ser removida no futuro e ser integrada a tarefa de KFF.
 * A vantagem de ser independente é que a base de hashes pode ser atualizada facilmente, apenas apontando para
 * a nova base, sem necessidade de importação.
 * 
 * @author Nassif
 *
 */
public class LedKFFTask extends AbstractTask {

    private static Object lock = new Object();
    private static HashMap<String, HashValue[]> hashArrays;
    private static final String taskName = "Consulta Base de Hashes do LED";
    private static String[] ledHashOrder = {"md5", null, "edonkey", "sha-1"};

    public LedKFFTask(Worker worker) {
        super(worker);
    }

    @Override
    public void init(Properties confParams, File confDir) throws Exception {

        synchronized (lock) {
            if (hashArrays != null)
            	return;

            //this.caseData.addBookmark(new FileGroup(ledCategory, "", ""));
            String hash = confParams.getProperty("hash");
            String ledWkffPath = confParams.getProperty("ledWkffPath");
            if (ledWkffPath == null)
            	return;
            
            File wkffDir = new File(ledWkffPath);
            if (!wkffDir.exists())
            	throw new Exception("Caminho para base de hashes do LED inválido!");

            hash = hash.toLowerCase();
            if (!hash.contains("md5") && !hash.contains("sha-1"))
            	throw new Exception("Habilite o hash md5 ou sha-1 para consultar a base do LED!");

            IndexFiles.getInstance().firePropertyChange("mensagem", "", "Carregando base de hashes do LED...");
            
            hashArrays = new HashMap<String, HashValue[]>();

            HashMap<String, ArrayList<HashValue>> hashList = new HashMap<String, ArrayList<HashValue>>();
            for(int col = 0; col < ledHashOrder.length; col++)
            	if(ledHashOrder[col] != null)
            		hashList.put(ledHashOrder[col], new ArrayList<HashValue>());
            
            for (File wkffFile : wkffDir.listFiles()) {
                BufferedReader reader = new BufferedReader(new FileReader(wkffFile));
                String line = reader.readLine();
                while ((line = reader.readLine()) != null) {
                    String[] hashes = line.split(" \\*");
                    for(int col = 0; col < ledHashOrder.length; col++)
                    	if(ledHashOrder[col] != null)
                    		hashList.get(ledHashOrder[col]).add(new HashValue(hashes[col].trim()));
                }
                reader.close();
            }
        	for(int col = 0; col < ledHashOrder.length; col++)
            	if(ledHashOrder[col] != null){
            		hashArrays.put(ledHashOrder[col], hashList.get(ledHashOrder[col]).toArray(new HashValue[0]));
            		hashList.remove(ledHashOrder[col]);
            		Arrays.sort(hashArrays.get(ledHashOrder[col]));
            	}
            
            Log.info(taskName, "Hashes carregados: " + hashArrays.get(ledHashOrder[0]).length);
            
            LedHashes.hashMap = hashArrays;
        }

    }

    @Override
    public void finish() throws Exception {
        hashArrays = null;

    }

    @Override
    protected void process(EvidenceFile evidence) throws Exception {

        if (hashArrays == null)
        	return;
        	
        for(int col = 0; col < ledHashOrder.length; col++)
        	if(ledHashOrder[col] != null){
        		String hash = (String)evidence.getExtraAttribute(ledHashOrder[col]);
            	if(hash != null){
            		if(Arrays.binarySearch(hashArrays.get(ledHashOrder[col]), new HashValue(hash)) >= 0)
            			evidence.setExtraAttribute(KFFTask.KFF_STATUS, "pedo");
            		break;
            	}   	
        	}

    }

}
