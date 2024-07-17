package iped.engine.task.dvr.hikvision;

import java.io.*;
import java.util.*;
import java.text.*;

import iped.io.SeekableInputStream;
import iped.engine.task.dvr.Util;


/*

 * HikvisionExtractor.
 *
 * @author guilherme.dutra


Reference information:
https://eudl.eu/pdf/10.1007/978-3-319-25512-5_13
https://github.com/theAtropos4n6/HikvisionLogAnalyzer


 */


public class HikvisionFSExtractor{  
	
	private SeekableInputStream is = null;
	private HVFS hvfs;
	
	public HikvisionFSExtractor (){
		this.hvfs = new HVFS();
	}
	
	public ArrayList<VideoFileHeader> getVideoList(){
		return this.hvfs.videoFileHeaderList;
	}
	
	public void clear(){
		hvfs.clear();
	}
	
	public void debug(){
		hvfs.debug();
	}
	
	public void init(SeekableInputStream is)  throws Exception{
		
		this.is = is;

		byte vetor_240 [] = new byte [240];
		byte vetor_4096 [] = new byte [4096];		
		//HIKVISION@HANGZHOU signature       										
		byte HVFS_SIG [] = {(byte)0x48,(byte)0x49,(byte)0x4B,(byte)0x56,(byte)0x49,(byte)0x53,(byte)0x49,(byte)0x4F,
							(byte)0x4E,(byte)0x40,(byte)0x48,(byte)0x41,(byte)0x4E,(byte)0x47,(byte)0x5A,(byte)0x48,
							(byte)0x4F,(byte)0x55};  
		//HIKBTREE signature
		byte HIKBTREE_SIG [] = {(byte)0x48,(byte)0x49,(byte)0x4B,(byte)0x42,(byte)0x54,(byte)0x52,(byte)0x45,(byte)0x45};  
		
		int dataBlockArraySize = 0;
		int found = 0;
		int dataBlockEntryOffset = 96;
		int dataBlockEntrySize = 48;
		int pageOffset = 96;
		int pageSize = 48;
		int auxOffset = 0;		
		
		Page objPage = null;		
		DataBlockEntry objDBEInit = null;		

		//Search for HIKVISION DVR signature - usualy is on byte 528
		readBytesFromAbsoluteFilePos(is, vetor_4096, 0, 4096);
		found = Util.indexOfKPM(vetor_4096, HVFS_SIG, 0);

		if (found == -1){ 
			throw new HikvisionFSExtractorException("HIKVISON Header not found. Expected signature:"+Arrays.toString(HVFS_SIG));
		}

		System.arraycopy(vetor_4096, found, hvfs.masterSector.signature, 0, 18);	
		
		readBytesFromAbsoluteFilePos(is, vetor_240, found, 240);
		
		hvfs.masterSector.hddSize = readLongFromBufLE(vetor_240, 64, 8);
		hvfs.masterSector.systemLogOffsetEnd =  readLongFromBufLE(vetor_240, 88, 8);
		hvfs.masterSector.systemLogSize =  readLongFromBufLE(vetor_240, 96, 8);
		hvfs.masterSector.videoDataAreaOffset =  readLongFromBufLE(vetor_240, 112, 8);
		hvfs.masterSector.dataBlockSize =  readLongFromBufLE(vetor_240, 128, 8);
		hvfs.masterSector.numDataBlocks =  readLongFromBufLE(vetor_240, 136, 8);
		hvfs.masterSector.hikBtree1Offset =  readLongFromBufLE(vetor_240, 144, 8);
		hvfs.masterSector.hikBtree1Size =  readLongFromBufLE(vetor_240, 152, 8);
		hvfs.masterSector.hikBtree2Offset =  readLongFromBufLE(vetor_240, 160, 8);
		hvfs.masterSector.hikBtree2Size =  readLongFromBufLE(vetor_240, 168, 8);
		hvfs.masterSector.systemTime =  readLongFromBufLE(vetor_240, 232, 8);
		

		//Read first tree
		readBytesFromAbsoluteFilePos(is, vetor_4096, hvfs.masterSector.hikBtree1Offset, 4096);
		System.arraycopy(vetor_4096, 16, hvfs.hikBtree1.signature, 0, 8);		
		hvfs.hikBtree1.createTime = readLongFromBufLE(vetor_4096, 64, 4);
		hvfs.hikBtree1.footerOffset =  readLongFromBufLE(vetor_4096, 80, 8);
		hvfs.hikBtree1.pageListOffset =  readLongFromBufLE(vetor_4096, 88, 8);
		hvfs.hikBtree1.pageOneOffset =  readLongFromBufLE(vetor_4096, 96, 8);		

		//Read second tree
		readBytesFromAbsoluteFilePos(is, vetor_4096, hvfs.masterSector.hikBtree2Offset, 4096);			
		System.arraycopy(vetor_4096, 16, hvfs.hikBtree2.signature, 0, 8);		
		hvfs.hikBtree2.createTime = readLongFromBufLE(vetor_4096, 64, 4);
		hvfs.hikBtree2.footerOffset =  readLongFromBufLE(vetor_4096, 80, 8);
		hvfs.hikBtree2.pageListOffset =  readLongFromBufLE(vetor_4096, 88, 8);
		hvfs.hikBtree2.pageOneOffset =  readLongFromBufLE(vetor_4096, 96, 8);			

		//Verify hikbtree signature
		if (Util.indexOfKPM(hvfs.hikBtree1.signature, HIKBTREE_SIG, 0) >= 0){

			//SET total pages and page one from hikbtree 1
			readBytesFromAbsoluteFilePos(is, vetor_4096, hvfs.hikBtree1.pageListOffset, 4096);				
			hvfs.totalPages = readLongFromBufLE(vetor_4096, 20, 4);
			hvfs.pageOneOffset =  readLongFromBufLE(vetor_4096, 32, 8);		
			
		} else if (Util.indexOfKPM(hvfs.hikBtree2.signature, HIKBTREE_SIG, 0) >= 0){

			//SET total pages and page one from hikbtree 2
			readBytesFromAbsoluteFilePos(is, vetor_4096, hvfs.hikBtree2.pageListOffset, 4096);				
			hvfs.totalPages = readLongFromBufLE(vetor_4096, 20, 4);
			hvfs.pageOneOffset =  readLongFromBufLE(vetor_4096, 32, 8);		
			
		} else{
			throw new HikvisionFSExtractorException("Invalid hikbtree1 and hikbtree2");
		}


		//Loas first page in pageList ( the pageOne doesnt exits in pageList)

		objPage = new Page();
		objPage.pageOffset = hvfs.pageOneOffset;
		hvfs.pageList.add(objPage);
		objPage = null;
		
		//Load page list info
		for (long i=0; i < hvfs.totalPages; i++){
			objPage = new Page();
			objPage.pageOffset = readLongFromBufLE(vetor_4096, pageOffset+auxOffset+8, 8);
			hvfs.pageList.add(objPage);		
			auxOffset += pageSize;
			objPage = null;
		}
		hvfs.totalPages = hvfs.totalPages + 1; // Plus 1 for the pageOne

		
		dataBlockArraySize = (int) hvfs.masterSector.dataBlockSize;
		
		if (dataBlockArraySize > Integer.MAX_VALUE){
			throw new HikvisionFSExtractorException("Max size of datablock allowed is 2GB");
		}
			
		
		for (Page p : hvfs.pageList) {
			readBytesFromAbsoluteFilePos(is, vetor_4096, p.pageOffset, 4096);
			p.totalDataBlockEntries = readLongFromBufLE(vetor_4096, 20, 4);
			
			auxOffset = 0;			
			for (long i=0; i < p.totalDataBlockEntries; i++){
				

				objDBEInit = new DataBlockEntry();
				
				objDBEInit.hasVideoData = readLongFromBufLE(vetor_4096, dataBlockEntryOffset+auxOffset+16, 8);
				objDBEInit.channelNumber = readLongFromBufLE(vetor_4096, dataBlockEntryOffset+auxOffset+18, 1);
				objDBEInit.startTime = readLongFromBufLE(vetor_4096, dataBlockEntryOffset+auxOffset+28, 4);
				objDBEInit.endTime = readLongFromBufLE(vetor_4096, dataBlockEntryOffset+auxOffset+32, 4);
				objDBEInit.dataOffset = readLongFromBufLE(vetor_4096, dataBlockEntryOffset+auxOffset+40, 8);
				
				hvfs.dataBlockEntryList.add(objDBEInit);		
				auxOffset += dataBlockEntrySize;
				
				objDBEInit = null;
			}
			

		}

		//Sort datablockentries on offset to better HDD seek
		Collections.sort(hvfs.dataBlockEntryList, new DataBlockEntryComparator());

	}

