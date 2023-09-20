package iped.engine.task.dvr.hikvision;

import java.io.*;
import java.util.*;
import java.text.*;

import iped.io.SeekableInputStream;


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
		found = KPM.indexOf(vetor_4096, HVFS_SIG, 0);

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
		if (KPM.indexOf(hvfs.hikBtree1.signature, HIKBTREE_SIG, 0) >= 0){

			//SET total pages and page one from hikbtree 1
			readBytesFromAbsoluteFilePos(is, vetor_4096, hvfs.hikBtree1.pageListOffset, 4096);				
			hvfs.totalPages = readLongFromBufLE(vetor_4096, 20, 4);
			hvfs.pageOneOffset =  readLongFromBufLE(vetor_4096, 32, 8);		
			
		} else if (KPM.indexOf(hvfs.hikBtree2.signature, HIKBTREE_SIG, 0) >= 0){

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
			
			found = KPM.indexOf(dataBlockArrayBuffer, ONFI8_SIG, next);
			
			if (found != -1){
				
			
				//Search for onfi8 no starnd size of 56 bytes
				System.arraycopy(dataBlockArrayBuffer, found+5, vetor_51, 0, 51);
				if (KPM.indexOf(vetor_51, ONFI8_SIG, 0)==-1){	

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
				
		objVideoFile.dataOffset = objDBE.dataOffset;
		objVideoFile.dataSize = hvfs.masterSector.dataBlockSize;
		objVideoFile.startTime = objDBE.startTime;
		objVideoFile.endTime = objDBE.endTime;
		objVideoFile.name = "ch "+objDBE.channelNumber+" - "+nameType+" - "+st+" to "+et+".avi";
		objVideoFile.channelNumber = objDBE.channelNumber;
		objVideoFile.path = String.valueOf(objDBE.channelNumber);
		objVideoFile.type = 0;
		
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
						
						objVideoFile.dataOffset = objDBE.dataOffset+firstTimeOffset;
						objVideoFile.dataSize = videoFileSize;
						objVideoFile.startTime = firstTimeVideo;
						objVideoFile.endTime = lastTimeVideo;
						objVideoFile.name = "ch "+objDBE.channelNumber+" - "+nameType+" - "+st+" to "+et+".avi";
						objVideoFile.channelNumber = objDBE.channelNumber;
						objVideoFile.path = String.valueOf(objDBE.channelNumber);
						objVideoFile.type = 1;
						
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
						
						objVideoFile.dataOffset = objDBE.dataOffset+firstTimeOffset;
						objVideoFile.dataSize = videoFileSize;
						objVideoFile.startTime = firstTimeVideo;
						objVideoFile.endTime = lastTimeVideo;
						objVideoFile.name = "ch "+objDBE.channelNumber+" - "+nameType+" - "+st+" to "+et+".avi";
						objVideoFile.channelNumber = objDBE.channelNumber;
						objVideoFile.path = String.valueOf(objDBE.channelNumber);
						objVideoFile.type = 2;
						
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
						
						objVideoFile.dataOffset = objDBE.dataOffset+firstTimeOffset;
						objVideoFile.dataSize = videoFileSize;
						objVideoFile.startTime = firstTimeVideo;
						objVideoFile.endTime = lastTimeVideo;
						objVideoFile.name = "ch "+objDBE.channelNumber+" - "+nameType+" - "+st+" to "+et+".avi";
						objVideoFile.channelNumber = objDBE.channelNumber;
						objVideoFile.path = String.valueOf(objDBE.channelNumber);						
						objVideoFile.type = 3;
						
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
						
						objVideoFile.dataOffset = objDBE.dataOffset+firstTimeOffset;
						objVideoFile.dataSize = videoFileSize;
						objVideoFile.startTime = firstTimeVideo;
						objVideoFile.endTime = lastTimeVideo;
						objVideoFile.name = "ch "+objDBE.channelNumber+" - "+nameType+" - "+st+" to "+et+".avi";
						objVideoFile.channelNumber = objDBE.channelNumber;
						objVideoFile.path = String.valueOf(objDBE.channelNumber);						
						objVideoFile.type = 4;
						
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
								
			objVideoFile.dataOffset = objDBE.dataOffset+objDBE.lowestVideoDataOffset;
			objVideoFile.dataSize = tmpOffset;
			objVideoFile.startTime = 0;
			objVideoFile.endTime = 0;
			nameType = "carved-data-start";
			objVideoFile.name = "ch "+objDBE.channelNumber+" - "+nameType+" - offset "+(objDBE.dataOffset+objDBE.highestVideoDataOffset)+" to "+(objDBE.dataOffset+objDBE.firstONFI8Offset)+".avi";
			objVideoFile.channelNumber = objDBE.channelNumber;
			objVideoFile.path = String.valueOf(objDBE.channelNumber);				
			objVideoFile.type = 5;
			
			videoFileHeaderList.add(objVideoFile);

		}				
		
		tmpOffset = (objDBE.firstONFI8Offset - objDBE.highestVideoDataOffset);
		if (tmpOffset > 0 && tmpOffset < hvfs.masterSector.dataBlockSize && (objDBE.highestVideoDataOffset != -1)){


			objVideoFile = new VideoFileHeader();
								
			objVideoFile.dataOffset = objDBE.dataOffset+objDBE.highestVideoDataOffset;
			objVideoFile.dataSize = tmpOffset;
			objVideoFile.startTime = 0;
			objVideoFile.endTime = 0;
			
			nameType = "carved-data-end";
			objVideoFile.name = "ch "+objDBE.channelNumber+" - "+nameType+" - offset "+(objDBE.dataOffset+objDBE.highestVideoDataOffset)+" to "+(objDBE.dataOffset+objDBE.firstONFI8Offset)+".avi";
			objVideoFile.channelNumber = objDBE.channelNumber;
			objVideoFile.path = String.valueOf(objDBE.channelNumber);				
			objVideoFile.type = 6;
			
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

		Date dateTime;
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
		
		foundEnd = 0;
		next = 0;
		while ( next !=  (logArraySize-1)){
			
			foundIni = KPM.indexOf(logArrayBuffer, RATS_SIG, next);
			foundEnd = KPM.indexOf(logArrayBuffer, RATS_SIG, foundIni + sigLength);
			
			if (foundIni == -1 && foundEnd == -1)
				break;

			if (foundEnd == -1){
				foundEnd = logArraySize -1;
			}

			int logSize = foundEnd - foundIni;
			byte variableArray [] = new byte [logSize];					
		

			System.arraycopy(logArrayBuffer, foundIni, variableArray, 0, logSize);

			SystemLogHeader objSystemLogHeader = new SystemLogHeader();
		
			objSystemLogHeader.createdTime = readLongFromBufLE(variableArray, sigLength + 4, 4);


			objSystemLogHeader.majorType = readLongFromBufLE(variableArray, sigLength + 4 + 2, 2);
			objSystemLogHeader.minorType = readLongFromBufLE(variableArray, sigLength + 4 + 2 + 2, 2);

			dateTime = new Date(objSystemLogHeader.createdTime * 1000);
			sTime = DateFor.format(dateTime);

			int dataLogIndex = 14;

			objSystemLogHeader.dataOffset = systemLogOffsetStart + dataLogIndex + next;
			objSystemLogHeader.dataSize = logSize - dataLogIndex;	

			objSystemLogHeader.name = "System Log - "+ objSystemLogHeader.getMajorTypeDescription() + " - " + objSystemLogHeader.getMinorTypeDescription()  +" - "+ sTime + ".log";
			objSystemLogHeader.path = "logs";

			objSystemLogHeader.description = objSystemLogHeader.parseMinorTypeInfo(Arrays.copyOfRange(variableArray, dataLogIndex, logSize-1),objSystemLogHeader.minorType);

			//System.out.println(objSystemLogHeader.getMinorTypeDescription() + "\n"+ objSystemLogHeader.description);

			systemLogHeaderList.add(objSystemLogHeader);

			variableArray = null;
			dateTime = null;

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
		pageList.clear();	
		if (dataBlockEntryList != null)		
			dataBlockEntryList.clear();
		if (videoFileHeaderList != null)
			videoFileHeaderList.clear();	

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

class DataBlockEntry {
	
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
		if (onfi8List != null)
			onfi8List.clear();
		onfi8List = null;
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

class VideoFileHeader {
	
	public long dataOffset = 0L;
	public long dataSize = 0L;
	public long startTime = 0L;
	public long endTime = 0;
	public long channelNumber = 0L;
	public String name = "";
	public String path = "";
	public int type = 0; // 0 full dataBlock, 1 regular file, 2 caverd-time-in, 3 carved-time-edge, 4 carved-time-out, 5 carved-data-start, 6 carved-data-end

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

class SystemLogHeader {
	
	public long createdTime = 0L;
	public long majorType = 0; 
	public long minorType = 0; 
	public long dataOffset = 0L;
	public long dataSize = 0L;	
	public String description = "";
	public String name = "";
	public String path = "";


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


class KPM {
    /**
     * Search the data byte array for the first occurrence 
     * of the byte array pattern.
     */
    public static int indexOf(byte[] data, byte[] pattern, int index) {
        int[] failure = computeFailure(pattern);

        int j = 0;

        for (int i = index; i < data.length; i++) {
            while (j > 0 && pattern[j] != data[i]) {
                j = failure[j - 1];
            }
            if (pattern[j] == data[i]) { 
                j++; 
            }
            if (j == pattern.length) {
                return i - pattern.length + 1;
            }
        }
        return -1;
    }

    /**
     * Computes the failure function using a boot-strapping process,
     * where the pattern is matched against itself.
     */
    private static int[] computeFailure(byte[] pattern) {
        int[] failure = new int[pattern.length];

        int j = 0;
        for (int i = 1; i < pattern.length; i++) {
            while (j>0 && pattern[j] != pattern[i]) {
                j = failure[j - 1];
            }
            if (pattern[j] == pattern[i]) {
                j++;
            }
            failure[i] = j;
        }

        return failure;
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
		return Long.valueOf(o1.dataOffset).compareTo(Long.valueOf(o2.dataOffset));
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