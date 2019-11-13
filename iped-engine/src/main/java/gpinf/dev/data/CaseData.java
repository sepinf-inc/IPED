package gpinf.dev.data;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import dpf.sp.gpinf.indexer.Messages;
import dpf.sp.gpinf.indexer.process.MimeTypesProcessingOrder;
import iped3.ICaseData;
import iped3.ICaseInfo;
import iped3.IItem;
import iped3.IFileGroup;
import iped3.IPathNode;

/**
 * Classe que define todos os dados do caso.
 *
 * @author Wladimir Leite (GPINF/SP)
 * @author Nassif (GPINF/SP)
 */
public class CaseData implements ICaseData {

    /**
     * Identificador utilizado para serialização da classe.
     */
    private static final long serialVersionUID = 197209091220L;

    /**
     * Informações do caso.
     */
    private final ICaseInfo caseInformation = new CaseInfo();

    /**
     * Grupos de arquivos por categoria.
     */
    public List<IFileGroup> bookmarks = new ArrayList<IFileGroup>();

    /**
     * Grupos de arquivos por data.
     */
    private final List<IFileGroup> timeGroups = new ArrayList<IFileGroup>();

    /**
     * Filas de processamento dos itens do caso
     */
    private TreeMap<Integer, LinkedBlockingDeque<IItem>> queues;

    private volatile Integer currentQueuePriority = 0;

    /**
     * Mapa genérico de objetos extras do caso. Pode ser utilizado como área de
     * compartilhamento de objetos entre as instâncias das tarefas.
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
     * @param volume
     *            tamanho do novo item descoberto
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
    private final IPathNode root = new PathNode(Messages.getString("CaseData.Case")); //$NON-NLS-1$

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
     * @param queueSize
     *            tamanho da fila de processamento dos itens
     */
    public CaseData(int queueSize) {
        this.maxQueueSize = queueSize;
        initQueues();
    }

    private void initQueues() {
        queues = new TreeMap<Integer, LinkedBlockingDeque<IItem>>();
        queues.put(0, new LinkedBlockingDeque<IItem>());
        for (Integer priority : MimeTypesProcessingOrder.getProcessingPriorities())
            queues.put(priority, new LinkedBlockingDeque<IItem>());
    }

    /**
     * Retorna o objeto com as informações do caso.
     *
     * @return objeto da classe CaseInformation com informações do caso
     */
    public ICaseInfo getCaseInformation() {
        return caseInformation;
    }

    /**
     * Adiciona um bookmark.
     *
     * @param bookmark
     *            bookmark a ser adicionado
     */
    public void addBookmark(IFileGroup bookmark) {
        bookmarks.add(bookmark);
    }

    /**
     * Obtém lista de bookmarks.
     *
     * @return lista não modificável de bookmarks.
     */
    public List<IFileGroup> getBookmarks() {
        return bookmarks;
    }

    /**
     * Adiciona um grupo de arquivos classificados por data.
     *
     * @param timeGroup
     *            grupo de arquivos classificados por data
     */
    public void addTimeGroup(IFileGroup timeGroup) {
        timeGroups.add(timeGroup);
    }

    /**
     * Obtém lista de grupo de arquivos por data.
     *
     * @return lista não modificável de grupo de arquivos por data.
     */
    public List<IFileGroup> getTimeGroups() {
        return Collections.unmodifiableList(timeGroups);
    }

    /**
     * Obtém o objeto raiz da árvore de arquivos do caso.
     *
     * @return objeto raiz, a partir do qual é possível navegar em todo estrutura de
     *         diretórios do caso.
     */
    public IPathNode getRootNode() {
        return root;
    }

    /**
     * Adiciona um arquivo de evidência.
     *
     * @param item
     *            arquivo a ser adicionado
     * @throws InterruptedException
     */
    public void addItem(IItem item) throws InterruptedException {
        addItemToQueue(item, 0);

    }

    public void addItemToQueue(IItem item, int queuePriority) throws InterruptedException {
        LinkedBlockingDeque<IItem> queue = queues.get(queuePriority);
        while (queue.size() >= maxQueueSize) {
            Thread.sleep(1000);
        }

        queue.put(item);
    }

    public Integer changeToNextQueue() {
        currentQueuePriority = queues.ceilingKey(currentQueuePriority + 1);
        return currentQueuePriority;
    }

    public Integer getCurrentQueuePriority() {
        return currentQueuePriority;
    }

    /**
     * Obtém fila de arquivos de evidência do caso.
     *
     * @return fila de arquivos.
     */
    public LinkedBlockingDeque<IItem> getItemQueue() {
        return queues.get(currentQueuePriority);
    }
    
    public LinkedBlockingDeque<IItem> getItemQueue(int priority) {
        return queues.get(priority);
    }

    /**
     * Salva o objeto atual em arquivo. Utiliza serialização direta do objeto e
     * compactação GZIP.
     *
     * @param file
     *            arquivo a ser salvo
     * @throws IOException
     *             Erro no acesso ao arquivo.
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
     * @param file
     *            arquivo a ser lido
     * @throws IOException
     *             Erro no acesso ao arquivo.
     * @throws ClassNotFoundException
     *             Arquivo não contém dados esperados.
     */
    public static ICaseData load(File file) throws IOException, ClassNotFoundException {
        ObjectInputStream in = new ObjectInputStream(new GZIPInputStream(new FileInputStream(file)));
        ICaseData data = (ICaseData) in.readObject();
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
     * @param containsReport
     *            se o caso contém um report
     */
    public void setContainsReport(boolean containsReport) {
        this.containsReport = containsReport;
    }

    /**
     * Retorna um objeto armazenado no caso.
     *
     * @param key
     *            Nome do objeto
     * @return O objeto armazenado no caso
     */
    public Object getCaseObject(String key) {
        return objectMap.get(key);
    }

    /**
     * Armazena um objeto genérico no caso.
     *
     * @param key
     *            Nome do objeto a armazenar
     * @param value
     *            Objeto a ser armazenado
     */
    public void putCaseObject(String key, Object value) {
        objectMap.put(key, value);
    }

}
