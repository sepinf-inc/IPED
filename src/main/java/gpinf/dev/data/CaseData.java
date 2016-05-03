package gpinf.dev.data;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Classe que define todos os dados do caso.
 *
 * @author Wladimir Leite (GPINF/SP)
 * @author Nassif (GPINF/SP)
 */
public class CaseData implements Serializable {

  /**
   * Identificador utilizado para serialização da classe.
   */
  private static final long serialVersionUID = 197209091220L;

  /**
   * Informações do caso.
   */
  private final CaseInfo caseInformation = new CaseInfo();

  /**
   * Grupos de arquivos por categoria.
   */
  public List<FileGroup> bookmarks = new ArrayList<FileGroup>();

  /**
   * Grupos de arquivos por data.
   */
  private final List<FileGroup> timeGroups = new ArrayList<FileGroup>();

  /**
   * Fila de processamento dos itens do caso
   */
  private LinkedBlockingDeque<EvidenceFile> evidenceFiles;

  /**
   * Mapa genérico de objetos extras do caso. Pode ser utilizado como área de compartilhamento de
   * objetos entre as instâncias das tarefas.
   */
  private HashMap<String, Object> objectMap = new HashMap<String, Object>();

  private int discoveredEvidences = 0;

  private int alternativeFiles = 0;

  /**
   * @return retorna o volume de dados descobertos até o momento
   */
  public synchronized long getDiscoveredVolume() {
    return discoveredVolume;
  }

  /**
   * @param volume tamanho do novo item descoberto
   */
  public synchronized void incDiscoveredVolume(Long volume) {
    if (volume != null) {
      this.discoveredVolume += volume;
    }
  }

  private long discoveredVolume = 0;

  /**
   * Árvore de arquivos de evidência.
   */
  private final PathNode root = new PathNode("Caso");

  /**
   * indica que o caso se trata de um relatório
   */
  private boolean containsReport = false, ipedReport = false;

  public boolean isIpedReport() {
    return ipedReport;
  }

  public void setIpedReport(boolean ipedReport) {
    this.ipedReport = ipedReport;
  }

  synchronized public void incAlternativeFiles(int inc) {
    alternativeFiles += inc;
  }

  synchronized public int getAlternativeFiles() {
    return alternativeFiles;
  }

  synchronized public void incDiscoveredEvidences(int inc) {
    discoveredEvidences += inc;
  }

  synchronized public int getDiscoveredEvidences() {
    return discoveredEvidences;
  }

  private int maxQueueSize;

  /**
   * Cria objeto do caso
   *
   * @param queueSize tamanho da fila de processamento dos itens
   */
  public CaseData(int queueSize) {
    this.maxQueueSize = queueSize;
    evidenceFiles = new LinkedBlockingDeque<EvidenceFile>();
  }

  /**
   * Retorna o objeto com as informações do caso.
   *
   * @return objeto da classe CaseInformation com informações do caso
   */
  public CaseInfo getCaseInformation() {
    return caseInformation;
  }

  /**
   * Adiciona um bookmark.
   *
   * @param bookmark bookmark a ser adicionado
   */
  public void addBookmark(FileGroup bookmark) {
    bookmarks.add(bookmark);
  }

  /**
   * Obtém lista de bookmarks.
   *
   * @return lista não modificável de bookmarks.
   */
  public List<FileGroup> getBookmarks() {
    return bookmarks;
  }

  /**
   * Adiciona um grupo de arquivos classificados por data.
   *
   * @param timeGroup grupo de arquivos classificados por data
   */
  public void addTimeGroup(FileGroup timeGroup) {
    timeGroups.add(timeGroup);
  }

  /**
   * Obtém lista de grupo de arquivos por data.
   *
   * @return lista não modificável de grupo de arquivos por data.
   */
  public List<FileGroup> getTimeGroups() {
    return Collections.unmodifiableList(timeGroups);
  }

  /**
   * Retorna String com os dados contidos no objeto.
   *
   * @return String listando dados do caso.
   */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(caseInformation);
    sb.append("\n").append("BOOKMARKS(").append(bookmarks.size()).append("):");
    for (int i = 0; i < bookmarks.size(); i++) {
      sb.append("\n\t").append(bookmarks.get(i));
    }
    sb.append("\n").append("ARQUIVOS(").append(evidenceFiles.size()).append("):");
    for (int i = 0; i < evidenceFiles.size(); i++) {
      sb.append("\n\t").append(evidenceFiles.element());
    }
    return sb.toString();
  }

  /**
   * Obtém o objeto raiz da árvore de arquivos do caso.
   *
   * @return objeto raiz, a partir do qual é possível navegar em todo estrutura de diretórios do
   * caso.
   */
  public PathNode getRootNode() {
    return root;
  }

  /**
   * Adiciona um arquivo de evidência.
   *
   * @param evidenceFile arquivo a ser adicionado
   * @throws InterruptedException
   */
  public void addEvidenceFile(EvidenceFile evidenceFile) throws InterruptedException {
    while (evidenceFiles.size() >= maxQueueSize) {
      Thread.sleep(1000);
    }

    evidenceFiles.put(evidenceFile);

  }

  /**
   * Obtém fila de arquivos de evidência do caso.
   *
   * @return fila de arquivos.
   */
  public LinkedBlockingDeque<EvidenceFile> getEvidenceFiles() {
    return evidenceFiles;
  }

  /**
   * Salva o objeto atual em arquivo. Utiliza serialização direta do objeto e compactação GZIP.
   *
   * @param file arquivo a ser salvo
   * @throws IOException Erro no acesso ao arquivo.
   */
  public void save(File file) throws IOException {
    file.getParentFile().mkdirs();
    ObjectOutputStream out = new ObjectOutputStream(new GZIPOutputStream(new FileOutputStream(file)));
    out.writeObject(this);
    out.close();
  }

  /**
   * Carrega objeto previamente salvo em arquivo.
   *
   * @param file arquivo a ser lido
   * @throws IOException Erro no acesso ao arquivo.
   * @throws ClassNotFoundException Arquivo não contém dados esperados.
   */
  public static CaseData load(File file) throws IOException, ClassNotFoundException {
    ObjectInputStream in = new ObjectInputStream(new GZIPInputStream(new FileInputStream(file)));
    CaseData data = (CaseData) in.readObject();
    in.close();
    return data;
  }

  /**
   * @return true se o caso contém um report
   */
  public boolean containsReport() {
    return containsReport;
  }

  /**
   *
   * @param containsReport se o caso contém um report
   */
  public void setContainsReport(boolean containsReport) {
    this.containsReport = containsReport;
  }

  /**
   * Retorna um objeto armazenado no caso.
   *
   * @param key Nome do objeto
   * @return O objeto armazenado no caso
   */
  public Object getCaseObject(String key) {
    return objectMap.get(key);
  }

  /**
   * Armazena um objeto genérico no caso.
   *
   * @param key Nome do objeto a armazenar
   * @param value Objeto a ser armazenado
   */
  public void putCaseObject(String key, Object value) {
    objectMap.put(key, value);
  }

}
