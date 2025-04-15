package iped.engine.task.dvr.wfs;

import java.io.*;
import java.util.*;
import java.text.*;

import iped.io.SeekableInputStream;
import iped.engine.task.dvr.Util;

/*

 * WFSExtractor.
 *
 * @author guilherme.dutra


Reference information:

The implementation of the WFS extractor was based on the paper below:

https://educapes.capes.gov.br/bitstream/capes/704325/1/ANALYSISAND.pdf

Galileu Batista de Sousa
IFRN
Polícia Federal
Natal, RN, Brasil

Unaldo de Oliveira Brito
IFRN
Natal, RN, Brasil

 */


public class WFSExtractor {

    private SeekableInputStream sis = null;
    private WFS wfs;
    private SimpleDateFormat sdf;

    public WFSExtractor (){
        this.wfs = new WFS();
        sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT-3"));
    }
    
    public void clear(){
        wfs.clear();
        this.wfs = null;
        this.sis = null;
        this.sdf = null;
    }
    
    public void debug(){
        if (wfs != null)
            wfs.debug();
    }    

    public SeekableInputStream getDescriptorFromIdentifier(String identifier)throws IOException{
        
        Descriptor des = getDescriptorByUuid(Long.parseLong(identifier));
        if (des!=null){
            return getSeekableInputStream(des);
        }
        return null;

    }

    public Descriptor getDescriptorByUuid(Long uuid)throws IOException{
        if (this.wfs != null){
            return wfs.getDescriptorByUuid(uuid);
        }else{
            return null;
        }
    }

    public void init(SeekableInputStream is) throws IOException{    

        this.sis = is;

        int found = -1;
        byte array_240 [] = new byte [240];
        byte array_4096 [] = new byte [4096];        
        byte WFS04_SIG [] = {(byte)0x57,(byte)0x46,(byte)0x53,(byte)0x30,(byte)0x2E,(byte)0x34};   //WFS0.4 signature        
        byte WFS05_SIG [] = {(byte)0x57,(byte)0x46,(byte)0x53,(byte)0x30,(byte)0x2E,(byte)0x35};   //WFS0.5 signature

        //Search for WFS signature
        if(Util.readBytesFromAbsoluteFilePos(this.sis,array_4096, 0, 4096)==-1){            
            throw new WFSExtractorException("Cannot read data to find WFS signature");
        }

        found = Util.indexOf(array_4096, WFS04_SIG, 0);

        if (found == -1){ 
            found = Util.indexOf(array_4096, WFS05_SIG, 0);
            if (found == -1){
                throw new WFSExtractorException("WFS Header not found. Expected signature:"+Arrays.toString(WFS04_SIG) + " or " + Arrays.toString(WFS05_SIG) );
            }
        }

        System.arraycopy(array_4096, found, wfs.signature, 0, 6);    
        
        long address = found + 0x3010; //default address superblock info
        if(Util.readBytesFromAbsoluteFilePos(this.sis,array_240, address, 240)==-1){
            throw new WFSExtractorException("Cannot read data to find superblock info.");
        }

        //Read Superblock info
        wfs.lastDataAreaTime = Util.readLongFromBufLE(array_240, 4, 4); //0x10 
        wfs.lastVideoRecordedTime = Util.readLongFromBufLE(array_240, 8, 4); //0x14
        wfs.lastFragmentRecorded = Util.readLongFromBufLE(array_240, 12, 4); //0x18
        wfs.firstValidFragment = Util.readLongFromBufLE(array_240, 16, 4); //0x1C
        wfs.fragmentTotalLength = Util.readLongFromBufLE(array_240, 20, 4); //0x20
        wfs.firstValidFragmentTime = Util.readLongFromBufLE(array_240, 24, 4); //0x24
        wfs.firstDataAreaTime = Util.readLongFromBufLE(array_240, 28, 4); //0x28
        wfs.diskBlockSize = Util.readLongFromBufLE(array_240, 32, 4); //0x2C
        wfs.fragmentBlockSize = Util.readLongFromBufLE(array_240, 36, 4); //0x30
        wfs.reservedFragments = Util.readLongFromBufLE(array_240, 44, 4); //0x38
        wfs.indiceAreaOffset = Util.readLongFromBufLE(array_240, 56, 4); //0x44
        wfs.dataAreaOffset = Util.readLongFromBufLE(array_240, 60, 4); //0x48
        wfs.fragmentLength = Util.readLongFromBufLE(array_240, 64, 4); //0x20

        parseDescription(found);

    }

