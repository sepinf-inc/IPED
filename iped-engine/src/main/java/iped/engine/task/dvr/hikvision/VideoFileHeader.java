package iped.engine.task.dvr.hikvision;

import java.util.Date;

public class VideoFileHeader {

	private long dataOffset = 0L;
	private long dataSize = 0L;
	private long startTime = 0L;
	private long endTime = 0;
	private long channelNumber = 0L;
	private String name = "";
	private String path = "";
	private int type = 0; // 0 full dataBlock, 1 regular file, 2 caverd-time-in, 3 carved-time-edge, 4 carved-time-out, 5 carved-data-start, 6 carved-data-end

	public static final int DATA_BLOCK = 0;
	public static final int REGULAR = 1;
	public static final int CARVED_TIME_IN = 2;
	public static final int CARVED_TIME_EDGE = 3;
	public static final int CARVED_TIME_OUT = 4;
	public static final int CARVED_DATA_START = 5;
	public static final int CARVED_DATA_END = 6;


	public Date getCreationDate(){
		return new Date(this.startTime * 1000);
	}

	public Date getModificationDate(){
		return new Date(this.endTime * 1000);
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

	public long getDataOffset(){
		return this.dataOffset;
	}

	public void setDataOffset(long dataOffset){
		this.dataOffset = dataOffset;
	}

	public long getDataSize(){
		return this.dataSize;
	}

	public void setDataSize(long dataSize){
		this.dataSize = dataSize;
	}

	public void setStartTime(long startTime){
		this.startTime = startTime;
	}

	public long getStartTime(){
		return this.startTime;
	}

	public void setEndTime(long endTime){
		this.endTime = endTime;
	}

	public long getEndTime(){
		return this.endTime;
	}

	public void setChannelNumber(long channelNumber){
		this.channelNumber = channelNumber;
	}

	public long getChannelNumber(){
		return this.channelNumber;
	}

	public void setType(int type){
		this.type = type;
	}

	public int getType(){
		return this.type;
	}

	public void clear(){
	}

	public void debug(){

		System.out.println("---VideoFileHeader---");
		System.out.println("dataOffset:      "+this.dataOffset+" | "+String.format("0x%08X", dataOffset));		
		System.out.println("dataSize:          "+this.dataSize+" | "+String.format("0x%08X", dataSize));
		System.out.println("startTime:    "+this.startTime+" | "+String.format("0x%08X", startTime));
		System.out.println("endTime:    "+this.endTime+" | "+String.format("0x%08X", endTime));
		System.out.println("channelNumber:    "+this.channelNumber+" | "+String.format("0x%08X", channelNumber));
		System.out.println("name:    "+name);
		System.out.println("path:    "+path);
		System.out.println("type:    "+this.type+" | "+String.format("0x%08X", type));
				
	}


}