	public ArrayList<DataBlockEntry> getDataBlockEntryList(){
		return this.hvfs.dataBlockEntryList;
	}	

	public long getDataBlockSize(){
		return this.hvfs.masterSector.dataBlockSize;
	}	
	
	public ArrayList<VideoFileHeader> getVideoFileHeaderList(DataBlockEntry objDBE, long dataBlockSize) throws Exception{

		long lastTimeVideo = -1L;
		long lastOffsetVideo = -1L;
		long firstTimeVideo = -1L;
		long firstTimeOffset = Long.MAX_VALUE;
		long diffTime = -1L;
		long auxTime = -1L;
		long skipSeconds = 2L;
		long tmpOffset = 0L;				
		int index=0;		
		int found = 0;
		int next = 0;		


		SimpleDateFormat DateFor = new SimpleDateFormat("dd-MM-yyyy EEE HH'h'-mm'm'-ss's'");
		DateFor.setTimeZone(TimeZone.getTimeZone("GMT+0"));

		Date dateStartTime;
		Date dateEndTime;
		String st = "";
		String et = "";		
		String nameType = "";



		byte vetor_51 [] = new byte [56];	
		byte ONFI8_SIG [] = {(byte)0x4F,(byte)0x46,(byte)0x4E,(byte)0x49,(byte)0x38};  //ONFI8 signature				
		byte dataBlockArrayBuffer []; 		
		
		int dataBlockArraySize = (int) dataBlockSize;
		
		dataBlockArrayBuffer = new byte [dataBlockArraySize]; 
		

		ArrayList<VideoFileHeader> videoFileHeaderList =  new ArrayList<VideoFileHeader>() ;	

		ONFI8 auxONFI8 = null;				
		

		//Load ONFI8 table
		readBytesFromAbsoluteFilePos(is, dataBlockArrayBuffer, objDBE.dataOffset, dataBlockArraySize);
		
		found = 0;
		while ( found != -1){
			
			found = Util.indexOfKPM(dataBlockArrayBuffer, ONFI8_SIG, next);
			
			if (found != -1){
				
			
				//Search for onfi8 no starnd size of 56 bytes
				System.arraycopy(dataBlockArrayBuffer, found+5, vetor_51, 0, 51);
				if (Util.indexOfKPM(vetor_51, ONFI8_SIG, 0)==-1){	

					auxONFI8 = new ONFI8();
				
					auxONFI8.headerOffset = found;
					auxONFI8.startTime = readLongFromBufLE(dataBlockArrayBuffer, found+28, 4);
					auxONFI8.dataOffset = readLongFromBufLE(dataBlockArrayBuffer, found+40, 4);

					objDBE.onfi8List.add(auxONFI8);

					next = found + 56;
					
				}else{
					next = found + 5;
				}

				

			}
		}

		//Get the first ONFI8 offset
		if (objDBE.onfi8List.size()>0)
			objDBE.firstONFI8Offset = objDBE.onfi8List.get(0).headerOffset;
	
		//ONFI8 table is placed on reversed order
		Collections.reverse(objDBE.onfi8List);				
		
		next = 0;


		VideoFileHeader objVideoFile = null;	

		//Load Video Files Info of type 0 - full DataBlock
		objVideoFile = new VideoFileHeader();
		
		dateStartTime = new Date(objDBE.startTime * 1000);
		dateEndTime = new Date(objDBE.endTime * 1000);
		st = DateFor.format(dateStartTime);
		et = DateFor.format(dateEndTime);
		nameType = "dataBlock";		
				
		objVideoFile.setDataOffset(objDBE.dataOffset);
		objVideoFile.setDataSize(hvfs.masterSector.dataBlockSize);
		objVideoFile.setStartTime(objDBE.startTime);
		objVideoFile.setEndTime(objDBE.endTime);
		objVideoFile.setName("ch "+objDBE.channelNumber+" - "+nameType+" - "+st+" to "+et+".avi");
		objVideoFile.setChannelNumber(objDBE.channelNumber);
		objVideoFile.setPath(String.valueOf(objDBE.channelNumber)+"/");
		objVideoFile.setType(VideoFileHeader.DATA_BLOCK);
		
		videoFileHeaderList.add(objVideoFile);

		index=0;
		lastTimeVideo = -1L;
		lastOffsetVideo = -1L;
		firstTimeVideo = -1L;
		firstTimeOffset = Long.MAX_VALUE;
		diffTime = -1L;
		auxTime = -1L;				
					
		// Parse onfi8 table and find timestamps			
		while (index < objDBE.onfi8List.size()){
			
			ONFI8 o = objDBE.onfi8List.get(index);
			
			auxTime = o.startTime;
			
			if ( o.dataOffset > objDBE.highestVideoDataOffset )
				objDBE.highestVideoDataOffset = o.dataOffset;
			if ( o.dataOffset < objDBE.lowestVideoDataOffset )
				objDBE.lowestVideoDataOffset = o.dataOffset;
			
			if (lastTimeVideo == -1){
				
				lastTimeVideo = auxTime;
				firstTimeVideo = auxTime;
				firstTimeOffset = o.dataOffset;
				
			}else if ( ( (auxTime - lastTimeVideo) <= skipSeconds &&  (auxTime - lastTimeVideo) >= 0 ) && ( firstTimeOffset < o.dataOffset ) ) {
				lastTimeVideo = auxTime;
				lastOffsetVideo = o.dataOffset;
				
			}else {
				
				int videoFileSize = (int)(lastOffsetVideo-firstTimeOffset);
				
				//Check videoFileSize
				if (videoFileSize > hvfs.masterSector.dataBlockSize)
					videoFileSize = (int)hvfs.masterSector.dataBlockSize;
				
				
				if(videoFileSize > 0){
					//Regular files - type 1
					if (( objDBE.startTime == firstTimeVideo && objDBE.endTime == lastTimeVideo )){
						
						objVideoFile = new VideoFileHeader();							
						
						dateStartTime = new Date(firstTimeVideo * 1000);
						dateEndTime = new Date(lastTimeVideo * 1000);
						st = DateFor.format(dateStartTime);
						et = DateFor.format(dateEndTime);

						nameType = "regular";
						
						objVideoFile.setDataOffset(objDBE.dataOffset+firstTimeOffset);
						objVideoFile.setDataSize(videoFileSize);
						objVideoFile.setStartTime(firstTimeVideo);
						objVideoFile.setEndTime(lastTimeVideo);
						objVideoFile.setName("ch "+objDBE.channelNumber+" - "+nameType+" - "+st+" to "+et+".avi");
						objVideoFile.setChannelNumber(objDBE.channelNumber);
						objVideoFile.setPath(String.valueOf(objDBE.channelNumber)+"/");
						objVideoFile.setType(VideoFileHeader.REGULAR);
						
						videoFileHeaderList.add(objVideoFile);


					}

					// caverd-time-in files - type 2
					if ( objDBE.startTime < firstTimeVideo && objDBE.endTime > lastTimeVideo ){
						
						objVideoFile = new VideoFileHeader();							
						
						dateStartTime = new Date(firstTimeVideo * 1000);
						dateEndTime = new Date(lastTimeVideo * 1000);
						st = DateFor.format(dateStartTime);
						et = DateFor.format(dateEndTime);

						nameType = "carved-time-in";							
						
						objVideoFile.setDataOffset(objDBE.dataOffset+firstTimeOffset);
						objVideoFile.setDataSize(videoFileSize);
						objVideoFile.setStartTime(firstTimeVideo);
						objVideoFile.setEndTime(lastTimeVideo);
						objVideoFile.setName("ch "+objDBE.channelNumber+" - "+nameType+" - "+st+" to "+et+".avi");
						objVideoFile.setChannelNumber(objDBE.channelNumber);
						objVideoFile.setPath(String.valueOf(objDBE.channelNumber)+"/");
						objVideoFile.setType(VideoFileHeader.CARVED_TIME_IN);
						
						videoFileHeaderList.add(objVideoFile);

					}

					// carved-time-edge files - type 3
					if ( (objDBE.startTime > firstTimeVideo && objDBE.endTime > lastTimeVideo) || 
						 (objDBE.startTime < firstTimeVideo && objDBE.endTime < lastTimeVideo) ){
						
						objVideoFile = new VideoFileHeader();							
						
						dateStartTime = new Date(firstTimeVideo * 1000);
						dateEndTime = new Date(lastTimeVideo * 1000);
						st = DateFor.format(dateStartTime);
						et = DateFor.format(dateEndTime);

						nameType = "carved-time-edge";							
						
						objVideoFile.setDataOffset(objDBE.dataOffset+firstTimeOffset);
						objVideoFile.setDataSize(videoFileSize);
						objVideoFile.setStartTime(firstTimeVideo);
						objVideoFile.setEndTime(lastTimeVideo);
						objVideoFile.setName("ch "+objDBE.channelNumber+" - "+nameType+" - "+st+" to "+et+".avi");
						objVideoFile.setChannelNumber(objDBE.channelNumber);
						objVideoFile.setPath(String.valueOf(objDBE.channelNumber)+"/");
						objVideoFile.setType(VideoFileHeader.CARVED_TIME_EDGE);
						
						videoFileHeaderList.add(objVideoFile);

					}
					
					// carved-time-out files - type 4
					if ( objDBE.startTime > firstTimeVideo && objDBE.endTime < lastTimeVideo ){
						
						objVideoFile = new VideoFileHeader();							
						
						dateStartTime = new Date(firstTimeVideo * 1000);
						dateEndTime = new Date(lastTimeVideo * 1000);
						st = DateFor.format(dateStartTime);
						et = DateFor.format(dateEndTime);

						nameType = "carved-time-out";							
						
						objVideoFile.setDataOffset(objDBE.dataOffset+firstTimeOffset);
						objVideoFile.setDataSize(videoFileSize);
						objVideoFile.setStartTime(firstTimeVideo);
						objVideoFile.setEndTime(lastTimeVideo);
						objVideoFile.setName("ch "+objDBE.channelNumber+" - "+nameType+" - "+st+" to "+et+".avi");
						objVideoFile.setChannelNumber(objDBE.channelNumber);
						objVideoFile.setPath(String.valueOf(objDBE.channelNumber)+"/");
						objVideoFile.setType(VideoFileHeader.CARVED_TIME_OUT);
						
						videoFileHeaderList.add(objVideoFile);

					}						
				}

				diffTime = (lastTimeVideo-firstTimeVideo)+1;

				lastTimeVideo = -1;
				firstTimeVideo = -1;

				index=index-1;						
				
			}

			index=index+1;
			
			
		}
		
		// Get carved video data
		
		// This case happens if DVR starts recording after zero position, less likely to happen
		tmpOffset = objDBE.lowestVideoDataOffset;
		if (tmpOffset > 0 && tmpOffset < hvfs.masterSector.dataBlockSize){

			objVideoFile = new VideoFileHeader();
								
			objVideoFile.setDataOffset(objDBE.dataOffset+objDBE.lowestVideoDataOffset);
			objVideoFile.setDataSize(tmpOffset);
			objVideoFile.setStartTime(0);
			objVideoFile.setEndTime(0);
			nameType = "carved-data-start";
			objVideoFile.setName("ch "+objDBE.channelNumber+" - "+nameType+" - offset "+(objDBE.dataOffset+objDBE.highestVideoDataOffset)+" to "+(objDBE.dataOffset+objDBE.firstONFI8Offset)+".avi");
			objVideoFile.setChannelNumber(objDBE.channelNumber);
			objVideoFile.setPath(String.valueOf(objDBE.channelNumber)+"/");
			objVideoFile.setType(VideoFileHeader.CARVED_DATA_START);
			
			videoFileHeaderList.add(objVideoFile);

		}				
		
		tmpOffset = (objDBE.firstONFI8Offset - objDBE.highestVideoDataOffset);
		if (tmpOffset > 0 && tmpOffset < hvfs.masterSector.dataBlockSize && (objDBE.highestVideoDataOffset != -1)){


			objVideoFile = new VideoFileHeader();
								
			objVideoFile.setDataOffset(objDBE.dataOffset+objDBE.highestVideoDataOffset);
			objVideoFile.setDataSize(tmpOffset);
			objVideoFile.setStartTime(0);
			objVideoFile.setEndTime(0);			
			nameType = "carved-data-end";
			objVideoFile.setName("ch "+objDBE.channelNumber+" - "+nameType+" - offset "+(objDBE.dataOffset+objDBE.highestVideoDataOffset)+" to "+(objDBE.dataOffset+objDBE.firstONFI8Offset)+".avi");
			objVideoFile.setChannelNumber(objDBE.channelNumber);
			objVideoFile.setPath(String.valueOf(objDBE.channelNumber));
			objVideoFile.setType(VideoFileHeader.CARVED_DATA_END);
			
			videoFileHeaderList.add(objVideoFile);

		}
		
		//Sort video header entries on offset to better HDD seek
		Collections.sort(videoFileHeaderList, new VideoFileHeaderComparator());

		dataBlockArrayBuffer = null;
		
		return videoFileHeaderList;

	}
	
