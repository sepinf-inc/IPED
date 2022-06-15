package iped;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;

/**
 * (INTERFACE DO IPED) Classe que define todos os dados do caso.
 *
 * @author Wladimir Leite (GPINF/SP)
 * @author Nassif (GPINF/SP)
 */
public interface ICaseData extends Serializable {

    /**
     * Adiciona um arquivo de evidência.
     *
     * @param item
     *            arquivo a ser adicionado
     * @throws InterruptedException
     */
    void addItem(IItem item) throws InterruptedException;

    void addItemFirst(IItem item) throws InterruptedException;

    void addItemNonBlocking(IItem item);

    void addItemFirstNonBlocking(IItem item);

    void addItemToQueue(IItem item, int queuePriority) throws InterruptedException;

    Integer changeToNextQueue();

    /**
     * @return true se o caso contém um report
     */
    boolean containsReport();

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
