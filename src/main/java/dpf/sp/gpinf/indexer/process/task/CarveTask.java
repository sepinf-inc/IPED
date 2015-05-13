/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de Evidências Digitais (IPED).
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
package dpf.sp.gpinf.indexer.process.task;

import gpinf.dev.data.EvidenceFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.parser.ParseContext;
import org.arabidopsis.ahocorasick.AhoCorasick;
import org.arabidopsis.ahocorasick.SearchResult;
import org.arabidopsis.ahocorasick.Searcher;

import dpf.sp.gpinf.indexer.process.Worker;
import dpf.sp.gpinf.indexer.util.IOUtil;

/**
 * Classe responsável pelo Data Carving. Utiliza o algoritmo aho-corasick, o qual gera uma máquina de estados
 * a partir dos padrões a serem pesquisados. Assim, o algoritmo é independente do número de assinaturas pesquisadas,
 * sendo proporcional ao volume de dados de entrada e ao número de padrões descobertos.
 */
public class CarveTask extends AbstractTask{

	private static final long serialVersionUID = 1L;
	
	public static String CARVE_CONFIG = "CarvingConfig.txt";
	public static MediaType UNALLOCATED_MIMETYPE = MediaType.parse("application/x-unallocated");
	private static HashSet<MediaType> TYPES_TO_PROCESS;
	private static HashSet<String> TYPES_TO_NOT_PROCESS = new HashSet<String>();
	private static HashSet<MediaType> TYPES_TO_CARVE = new HashSet<MediaType>();	
	public static boolean enableCarving = false;

	
	public static boolean ignoreCorrupted = true;
	private static int CLUSTER_SIZE = 1;
	
	static ArrayList<CarverType> sigArray = new ArrayList<CarverType>();
	static CarverType[] signatures;
	
	public static int itensCarved = 0;
	private static int largestPatternLen = 100;
	
	EvidenceFile evidence;
	MediaTypeRegistry registry;

	
	long prevLen = 0;
	int len = 0, k = 0;
	byte[] buf = new byte[1024 * 1024];
	byte[] cBuf;
	
	synchronized public static void incItensCarved() {
		CarveTask.itensCarved++;
	}

	synchronized public static int getItensCarved() {
		return itensCarved;
	}

	public CarveTask(Worker worker){
		super(worker);
		this.registry = worker.config.getMediaTypeRegistry();
	}

	public Set<MediaType> getSupportedTypes(ParseContext context) {
		return TYPES_TO_PROCESS;
	}
	
	public static void loadConfigFile(File file) throws Exception{
		if(signatures != null)
			return;
		BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
		String line;
		while ((line = reader.readLine()) != null) {
			if (line.trim().startsWith("#") || line.trim().isEmpty())
				continue;
			
			else if(line.startsWith("bytesToSkip"))
				CLUSTER_SIZE = Integer.valueOf(line.split("=")[1].trim());
			
			else if(line.startsWith("ignoreCorrupted"))
				ignoreCorrupted = Boolean.valueOf(line.split("=")[1].trim());
			
			else if(line.startsWith("typesToProcess")){
				if(TYPES_TO_PROCESS == null)
					TYPES_TO_PROCESS = new HashSet<MediaType>();
				String[] types = line.split("=")[1].split(";");
				for(String type : types)
					TYPES_TO_PROCESS.add(MediaType.parse(type.trim()));
				
			}else if(line.startsWith("typesToNotProcess")){
				String[] types = line.split("=")[1].split(";");
				for(String type : types)
					TYPES_TO_NOT_PROCESS.add(type.trim());
			}
			else{
				String[] values = line.split(",");
				CarverType sig = new CarverType(values);
				sigArray.add(sig);
				TYPES_TO_CARVE.add(sig.mimeType);
			}
			
		}
		signatures = sigArray.toArray(new CarverType[0]);
		reader.close();
		
		//popula máquina de estado com assinaturas
		tree = new AhoCorasick();
		for(int i = 0; i < signatures.length * 2; i++){
			if(signatures[i/2].sigs[i%2].len > 0)
				for(int j = 0; j < signatures[i/2].sigs[i%2].seqs.length; j++){
					int[] s = {i, j};
					tree.add(signatures[i/2].sigs[i%2].seqs[j], s);
					//System.out.println(i + " " + j + " " + HashClass.getHashString(signatures[i/2].sigs[i%2].seqs[j]) + " " +signatures[i/2].sigs[i%2].seqEndPos[j]);
				}
					
		}
		tree.prepare();
	}
	