	public ArrayList<SystemLogHeader> getSystemLogHeaderList() throws Exception{


		long systemLogOffsetStart = 0xA200;
		long systemLogSize = this.hvfs.masterSector.systemLogOffsetEnd - systemLogOffsetStart;

		int foundIni = 0;
		int foundEnd = 0;
		int next = 0;		

		SimpleDateFormat DateFor = new SimpleDateFormat("dd-MM-yyyy EEE HH'h'-mm'm'-ss's'");
		DateFor.setTimeZone(TimeZone.getTimeZone("GMT+0"));

		String sTime = "";

		byte RATS_SIG [] = {(byte)0x52,(byte)0x41,(byte)0x54,(byte)0x53};  //RATS signature
		int sigLength = RATS_SIG.length;
		sigLength += 4; //There are two types = 0x01,0x00,0x00,0x00 and 0x14,0x00,0x00,0x00
		byte logArrayBuffer []; 		
		
		int logArraySize = (int) systemLogSize;
		
		logArrayBuffer = new byte [logArraySize]; 	

		ArrayList<SystemLogHeader> systemLogHeaderList =  new ArrayList<SystemLogHeader>() ;	

		//Load RATS table
		readBytesFromAbsoluteFilePos(is, logArrayBuffer, systemLogOffsetStart, logArraySize);


		String temp = "";

		foundEnd = 0;
		next = 0;
		while ( next !=  (logArraySize-1)){
			
			foundIni = Util.indexOfKPM(logArrayBuffer, RATS_SIG, next);
			foundEnd = Util.indexOfKPM(logArrayBuffer, RATS_SIG, foundIni + sigLength);
			
			if (foundIni == -1 && foundEnd == -1)
				break;

			if (foundEnd == -1){
				foundEnd = logArraySize -1;
			}

			int logSize = foundEnd - foundIni;
			byte variableArray [] = new byte [logSize];					
		

			System.arraycopy(logArrayBuffer, foundIni, variableArray, 0, logSize);

			SystemLogHeader objSystemLogHeader = new SystemLogHeader();
		
			objSystemLogHeader.setCreatedTime(readLongFromBufLE(variableArray, sigLength + 4, 4));


			objSystemLogHeader.setMajorType(readLongFromBufLE(variableArray, sigLength + 4 + 2, 2));
			objSystemLogHeader.setMinorType(readLongFromBufLE(variableArray, sigLength + 4 + 2 + 2, 2));

			sTime = DateFor.format(objSystemLogHeader.getCreationDate());

			int dataLogIndex = 14;

			objSystemLogHeader.setDataOffset(systemLogOffsetStart + dataLogIndex + next);
			objSystemLogHeader.setDataSize(logSize - dataLogIndex);

			temp = "System Log - "+ objSystemLogHeader.getMajorTypeDescription() + " - " + objSystemLogHeader.getMinorTypeDescription()  +" - "+ sTime + ".log";
			objSystemLogHeader.setName(temp);
			objSystemLogHeader.setPath("logs/");

			temp = "Major Type: " + objSystemLogHeader.getMajorTypeDescription() + "\n";
			temp += "Minor Type: " + objSystemLogHeader.getMinorTypeDescription() + "\n";
			temp += "Date: " + objSystemLogHeader.getCreationDate().toString() + "\n";
			temp += objSystemLogHeader.parseMinorTypeInfo(Arrays.copyOfRange(variableArray, dataLogIndex, logSize-1),objSystemLogHeader.getMinorType());

			objSystemLogHeader.setDescription(temp);

			systemLogHeaderList.add(objSystemLogHeader);

			variableArray = null;

			next = foundEnd;
			
		}

		logArrayBuffer = null;
		
		return systemLogHeaderList;

	}
	
