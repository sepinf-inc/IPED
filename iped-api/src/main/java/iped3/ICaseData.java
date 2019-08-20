package iped3;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * (INTERFACE DO IPED) Classe que define todos os dados do caso.
 *
 * @author Wladimir Leite (GPINF/SP)
 * @author Nassif (GPINF/SP)
 */
public interface ICaseData extends Serializable {

    /**
     * Adiciona um bookmark.
     *
     * @param bookmark
     *            bookmark a ser adicionado
     */
    void addBookmark(IFileGroup bookmark);

    /**
     * Adiciona um arquivo de evidência.
     *
     * @param item
     *            arquivo a ser adicionado
     * @throws InterruptedException
     */
    void addItem(IItem item) throws InterruptedException;

    void addItemToQueue(IItem item, int queuePriority) throws InterruptedException;

    /**
     * Adiciona um grupo de arquivos classificados por data.
     *
     * @param timeGroup
     *            grupo de arquivos classificados por data
     */
    void addTimeGroup(IFileGroup timeGroup);

    Integer changeToNextQueue();

    /**
     * @return true se o caso contém um report
     */
    boolean containsReport();

    int getAlternativeFiles();

    /**
     * Obtém lista de bookmarks.
     *
     * @return lista não modificável de bookmarks.
     */
    List<IFileGroup> getBookmarks();

    /**
     * Retorna o objeto com as informações do caso.
     *
     * @return objeto da classe CaseInformation com informações do caso
     */
    ICaseInfo getCaseInformation();

    /**
     * Retorna um objeto armazenado no caso.
     *
     * @param key
     *            Nome do objeto
     * @return O objeto armazenado no caso
     */
    Object getCaseObject(String key);

    Integer getCurrentQueuePriority();

    int getDiscoveredEvidences();

    /**
     * @return retorna o volume de dados descobertos até o momento
     */
    long getDiscoveredVolume();

    /**
     * Obtém fila de arquivos de evidência do caso.
     *
     * @return fila de arquivos.
     */
    LinkedBlockingDeque<IItem> getItemQueue();

    /**
     * Obtém o objeto raiz da árvore de arquivos do caso.
     *
     * @return objeto raiz, a partir do qual é possível navegar em todo estrutura de
     *         diretórios do caso.
     */
    IPathNode getRootNode();

    /**
     * Obtém lista de grupo de arquivos por data.
     *
     * @return lista não modificável de grupo de arquivos por data.
     */
    List<IFileGroup> getTimeGroups();

    void incAlternativeFiles(int inc);

    void incDiscoveredEvidences(int inc);

    /**
     * @param volume
     *            tamanho do novo item descoberto
     */
    void incDiscoveredVolume(Long volume);

    boolean isIpedReport();

    /**
     * Armazena um objeto genérico no caso.
     *
     * @param key
     *            Nome do objeto a armazenar
     * @param value
     *            Objeto a ser armazenado
     */
    void putCaseObject(String key, Object value);

    /**
     * Salva o objeto atual em arquivo. Utiliza serialização direta do objeto e
     * compactação GZIP.
     *
     * @param file
     *            arquivo a ser salvo
     * @throws IOException
     *             Erro no acesso ao arquivo.
     */
    void save(File file) throws IOException;

    /**
     *
     * @param containsReport
     *            se o caso contém um report
     */
    void setContainsReport(boolean containsReport);

    void setIpedReport(boolean ipedReport);

}
