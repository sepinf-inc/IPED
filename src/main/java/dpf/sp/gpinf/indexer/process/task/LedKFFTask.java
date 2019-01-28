package dpf.sp.gpinf.indexer.process.task;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.parsers.util.LedHashes;
import dpf.sp.gpinf.indexer.process.Worker;
import dpf.sp.gpinf.indexer.util.HashValue;
import dpf.sp.gpinf.indexer.util.IPEDException;
import dpf.sp.gpinf.indexer.util.Log;
import gpinf.dev.data.EvidenceFile;

/**
 * Tarefa de consulta a base de hashes do LED. Pode ser removida no futuro e ser integrada a tarefa
 * de KFF. A vantagem de ser independente é que a base de hashes pode ser atualizada facilmente,
 * apenas apontando para a nova base, sem necessidade de importação.
 *
 * @author Nassif
 *
 */
public class LedKFFTask extends AbstractTask {

  private static Object lock = new Object();
  private static HashMap<String, HashValue[]> hashArrays;
  public static KffItem[] kffItems;
  private static final String taskName = "LED Database Search"; //$NON-NLS-1$
  private static final String[] ledHashOrder = {"md5",null,"edonkey","sha-1","md5-512",null,null}; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
  private static final int idxMd5 = 0;
  private static final int idxMd5_64K = 1;
  private static final int idxLength = 5;
  private static final int idxName = 6;
  private static final String ENABLE_PARAM = "enableLedWkff"; //$NON-NLS-1$
  private static boolean taskEnabled = false;
  
  public static void setEnabled(boolean enabled) {
      taskEnabled = enabled;
  }

  @Override
  public void init(Properties confParams, File confDir) throws Exception {

    synchronized (lock) {
      if (hashArrays != null) {
        return;
      }

      String enabled = confParams.getProperty(ENABLE_PARAM);
      if(enabled != null)
    	  taskEnabled = Boolean.valueOf(enabled.trim());
      
      String hash = confParams.getProperty("hash"); //$NON-NLS-1$
      String ledWkffPath = confParams.getProperty("ledWkffPath"); //$NON-NLS-1$
      if (taskEnabled && ledWkffPath == null)
    	throw new IPEDException("Configure LED database path on " + Configuration.LOCAL_CONFIG); //$NON-NLS-1$

      //backwards compatibility
      if(enabled == null && ledWkffPath != null)
      	taskEnabled = true;
      
      if(!taskEnabled)
    	  return;
      
      File wkffDir = new File(ledWkffPath.trim());
      if (!wkffDir.exists()) {
        throw new IPEDException("Invalid LED database path: " + wkffDir.getAbsolutePath()); //$NON-NLS-1$
      }

      if(hash == null || hash.trim().isEmpty())
          throw new IPEDException("Configure a hash algorithm in configuration!"); //$NON-NLS-1$
      
      hash = hash.toLowerCase();
      if (!hash.contains("md5") && !hash.contains("sha-1")) { //$NON-NLS-1$ //$NON-NLS-2$
        throw new IPEDException("Enable md5 or sha-1 hash to search on LED database!"); //$NON-NLS-1$
      }

      List<KffItem> kffList = new ArrayList<KffItem>();

      List<List<HashValue>> hashList = new ArrayList<List<HashValue>>();
      for (int col = 0; col < ledHashOrder.length; col++) {
        hashList.add(new ArrayList<HashValue>());
      }
      Pattern pattern = Pattern.compile(" \\*"); //$NON-NLS-1$
      File[] wkffFiles = wkffDir.listFiles();
      Arrays.sort(wkffFiles, new Comparator<File>() {
        public int compare(File o1, File o2) {
          return o1.getName().compareTo(o2.getName());
        }
      });
      StringBuilder sb = new StringBuilder();
      sb.append(ledWkffPath);
      for (File wkffFile : wkffFiles) {
        sb.append('|').append(wkffFile.getName()).append('|').append(wkffFile.length());
      }
      String cacheKey = sb.toString();
      File ledWkffCache = new File(System.getProperty("user.home"), ".indexador/ledWkff.cache"); //$NON-NLS-1$ //$NON-NLS-2$
      if (ledWkffCache.exists() && ledWkffCache.canRead()) {
        readCache(ledWkffCache, cacheKey);
      }

      if (hashArrays == null) {
        hashArrays = new HashMap<String, HashValue[]>();
        for (File wkffFile : wkffFiles) {
          BufferedReader reader = new BufferedReader(new FileReader(wkffFile));
          String line = reader.readLine();
          while ((line = reader.readLine()) != null) {
            String[] hashes = pattern.split(line);
            long length = -1;
            HashValue md5 = null;
            HashValue md5_64K = null;
            String ext = null;
            for (int col = 0; col < ledHashOrder.length; col++) {
              HashValue hv = null;
              if (ledHashOrder[col] != null) {
                hv = new HashValue(hashes[col].trim());
                hashList.get(col).add(hv);
              }
              if (col == idxMd5) {
                md5 = hv;
              } else if (col == idxMd5_64K) {
                md5_64K = new HashValue(hashes[col].trim());
              } else if (col == idxLength) {
                length = Long.parseLong(hashes[col]);
              } else if (col == idxName) {
                int pos = hashes[col].lastIndexOf('.');
                if (pos >= 0) ext = hashes[col].substring(pos + 1);
              }
            }
            if (md5_64K != null && length >= 65536) {
              kffList.add(new KffItem(md5_64K, length, ext, md5));
            }
          }
          reader.close();
        }
        for (int col = 0; col < ledHashOrder.length; col++) {
          if (ledHashOrder[col] != null) {
            hashArrays.put(ledHashOrder[col], hashList.get(col).toArray(new HashValue[0]));
            hashList.get(col).clear();
            Arrays.sort(hashArrays.get(ledHashOrder[col]));
          }
        }
        kffItems = kffList.toArray(new KffItem[0]);
        Arrays.sort(kffItems);
        writeCache(ledWkffCache, cacheKey);
      }
      Log.info(taskName, "Loaded hashes: " + hashArrays.get(ledHashOrder[0]).length); //$NON-NLS-1$
      LedHashes.hashMap = hashArrays;
    }
  }

