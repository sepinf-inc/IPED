package dpf.sp.gpinf.indexer.process.task;

import gpinf.dev.data.EvidenceFile;
import gpinf.dev.data.FileGroup;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;

import dpf.sp.gpinf.indexer.IndexFiles;
import dpf.sp.gpinf.indexer.process.Worker;
import dpf.sp.gpinf.indexer.process.task.HashTask.HashValue;

/**
 * Tarefa de consulta a base de hashes do LED.
 * 
 * @author Nassif
 *
 */
public class LedKFFTask extends AbstractTask{
    
    private static String ledCategory = "Hash com Alerta (PI)";
    private static Object lock = new Object();
    private static HashValue[] hashArray;

    public LedKFFTask(Worker worker) {
        super(worker);
    }

    @Override
    public void init(Properties confParams, File confDir) throws Exception {
        
        synchronized(lock){
            if(hashArray != null)
                return;
            
            this.caseData.addBookmark(new FileGroup(ledCategory, "", ""));
            String hash = confParams.getProperty("hash");
            String ledWkffPath = confParams.getProperty("ledWkffPath");
            if(ledWkffPath == null)
                return;
            File wkffDir = new File(ledWkffPath);
            if(!wkffDir.exists())
            	throw new Exception("Caminho para base de hashes do LED inválido!");
            
            IndexFiles.getInstance().firePropertyChange("mensagem", "", "Carregando base de hashes do LED...");
            
            ArrayList<HashValue> hashList = new ArrayList<HashValue>();
            for(File wkffFile : wkffDir.listFiles()){
                BufferedReader reader = new BufferedReader(new FileReader(wkffFile));
                String line = reader.readLine();
                while((line = reader.readLine()) != null){
                    String[] hashes = line.split(" \\*");
                    if(hash.equals("md5"))
                        hashList.add(new HashValue(hashes[0].trim()));
                    else if(hash.equals("sha-1"))
                        hashList.add(new HashValue(hashes[3].trim()));
                }
                reader.close();
            }
            hashArray = hashList.toArray(new HashValue[0]);
            hashList = null;
            Arrays.sort(hashArray);
        }
        
    }

    @Override
    public void finish() throws Exception {
        hashArray = null;
        
    }

    @Override
    protected void process(EvidenceFile evidence) throws Exception {
        
        String hash = evidence.getHash();
        if(hash != null && hashArray != null){
            if(Arrays.binarySearch(hashArray, new HashValue(hash)) >= 0)
                evidence.addCategory(ledCategory);
                
        }
        
    }

}