	private long readLongFromBufLE(byte [] cbuf, int pos, int tam) {
	
		long r = 0L;
		
		for (int i = (pos-1); i > ((pos-1) - tam); i--){
			r = (r << 8) + (cbuf[i] & 0xFF);
		}
		
		return r;
	
	}



    private int readBytesFromAbsoluteFilePos(SeekableInputStream is,byte[] cbuf, long off, long len)  throws IOException  {
				
		int r = -1;
		
		is.seek(off);
		r = is.readNBytes(cbuf, 0, (int) len);
	
		return r;
		
	}
	
		
		   
 
}


class HVFS {
	
	public MasterSector masterSector;
	public HikBTree hikBtree1;
	public HikBTree hikBtree2;
	public long totalPages = 0L;
	public long pageOneOffset = 0L;
	public ArrayList<Page> pageList;	
	// The right place of dataBlockEntryList is on the page, but let's put it here and it won't even be used
	public ArrayList<DataBlockEntry> dataBlockEntryList;
	public ArrayList<VideoFileHeader> videoFileHeaderList;	
	
	public HVFS (){
		
		masterSector = new MasterSector();
		hikBtree1 = new HikBTree();
		hikBtree2 = new HikBTree();
		pageList = new ArrayList<Page>();	
		dataBlockEntryList = new ArrayList<DataBlockEntry>();
		videoFileHeaderList = new ArrayList<VideoFileHeader>();		
	}
	
