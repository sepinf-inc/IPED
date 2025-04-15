package iped.engine.task.dvr.hikvision;


import java.util.ArrayList;

public class DataBlockEntry {

	public long hasVideoData = 0L;
	public long channelNumber = 0L;
	public long startTime = 0L;
	public long endTime = 0L;
	public long dataOffset = 0L;
	public long firstONFI8Offset = 0L;
	public long lowestVideoDataOffset = Long.MAX_VALUE;
	public long highestVideoDataOffset = -1;
	public ArrayList<ONFI8> onfi8List = new ArrayList<ONFI8>();
	
	
	public void clear(){
		if (onfi8List != null){
			for(ONFI8 o1 : this.onfi8List)
            	o1.clear();
			onfi8List.clear();
			onfi8List = null;
		}
	}
	
	public void debug(){


		System.out.println("---DataBlockEntry---");
		System.out.println("hasVideoData:       "+this.hasVideoData+" | "+String.format("0x%08X", hasVideoData));
		System.out.println("channelNumber:      "+this.channelNumber+" | "+String.format("0x%08X", channelNumber));
		System.out.println("startTime:          "+this.startTime+" | "+String.format("0x%08X", startTime));
		System.out.println("endTime:            "+this.endTime+" | "+String.format("0x%08X", endTime));
		System.out.println("dataOffset:    "+this.dataOffset+" | "+String.format("0x%08X", dataOffset));
		System.out.println("firstONFI8Offset:    "+this.firstONFI8Offset+" | "+String.format("0x%08X", firstONFI8Offset));
		System.out.println("lowestVideoDataOffset:    "+this.lowestVideoDataOffset+" | "+String.format("0x%08X", lowestVideoDataOffset));
		System.out.println("highestVideoDataOffset:    "+this.highestVideoDataOffset+" | "+String.format("0x%08X", highestVideoDataOffset));
		onfi8List.forEach(e->e.debug());
		
		
	}	

}
