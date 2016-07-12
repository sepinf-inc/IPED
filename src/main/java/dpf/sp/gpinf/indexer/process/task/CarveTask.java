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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.process.Worker;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.SeekableInputStream;
import gpinf.dev.data.EvidenceFile;

/**
 * Classe responsável pelo Data Carving. Utiliza o algoritmo aho-corasick, o qual gera uma máquina
 * de estados a partir dos padrões a serem pesquisados. Assim, o algoritmo é independente do número
 * de assinaturas pesquisadas, sendo proporcional ao volume de dados de entrada e ao número de
 * padrões descobertos.
 */
public class CarveTask extends BaseCarveTask {

  private static Logger LOGGER = LoggerFactory.getLogger(CarveTask.class);
  private static final long serialVersionUID = 1L;

  public static String CARVE_CONFIG = "CarvingConfig.txt";
  public static MediaType UNALLOCATED_MIMETYPE = MediaType.parse("application/x-unallocated");
  public static boolean enableCarving = false;

  public static boolean ignoreCorrupted = true;
  private static int CLUSTER_SIZE = 1;

  static ArrayList<CarverType> sigArray = new ArrayList<CarverType>();
  static CarverType[] signatures;

  private static int largestPatternLen = 100;

  EvidenceFile evidence;
  MediaTypeRegistry registry;

  long prevLen = 0;
  int len = 0, k = 0;
  byte[] buf = new byte[1024 * 1024];
  byte[] cBuf;

  public CarveTask(Worker worker) {
    super(worker);
    this.registry = worker.config.getMediaTypeRegistry();
  }

  @Override
  public boolean isEnabled() {
    return enableCarving;
  }
  
  public Set<MediaType> getSupportedTypes(ParseContext context) {
    return TYPES_TO_PROCESS;
  }