	public void clear(){
		masterSector.clear();
		hikBtree1.clear();
		hikBtree2.clear();
		if (dataBlockEntryList != null){
			for(DataBlockEntry o1 : this.dataBlockEntryList)
            	o1.clear();			
			dataBlockEntryList.clear();				
		}
		if (videoFileHeaderList != null){
			for(VideoFileHeader o2 : this.videoFileHeaderList)
            	o2.clear();			
			videoFileHeaderList.clear();	
		}
		if (pageList != null){
			for(Page o3 : this.pageList)
            	o3.clear();			
			pageList.clear();	
		}


		masterSector = null;
		hikBtree1 = null;
		hikBtree2 = null;
		pageList = null;
		dataBlockEntryList = null;
		videoFileHeaderList = null;
		
	}
	
	public void debug(){
		System.out.println("---HVFS---");
		masterSector.debug();
		hikBtree1.debug();
		hikBtree2.debug();
		pageList.forEach(e->e.debug());
		dataBlockEntryList.forEach(e->e.debug());
		videoFileHeaderList.forEach(e->e.debug());		
	}
	
}

class MasterSector {

	byte signature []  = new byte [18];
	public long hddSize = 0L;
	public long systemLogOffsetEnd = 0L;
	public long systemLogSize = 0L;
	public long dataBlockSize = 0L;
	public long videoDataAreaOffset = 0L;
	public long numDataBlocks = 0L;
	public long hikBtree1Offset = 0L;
	public long hikBtree1Size = 0L;
	public long hikBtree2Offset = 0L;
	public long hikBtree2Size = 0L;
	public long systemTime = 0L;
	