	public void process(EvidenceFile evidence) {
		//Nova instancia pois o mesmo objeto é reusado e nao é imutável
		new CarveTask(worker).safeProcess(evidence);
	}

	private void safeProcess(EvidenceFile evidence) {
		
		this.evidence = evidence;
		MediaType type = evidence.getMediaType();
		
		if (!enableCarving || evidence.isCarved() ||
			(TYPES_TO_PROCESS != null && !TYPES_TO_PROCESS.contains(type)))
			return;
		
		InputStream tis = null;
		try{
		
			tis = evidence.getBufferedStream();
			
			while(!MediaType.OCTET_STREAM.equals(type)){
				//avança 1 byte para não recuperar o próprio arquivo analisado
				if(TYPES_TO_CARVE.contains(type)){
					prevLen = (int)tis.skip(1);
					//break;
				}
				if(TYPES_TO_NOT_PROCESS.contains(type.toString()) || TYPES_TO_NOT_PROCESS.contains(type.getType())){
					tis.close();
					return;
				}
					
				type = registry.getSupertype(type);
			}
			
			for(int i = 0; i < signatures.length; i++)
				sigsFound.put(signatures[i].name, new ArrayDeque<Hit>());
			
			map = new TreeMap[signatures.length * 2];
			for(int i = 0; i < signatures.length * 2; i++)
				map[i] = new TreeMap<Long, Integer>();
		
		
			findSig(tis);
			
		}catch(Exception t){
			System.out.println(new Date() + "\t[AVISO]\t" + Thread.currentThread().getName() + " Erro no Carving de " + evidence.getPath() + " " + t.toString());
			//t.printStackTrace();
			
		}finally{
			IOUtil.closeQuietly(tis);
		}


	}
	
	static class Signature{
		byte[][] seqs;
		int[] seqEndPos;
		int len = 0;
	}
	
	
	static class CarverType{
		String name;
		MediaType mimeType;
		int minSize, maxSize;
		Signature[] sigs = new Signature[2];
		boolean bigendian;
		int sizePos, sizeBytes;
		
		private Signature decodeSig(String str) throws DecoderException{
			ArrayList<Byte> sigArray = new ArrayList<Byte>();
			for(int i = 0; i < str.length(); i++){
				if(str.charAt(i) != '\\')
					sigArray.add((byte)str.charAt(i));
				else{
					char[] hex = {str.charAt(i + 1), str.charAt(i + 2)};
					byte[] hexByte = Hex.decodeHex(hex);
					sigArray.add(hexByte[0]);
					i += 2;
				}	
			}
			
			//divide assinaturas com coringas em várias sem coringa
			Signature sig = new Signature();
			int i = 0;
			ArrayList<byte[]> seqs = new ArrayList<byte[]>();
			ArrayList<Integer> seqEnd = new ArrayList<Integer>();
			ArrayList<Byte> seq = new ArrayList<Byte>();
			for(Byte b : sigArray){
				if(b != '?')
					seq.add(b);
				
				if(seq.size() > 0 && (b == '?' || (i + 1 == sigArray.size() && ++i > 0))){
					byte[] array = new byte[seq.size()];
					int j = 0;
					for(Byte b1 : seq)
						array[j++] = b1;
					
					seqs.add(array);
					seqEnd.add(i);
					seq = new ArrayList<Byte>();
				}
				i++;
			}
				
			sig.len = sigArray.size();
			sig.seqs = seqs.toArray(new byte[0][]);
			int[] seqEndPos = new int[seqEnd.size()];
			i = 0;
			for(int end : seqEnd)
				seqEndPos[i++] = end;
			sig.seqEndPos = seqEndPos;
			
			
			return sig;
		}
		
		public CarverType(String[] values) throws DecoderException{
			this.name = values[0].trim();
			if(!values[1].trim().equals("null"))
				this.mimeType = MediaType.parse(values[1].trim());
			this.minSize = Integer.parseInt(values[2].trim());
			this.maxSize = Integer.parseInt(values[3].trim());
			this.sigs[0] = decodeSig(values[4].trim());
			if(!values[5].trim().equals("null"))
				this.sigs[1] = decodeSig(values[5].trim());
			else
				this.sigs[1] = new Signature();

			if(values.length < 7)
				return;
			this.sizePos = Integer.parseInt(values[6].trim());
			this.sizeBytes = Integer.parseInt(values[7].trim());
			this.bigendian = Boolean.parseBoolean(values[8].trim());
		}
	}
	

