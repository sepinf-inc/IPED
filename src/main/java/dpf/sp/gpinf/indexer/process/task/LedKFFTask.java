package dpf.sp.gpinf.indexer.process.task;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import dpf.sp.gpinf.indexer.IndexFiles;
import dpf.sp.gpinf.indexer.parsers.util.LedHashes;
import dpf.sp.gpinf.indexer.process.Worker;
import dpf.sp.gpinf.indexer.util.HashValue;
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
  public static HashMap<String, HashValue[]> hashArrays;
  public static KffItem[] kffItems;
  private static final String taskName = "Consulta Base de Hashes do LED";
  private static String[] ledHashOrder = {"md5",null,"edonkey","sha-1","md5-512",null,null};
  private static final int idxMd5 = 0;
  private static final int idxMd5_64K = 1;
  private static final int idxLength = 5;
  private static final int idxName = 6;

  public LedKFFTask(Worker worker) {
    super(worker);
  }

  @Override
  public void init(Properties confParams, File confDir) throws Exception {

    synchronized (lock) {
      if (hashArrays != null) {
        return;
      }

      //this.caseData.addBookmark(new FileGroup(ledCategory, "", ""));
      String hash = confParams.getProperty("hash");
      String ledWkffPath = confParams.getProperty("ledWkffPath");
      if (ledWkffPath == null) {
        return;
      }

      File wkffDir = new File(ledWkffPath);
      if (!wkffDir.exists()) {
        throw new Exception("Caminho para base de hashes do LED inválido!");
      }

      hash = hash.toLowerCase();
      if (!hash.contains("md5") && !hash.contains("sha-1")) {
        throw new Exception("Habilite o hash md5 ou sha-1 para consultar a base do LED!");
      }

      IndexFiles.getInstance().firePropertyChange("mensagem", "", "Carregando base de hashes do LED...");

      hashArrays = new HashMap<String, HashValue[]>();
      List<KffItem> kffList = new ArrayList<KffItem>();

      List<List<HashValue>> hashList = new ArrayList<List<HashValue>>();
      for (int col = 0; col < ledHashOrder.length; col++) {
        hashList.add(new ArrayList<HashValue>());
      }
      Pattern pattern = Pattern.compile(" \\*");
      for (File wkffFile : wkffDir.listFiles()) {
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

      Log.info(taskName, "Hashes carregados: " + hashArrays.get(ledHashOrder[0]).length);
      LedHashes.hashMap = hashArrays;
    }
  }

  @Override
  public void finish() throws Exception {
    hashArrays = null;
  }
  
  @Override
  public boolean isEnabled() {
    return hashArrays != null;
  }
  
  @Override
  protected void process(EvidenceFile evidence) throws Exception {
    if (hashArrays == null) {
      return;
    }

    for (int col = 0; col < ledHashOrder.length; col++) {
      if (ledHashOrder[col] != null) {
        String hash = (String) evidence.getExtraAttribute(ledHashOrder[col]);
        if (hash != null) {
          if (Arrays.binarySearch(hashArrays.get(ledHashOrder[col]), new HashValue(hash)) >= 0) {
            evidence.setExtraAttribute(KFFTask.KFF_STATUS, "pedo");
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