	public MasterSector(){
	}
	
	public void clear(){
		signature = null;
	}	
	
	public void debug(){

		try {
			System.out.println("---MasterSector---");
			System.out.println("signature:           "+(new String(this.signature, "ISO-8859-1"))+" | "+Arrays.toString(signature));
			System.out.println("hddSize:             "+this.hddSize+" | "+String.format("0x%08X", hddSize));
			System.out.println("systemLogOffsetEnd:  "+this.systemLogOffsetEnd+" | "+String.format("0x%08X", systemLogOffsetEnd));
			System.out.println("systemLogSize:       "+this.systemLogSize+" | "+String.format("0x%08X", systemLogSize));
			System.out.println("dataBlockSize:       "+this.dataBlockSize+" | "+String.format("0x%08X", dataBlockSize));
			System.out.println("videoDataAreaOffset: "+this.videoDataAreaOffset+" | "+String.format("0x%08X", videoDataAreaOffset));
			System.out.println("numDataBlocks:       "+this.numDataBlocks+" | "+String.format("0x%08X", numDataBlocks));
			System.out.println("hikBtree1Offset:     "+this.hikBtree1Offset+" | "+String.format("0x%08X", hikBtree1Offset));
			System.out.println("hikBtree1Size:       "+this.hikBtree1Size+" | "+String.format("0x%08X", hikBtree1Size));
			System.out.println("hikBtree2Offset:     "+this.hikBtree2Offset+" | "+String.format("0x%08X", hikBtree2Offset));
			System.out.println("hikBtree2Size:       "+this.hikBtree2Size+" | "+String.format("0x%08X", hikBtree2Size));
			System.out.println("systemTime:          "+this.systemTime+" | "+String.format("0x%08X", systemTime));
		}catch (Exception e){
			;
		}
		
	}
	

	
}




