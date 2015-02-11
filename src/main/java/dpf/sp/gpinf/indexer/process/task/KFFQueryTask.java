/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dpf.sp.gpinf.indexer.process.task;

import dpf.sp.gpinf.indexer.process.Worker;
import gpinf.dev.data.EvidenceFile;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.commons.codec.binary.Hex;


/**
 *
 * @author Fredim
 */
public class KFFQueryTask extends AbstractTask{
       private MessageDigest digestMD5_512 = null; 
       private MessageDigest digestMD5_64k = null; 
       private static final int LIST_SIZE = 100;
       private List<EvidenceFile> listEvidenceFile = new ArrayList<>();
       int count =0;

    public KFFQueryTask(Worker worker) {
        super(worker);        
    }

    @Override
    public void init(Properties confParams, File confDir) throws Exception {
         digestMD5_512 = MessageDigest.getInstance("MD5");
         digestMD5_64k = MessageDigest.getInstance("MD5");
    }

    @Override
    public void finish() throws Exception {
        
    }

    @Override
    protected void process(EvidenceFile evidence) throws Exception {
    	InputStream in = evidence.getStream();
    	try{
    		String[] partialHashes = partialMd5Digest(in);       
            evidence.setExtraAttribute("MD5_512", partialHashes[0]);
            evidence.setExtraAttribute("MD5_64K", partialHashes[1]);
            
            //System.out.println(evidence.getPath()+ " " + partialHashes[0]);
            
    	}finally{
    		in.close();
    	}
        
        listEvidenceFile.add(evidence);
        
        if ((listEvidenceFile.size()==LIST_SIZE) || (evidence.isQueueEnd())){
            //criar arquivo txt
       
            //zipar
            //enviar 
            //receber
            //unzipar
            //tratar        
            
        }
        
        
        
        
        
    }
    

    @Override
    protected void sendToNextTask(EvidenceFile evidence) throws Exception {
        
        if ((listEvidenceFile.size()<LIST_SIZE) && (!evidence.isQueueEnd())){
            return;
        }
        //System.out.println("Worker " + worker.getName() + " Lista: " + listEvidenceFile.size());
        for (EvidenceFile item : listEvidenceFile){
            //System.out.println(worker.getName() + " Enviando item: " + item.getId());
            super.sendToNextTask(item);
        }
        listEvidenceFile.clear(); 
        
    }
    
    
    
    
    
    
    
    private String[] partialMd5Digest(final InputStream is) throws IOException {
        digestMD5_512.reset();
        digestMD5_64k.reset();
             
        byte[] buffer = new byte[65536];
        int read = 0;               
        int lsize=0;
        while(read!=-1&&(lsize+=read)<buffer.length){
            read = is.read(buffer, lsize, buffer.length-lsize);
        }
        if (lsize<512){
            digestMD5_512.update(buffer,0,lsize);            
        }else{
            digestMD5_512.update(buffer,0,512);            
        }        
        digestMD5_64k.update(buffer,0,lsize);
        
        
        byte[] d_512 = digestMD5_512.digest();
        byte[] d_64k = digestMD5_64k.digest();
        
	String md5_512 = new String(Hex.encodeHex(d_512));
        String md5_64k= new String(Hex.encodeHex(d_64k));                
        
        return new String[]{ md5_512, md5_64k};
    }    
    
    
    
}
