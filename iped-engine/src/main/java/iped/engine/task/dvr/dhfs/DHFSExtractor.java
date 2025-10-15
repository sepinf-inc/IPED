package iped.engine.task.dvr.dhfs;


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

The implementation of the DHFS extractor was based on:

https://github.com/gbatmobile/dhfs_extractor

Galileu Batista de Sousa
IFRN
Polícia Federal
Natal, RN, Brasil


 */

public class DHFSExtractor {

    private SeekableInputStream sis = null;
    private DHFS dhfs;

    public DHFSExtractor (){
        this.dhfs = new DHFS();
    }

    public void clear(){
        dhfs.clear();
        this.dhfs = null;
        this.sis = null;
    }
    
    public void debug(){
        if (this.dhfs != null)
            dhfs.debug();
    }      

    public void init(SeekableInputStream is) throws IOException{    

        this.sis = is;
        int found = -1;
        byte array_4096 [] = new byte [4096];
        byte DHFS_SIG [] = {(byte)0x44,(byte)0x48,(byte)0x46,(byte)0x53,(byte)0x34,(byte)0x2E,(byte)0x31};   //DHFS4.1 signature

        //Search for WFS signature
        if(Util.readBytesFromAbsoluteFilePos(this.sis,array_4096, 0, 4096)==-1){            
            throw new DHFSExtractorException("Cannot read data to find DHFS signature");
        }

        found = Util.indexOf(array_4096, DHFS_SIG, 0);

        if (found == -1){ 
            throw new DHFSExtractorException("DHFS Header not found. Expected signature:"+Arrays.toString(DHFS_SIG));
        }

        System.arraycopy(array_4096, found, dhfs.signature, 0, 7);    

        loadPartitionTable();

        this.dhfs.debug();


    }

    private void loadPartitionTable() throws IOException{    

        byte PART_SIG [] = {(byte)0xAA,(byte)0x55,(byte)0xAA,(byte)0x55};   //part signature
        byte array_64 [] = new byte [64];
        long address = this.dhfs.partTableOffset + 52;// 0x34


        long blkSize = 512;
		long found = -1;
		while ( found == -1){

            if(Util.readBytesFromAbsoluteFilePos(this.sis,array_64, address, 64)==-1){            
                throw new DHFSExtractorException("Cannot read data from partition");
            }

			found = Util.indexOf(array_64, PART_SIG, 0);
            System.out.println(found);
			
			if (found <= -1){

                this.dhfs.sbList.add(Util.readLongFromBufLE(array_64, 20, 4)* blkSize);
                this.dhfs.partList.add(Util.readLongFromBufLE(array_64, 52, 4)* blkSize);
                this.dhfs.numParts += 1;               
                address += 64;   

            }


        }

/*
        self.PART_OFFS = []
        self.SB_OFFS   = []

        self.disk.seek(self.PART_TABLE_OFF+0x34)
        partInfo = self.disk.read(64)

        self.num_parts = 0
        blkSize = 512
        while partInfo[:4] != b"\xAA\x55\xAA\x55":
            self.SB_OFFS.append(int.from_bytes(partInfo[20:24],byteorder='little') * blkSize)
            self.PART_OFFS.append(int.from_bytes(partInfo[48:56],byteorder='little') * blkSize)
            partInfo = self.disk.read(64)
            self.num_parts += 1
*/            
    }    


}

class DHFS {

    byte signature []  = new byte [18];

    //long PART_TABLE_OFF = 0x3C00;
    int DESC_SIZE = 32;
    int numParts = 0;


    public ArrayList<Long> partList;
    public ArrayList<Long> sbList;
    public long partTableOffset = 0x3C00;

    public DHFS (){        
        this.partList = new ArrayList<Long>();
        this.sbList = new ArrayList<Long>();
    }

	public void clear(){
        partList.clear();				
		partList = null;
        sbList.clear();				
		sbList = null;
		
	}

    public void debug(){

        try{
            System.out.println("---DHFS---");
            System.out.println("signature:              "+(new String(this.signature, "ISO-8859-1"))+" | "+Arrays.toString(signature));
            System.out.println("partList:         ");
            this.partList.forEach(System.out::println);
            /*
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
            */

            //descriptorList.forEach(e->e.debug());
        }catch (Exception e){

        }


    }



}

class DHFSExtractorException extends IOException {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public DHFSExtractorException() {

    }

    public DHFSExtractorException(Exception source) {
        super(source);
    }

    public DHFSExtractorException(String message) {
        super(message);
    }

    public DHFSExtractorException(Throwable cause) {
        super(cause);
    }

    public DHFSExtractorException(String message, Throwable throwable) {
        super(message, throwable);
    }

}