class HikBTree  {

	public long createTime = 0L;
	public long footerOffset = 0L;
	public long pageListOffset = 0L;
	public long pageOneOffset = 0L;
	byte signature [] = new byte [8];
	

	public HikBTree(){
		
	}
	
	public void clear(){
		signature = null;
	}	
	
	public void debug(){

		try {
			System.out.println("---HikBTree---");
			System.out.println("signature:           "+(new String(this.signature, "ISO-8859-1"))+" | "+Arrays.toString(signature));
			System.out.println("createTime:             "+this.createTime+" | "+String.format("0x%08X", createTime));
			System.out.println("footerOffset:     "+this.footerOffset+" | "+String.format("0x%08X", footerOffset));
			System.out.println("pageListOffset:       "+this.pageListOffset+" | "+String.format("0x%08X", pageListOffset));
			System.out.println("pageOneOffset:       "+this.pageOneOffset+" | "+String.format("0x%08X", pageOneOffset));
		}catch (Exception e){
			;
		}		
	}
	

	
}


class Page {

	public long pageOffset = 0L;
	public long totalDataBlockEntries = 0L;
	
	

	public Page(){
		
	}
	
	public void clear(){
	}	
	
	public void debug(){

		System.out.println("---Page---");
		System.out.println("pageOffset:                  "+this.pageOffset+" | "+String.format("0x%08X", pageOffset));
		System.out.println("totalDataBlockEntries:       "+this.totalDataBlockEntries+" | "+String.format("0x%08X", totalDataBlockEntries));		
		
	}
	
	
}