	class Hit{
		int sig;
		long off;
		public Hit(int sig, long off){
			this.sig = sig;
			this.off = off;
		}
	}
	
	
	
	private void fillBuf(InputStream in) throws IOException{
		
		prevLen += len;
		len = 0; k = 0;
		while(k != -1 && (len += k) < buf.length)
			k = in.read(buf, len, buf.length - len);
		
		cBuf = new byte[len];
		System.arraycopy(buf, 0, cBuf, 0, len);
		
	}

	
	//private HashMap<Integer, Hit> sigsFound = new HashMap<Integer, Hit>();
	//private ArrayDeque<Hit>[] sigsFound = new ArrayDeque[signatures.length];
	private HashMap<String, ArrayDeque<Hit>> sigsFound = new HashMap<String, ArrayDeque<Hit>>();
	static AhoCorasick tree = null;
	TreeMap<Long, Integer>[] map;
	
	private Hit findSig(InputStream in) throws Exception{
		
		SearchResult lastResult = new SearchResult(tree.root, null, 0);
		do{
			fillBuf(in);
			lastResult = new SearchResult(lastResult.lastMatchedState, cBuf, 0);
			Iterator<SearchResult> searcher = new Searcher(tree, tree.continueSearch(lastResult));
			while (searcher.hasNext()) {
				lastResult = searcher.next();
				
				for(Object out : lastResult.getOutputs()){
					int[] a = (int[]) out;
					int s = a[0];
					int seq = a[1];
					int i = lastResult.getLastIndex() - signatures[s/2].sigs[s%2].seqEndPos[seq];
					
					//tratamento para assinaturas com ? (divididas)
					if(signatures[s/2].sigs[s%2].seqs.length > 1){
						Integer hits = (Integer)map[s].get(prevLen + i);
						if(hits == null)
							hits = 0;
						if(hits != seq)
							continue;
						map[s].put(prevLen + i, ++hits);
						if(map[s].size() > largestPatternLen)
							map[s].remove(map[s].firstKey());
						
						if(hits < signatures[s/2].sigs[s%2].seqs.length)
							continue;
					}
					
					Hit foot = null;
					Hit head = null;
					if(s % 2 == 0){
						head = new Hit(s, prevLen + i);
						
						if(signatures[s/2].name.equals("OLE")){
							int oleLen = getLenFromOLE(buf, i, evidence.getLength() - (prevLen + i));
							foot = new Hit(s, head.off + oleLen);
						
						//Testa se possui info de tamanho
						}else if(signatures[s/2].sizeBytes > 0){
							long length = getLenFromHeader(i, s);
							if(length > 0)
								foot = new Hit(s, head.off + length);
							 
						//Testa se possui footer
						}else if(signatures[s/2].sigs[1].len > 0 ){
							/*if(!signatures[s/2].name.equals("ZIP") ){
								sigsFound[0].addLast(head);
							}else if(sigsFound[1].isEmpty())
								sigsFound[1].addLast(head);
							*/
							if(!signatures[s/2].name.equals("ZIP") || sigsFound.get(signatures[s/2].name).isEmpty())
								sigsFound.get(signatures[s/2].name).addLast(head);
							
							//descarta headers antigos
							if(sigsFound.get(signatures[s/2].name).size() > 100)
								sigsFound.get(signatures[s/2].name).pollFirst();
							
						}else{
							long length = Math.min(signatures[s/2].maxSize, evidence.getLength() - head.off);
							foot = new Hit(s, head.off + (int)length);
						}
						
					}else{
						foot = new Hit(s, prevLen + i);
						/*if(signatures[s/2].name.equals("ZIP"))
							head = sigsFound[1].pollLast();
						else{
							Hit hit = sigsFound[0].peekLast();
							if(hit != null && hit.sig/2 == s/2)
								head = sigsFound[0].pollLast();
						}*/
						head = sigsFound.get(signatures[s/2].name).pollLast();
					}
					
					if(foot != null && head != null){
						long length = foot.off + signatures[foot.sig/2].sigs[1].len - head.off;
						if(length >= signatures[s/2].minSize && length <= signatures[s/2].maxSize)
							addCarvedFile(head.off, length, s/2);
						break;
					}
				}
				
			}
			
		}while(k != -1);
		
		return null;
	}
	

	
	private long getLenFromHeader(int i, int s){
		long length = 0;
		int off = i + signatures[s/2].sizePos;
		for(int j = 0; j < signatures[s/2].sizeBytes; j++)
			if(!signatures[s/2].bigendian)
				length |= (buf[off + j] & 0xff) << (8 * j);
			else
				length |= (buf[off + j] & 0xff) << (8 * (signatures[s/2].sizeBytes - j - 1));
		
		long maxLen = evidence.getLength();
		if(i + prevLen + length > maxLen)
			length = maxLen - (i + prevLen);
		
		return length;
	}
	
