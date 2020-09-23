package iped3.io;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

public interface IItemBase extends IStreamSource {
    /**
     *
     * @return o id do item
     */
    public int getId();

    /**
     *
     * @return o id do item pai. Tem o nome do caso prefixado no caso de reports do
     *         FTK3+
     */
    public Integer getParentId();

    /**
     * @return the subitem order into its parent, null if this is not a subitem.
     */
    public Integer getSubitemId();

    /**
     * @return nome do arquivo
     */
    public String getName();

    /**
     *
     * @return a extensão original do item
     */
    public String getExt();

    public String getTypeExt();

    /**
     *
     * @return o mediaType do arquivo, resultado da análise de assinatura
     */
    public MediaType getMediaType();

    public HashSet<String> getCategorySet();

    /**
     * @return String com o caminho completo do item
     */
    public String getPath();

    /**
     * @return tamanho do arquivo em bytes
     */
    public Long getLength();

    /**
     *
     * @return o hash do arquivo, caso existente.
     */
    public String getHash();

    /**
     *
     * @return true se o item está apagado
     */
    public boolean isDeleted();

    /**
     *
     * @return true se o item é proveniente de carving
     */
    public boolean isCarved();

    /**
     * @return true se é um subitem de um container
     */
    public boolean isSubItem();

    /**
     *
     * @return true se o item é um diretório
     */
    public boolean isDir();

    /**
     * @return true se é um item raiz
     */
    public boolean isRoot();

    /**
     * @return true se o item é uma duplicata de outro, baseado no hash
     */
    public boolean isDuplicate();

    /**
     * @return true se o parsing do item ocasionou timeout
     */
    public boolean isTimedOut();

    /**
     *
     * @return true se o item tem filhos, como subitens ou itens carveados
     */
    public boolean hasChildren();

    /**
     *
     * @return o arquivo com o conteúdo do item. Retorna não nulo apenas em
     *         processamentos de pastas, reports e no caso de subitens de
     *         containers. Consulte {@link #getTempFile()}} e {@link #getStream()}
     */
    public File getTempFile() throws IOException;

    /**
     * @return the local File referenced by this Item, null if it does not exist (eg
     *         item into a DD image).
     * 
     * @see iped3.io.IStreamSource#getFile()
     */
    public File getFile();

    /**
     * @return true if this Item refers to a local File, false otherwise.
     */
    public boolean hasFile();

    /**
     * Obtém o arquivo de visualização. Retorna nulo caso inexistente.
     *
     * @return caminho relativo ao caso do arquivo de visualização
     */
    public File getViewFile();

    public byte[] getThumb();

    public byte[] getImageSimilarityFeatures();

    public BufferedInputStream getBufferedStream() throws IOException;

    /**
     * @return data da última modificação do arquivo
     */
    public Date getModDate();

    public Date getCreationDate();

    public Date getAccessDate();

    public Date getRecordDate();

    public Object getExtraAttribute(String key);

    public Map<String, Object> getExtraAttributeMap();

    /**
     * @return Object containing the metadata of the item
     */
    public Metadata getMetadata();
}
