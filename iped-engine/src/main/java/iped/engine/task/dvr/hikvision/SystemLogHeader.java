package iped.engine.task.dvr.hikvision;

import java.util.Date;

public class SystemLogHeader {

	private long createdTime = 0L;
	private long majorType = 0; 
	private long minorType = 0; 
	private long dataOffset = 0L;
	private long dataSize = 0L;	
	private String description = "";
	private String name = "";
	private String path = "";

	public Date getCreationDate(){
		return new Date(this.createdTime * 1000);
	}

	public String getDescription(){
		return this.description;
	}

	public String getName(){
		return this.name;
	}

	public String getPath(){
		return this.path;
	}

	public long getCreatedTime(){
		return this.createdTime;
	}

	public long getMajorType(){
		return this.majorType;
	}

	public long getMinorType(){
		return this.minorType;
	}

	public long getDataOffset(){
		return this.dataOffset;
	}


	public long getDataSize(){
		return this.dataSize;
	}

	public void setDescription(String description){
		this.description = description;
	}

	public void setName(String name){
		this.name = name;
	}

	public void setPath(String path){
		this.path = path;
	}

	public void setCreatedTime(long createdTime){
		this.createdTime = createdTime;
	}

	public void setDataOffset(long dataOffset){
		this.dataOffset = dataOffset;
	}

	public void setDataSize(long dataSize){
		this.dataSize = dataSize;
	}

	public void setMinorType(long minorType){
		this.minorType = minorType;
	}

	public void setMajorType(long majorType){
		this.majorType = majorType;
	}


	public String getMajorTypeDescription(){

		String ret = "Major Type Unknown";
		int code = (int)this.majorType;

		switch(code){

			case 0x1:
				ret = "Alarm";
				break;
			case 0x2:
				ret = "Exception";
				break;
			case 0x3:
				ret = "Operation";
				break;
			case 0x4:
				ret = "Information";
				break;								

		}

		return ret;
	}

	public String getMinorTypeDescription(){

		String ret = "Minor Type Unknown";
		int code = (int)this.minorType;

		switch(code){

			case 0x41:
				ret = "Power On";
				break;
			case 0x42:
				ret = "Local: Shutdown";
				break;
			case 0x43:
				ret = "Local: Abnormal Shutdown";
				break;
			case 0x50:
				ret = "Local: Login";
				break;								
			case 0x51:
				ret = "Local: Logout";
				break;
			case 0x52:
				ret = "Local: Configure Parameters";
				break;
			case 0x5C:
				ret = "Local: Initialize HDD";
				break;				
			case 0x6e:
				ret = "HDD Detect";
				break;				
			case 0x70:
				ret = "Remote: Login";
				break;				
			case 0x71:
				ret = "Remote: Logout";
				break;				
			case 0x76:
				ret = "Remote: Get Parameters";
				break;
			case 0x77:
				ret = "Remote: Configure Parameters";
				break;
			case 0x78:
				ret = "Remote: Get Working Status";
				break;				
			case 0x79:
				ret = "Remote: Alarm Arming";
				break;				
			case 0x7a:
				ret = "Remote: Alarm Disarming";
				break;				
			case 0x80:
				ret = "Remote: Playback by Time";
				break;				
			case 0x82:
				ret = "Remote: Initialize HDD";
				break;				
			case 0x86:
				ret = "Remote: Export Config File";
				break;				
			case 0xa0:
				ret = "Time Sync.";
				break;
			case 0xa1:
				ret = "HDD Information";
				break;
			case 0xa2:
				ret = "S.M.A.R.T. Information";
				break;												
			case 0xa3:
				ret = "Start Record";
				break;												
			case 0xa4:
				ret = "Stop Record";
				break;								
			case 0xaa:
				ret = "System Running State";
				break;
			case 0x03:
				ret = "Start Motion Detection";
				break;												
			case 0x04:
				ret = "Stop Motion Detection";
				break;												
			case 0x05:
				ret = "Start Video Tampering";
				break;												
			case 0x06:
				ret = "Stop Video Tampering";
				break;												
			case 0x22:
				ret = "Illegal Login";
				break;									
			case 0x24:
				ret = "HDD Error";
				break;
			case 0x27:
				ret = "Network Disconnected";
				break;
			case 0x54:
				ret = "Hik-Connect Offline Exception";
				break;

		}

		return ret;
	}