    public void parseDescription(long relative)  throws IOException{       

        long address = relative;
        int descriptorSize = 32;
        byte arrayDescriptor [] = new byte [descriptorSize];

        HashMap<Long, Descriptor> mapDescriptor = new HashMap<Long, Descriptor>();

        SimpleDateFormat DateFor = new SimpleDateFormat("dd-MM-yyyy EEE HH'h'-mm'm'-ss's'");

        String nameType = "regular";
        String st,et;

        address += wfs.indiceAreaOffset*wfs.diskBlockSize;
        byte bufferIndiceArea [] = new byte [(int)wfs.fragmentLength*descriptorSize];
        if(Util.readBytesFromAbsoluteFilePos(this.sis,bufferIndiceArea,address,bufferIndiceArea.length)==-1){
            throw new WFSExtractorException("Cannot read Descriptors info");
        }        

        for (long i = 0; i < wfs.fragmentLength; i++){

            System.arraycopy(bufferIndiceArea,((int)(i*descriptorSize)),arrayDescriptor,0,descriptorSize);

            Descriptor aux = new Descriptor();
            aux.id = i;
            aux.unknownFlag = (short)Util.readLongFromBufLE(arrayDescriptor, 1, 1);
            aux.attribute = (short)Util.readLongFromBufLE(arrayDescriptor, 2, 1);
            aux.numBlocks = (int)Util.readLongFromBufLE(arrayDescriptor, 4, 2) + 1;
            aux.previousBlock = Util.readLongFromBufLE(arrayDescriptor, 8, 4);
            aux.nextBlock = Util.readLongFromBufLE(arrayDescriptor, 12, 4);
            aux.startTime = decodeTimeStampToEpoch(Util.readLongFromBufLE(arrayDescriptor, 16, 4));
            aux.endTime = decodeTimeStampToEpoch(Util.readLongFromBufLE(arrayDescriptor, 20, 4));
            aux.lastFragmentLength = (int)(Util.readLongFromBufLE(arrayDescriptor, 24, 2) * wfs.diskBlockSize);
            aux.startBlock = Util.readLongFromBufLE(arrayDescriptor, 28, 4);
            aux.order = (short)Util.readLongFromBufLE(arrayDescriptor, 31, 1);
            aux.camera = (short)((Util.readLongFromBufLE(arrayDescriptor, 32, 1) + 2) / 4);
            aux.fragmentLength = wfs.fragmentBlockSize*wfs.diskBlockSize;
            aux.length = (aux.numBlocks-1)*aux.fragmentLength + aux.lastFragmentLength; 

            st = DateFor.format(aux.startTime);
            et = DateFor.format(aux.endTime);

            aux.setName("video - "+(i)+" - ch "+aux.camera+" - "+nameType+" - "+st+" to "+et+".h264");
            
            if (aux.attribute == 2 || aux.attribute == 3){
                aux.offset =  wfs.dataAreaOffset*wfs.diskBlockSize + aux.startBlock*wfs.fragmentBlockSize*wfs.diskBlockSize;
                wfs.descriptorList.add(aux);
            }else if (aux.attribute == 1){
                aux.offset =  wfs.dataAreaOffset*wfs.diskBlockSize + aux.id*wfs.fragmentBlockSize*wfs.diskBlockSize;
                mapDescriptor.put(aux.id,aux);              
            }

            address += descriptorSize;

            //if (i==12000)
            //    break;

        }

        //Add offset pices into descriptor
        for (Descriptor de :  wfs.descriptorList){
            de.offsetList.add(de.offset);
            Descriptor aux = de;
            for(int j=0; j < de.numBlocks-1; j++){
                if (aux != null){
                    aux = mapDescriptor.get(aux.nextBlock);
                }
                if (aux != null){
                    de.offsetList.add(aux.offset);
                }
            }
        }

        mapDescriptor.clear();

    }
    public ArrayList<Descriptor> getDescriptorList() throws Exception{
        return this.wfs.getDescriptorList();
    }