  private void writeCache(File ledWkffCache, String cacheKey) {
    boolean ok = false; 
    BufferedOutputStream os = null;
    try {
      os = new BufferedOutputStream(new FileOutputStream(ledWkffCache), 1 << 24);
      write(os, cacheKey);
      write(os, hashArrays.size());
      for (String key : hashArrays.keySet()) {
        write(os, key);
        HashValue[] v = hashArrays.get(key);
        write(os, v.length);
        if (v.length > 0) {
          write(os, v[0].getBytes().length);
          for (HashValue h : v) {
            os.write(h.getBytes());
          }
        }
      }
      write(os, kffItems.length);
      if (kffItems.length > 0) {
        write(os, kffItems[0].getMD5_64K().getBytes().length);
        write(os, kffItems[0].getMD5().getBytes().length);
        for (KffItem item : kffItems) {
          os.write(item.getMD5_64K().getBytes());
          os.write(item.getMD5().getBytes());
          write(os, item.getLength());
          write(os, item.getExt());
        }
      }
      ok = true;
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      if (os != null) {
        try {
          os.close();
        } catch (Exception e) {}
      }
      if (!ok) {
        try {
          if (ledWkffCache.exists()) ledWkffCache.delete();
        } catch (Exception e1) {}
      }
    }
  }

