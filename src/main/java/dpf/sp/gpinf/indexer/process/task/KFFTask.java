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
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;

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
    
    private static String ALERT = "Alerta de Hash";
    private static String IGNORE = "Ignorável por Hash";
    
    private static Object lock = new Object();
    private static Map<HashValue, Boolean> map;
    //private static Map<HashValue, Boolean> sha1Map;
    private static DB db;
    private static int excluded = 0;
    private boolean excludeKffIgnorable = true;

    public KFFTask(Worker worker) {
        super(worker);
    }
    
    public static void staticInit(Properties confParams){
        excluded = 0;
        String kffDbPath = confParams.getProperty("kffDb");
        if(kffDbPath == null)
            return;
        
        File kffDb = new File(kffDbPath);
        openDb(kffDb);
    }

    @Override
    public void init(Properties confParams, File confDir) throws Exception {
     
        if(map == null){
            staticInit(confParams);
            excludeKffIgnorable = Boolean.valueOf(confParams.getProperty("excludeKffIgnorable"));
            if(caseData != null){
                this.caseData.addBookmark(new FileGroup(ALERT, "", ""));
                this.caseData.addBookmark(new FileGroup(IGNORE, "", ""));
            }
            
        }
    }
    
    private static void openDb(File kffDb){
        db = DBMaker.newFileDB(kffDb)
                .transactionDisable()
                .mmapFileEnableIfSupported()
                .asyncWriteEnable()
                .asyncWriteFlushDelay(1000)
                .asyncWriteQueueSize(1024000)
                .make();
        map = db.getHashMap("hashMap");
        //sha1Map = db.getHashMap("sha1Map");
    }
    
    @Override
    public void finish() throws Exception {
        if(excluded != -1)
            System.out.println(new Date() + "\t[INFO]\t" + "Arquivos ignorados via KFF: " + excluded);
        excluded = -1;
    }
    
    public static void importKFF(File kffDir) throws IOException{
        boolean md5 = true;
        /*String hash = Configuration.properties.getProperty("hash");
        if(hash.equals("md5"))
            md5 = true;
        else if(hash.equals("sha-1"))
            md5 = false;
        */
        
        for(File kffFile : kffDir.listFiles()){
            if(!kffFile.getName().contains("NSRLFile"))
                continue;
            long length = kffFile.length();
            long progress = 0;
            int i = 0;
            ProgressMonitor monitor = new ProgressMonitor(null, "", "Importando " + kffFile.getName(), 0, (int)(length/1000));
            BufferedReader reader = new BufferedReader(new FileReader(kffFile));
            String line = reader.readLine();
            String ignoreStr = "\""; 
            while((line = reader.readLine()) != null){
                String[] values = line.split("\",\"");
                KffAttr attr = new KffAttr();
                //attr.group = values[5];
                if(values[4].equals(ignoreStr))
                    attr.ignore = true;
                
                if(md5)
                    map.put(new HashValue(values[1]), attr.ignore);
                else
                    map.put(new HashValue(values[0].substring(1)), attr.ignore);
                
                progress += line.length() + 2;
                if(progress > i * length / 1000){
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
        if(hash != null && map != null){
            Boolean kffattr = map.get(new HashValue(hash));
            if(kffattr != null){
                if(kffattr){
                    if(excludeKffIgnorable){
                        evidence.setToIgnore(true);
                        synchronized(lock){
                            excluded++;
                        }
                    }else
                        evidence.addCategory(IGNORE);
                }else
                    evidence.addCategory(ALERT);
                
                //evidence.setExtraAttribute("kffgroup", kffattr.group);
            }
        }
    }
    
    private static class KffAttr implements Serializable{
        /**
         * 
         */
        private static final long serialVersionUID = 1L;
        /*
         * ignore: status = 1
         * alert: status = 2
         */
        boolean ignore = false;
        //String group;
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null) return false;

            KffAttr attr = (KffAttr) o;

            if (ignore != attr.ignore) return false;
            //if (group != null ? !group.equals(attr.group) : attr.group != null) return false;

            return true;
        }
    }
    
    static class ValueSerializer implements Serializer<KffAttr>, Serializable{

        @Override
        public void serialize(DataOutput out, KffAttr value) throws IOException {
            out.writeBoolean(value.ignore);
            //out.writeUTF(value.group);
        }

        @Override
        public KffAttr deserialize(DataInput in, int available) throws IOException {
            KffAttr value = new KffAttr();
            value.ignore = in.readBoolean();
            //value.group  = in.readUTF();
            return value;
        }

        @Override
        public int fixedSize() {
            return -1;
        }

    }


}
