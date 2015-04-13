package dpf.sp.gpinf.indexer.process.task;

import gpinf.dev.data.EvidenceFile;
import gpinf.dev.data.FileGroup;

import java.io.BufferedReader;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.swing.ProgressMonitor;
import javax.swing.ProgressMonitorInputStream;

import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;
import org.mapdb.SerializerBase;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.process.Worker;
import dpf.sp.gpinf.indexer.process.task.HashTask.HashValue;
import dpf.sp.gpinf.indexer.util.CancelableWorker;
import dpf.sp.gpinf.indexer.util.ProgressDialog;

public class KFFTask extends AbstractTask{
    
    private static String ALERT = "Hash com Alerta";
    private static String IGNORE = "Hash Ignorável";
    private static String CONF_FILE = "KFFTaskConfig.txt";
    
    public static int excluded = 0;
    private static Object lock = new Object();
    
    /*
     * valor negativo no mapa indica hash ignorável
     * valor positivo indica alerta
     */
    private static Map<HashValue, Integer> map;
    private static Map<HashValue, Integer> md5Map;
    private static Map<HashValue, Integer> sha1Map;
    
    private static Map<Integer, String[]> products; 
    private static Set<String> alertProducts;
    private static DB db;
    
    private boolean excludeKffIgnorable = true;
    private boolean md5 = true;

    public KFFTask(Worker worker) {
        super(worker);
    }

    @Override
    public void init(Properties confParams, File confDir) throws Exception {
     
        String hash = confParams.getProperty("hash");
        if(hash == null)
            return;
        if(hash.equals("md5"))
            md5 = true;
        else if(hash.equals("sha-1"))
            md5 = false;
        
        excludeKffIgnorable = Boolean.valueOf(confParams.getProperty("excludeKffIgnorable"));
        
        String kffDbPath = confParams.getProperty("kffDb");
        if(kffDbPath == null)
            return;
        
        if(map == null){
            excluded = 0;
            
            File kffDb = new File(kffDbPath);
            db = DBMaker.newFileDB(kffDb)
                    .transactionDisable()
                    .mmapFileEnableIfSupported()
                    .asyncWriteEnable()
                    .asyncWriteFlushDelay(1000)
                    .asyncWriteQueueSize(1024000)
                    .make();
            md5Map = db.getHashMap("md5Map");
            sha1Map = db.getHashMap("sha1Map");
            products = db.getHashMap("productMap");
            
            if(md5)
            	map = md5Map;
            else
            	map = sha1Map;
            
            if(confDir != null){
            	alertProducts = new HashSet<String>();
                File confFile = new File(confDir, CONF_FILE);
            	BufferedReader reader = new BufferedReader(new FileReader(confFile));
            	String line; 
                while((line = reader.readLine()) != null){
                	if(line.startsWith("#"))
                		continue;
                	alertProducts.add(line.trim());
                }
                reader.close();
            }
            
            if(caseData != null){
                this.caseData.addBookmark(new FileGroup(ALERT, "", ""));
                this.caseData.addBookmark(new FileGroup(IGNORE, "", ""));
            }
            
        }
    }
    
    @Override
    public void finish() throws Exception {
        if(excluded != -1)
            System.out.println(new Date() + "\t[INFO]\t" + "Itens ignorados via KFF: " + excluded);
        excluded = -1;
    }
    
    public void importKFF(File kffDir) throws IOException{
        
    	File NSRLProd = new File(kffDir, "NSRLProd.txt");
    	BufferedReader reader = new BufferedReader(new FileReader(NSRLProd));
    	String line = reader.readLine();
        while((line = reader.readLine()) != null){
        	int idx = line.indexOf(',');
        	String key = line.substring(0, idx);
        	String[] values = line.substring(idx + 2).split("\",\"");
        	String[] prod = {values[0], values[1]};
        	products.put(Integer.valueOf(key), prod);
        }
        reader.close();
        for(File kffFile : kffDir.listFiles()){
            if(!kffFile.getName().contains("NSRLFile"))
                continue;
            long length = kffFile.length();
            long progress = 0;
            int i = 0;
            ProgressMonitor monitor = new ProgressMonitor(null, "", "Importando " + kffFile.getName(), 0, (int)(length/1000));
            reader = new BufferedReader(new FileReader(kffFile));
            line = reader.readLine();
            String ignoreStr = "\"\""; 
            while((line = reader.readLine()) != null){
                String[] values = line.split(",");
                KffAttr attr = new KffAttr();
                attr.group = Integer.valueOf(values[values.length - 3]);
                if(values[values.length - 1].equals(ignoreStr))
                	attr.group *= -1;
                else
                	System.out.println(line);
                
                HashValue md5 = new HashValue(values[1].substring(1, 33));	
                HashValue sha1 = new HashValue(values[0].substring(1, 41));

                Integer value = md5Map.get(md5);
                if(value == null || (value > 0 && attr.group < 0)){
                	md5Map.put(md5, attr.group);
                	sha1Map.put(sha1, attr.group);
                }
                
                progress += line.length() + 2;
                if(progress > i * length / 1000){
                    if(monitor.isCanceled())
                        return;
                    monitor.setProgress((int)(progress/1000));
                    i++;
                }
                
            }
            reader.close();
            db.commit();
            monitor.close();
        }
    }

    @Override
    protected void process(EvidenceFile evidence) throws Exception {
         
        String hash = evidence.getHash();
        if(map != null && hash != null && !evidence.isDir() && !evidence.isRoot()){
        	Integer attr = map.get(new HashValue(hash));
            if(attr != null){
                if(attr > 0 || alertProducts.contains(products.get(-attr)[0]))
                	evidence.addCategory(ALERT);
                else{
                    if(excludeKffIgnorable){
                        evidence.setToIgnore(true);
                        stats.incIgnored();
                        synchronized(lock){
                            excluded++;
                        }
                    }else
                        evidence.addCategory(IGNORE);
                }
                    
                //evidence.setExtraAttribute("kffgroup", kffattr.group);
            }
        }
    }
    
    private static class KffAttr implements Serializable{
        /**
         * 
         */
        private static final long serialVersionUID = 1L;
 
        boolean alert = false;
        int group;
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null) return false;

            KffAttr attr = (KffAttr) o;

            if (alert != attr.alert) return false;
            if (group != attr.group) return false;

            return true;
        }
    }
    
    static class ValueSerializer implements Serializer<KffAttr>, Serializable{

        @Override
        public void serialize(DataOutput out, KffAttr value) throws IOException {
            out.writeBoolean(value.alert);
            //out.writeUTF(value.group);
        }

        @Override
        public KffAttr deserialize(DataInput in, int available) throws IOException {
            KffAttr value = new KffAttr();
            value.alert = in.readBoolean();
            //value.group  = in.readUTF();
            return value;
        }

        @Override
        public int fixedSize() {
            return -1;
        }

    }


}