    public long decodeTimeStampToEpoch(long ts){

        long ret = 0;

        try{
        
            String timestamp = "";
            timestamp = String.format("%02d",(((ts >> 26 )& 0x3F)+2000))+"-";
            timestamp += String.format("%02d", (ts >> 22 )& 0x0F)+"-";
            timestamp += String.format("%02d", (ts >> 17 )& 0x1F)+"T";
            timestamp += String.format("%02d", (ts >> 12 )& 0x1F)+":";
            timestamp += String.format("%02d", (ts >> 6 ) & 0x3F)+":";
            timestamp += String.format("%02d", (ts & 0x3F));

            Date date = null;
            date = this.sdf.parse(timestamp);

            if (date!=null){
                ret = date.getTime();
                date = null;
            }

        }catch (Exception e){
            ret = 0;
        }

        return ret;

    }   

    public SeekableInputStream getSeekableInputStream(Descriptor des) throws IOException {

        return new WFSSeekableInputstream(des, this.sis);

    }

    class WFSSeekableInputstream extends SeekableInputStream {

        private volatile boolean closed;
        private Descriptor descriptor;
        long position = 0;
        SeekableInputStream isa;

        public WFSSeekableInputstream(Descriptor descriptor, SeekableInputStream is) {
            this.descriptor = descriptor;      
            this.isa = is;      
        }

        @Override
        public void seek(long pos) throws IOException {
            checkIfClosed();
            if (pos >= size())
                throw new IOException("Position requested larger than size");
            position = pos;
        }

        @Override
        public long position() throws IOException {
            checkIfClosed();
            return position;
        }

        @Override
        public long size() throws IOException {
            // allow reading size even if closed
            return descriptor.getLength();
        }

        @Override
        public int read() throws IOException {
            checkIfClosed();
            byte[] b = new byte[1];
            int i;
            do {
                i = read(b, 0, 1);
            } while (i == 0);

            if (i == -1)
                return -1;
            else
                return b[0] & 0xFF;
        }

        public long skip(long n) throws IOException {
            checkIfClosed();
            this.seek(position + n);
            return n;
        }

        public int read(byte buf[], int off, int len) throws IOException {

            checkIfClosed();

            if (position >= size())
                return -1;

            int i = 0;
            long address = 0;
            int address_size = descriptor.offsetList.size();
            int chunkSize = (int)descriptor.fragmentLength;
            byte[] buffer = new byte[chunkSize];
            int copyLen = 0 ;
            int len_partial = 0;
            int posInChunk = 0;
            int chunk = 0;
            int remainder = 0;
            int left = 0;
            long file_size = size();
            while (i < len){

                chunk = (int) ((i+position) / chunkSize);
                posInChunk = (int) ((i+position) % chunkSize);
                remainder = len - i;
                len_partial = (remainder <= chunkSize)?remainder:chunkSize;
                left = (int)(file_size-(i+position));
                if (left > 0)
                    len_partial = (len_partial > left)?left:len_partial;

                copyLen = 0;
                if (chunk < address_size){
                    address = descriptor.offsetList.get(chunk);            
                    copyLen = Util.readBytesFromAbsoluteFilePos(this.isa,buffer, address+posInChunk, len_partial);
                    if (copyLen > 0){
                        System.arraycopy(buffer, 0, buf, (off+i), copyLen);                    
                    }
                }
                if (copyLen <= 0){ // Just fill data with zeros if chunk is not found or can not be read
                    Arrays.fill(buf,(off+i),(len_partial+i),(byte)0);
                    i+= len_partial;
                }else{
                    i+= copyLen;
                }
                if (position+i >= file_size){
                    break;
                }

            }

            //System.out.print(descriptor.getName()+",i:"+i+",len:"+len+", chunk:"+chunk+", file_size:"+file_size+", copyLen:"+copyLen+", chunkSize:"+chunkSize);
            //System.out.println(",address_size:"+address_size+",posInChunk:"+posInChunk+", off:"+off+", len_partial:"+len_partial+", position:"+position);

            position += i;
            return i;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
            }
            isa = null;
            descriptor = null;

        }