class ONFI8 {

	public long headerOffset = 0L;
	public long startTime = 0L;
	public long dataOffset = 0L;
	
	public void clear(){
	}	
	
	public void debug(){

		System.out.println("---ONFI8---");
		System.out.println("headerOffset:      "+this.headerOffset+" | "+String.format("0x%08X", headerOffset));		
		System.out.println("startTime:          "+this.startTime+" | "+String.format("0x%08X", startTime));
		System.out.println("dataOffset:    "+this.dataOffset+" | "+String.format("0x%08X", dataOffset));
		
		
	}
	
	
}

class DataBlockEntryComparator implements Comparator<DataBlockEntry> {
	@Override
	public int compare(DataBlockEntry o1, DataBlockEntry o2) {
		return Long.valueOf(o1.dataOffset).compareTo(Long.valueOf(o2.dataOffset));
	}
}

class VideoFileHeaderComparator implements Comparator<VideoFileHeader> {
	@Override
	public int compare(VideoFileHeader o1, VideoFileHeader o2) {
		return Long.valueOf(o1.getDataOffset()).compareTo(Long.valueOf(o2.getDataOffset()));
	}
}


class HikvisionFSExtractorException extends IOException {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public HikvisionFSExtractorException() {

    }

    public HikvisionFSExtractorException(Exception source) {
        super(source);
    }

    public HikvisionFSExtractorException(String message) {
        super(message);
    }

    public HikvisionFSExtractorException(Throwable cause) {
        super(cause);
    }

    public HikvisionFSExtractorException(String message, Throwable throwable) {
        super(message, throwable);
    }

}