	private int getLenFromOLE(byte[] buf, int off, long maxLen){
		
		int blockSizeOff = off + 0x1E;
		
		int blockSize = (int)Math.pow(2, ((buf[blockSizeOff] & 0xff) << 0 | 
							(buf[blockSizeOff + 1] & 0xff) << 8));
		
		
		int numBatBlocksOff = off + 0x2C;
		
		int numBatBlocks = (buf[numBatBlocksOff] & 0xff) << 0 | 
						(buf[numBatBlocksOff + 1] & 0xff) << 8 |
						(buf[numBatBlocksOff + 2] & 0xff) << 16 |
						(buf[numBatBlocksOff + 3] & 0xff) << 24;
		
		/*int lastBatOffPos = i + 0x4C + (batSize - 1) * 4;
		int lastBatOffP = 	(buf[lastBatOffPos] & 0xff) << 0 | 
							(buf[lastBatOffPos + 1] & 0xff) << 8 |
							(buf[lastBatOffPos + 2] & 0xff) << 16 |
							(buf[lastBatOffPos + 3] & 0xff) << 24;
		*/
		
		int len = (numBatBlocks * blockSize / 4 + 1) * blockSize;
		
		return len;
	}
	
	
	private void addCarvedFile(long off, long len, int sig) throws Exception{
		
		EvidenceFile evidence = new EvidenceFile();
		evidence.setName("Carved-" + off);
		evidence.setPath(this.evidence.getPath() + ">>" + evidence.getName());
		evidence.setLength(len);
		
		int parentId = this.evidence.getId();
		evidence.setParentId(Integer.toString(parentId));
		evidence.addParentIds(this.evidence.getParentIds());
		evidence.addParentId(parentId);
		
		evidence.setDeleted(this.evidence.isDeleted());
		evidence.setCarved(true);
		if(!signatures[sig].name.equals("OLE") && !signatures[sig].name.equals("ZIP"))
			evidence.setMediaType(signatures[sig].mimeType);
		
		long prevOff = this.evidence.getFileOffset();
		if(prevOff == -1)
			evidence.setFileOffset(off);
		else
			evidence.setFileOffset(prevOff + off);
		
		if(this.evidence.getSleuthFile() != null){
			evidence.setSleuthFile(this.evidence.getSleuthFile());
			evidence.setSleuthId(this.evidence.getSleuthId());
			//if(this.evidence.hasTmpFile())
			//	evidence.setFile(this.evidence.getTempFile());
		}else{
			evidence.setFile(this.evidence.getFile());
			evidence.setExportedFile(this.evidence.getExportedFile());
		}
		
		this.evidence.setHasChildren(true);
		
		incItensCarved();
		
		// Caso o item pai seja um subitem a ser excluído pelo filtro de exportação, processa no worker atual
		if (ExportFileTask.hasCategoryToExtract() && this.evidence.isSubItem() && !this.evidence.isToExtract()){
			caseData.incDiscoveredEvidences(1);
			worker.process(evidence);
		}else
			worker.processNewItem(evidence);
			
	}

	@Override
	public void init(Properties confProps, File confDir) throws Exception {
		String value = confProps.getProperty("enableCarving");
		if (value != null)
			value = value.trim();
		if (value != null && !value.isEmpty())
			enableCarving = Boolean.valueOf(value);
		
		loadConfigFile(new File(confDir, CARVE_CONFIG));
		
		itensCarved = 0;
	}

	@Override
	public void finish() throws Exception {
		// TODO Auto-generated method stub
		
	}
	

}