        private void checkIfClosed() throws IOException {
            if (closed) {
                throw new IOException("InputStream already closed.");
            }
        }

    }


}

class WFS {
    
    byte signature []  = new byte [18];
    public long lastDataAreaTime = 0L;
    public long lastVideoRecordedTime = 0L;
    public long lastFragmentRecorded = 0L;
    public long firstValidFragment = 0L;
    public long fragmentTotalLength = 0L;
    public long firstValidFragmentTime = 0L;
    public long firstDataAreaTime = 0L;
    public long diskBlockSize = 0L;
    public long fragmentBlockSize = 0L;
    public long reservedFragments = 0L;
    public long indiceAreaOffset = 0L;
    public long dataAreaOffset = 0L;
    public long fragmentLength = 0L;

    public ArrayList<Descriptor> descriptorList;

    public WFS (){        
        this.descriptorList = new ArrayList<Descriptor>();
    }
    
    public ArrayList<Descriptor> getDescriptorList(){
        return this.descriptorList;
    }

    public Descriptor getDescriptorByUuid(Long uuid){
        ArrayList<Descriptor> descList = getDescriptorList();
        if (descList != null){
            for (Descriptor d: descList){
                if (d.getUUID()==uuid){
                    return d;
                }
            }
        }
        return null;
    }

    public void clear(){
        if (descriptorList != null){
            for(Descriptor o1 : this.descriptorList)
                o1.clear();
            descriptorList.clear();
            descriptorList = null;
        }
    }
    
    public void debug(){
        try{
            System.out.println("---WFS---");
            System.out.println("signature:              "+(new String(this.signature, "ISO-8859-1"))+" | "+Arrays.toString(signature));
            System.out.println("---SuperBlock---");
            System.out.println("lastDataAreaTime:       "+this.lastDataAreaTime+" | "+String.format("0x%08X", lastDataAreaTime));
            System.out.println("lastVideoRecordedTime:  "+this.lastVideoRecordedTime+" | "+String.format("0x%08X", lastVideoRecordedTime));
            System.out.println("lastFragmentRecorded:   "+this.lastFragmentRecorded+" | "+String.format("0x%08X", lastFragmentRecorded));
            System.out.println("firstValidFragment:     "+this.firstValidFragment+" | "+String.format("0x%08X", firstValidFragment));
            System.out.println("fragmentTotalLength:    "+this.fragmentTotalLength+" | "+String.format("0x%08X", fragmentTotalLength));
            System.out.println("firstValidFragmentTime: "+this.firstValidFragmentTime+" | "+String.format("0x%08X", firstValidFragmentTime));
            System.out.println("firstDataAreaTime:      "+this.firstDataAreaTime+" | "+String.format("0x%08X", firstDataAreaTime));
            System.out.println("diskBlockSize:          "+this.diskBlockSize+" | "+String.format("0x%08X", diskBlockSize));
            System.out.println("fragmentBlockSize:      "+this.fragmentBlockSize+" | "+String.format("0x%08X", fragmentBlockSize));
            System.out.println("reservedFragments:      "+this.reservedFragments+" | "+String.format("0x%08X", reservedFragments));
            System.out.println("indiceAreaOffset:       "+this.indiceAreaOffset+" | "+String.format("0x%08X", indiceAreaOffset));
            System.out.println("dataAreaOffset:         "+this.dataAreaOffset+" | "+String.format("0x%08X", dataAreaOffset));
            System.out.println("fragmentLength:         "+this.fragmentLength+" | "+String.format("0x%08X", fragmentLength));

            descriptorList.forEach(e->e.debug());
        }catch (Exception e){

        }

    }
    
}


class WFSExtractorException extends IOException {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public WFSExtractorException() {

    }

    public WFSExtractorException(Exception source) {
        super(source);
    }

    public WFSExtractorException(String message) {
        super(message);
    }

    public WFSExtractorException(Throwable cause) {
        super(cause);
    }

    public WFSExtractorException(String message, Throwable throwable) {
        super(message, throwable);
    }

}
