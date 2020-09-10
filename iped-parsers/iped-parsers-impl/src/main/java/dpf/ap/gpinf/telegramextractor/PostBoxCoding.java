/*
 * Copyright 2020-2020, João Vitor de Sá Hauck
 * 
 * This file is part of Indexador e Processador de Evidencias Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package dpf.ap.gpinf.telegramextractor;



import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.ap.gpinf.interfacetelegram.PhotoData;



/**
 *
 * @author ADMHauck
 */
public class PostBoxCoding {

    private static final Logger logger = LoggerFactory.getLogger(PostBoxCoding.class);

    public static int int32 = 0;
    public static int Int64 = 1;
    public static int Bool = 2;
    public static int Double = 3;
    public static int STRING = 4;
    public static int OBJECT = 5;
    public static int Int32Array = 6;
    public static int Int64Array = 7;
    public static int ObjectArray = 8;
    public static int ObjectDictionary = 9;
    public static int Bytes = 10;
    public static int Nil = 11;
    public static int StringArray = 12;
    public static int BytesArray = 13;
    
    private byte[] data=null;
    
    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
        
    }
    
    
    
    
    private int offset=0;
    
    public int readInt32(int start) {
        return readInt32(start,true);
    }
     public long readInt64(int start) {
        return readInt64(start,true);
    }
    public int readInt32(int start, boolean bigEndian) {
        try {
            int i = 0;
            byte len=4;
            for (int j = 0; j < len; j++) {
                int a=data[start+j];
                a=a&0xFF;
                if(bigEndian){
                    i |= (a << (j * 8));
                }else{
                    i |= (a << ((len-j-1) * 8));
                }
            }
            return i;
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return 0;
    }
    
    public long readInt64(int start, boolean bigEndian) {
        try {
            long i = 0;
            byte len=8;
            for (int j = 0; j < len; j++) {
                long a=data[start+j];
                a=a&0xFF;
                if(bigEndian){
                    i |= (a << (j * 8L));
                }else{
                    i |= (a << ((len-j-1) * 8L));
                }
            }
            return i;
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return 0;
    }
    
    public String readString(int start, int tam){
        if (start + tam > data.length || tam == 0)
            return null;
        return new String(Arrays.copyOfRange(data, start,start+tam ),StandardCharsets.UTF_8);
    }
    
    private boolean findOfset(String key,int type){
        int start=offset;
        while(offset<data.length){
            int offant=offset;
            int keylength = data[offset];
            keylength=keylength & 0xFF;
            String readk=readString(++offset, keylength);
            offset+=keylength;
            
            if(offset>=data.length){
                offset=offant+1;
                continue;
            }
            
            int readtype=data[offset];
            readtype=readtype & 0xFF;
            
            if(keylength==key.length() && key.equals(readk) && type==readtype){
                offset++;
                return true;
            }
            offset=offant+1;
            
        }
        if(start>0){
            offset=0;
            return findOfset(key, type);
        }
        return false;
    }

    

    public byte[] decodeBytesForKey(String key) {
        if (findOfset(key, Bytes)) {
            int tam = readInt32(offset);
            offset += 4;
            if (offset + tam > data.length || tam == 0)
                return null;
            return Arrays.copyOfRange(data, offset, offset + tam);

        }
        return null;
    }

    public String decodeStringForKey(String key){
        if(findOfset(key, STRING)){
            int tam=readInt32(offset);
            offset+=4;
            String aux=readString(offset, tam);
            offset+=tam;
            return aux;
        }
        return null;
        
    }
    public GenericObj readObj(){
        try{
        GenericObj obj=new GenericObj();
        obj.hash=readInt32(offset);
        offset+=4;
        int btam=readInt32(offset);
        offset+=4;
        obj.content=Arrays.copyOfRange(data, offset, offset+btam);
        offset+=btam;
        return obj;
        }catch(Exception e){
            e.printStackTrace();
            return null;
        }
    }
    public GenericObj decodeObjectForKey(String key){
        if(findOfset(key, OBJECT)){
            return readObj();
        }
        return null;
    }
    public int decodeInt32ForKey(String key){
        if(findOfset(key, int32)){
            int val=readInt32(offset);
            offset+=4;
            return val;
        }
        return 0;
    }
    
    public long decodeInt64ForKey(String key){
        if(findOfset(key,Int64)){
            logger.debug("offset {}", offset);
            long val=readInt64(offset);
            offset+=8;
            return val;
        }
        return 0;
    }
    
    public List<GenericObj> decodeObjectArrayForKey(String key){
        ArrayList<GenericObj> l=new ArrayList<>();
        if(findOfset(key, ObjectArray)){
            int tam=readInt32(offset);
            offset+=4;
            for(int i=0;i<tam;i++){
                GenericObj o=readObj();
                if(o!=null){
                    l.add(o);
                }
            }
        }
        return l;
    }
    static boolean testbit(byte data,int bit){
        return (data& (1<<bit))!=0;
    }
    static boolean testbit(int data,int bit){
        return (data& (1<<bit))!=0;
    }
    
    void readForward(byte flags){
        long forwardAuthorId=readInt64(offset);
        offset+=8;
        int forwardDate=readInt32(offset);
        offset+=4;
       
                
        if (testbit(flags, 1)) {
            long sourceID=readInt64(offset);
            offset+=8;
            
        }

        if (testbit(flags, 2)) {
            long MessagePeerId = readInt64(offset);
            offset+=8;;
            int forwardSourceMessageNamespace= readInt32(offset);
            offset+=4;
            int forwardSourceMessageIdId = readInt32(offset);
            offset+=4;;
            
        }

        if (testbit(flags, 3)) {
            int signatureLength = readInt32(offset);
            offset+=4;
            String authorSignature=readString(offset, signatureLength);
            offset+=signatureLength;
        }

        if (testbit(flags, 4)){
            int psaTypeLength= readInt32(offset);
            offset+=4;
           String psaType=readString(offset, psaTypeLength);
          offset+=psaTypeLength;
        }

    }
    
    List<byte[]> readArray() {
        int nel = readInt32(offset);
        offset += 4;
        ArrayList<byte[]> els = new ArrayList<>();
        for (int i = 0; i < nel; i++) {
            int size = readInt32(offset);
            offset += 4;
            if (offset + size < data.length) {
                els.add(Arrays.copyOfRange(data, offset, offset + size));
                offset += size;
            }
        }
        return els;
    }

    long[] readInt64Array() {
        int nel = readInt32(offset);
        offset += 4;
        long els[] = new long[nel];
        for (int i = 0; i < nel; i++) {
            long val = readInt64(offset);
            offset += 8;
            els[i] = val;
        }
        return els;
    }

    List<PhotoData> getPhotos(List<GenericObj> sizes) {
        ArrayList<PhotoData> photos = new ArrayList<>();

        for (GenericObj photo : sizes) {
            this.data = photo.content;
            this.offset = 0;
            long id = decodeInt64ForKey("i");
            long volume = decodeInt64ForKey("v");
            int local = decodeInt32ForKey("l");
            int size = decodeInt32ForKey("n");

            if (id != 0) {
                Photo f = new Photo();
                f.setName(id + "");
                f.setSize(size);
                photos.add(f);
            }

            if (volume != 0 && local != 0) {
                Photo f = new Photo();
                f.setName(volume + "_" + local);
                f.setSize(size);
                photos.add(f);
            }
        }

        return photos;
    }

    void readPeersIds(Message m, byte[] d) {
        if (m == null || d == null)
            return;
        PostBoxCoding peersDec = new PostBoxCoding();
        peersDec.setData(d);
        long peers[] = peersDec.readInt64Array();

        String message = m.getData();
        if (message == null) {
            message = "Id ";
        }
        boolean first = true;
        for (long peer : peers) {
            if (!first) {
                message += ", ";
            } else {
                first = false;
            }
            message += peer;
        }

        m.setData(message);

    }

    void readMedia(Message m) {
        
        List<PhotoData> files = new ArrayList<>();
        String mimetype = null;
        String url = decodeStringForKey("u");
        GenericObj im = decodeObjectForKey("im");
        int size=0;
        int action=0;
        if (im != null && url != null) {
            // link with image
            this.data = im.content;
            this.offset = 0;
            List<GenericObj> sizes = decodeObjectArrayForKey("r");
            mimetype = "link/image";
            logger.debug("url: {}", url);
            files = getPhotos(sizes);


        } else {
            List<GenericObj> sizes = decodeObjectArrayForKey("r");
            if (sizes.size() == 3) {
                // image
                mimetype = "image";
                logger.debug("url: {}", url);
                files = getPhotos(sizes);

            } else {
                // other documents
                long id = decodeInt64ForKey("i");
                if (id == 0) {
                    // case id=fileid
                    id = decodeInt64ForKey("f");
                }
                long volume = decodeInt64ForKey("v");
                int local = decodeInt32ForKey("l");
                size = decodeInt32ForKey("n");
                String fname = decodeStringForKey("fn");
                action = decodeInt32ForKey("_rawValue");

                mimetype = decodeStringForKey("mt");
                byte[] thumb = null;
                thumb = decodeBytesForKey("itd");

                logger.debug("v: {}", volume);
                logger.debug("l: {}", local);
                logger.debug("n: {}", size);
                logger.debug("action: {}", action);

                if (fname != null) {
                    Photo f = new Photo();
                    logger.debug("name: {}", fname);
                    f.setName(fname);
                    f.setSize(size);
                    files.add(f);
                }
                if (id != 0) {
                    Photo f = new Photo();
                    logger.debug("name: {}", id);
                    f.setName(id + "");
                    f.setSize(size);
                    files.add(f);
                  
                }

                if (volume != 0 && local != 0) {
                    Photo f = new Photo();
                    f.setName(volume + "_" + local);
                    logger.debug("name: {}", f.getName());
                    f.setSize(size);
                    files.add(f);
                }
                if (action == 2 || action == 3) {

                    byte d[] = decodeBytesForKey("peerIds");
                    readPeersIds(m, d);
                    
                }

            }
           
        }
        if (m != null) {
            // m.setThumb(thumb);
            logger.debug("mimetype: {}", mimetype);
            m.setMediaMime(mimetype);
            if (files.size() == 1) {
                m.setMediasize(files.get(0).getSize());
            }
            m.setNames(files);
            if (url != null) {
                m.setLink(true);
                m.setMediaMime("link");
            }

            
            m.setType(MapTypeMSG.decodeMsg(action));
            m.setMediasize(size);
        }

    }
      
    void  readMessage(byte[] key, byte[] data,Message m){
        //reference MessageHistoryTable.swift
        this.data=data;
        PostBoxCoding pk=new PostBoxCoding();
        pk.setData(key);
        long peerKey=pk.readInt64(0, false);
        
        int namespacekey=pk.readInt32(8,false);
        int timestampkey=pk.readInt32(12,false);
        
        byte type=data[offset++];      
        if(type==int32){
            
            int stableId = readInt32(offset);
            
            
            offset+=4;
            int stableVersion=readInt32(offset);
            offset+=4;
            
            byte dataFlagsValue= data[offset++];
            long globalID=0;
            if(testbit(dataFlagsValue, 0)){
                globalID=readInt64(offset);
                offset+=8;
            }
            int globtag=0; 
            if(testbit(dataFlagsValue, 1)){
                globtag=readInt32(offset);
                offset+=4;
            }
            long groupingKey=0;
            if(testbit(dataFlagsValue, 2)){
                groupingKey=readInt64(offset);
                offset+=8;
            }
            int groupInfoID=0;
            if (testbit(dataFlagsValue,3)) {
                groupInfoID=readInt32(offset);
                offset+=4;
            }
            
            int localTagsValue=0;
            if (testbit(dataFlagsValue,4)) {
                localTagsValue=readInt32(offset);
                offset+=4;
            }
            
            int flags=readInt32(offset);
            offset+=4;
            
            int tagsValue = readInt32(offset);
            offset+=4;
            
            byte forwardInfoFlags=data[offset++];
            if(forwardInfoFlags!=0){
                readForward(forwardInfoFlags);
            }
            byte hasautor=data[offset++];
            long authorId=0;
            
            if(hasautor==1){
                authorId=readInt64(offset);
                offset+=8;
            }
            
            
            int msglen=readInt32(offset);
            offset+=4;
            String txt=readString(offset, msglen);
            offset += msglen;
            // atributos
            List<byte[]> atrs = readArray();
            List<byte[]> embededmedia = readArray();
            List<byte[]> referencemedia = readArray();

            if(m!=null){
                m.setTimeStamp(Date.from(Instant.ofEpochSecond(pk.readInt32(12,false))));
                m.setData(txt);
                m.setId(stableId);

                boolean incoming=testbit(flags, 2) || testbit(flags, 7);
                m.setFromMe(!incoming);
                
            }
            if(m.getFrom()==null) {
            	m.setFrom(new Contact(authorId));
            }else {            	
                m.getFrom().setId(authorId);
            }


            for (byte[] b : embededmedia) {
                logger.debug("embededmedia: {}", b.length);
                PostBoxCoding media = new PostBoxCoding();
                media.setData(b);
                media.readMedia(m);
            }
            
        }
        
    }

    long getAccountId() {
        return decodeInt64ForKey("peerId");
    }
    void readUser(Contact c){
        GenericObj user=decodeObjectForKey("_");
        setData(user.content);
        c.setName(decodeStringForKey("fn"));
        c.setLastName(decodeStringForKey("ln"));
        c.setUsername(decodeStringForKey("un"));
        c.setPhone(decodeStringForKey("p"));
        List<GenericObj> l= decodeObjectArrayForKey("ph");
        ArrayList<PhotoData> photos=new ArrayList<>();
        String title=decodeStringForKey("t");
        if(title!=null) {
        	c.setName("gp_name:"+title);
        }
        for(GenericObj ph:l){
            PostBoxCoding p2=new PostBoxCoding();
            p2.setData(ph.content);
            Photo p=new Photo();
            
            p.setName(p2.decodeInt64ForKey("v")+"_"+p2.decodeInt32ForKey("l"));
            photos.add(p);
            logger.debug("photo: {}", p.getName());
            c.setPhotos(photos);
        }
        
    }
    
   
        
    
}


class Photo implements PhotoData{

   String name=null;
   int size=0;
   
   
   
	public void setName(String name) {
		this.name = name;
	}

	public void setSize(int size) {
		this.size = size;
	}

	@Override
	public String getName() {

		return name;
	}

	@Override
	public int getSize() {
	
		return size;
	}
	
}