	private String getStringFromByteArray(byte [] byteArray, int offsetIni, int length, String charset) throws Exception{

		String ret = "";

		byte stringArray [] = new byte[length];				
		byte byteRead = 0;
		
		int j = 0;
		for (int i=0; i < length && (i+offsetIni) < byteArray.length ; i++){
			byteRead = byteArray[i+offsetIni];
			if (byteRead != 0x00 && j < stringArray.length){
				stringArray[j++] = byteRead;
			}
		}

		ret = new String(stringArray, 0, j,charset);
		stringArray = null;

		return ret;
	}

	private String getIPFromByteArray(byte [] byteArray, int offsetIni, int length) throws Exception{

		String ret = "";

		for (int i=0; i < length && (i+offsetIni) < byteArray.length ; i++){
			if (i!=length-1)
				ret += String.valueOf((int)byteArray[i+offsetIni] & 0xff)+".";
			else
				ret += String.valueOf((int)byteArray[i+offsetIni] & 0xff);
		}			

		return ret;

	}

	//TODO - parse log special info
	public String parseMinorTypeInfo(byte [] byteArrayLogInfo, long minorType) throws Exception{

		String ret = "";

		// Remote Login, Logout; Remote: Alarm Disarming, Arming; Remote:  Get Working Status, Export Config File,Get Parameters;
		if ( minorType == 0x70 || minorType == 0x71 || minorType == 0x7a || minorType == 0x78 || minorType == 0x86 || minorType == 0x76 || minorType == 0x79) { 

			String user = getStringFromByteArray(byteArrayLogInfo, 2, 6,"UTF-8");
			String ip = getIPFromByteArray(byteArrayLogInfo, 18,4);
			
			ret += "User: "+user+"\n";
			ret += "IP: "+ip+"\n";

		}

		//S.M.A.R.T. Information
		if (minorType == 0xa2 ){ 

			String firmware = getStringFromByteArray(byteArrayLogInfo, 1081, 10,"UTF-8");
			String model = getStringFromByteArray(byteArrayLogInfo, 1091, 41,"UTF-8");
			String serial = getStringFromByteArray(byteArrayLogInfo, 1132, 22,"UTF-8");
			
			ret += "Model: "+model+"\n";
			ret += "Serial: "+serial+"\n";
			ret += "Firmware: "+firmware+"\n";

		}

		//HDD Information
		if (minorType == 0xa1 ){

			String hddNumber = getStringFromByteArray(byteArrayLogInfo, 46, 1,"UTF-8");
			String firmware = getStringFromByteArray(byteArrayLogInfo, 78, 12,"UTF-8");
			String model = getStringFromByteArray(byteArrayLogInfo, 90, 40,"UTF-8");
			String serial = getStringFromByteArray(byteArrayLogInfo, 58, 20,"UTF-8");
			
			ret += "HDD: "+hddNumber+"\n";
			ret += "Model: "+model+"\n";
			ret += "Serial: "+serial+"\n";
			ret += "Firmware: "+firmware+"\n";

		}

		//Power On
		if (minorType == 0x41 ){

			String model = getStringFromByteArray(byteArrayLogInfo, 54, 65,"UTF-8");
			String serial = getStringFromByteArray(byteArrayLogInfo, 118, 27,"UTF-8");
			String firmware = "";
			String build = "";

			ret += "Model: "+model+"\n";
			ret += "Serial: "+serial+"\n";
			ret += "Firmware: "+firmware+"\n";
			ret += "Build: "+build+"\n";

		}

		//Start Record; Stop Record
		if (minorType == 0xa3 || minorType == 0xa4){ 

			int channelNumber = (int)byteArrayLogInfo[54];
			int streamType  = (int)byteArrayLogInfo[66];
			String streamTypeString = "Unknown Stream";
			boolean recordEnabled  = (byteArrayLogInfo[62]==0x01)?true:false;
			boolean eventEnabled  = (byteArrayLogInfo[63]==0x01)?true:false;
			int recordTypeStatus = (int)byteArrayLogInfo[64];
			String recordTypeString = "Unknown";
			int motionDetectionStatus = (int)byteArrayLogInfo[70];
			String motionDetectionString = "None";

			if (recordTypeStatus==0x09){
				recordTypeString = "Event";
			}else if (recordTypeStatus==0x00){
				recordTypeString = "Continuous";
			}

			if (streamType==0x01){
				streamTypeString = "Sub-Stream";
			}else if (recordTypeStatus==0x00){
				streamTypeString = "Main Stream";
			}

			if (motionDetectionStatus != 0x00){
				motionDetectionString = String.valueOf(motionDetectionStatus);
			}
			
			ret += "Channel: "+channelNumber+"\n";
			ret += "Stream Type: "+streamTypeString+"\n";
			ret += "Record: "+(recordEnabled?"On":"Off")+"\n";
			ret += "Event: "+(eventEnabled?"On":"Off")+"\n";
			ret += "Record Type: "+recordTypeString+"\n";
			ret += "Motion Detection on Camera: "+motionDetectionString+"\n";

		}

		//Illegal Login
		if (minorType == 0x22){ 

			String user = getStringFromByteArray(byteArrayLogInfo, 2, 20,"UTF-8");
			String ip = getIPFromByteArray(byteArrayLogInfo, 22,4);
			
			ret += "User: "+user+"\n";
			ret += "IP: "+ip+"\n";

		}

		// Local Login, Logout
		if ( minorType == 0x50 || minorType == 0x51){ 

			String user = getStringFromByteArray(byteArrayLogInfo, 2, 20,"UTF-8");
			
			ret += "User: "+user+"\n";

		}

		//Remote: Initialize HDD
		if (minorType == 0x82){ 

			String user = getStringFromByteArray(byteArrayLogInfo, 2, 6,"UTF-8");
			String ip = getIPFromByteArray(byteArrayLogInfo, 18,4);
			
			ret += "User: "+user+"\n";
			ret += "IP: "+ip+"\n";

		}

		// Local: Initialize HDD
		if ( minorType == 0x5C){ 

			String user = getStringFromByteArray(byteArrayLogInfo, 2, 20,"UTF-8");
			
			ret += "User: "+user+"\n";

		}

		//Start Motion Detection, Stop Motion Detection, Start Video Tampering, Stop Video Tampering
		if (minorType == 0x03 || minorType == 0x04 || minorType == 0x05 || minorType == 0x06){ 

			int channelNumber = (int)byteArrayLogInfo[2];

			ret += "Channel: "+channelNumber+"\n";

		}

		//HDD Detect
		if (minorType == 0x6e){ 

			ret = "";

		}

		// Local: Shutdown
		if ( minorType == 0x42){ 

			String user = getStringFromByteArray(byteArrayLogInfo, 2, 6,"UTF-8");
			
			ret += "User: "+user+"\n";

		}

		return ret;

	}


	public void clear(){
	}

	public void debug(){

		System.out.println("---SystemLogHeader---");
		System.out.println("createdTime     :"+this.createdTime+" | "+String.format("0x%08X", createdTime));
		System.out.println("majorType       :"+this.majorType+" | "+String.format("0x%08X", majorType));
		System.out.println("minorType       :"+this.minorType+" | "+String.format("0x%08X", minorType));
		System.out.println("dataOffset      :"+this.dataOffset+" | "+String.format("0x%08X", dataOffset));
		System.out.println("dataSize        :"+this.dataSize+" | "+String.format("0x%08X", dataSize));
		System.out.println("description     :"+description);
		System.out.println("name:           :"+name);
		System.out.println("path:           :"+path);

	}

}
