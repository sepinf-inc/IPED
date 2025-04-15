package iped.engine.task.dvr.wfs;

import java.io.*;
import java.util.*;
import java.text.*;

import iped.io.SeekableInputStream;

/*

 * Descriptor.
 *
 * @author guilherme.dutra

*/

public class Descriptor{

    public String name = "";
    public String path = "";

    public long id = 0;
    public short unknownFlag = 0;
    public short attribute = 0;
    public int numBlocks = 0;
    public long previousBlock = 0L;
    public long nextBlock = 0L;
    public long startTime = 0L;
    public long endTime = 0L;
    public int lastFragmentLength = 0;
    public long startBlock = 0L;
    public short order = 0;
    public short camera = 0;
    public long length = 0L;
    public long offset = 0L;
    public long fragmentLength = 0L;


    public ArrayList<Long> offsetList = new ArrayList<Long>();

    public Descriptor(){
    }
    
    public boolean isVideoDescriptor(){
        if (this.attribute == 2 || this.attribute == 3)
            return true;
        else
            return false;
    }

    public Date getCreationDate(){
        return new Date(this.startTime);
    }

    public Date getModificationDate(){
        return new Date(this.endTime);
    }

    public long getLength(){
        return this.length;
    }

    public String getName(){
        return this.name;
    }

    public void setName(String name){
        this.name = name;
    }

    public String getPath(){
        return this.path;
    }

    public void setPath(String path){
        this.path = path;
    }

    public void clear(){
    }    

    public long getUUID(){
        return offset;
    }
    
    public void debug(){

        SimpleDateFormat DateFor = new SimpleDateFormat("dd-MM-yyyy EEE HH'h'-mm'm'-ss's'");
        //DateFor.setTimeZone(TimeZone.getTimeZone("GMT+0"));            

        try {
            System.out.println("---Descriptor---");
            System.out.println("id:                "+this.id+" | "+String.format("0x%08X", id));
            System.out.println("unknownFlag:       "+this.unknownFlag+" | "+String.format("0x%08X", unknownFlag));
            System.out.println("attribute:         "+this.attribute+" | "+String.format("0x%08X", attribute));
            System.out.println("numBlocks:         "+this.numBlocks+" | "+String.format("0x%08X", numBlocks));
            System.out.println("previousBlock:     "+this.previousBlock+" | "+String.format("0x%08X", previousBlock));
            System.out.println("nextBlock:         "+this.nextBlock+" | "+String.format("0x%08X", nextBlock));
            System.out.println("startTime:         "+this.startTime+" | "+String.format("0x%08X", startTime)+" | "+DateFor.format(this.startTime));
            System.out.println("endTime:           "+this.endTime+" | "+String.format("0x%08X", endTime)+" | "+DateFor.format(this.endTime));
            System.out.println("lastFragmentLength:"+this.lastFragmentLength+" | "+String.format("0x%08X", lastFragmentLength));
            System.out.println("startBlock:        "+this.startBlock+" | "+String.format("0x%08X", startBlock));
            System.out.println("order:             "+this.order+" | "+String.format("0x%08X", order));
            System.out.println("camera:            "+this.camera+" | "+String.format("0x%08X", camera));
            System.out.println("length:            "+this.length+" | "+String.format("0x%08X", length));
            System.out.println("fragmentLength:    "+this.fragmentLength+" | "+String.format("0x%08X", fragmentLength));
            System.out.println("offset:            "+this.offset+" | "+String.format("0x%08X", offset));
            System.out.print(  "offsetList:        ");
            for (long i: offsetList){
                System.out.print(i + " - ");
            }
            System.out.println("");

        }catch (Exception e){
            ;
        }
        DateFor = null;
    }


}

