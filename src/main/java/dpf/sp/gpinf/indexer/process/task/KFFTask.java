package dpf.sp.gpinf.indexer.process.task;

import gpinf.dev.data.EvidenceFile;
import gpinf.dev.data.FileGroup;

import java.io.BufferedReader;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;

import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;
import org.mapdb.SerializerBase;

import dpf.sp.gpinf.indexer.process.Worker;
import dpf.sp.gpinf.indexer.process.task.HashTask.HashValue;

public class KFFTask extends AbstractTask{
    
    private static String ALERT = "Alerta de Hash";
    private static String IGNORE = "Ignorável por Hash";
    
    private static Object lock = new Object();
    private static Map<HashValue, Boolean> map;
    private static Map<HashValue, Boolean> sha1Map;
    private static DB db;

    public KFFTask(Worker worker) {
        super(worker);
    }

    @Override
    public void init(Properties confParams, File confDir) throws Exception {
     
        synchronized(lock){
            if(map == null){
                String hash = confParams.getProperty("hash");
                boolean md5 = false;
                if(hash.equals("md5"))
                    md5 = true;
                else if(hash.equals("sha-1"))
                    md5 = false;
                
                File kffDb = new File(confParams.getProperty("kffDb"));
                boolean dbExists = kffDb.exists();
                
                this.caseData.addBookmark(new FileGroup(ALERT, "", ""));
                this.caseData.addBookmark(new FileGroup(IGNORE, "", ""));
                
                db = DBMaker.newFileDB(kffDb)
                        .transactionDisable()
                        .mmapFileEnableIfSupported()
                        .asyncWriteEnable()
                        .asyncWriteFlushDelay(1000)
                        .asyncWriteQueueSize(102400)
                        .make();
                map = db.getHashMap("md5Map");
                sha1Map = db.getHashMap("sha1Map");
                if(!dbExists)
                {
                    File kffDir = new File(confParams.getProperty("kffDir")); 
                    for(File kffFile : kffDir.listFiles()){
                        if(!kffFile.getName().contains("NSRLFile"))
                            continue;
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
                            
                        }
                        reader.close();
                    }
                }
                db.commit();
            }
        }
        
        
    }
    
    @Override
    public void finish() throws Exception {
        if(!db.isClosed())
            db.close();
    }

    @Override
    protected void process(EvidenceFile evidence) throws Exception {
         
    
        String hash = evidence.getHash();
        if(hash != null){
            Boolean kffattr = map.get(new HashValue(hash));
            if(kffattr != null){
                if(kffattr)
                    evidence.addCategory(IGNORE);
                else
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
