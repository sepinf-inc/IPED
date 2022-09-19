package iped.parsers.evtx.model;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import org.apache.lucene.util.ArrayUtil;

import iped.parsers.evtx.template.TemplateData;
import iped.parsers.evtx.template.TemplateInstance;

public class EvtxFile {
	RandomAccessFile ras;
	HashMap<Integer, TemplateData> templateDatas = new HashMap<Integer, TemplateData>();
	HashMap<Integer, EvtxXmlFragment> templateXmls = new HashMap<Integer, EvtxXmlFragment>();

	byte[] header = new byte[4096];
	byte[] curChunk = new byte[64*1024];
	int chunckCount=0;
	
	static long totalCount = 0;
	
	ArrayList<Object> templateValues = new ArrayList<Object>();

	EvtxRecordConsumer evtxRecordConsumer;
	private InputStream is;		

	public EvtxFile(File src) throws FileNotFoundException {
		this.is = new FileInputStream(src);
	}

	public EvtxFile(InputStream is) {
		this.is = is;
	}

	public void processFile() {
		try {
			DataInputStream dis = new DataInputStream(new BufferedInputStream(is, 64*1024));

			dis.read(header);

			ByteBuffer bb = ByteBuffer.wrap(header);
			bb.order(ByteOrder.LITTLE_ENDIAN);

			String sig = new String(ArrayUtil.copyOfSubArray(header, 0, 8));

			if(!sig.equals("ElfFile\0")) {
				throw new EvtxParseExeption("Invalid header signature");
			}

			chunckCount=bb.asShortBuffer().get(20);
			chunckCount=bb.asShortBuffer().get(21);

			for (int i = 0; i < chunckCount; i++) {
				dis.read(curChunk);
				EvtxChunk chunk = new EvtxChunk(this, curChunk);
				chunk.processChunk();
				templateXmls.clear();
			}
		}catch(Exception e) {
			e.printStackTrace();
			
		}
	}
	
	static class EvtxRecConsumer implements EvtxRecordConsumer{
		ArrayList<Exception> es = new ArrayList<>();
		int count;
		
		@Override
		public void accept(EvtxRecord evtxRecord) {
			try {
				System.out.println("ID:"+evtxRecord.id);
				System.out.println("Written time:"+evtxRecord.writtenTime);
				System.out.println("---------------------------------------------------");
				
				EvtxBinXml binXml = evtxRecord.getBinXml();
				System.out.println(evtxRecord.getEventId());
				System.out.println(binXml.toString());
				System.out.println("");
				count++;
			}catch(Exception e) {
				es.add(e);
			}
			//throw new NullPointerException(); 
		}
	}

	public EvtxRecordConsumer getEvtxRecordConsumer() {
		return evtxRecordConsumer;
	}

	public void setEvtxRecordConsumer(EvtxRecordConsumer evtxRecordConsumer) {
		this.evtxRecordConsumer = evtxRecordConsumer;
	}
	
	public void addTemplateData(int offset, TemplateData templateDefinition) {
		templateDatas.put(offset, templateDefinition);
	}
	
	public TemplateData getTemplateData(int offset) {
		return templateDatas.get(offset);
	}
	
	public void addTemplateXml(int offset, EvtxXmlFragment templateXml) {
		templateXmls.put(offset, templateXml);
	}
	
	public EvtxXmlFragment getTemplateXml(int offset) {
		return templateXmls.get(offset);
	}
	
	public static void main(String[] args) throws FileNotFoundException {
		File f = new File("/home/patrick.pdb/multicase/system.out");
		//System.setOut(new PrintStream(f));
		File dir = new File("/home/patrick.pdb/multicase/events");
		File[] files = dir.listFiles();
		EvtxRecConsumer rc = new EvtxRecConsumer();
		for (int i = 0; i < files.length; i++) {
			if(files[i].getName().contains("System.evtx")) {
				EvtxFile evtxfile = new EvtxFile(files[i]);
				evtxfile.setEvtxRecordConsumer(rc);
				evtxfile.processFile();
			}
		}
	}
}