  public static void loadConfigFile(File file) throws Exception {
    if (signatures != null) {
      return;
    }
    BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
    String line = reader.readLine();
    while ((line = reader.readLine()) != null) {
      if (line.trim().startsWith("#") || line.trim().isEmpty()) {
        continue;
      } else if (line.startsWith("bytesToSkip")) {
        CLUSTER_SIZE = Integer.valueOf(line.split("=")[1].trim());
      } else if (line.startsWith("ignoreCorrupted")) {
        ignoreCorrupted = Boolean.valueOf(line.split("=")[1].trim());
      } else if (line.startsWith("typesToProcess")) {
        if (TYPES_TO_PROCESS == null) {
          TYPES_TO_PROCESS = new HashSet<MediaType>();
        }
        String[] types = line.split("=")[1].split(";");
        for (String type : types) {
          TYPES_TO_PROCESS.add(MediaType.parse(type.trim()));
        }

      } else if (line.startsWith("typesToNotProcess")) {
        String[] types = line.split("=")[1].split(";");
        for (String type : types) {
          TYPES_TO_NOT_PROCESS.add(type.trim());
        }
      } else {
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
    for (int i = 0; i < signatures.length * 2; i++) {
      if (signatures[i / 2].sigs[i % 2].len > 0) {
        for (int j = 0; j < signatures[i / 2].sigs[i % 2].seqs.length; j++) {
          int[] s = {i, j};
          tree.add(signatures[i / 2].sigs[i % 2].seqs[j], s);
          //System.out.println(i + " " + j + " " + HashClass.getHashString(signatures[i/2].sigs[i%2].seqs[j]) + " " +signatures[i/2].sigs[i%2].seqEndPos[j]);
        }
      }

    }
    tree.prepare();
  }

  public void process(EvidenceFile evidence) {
    if (!enableCarving) {
      return;
    }
    //Nova instancia pois o mesmo objeto é reusado e nao é imutável
    new CarveTask(worker).safeProcess(evidence);
  }

  private void safeProcess(EvidenceFile evidence) {

    this.evidence = evidence;

    if (!isToProcess(evidence)) {
      return;
    }

    Set<Long> kffCarvedOffsets = null;
    synchronized (kffCarved) {
      kffCarvedOffsets = kffCarved.get(evidence);
    }

    InputStream tis = null;
    try {
      MediaType type = evidence.getMediaType();

      tis = evidence.getBufferedStream();

      while (!MediaType.OCTET_STREAM.equals(type)) {
        //avança 1 byte para não recuperar o próprio arquivo analisado
        if (TYPES_TO_CARVE.contains(type)) {
          prevLen = (int) tis.skip(1);
          //break;
        }
        if (TYPES_TO_NOT_PROCESS.contains(type.toString()) || TYPES_TO_NOT_PROCESS.contains(type.getType())) {
          tis.close();
          return;
        }

        type = registry.getSupertype(type);
      }

      for (int i = 0; i < signatures.length; i++) {
        sigsFound.put(signatures[i].name, new ArrayDeque<Hit>());
      }

      map = new TreeMap[signatures.length * 2];
      for (int i = 0; i < signatures.length * 2; i++) {
        map[i] = new TreeMap<Long, Integer>();
      }

      findSig(tis, kffCarvedOffsets);

    } catch (Exception t) {
      LOGGER.warn("{} Erro no Carving de {} {}", Thread.currentThread().getName(), evidence.getPath(), t.toString());
      //t.printStackTrace();

    } finally {
      IOUtil.closeQuietly(tis);
    }

  }

  static class Signature {

    byte[][] seqs;
    int[] seqEndPos;
    int len = 0;
  }

  static class CarverType {

    String name;
    MediaType mimeType;
    long minSize, maxSize;
    Signature[] sigs = new Signature[2];
    boolean bigendian;
    int sizePos, sizeBytes;

    private Signature decodeSig(String str) throws DecoderException {
      ArrayList<Byte> sigArray = new ArrayList<Byte>();
      for (int i = 0; i < str.length(); i++) {
        if (str.charAt(i) != '\\') {
          sigArray.add((byte) str.charAt(i));
        } else {
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
      for (Byte b : sigArray) {
        if (b != '?') {
          seq.add(b);
        }

        if (seq.size() > 0 && (b == '?' || (i + 1 == sigArray.size() && ++i > 0))) {
          byte[] array = new byte[seq.size()];
          int j = 0;
          for (Byte b1 : seq) {
            array[j++] = b1;
          }

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
      for (int end : seqEnd) {
        seqEndPos[i++] = end;
      }
      sig.seqEndPos = seqEndPos;

      return sig;
    }

    public CarverType(String[] values) throws DecoderException {
      this.name = values[0].trim();
      if (!values[1].trim().equals("null")) {
        this.mimeType = MediaType.parse(values[1].trim());
      }
      this.minSize = Long.parseLong(values[2].trim());
      this.maxSize = Long.parseLong(values[3].trim());
      this.sigs[0] = decodeSig(values[4].trim());
      if (!values[5].trim().equals("null")) {
        this.sigs[1] = decodeSig(values[5].trim());
      } else {
        this.sigs[1] = new Signature();
      }

      if (values.length < 7) {
        return;
      }
      this.sizePos = Integer.parseInt(values[6].trim());
      this.sizeBytes = Integer.parseInt(values[7].trim());
      this.bigendian = Boolean.parseBoolean(values[8].trim());
    }
  }

  class Hit {

    int sig;
    long off;

    public Hit(int sig, long off) {
      this.sig = sig;
      this.off = off;
    }
  }

  private void fillBuf(InputStream in) throws IOException {

    prevLen += len;
    len = 0;
    k = 0;
    while (k != -1 && (len += k) < buf.length) {
      k = in.read(buf, len, buf.length - len);
    }

    cBuf = new byte[len];
    System.arraycopy(buf, 0, cBuf, 0, len);

  }

  //private HashMap<Integer, Hit> sigsFound = new HashMap<Integer, Hit>();
  //private ArrayDeque<Hit>[] sigsFound = new ArrayDeque[signatures.length];
  private HashMap<String, ArrayDeque<Hit>> sigsFound = new HashMap<String, ArrayDeque<Hit>>();
  static AhoCorasick tree = null;
  TreeMap<Long, Integer>[] map;

  private Hit findSig(InputStream in, Set<Long> kffCarvedOffsets) throws Exception {

    SearchResult lastResult = new SearchResult(tree.root, null, 0);
    do {
      fillBuf(in);
      lastResult = new SearchResult(lastResult.lastMatchedState, cBuf, 0);
      Iterator<SearchResult> searcher = new Searcher(tree, tree.continueSearch(lastResult));
      while (searcher.hasNext()) {
        lastResult = searcher.next();

        for (Object out : lastResult.getOutputs()) {
          int[] a = (int[]) out;
          int s = a[0];
          int seq = a[1];
          int i = lastResult.getLastIndex() - signatures[s / 2].sigs[s % 2].seqEndPos[seq];

          //tratamento para assinaturas com ? (divididas)
          if (signatures[s / 2].sigs[s % 2].seqs.length > 1) {
            Integer hits = (Integer) map[s].get(prevLen + i);
            if (hits == null) {
              hits = 0;
            }
            if (hits != seq) {
              continue;
            }
            map[s].put(prevLen + i, ++hits);
            if (map[s].size() > largestPatternLen) {
              map[s].remove(map[s].firstKey());
            }

            if (hits < signatures[s / 2].sigs[s % 2].seqs.length) {
              continue;
            }
          }

          Hit foot = null;
          Hit head = null;
          
          //testa se encontrou algum header
          if (s % 2 == 0) {
            head = new Hit(s, prevLen + i);

            //calcula tamanho customizado para OLE
            if (signatures[s / 2].name.equals("OLE")) {
              int oleLen = getLenFromOLE(buf, i, evidence.getLength() - (prevLen + i));
              foot = new Hit(s, head.off + oleLen);

            //calcula tamanho customizado para MOV
            } else if (signatures[s / 2].name.equals("MOV")) {
            	long mp4Len = getLenFromMP4(prevLen + i);
                foot = new Hit(s, head.off + mp4Len);
            	
            //Testa se possui info de tamanho no header
            } else if (signatures[s / 2].sizeBytes > 0) {
              long length = getLenFromHeader(i, s);

              //utiliza cabeçalho anterior ASF encontrado
              if (signatures[s / 2].name.equals("ASF")) {
                head = sigsFound.get("ASF").pollLast();
              }

              if (length > 0 && head != null) {
                foot = new Hit(s, head.off + length);
              }

              //Testa se possui footer
            } else if (signatures[s / 2].sigs[1].len > 0) {

              //tratamento específico para ZIP e EML: utiliza primeiro cabeçalho encontrado
              if (sigsFound.get(signatures[s / 2].name).isEmpty()
                  || (!signatures[s / 2].name.startsWith("FH")
                  && (!signatures[s / 2].name.equals("EML") || sigsFound.get("EML").peekLast().sig % 2 == 1))) {
            	  
            	//adiciona header na pilha
                sigsFound.get(signatures[s / 2].name).addLast(head);
              }

              //descarta headers antigos
              while (sigsFound.get(signatures[s / 2].name).size() > 1000)
                sigsFound.get(signatures[s / 2].name).pollFirst();

              //não possui footer, utiliza tamanho fixo
            } else {
              long length = Math.min(signatures[s / 2].maxSize, evidence.getLength() - head.off);
              foot = new Hit(s, head.off + (int) length);
            }

          //nao encontrou header, entao encontrou um footer
          } else {
            foot = new Hit(s, prevLen + i);

            //tratamento específico para EML: guarda último rodapé encontrado para recuperação posterior
            if (signatures[s / 2].name.equals("EML")) {
              eml = s / 2;
              Hit lastHit = sigsFound.get(signatures[s / 2].name).peekLast();
              if (lastHit != null) {
                if (lastHit.sig % 2 == 1) {
                  sigsFound.get(signatures[s / 2].name).pollLast();
                }
                sigsFound.get(signatures[s / 2].name).addLast(foot);
              }

            //para demais formatos, pega da pilha ultimo header encontrado
            } else {
              head = sigsFound.get(signatures[s / 2].name).pollLast();
            }
          }

          if (foot != null && head != null) {
            long length = foot.off + signatures[foot.sig / 2].sigs[1].len - head.off;
            if (length >= signatures[s / 2].minSize && length <= signatures[s / 2].maxSize) {
              if (kffCarvedOffsets == null || !kffCarvedOffsets.contains(head.off)) {
                addCarvedFile(this.evidence, head.off, length, "Carved-" + head.off, signatures[s / 2].mimeType);
              }
            }
            break;
          }
        }

      }
      //varre lista de cabeçalhos e rodapés EML encontrados e recupera
      ArrayDeque<Hit> deque = sigsFound.get("EML");
      if (deque != null) {
        while (deque.size() > 2 || (k == -1 && deque.size() > 1)) {
          Hit head = deque.pollFirst();
          Hit foot = deque.pollFirst();
          if (head.sig % 2 == 0 && foot.sig % 2 == 1) {
            long length = foot.off + signatures[foot.sig / 2].sigs[1].len - head.off;
            if (length >= signatures[eml].minSize && length <= signatures[eml].maxSize) {
                addCarvedFile(this.evidence, head.off, length, "Carved-" + head.off, signatures[eml].mimeType);
            }
          }
        }
      }

    } while (k != -1);

    return null;
  }

  private int eml;

  private long getLenFromHeader(int i, int s) {
    long length = 0;
    int off = i + signatures[s / 2].sizePos;
    for (int j = 0; j < signatures[s / 2].sizeBytes; j++) {
      if (!signatures[s / 2].bigendian) {
        length |= (long)(buf[off + j] & 0xff) << (8 * j);
      } else {
        length |= (long)(buf[off + j] & 0xff) << (8 * (signatures[s / 2].sizeBytes - j - 1));
      }
    }

    long maxLen = evidence.getLength();
    if (i + prevLen + length > maxLen) {
      length = maxLen - (i + prevLen);
    }

    if (signatures[s / 2].name.equals("RIFF")) {
      length += 8;
    }

    return length;
  }

  private int getLenFromOLE(byte[] buf, int off, long maxLen) {

    int blockSizeOff = off + 0x1E;

    int blockSize = (int) Math.pow(2, ((buf[blockSizeOff] & 0xff) << 0
        | (buf[blockSizeOff + 1] & 0xff) << 8));

    int numBatBlocksOff = off + 0x2C;

    int numBatBlocks = (buf[numBatBlocksOff] & 0xff) << 0
        | (buf[numBatBlocksOff + 1] & 0xff) << 8
        | (buf[numBatBlocksOff + 2] & 0xff) << 16
        | (buf[numBatBlocksOff + 3] & 0xff) << 24;

    //TODO melhorar detecção de tamanho para OLE
    /*int lastBatOffPos = i + 0x4C + (batSize - 1) * 4;
     int lastBatOffP = 	(buf[lastBatOffPos] & 0xff) << 0 | 
     (buf[lastBatOffPos + 1] & 0xff) << 8 |
     (buf[lastBatOffPos + 2] & 0xff) << 16 |
     (buf[lastBatOffPos + 3] & 0xff) << 24;
     */
    int len = (numBatBlocks * blockSize / 4 + 1) * blockSize;

    return len;
  }
  
  private static final String[] mp4AtomTypes = {"ftyp", "moov", "mdat", "skip", "free", "wide", "pnot", "uuid", "meta", "pict", "PICT", "pdin", "junk"};
  private static HashSet<String> atomSet = new HashSet<String>();
  
  static{
	  atomSet.addAll(Arrays.asList(mp4AtomTypes));
  }
  
  private long getLenFromMP4(long startOffset){
	  
	  long atomStart = startOffset;
	  SeekableInputStream is = null;
	  try{
		  is = evidence.getStream();
		  byte[] data = new byte[4];
		  while(true){
			  is.seek(atomStart + 4);
			  int i = 0, off = 0;
			  while(i != -1 && (off += i) < data.length)
				  i = is.read(data, off, data.length - off);
			  
			  String atomType = new String(data, "windows-1252");
			  if(!atomSet.contains(atomType))
				  //EOF
				  break;
			  
			  is.seek(atomStart);
			  i = 0; off = 0;
			  while(i != -1 && (off += i) < data.length)
				  i = is.read(data, off, data.length - off);
				  
			  long atomSize = (long)(data[0] & 0xff) << 24
					  | (data[1] & 0xff) << 16
					  | (data[2] & 0xff) << 8
					  | (data[3] & 0xff) ;
			  
			  if(atomSize == 0)
				  break;
			  
			  if(atomSize == 1){
				  byte[] extendedSize = new byte[8];
				  is.seek(atomStart + 8);
				  i = 0; off = 0;
				  while(i != -1 && (off += i) < extendedSize.length)
					  i = is.read(extendedSize, off, extendedSize.length - off);
				
				  atomSize = (long)(extendedSize[0] & 0xff) << 56
						  | (long)(extendedSize[1] & 0xff) << 48
						  | (long)(extendedSize[2] & 0xff) << 40
						  | (long)(extendedSize[3] & 0xff) << 32
				  		  | (long)(extendedSize[4] & 0xff) << 24
						  | (extendedSize[5] & 0xff) << 16
						  | (extendedSize[6] & 0xff) << 8
						  | (extendedSize[7] & 0xff) ;
			  }
			  
			  atomStart += atomSize;
			  
			  if(atomStart >= evidence.getLength())
				  break;
		  }
	  }catch(Exception e){
		  e.printStackTrace();
		  
	  }finally{
		  IOUtil.closeQuietly(is);
	  }
	  
	  return atomStart - startOffset;
  }

  @Override
  public void init(Properties confProps, File confDir) throws Exception {
    String value = confProps.getProperty("enableCarving");
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      enableCarving = Boolean.valueOf(value);
    }

    loadConfigFile(new File(confDir, CARVE_CONFIG));
  }

  @Override
  public void finish() throws Exception {
    // TODO Auto-generated method stub

  }

}