  private void readCache(File ledWkffCache, String cacheKey) {
    BufferedInputStream is = null;
    try {
      is = new BufferedInputStream(new FileInputStream(ledWkffCache), 1 << 24);
      String fileKey = readString(is);
      if (fileKey.equals(cacheKey)) {
        hashArrays = new HashMap<String, HashValue[]>();
        int n = readInt(is);
        for (int i = 0; i < n; i++) {
          String key = readString(is);
          int arrLen = readInt(is);
          HashValue[] v = new HashValue[arrLen];
          hashArrays.put(key, v);
          if (arrLen > 0) {
            int hashLen = readInt(is);
            for (int j = 0; j < arrLen; j++) {
              byte[] bytes = new byte[hashLen];
              is.read(bytes);
              v[j] = new HashValue(bytes);
            }
          }
        }
        int arrLen = readInt(is);
        kffItems = new KffItem[arrLen];
        int hashLen1 = readInt(is);
        int hashLen2 = readInt(is);
        for (int j = 0; j < arrLen; j++) {
          byte[] bytes1 = new byte[hashLen1];
          byte[] bytes2 = new byte[hashLen2];
          is.read(bytes1);
          is.read(bytes2);
          kffItems[j] = new KffItem(new HashValue(bytes1), readLong(is), readString(is), new HashValue(bytes2));
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
      hashArrays = null;
    } finally {
      if (is != null) {
        try {
          is.close();
        } catch (Exception e) {}
        if (hashArrays == null) {
          try {
            if (ledWkffCache.exists()) ledWkffCache.delete();
          } catch (Exception e1) {}
        }
      }
    }
  }

  private void write(BufferedOutputStream os, int v) throws IOException {
    os.write((v >>> 24) & 0xFF);
    os.write((v >>> 16) & 0xFF);
    os.write((v >>> 8) & 0xFF);
    os.write(v & 0xFF);
  }

  private void write(BufferedOutputStream os, long v) throws IOException {
    write(os, (int) ((v >>> 32) & 0xFFFFFFFF));
    write(os, (int) (v & 0xFFFFFFFF));
  }

  private void write(BufferedOutputStream os, String s) throws IOException {
    if (s == null) {
      os.write(0);
      os.write(0);
      return;
    }
    byte[] bytes = s.getBytes();
    os.write((bytes.length >>> 8) & 0xFF);
    os.write(bytes.length & 0xFF);
    os.write(bytes);
  }

  private String readString(BufferedInputStream is) throws IOException {
    int len = ((is.read() & 0xFF) << 8) | (is.read() & 0xFF);
    if (len == 0) return ""; //$NON-NLS-1$
    byte[] bytes = new byte[len];
    is.read(bytes);
    return new String(bytes);
  }

  private int readInt(BufferedInputStream is) throws IOException {
    byte[] bytes = new byte[4];
    is.read(bytes);
    return ((bytes[0] & 0xFF) << 24) | ((bytes[1] & 0xFF) << 16) | ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF);
  }

  private long readLong(BufferedInputStream is) throws IOException {
    return ((readInt(is) & 0xFFFFFFFFL) << 32) | (readInt(is) & 0xFFFFFFFFL);
  }

  @Override
  public void finish() throws Exception {
    hashArrays = null;
    kffItems = null;
  }

  @Override
  public boolean isEnabled() {
    return taskEnabled;
  }

  @Override
  protected void process(EvidenceFile evidence) throws Exception {
    if (!taskEnabled) {
      return;
    }

    for (int col = 0; col < ledHashOrder.length; col++) {
      if (ledHashOrder[col] != null) {
        String hash = (String) evidence.getExtraAttribute(ledHashOrder[col]);
        if (hash != null) {
          if (Arrays.binarySearch(hashArrays.get(ledHashOrder[col]), new HashValue(hash)) >= 0) {
            evidence.setExtraAttribute(KFFTask.KFF_STATUS, "pedo"); //$NON-NLS-1$
          }
          break;
        }
      }
    }
  }
}

class KffItem implements Comparable<KffItem> {
  private final HashValue md5_64K, md5;
  private final long length;
  private final String ext;

  public KffItem(HashValue md5_64K, long length, String ext, HashValue md5) {
    this.md5_64K = md5_64K;
    this.length = length;
    this.ext = ext;
    this.md5 = md5;
  }

  public HashValue getMD5_64K() {
    return md5_64K;
  }

  public long getLength() {
    return length;
  }

  public String getExt() {
    return ext;
  }

  public HashValue getMD5() {
    return md5;
  }

  public int hashCode() {
    return md5_64K.hashCode();
  }

  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    KffItem other = (KffItem) obj;
    return md5_64K.equals(other.md5_64K);
  }

  public int compareTo(KffItem o) {
    return md5_64K.compareTo(o.md5_64K);
  }

  public static KffItem kffSearch(KffItem[] items, HashValue hash) {
    int low = 0;
    int high = items.length - 1;
    while (low <= high) {
      int mid = (low + high) >>> 1;
      int cmp = items[mid].md5_64K.compareTo(hash);
      if (cmp < 0) low = mid + 1;
      else if (cmp > 0) high = mid - 1;
      else return items[mid];
    }
    return null;
  }
}