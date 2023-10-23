package iped.engine.data;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import iped.data.ICaseData;

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
     * Mapa genérico de objetos extras do caso. Pode ser utilizado como área de
     * compartilhamento de objetos entre as instâncias das tarefas.
     */
    private Map<String, Object> objectMap = Collections.synchronizedMap(new HashMap<>());

    /**
     * indica que o caso se trata de um relatório
     */
    private boolean containsReport = false, ipedReport = false;

    private int discoveredEvidences = 0;

    private long discoveredVolume = 0;

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

    public boolean isIpedReport() {
        return ipedReport;
    }

    public void setIpedReport(boolean ipedReport) {
        this.ipedReport = ipedReport;
    }

    public synchronized void incDiscoveredEvidences(int inc) {
        discoveredEvidences += inc;
    }

    public synchronized int getDiscoveredEvidences() {
        return discoveredEvidences;
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

    @Override
    public Object addCaseObject(String key, Object data) {
        return objectMap.put(key, data);
    }

}
