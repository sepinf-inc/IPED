package dpf.sp.gpinf.indexer.process.task;

import java.io.BufferedInputStream;
import java.io.File;
import java.security.MessageDigest;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import dpf.sp.gpinf.indexer.process.Worker;
import dpf.sp.gpinf.indexer.util.HashValue;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.Log;
import gpinf.dev.data.EvidenceFile;

public class KFFCarveTask extends BaseCarveTask {
  /**
   * Nome da tarefa.
   */
  private static final String taskName = "KFF Carving";

  /**
   * Indica se a tarefa está habilitada ou não.
   */
  private static boolean taskEnabled = false;

  /**
   * Indicador de inicialização, para controle de sincronização entre instâncias da classe.
   */
  private static final AtomicBoolean init = new AtomicBoolean(false);

  /**
   * Objeto estático para sincronizar finalização.
   */
  private static final AtomicBoolean finished = new AtomicBoolean(false);

  /**
   * Contador de arquivos recuperados.
   */
  private static final AtomicInteger numCarvedItems = new AtomicInteger();

  /**
   * Contador de bytes com hash calculado.
   */
  private static final AtomicLong bytesHashed = new AtomicLong();

  /**
   * Contador do total de blocos de 512 processados.
   */
  private static final AtomicLong num512total = new AtomicLong();

  /**
   * Contador do total de hits em blocos de 512 bytes.
   */
  private static final AtomicLong num512hit = new AtomicLong();

  /**
   * Digest utilizado para cálculo do MD5.
   */
  private MessageDigest digest = null;

  /**
   * Lista de hashes dos 512 bytes iniciais.
   */
  private static HashValue[] md5_512;

  /**
   * Construtor.
   */
  public KFFCarveTask(Worker worker) {
    super(worker);
  }

  @Override
  public boolean isEnabled() {
    return taskEnabled;
  }

  /**
   * Inicializa tarefa.
   */
  public void init(Properties confParams, File confDir) throws Exception {
    synchronized (init) {
      if (!init.get()) {
        String value = confParams.getProperty("enableKFFCarving");
        if (value != null && value.trim().equalsIgnoreCase("true")) {
          if (LedKFFTask.kffItems != null) {
            md5_512 = LedKFFTask.hashArrays.get("md5-512");
            System.err.println("LEN=" + md5_512.length);
            taskEnabled = true;
          } else {
            Log.warning(taskName, "Base do LED precisa ser carregada para o KFFCarving funcionar.");
          }
        }
        Log.info(taskName, taskEnabled ? "Tarefa habilitada." : "Tarefa desabilitada.");
        init.set(true);
      }
    }
    if (taskEnabled) digest = MessageDigest.getInstance("MD5");
  }

  /**
   * Finaliza a tarefa.
   */
  public void finish() throws Exception {
    synchronized (finished) {
      if (taskEnabled && !finished.get()) {
        finished.set(true);
        NumberFormat nf = new DecimalFormat("#,##0");
        Log.info(taskName, "Arquivos carveados: " + nf.format(numCarvedItems.get()));
        Log.info(taskName, "Blocos de 512 (Hits / Total): " + nf.format(num512hit.get()) + " / " + nf.format(num512total.get()));
        Log.info(taskName, "Bytes com hash calculado: " + nf.format(bytesHashed.get()));
      }
    }
  }

  protected void process(EvidenceFile evidence) throws Exception {
    if (!taskEnabled || !isToProcess(evidence)) return;

    byte[] buf512 = new byte[512];
    byte[] buf64K = new byte[65536 - buf512.length];
    BufferedInputStream is = null;

    int cntCarvedItems = 0;
    long cnt512hit = 0;
    long cnt512total = 0;
    long cntBytesHashed = 0;
    Set<Long> offsets = null;
    try {
      long offset = 0;
      int read512 = 0;
      is = evidence.getBufferedStream();
      while ((read512 = is.read(buf512)) > 0) {
        if (read512 != buf512.length) break;
        cnt512total++;
        boolean empty = true;
        byte first = buf512[0];
        for (int i = 1; i < read512; i++) {
          if (buf512[i] != first) {
            empty = false;
            break;
          }
        }
        if (!empty) {
          digest.update(buf512, 0, read512);
          cntBytesHashed += read512;
          HashValue hash512 = new HashValue(digest.digest());
          if (Arrays.binarySearch(md5_512, hash512) >= 0) {
            cnt512hit++;
            is.mark(65536);
            int read64K = is.read(buf64K);
            is.reset();
            if (read64K == buf64K.length) {
              cntBytesHashed += read512 + read64K;
              digest.update(buf512, 0, read512);
              digest.update(buf64K, 0, read64K);
              HashValue hash64K = new HashValue(digest.digest());
              KffItem kffItem = KffItem.kffSearch(LedKFFTask.kffItems, hash64K);
              if (kffItem != null) {
                String name = "CarvedKff-" + offset;
                String ext = kffItem.getExt();
                if (ext != null) name += '.' + ext.toLowerCase();
                EvidenceFile carvedItem = addCarvedFile(evidence, offset, kffItem.getLength(), name, null);
                carvedItem.setExtraAttribute("kffCarvedMD5", kffItem.getMD5().toString());
                cntCarvedItems++;
                if (offsets == null) {
                  offsets = new HashSet<Long>();
                  synchronized (kffCarved) {
                    kffCarved.put(evidence, offsets);
                  }
                }
                offsets.add(offset);
              }
            }
          }
        }
        offset += read512;
      }
    } catch (Exception e) {
      e.printStackTrace();
      Log.warning(taskName, "Erro no KFF Carving: " + evidence.getPath() + " : " + e);
    } finally {
      IOUtil.closeQuietly(is);
    }
    numCarvedItems.addAndGet(cntCarvedItems);
    num512hit.addAndGet(cnt512hit);
    num512total.addAndGet(cnt512total);
    bytesHashed.addAndGet(cntBytesHashed);
  }
}