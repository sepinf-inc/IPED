package iped.data;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;

import javax.imageio.stream.ImageInputStream;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

import iped.datasource.IDataSource;
import iped.io.ISeekableInputStreamFactory;
import iped.io.IStreamSource;
import iped.io.SeekableInputStream;

public interface IItemReader extends IStreamSource {
    /**
     *
     * @return o id do item
     */
    public int getId();

    /**
     *
     * @return o id do item pai.
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

    /**
     * @return the detected file type extension
     */
    public String getType();

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
     * @return true se o parsing do item ocasionou timeout
     */
    public boolean isTimedOut();

    /**
     *
     * @return true se o item tem filhos, como subitens ou itens carveados
     */
    public boolean hasChildren();

    /**
     * Returns a temp file with item content. This can spool data to the temp
     * directory, so avoid using this if you can work directly with item data
     * streams.
     */
    public File getTempFile() throws IOException;

    public String getIdInDataSource();

    public ISeekableInputStreamFactory getInputStreamFactory();

    /**
     * Obtém o arquivo de visualização. Retorna nulo caso inexistente.
     *
     * @return caminho relativo ao caso do arquivo de visualização
     */
    public File getViewFile();

    boolean hasPreview();

    File getPreviewBaseFolder();

    String getPreviewExt();

    SeekableInputStream getPreviewSeekeableInputStream() throws SQLException, IOException;

    public byte[] getThumb();

    public BufferedInputStream getBufferedInputStream() throws IOException;

    public ImageInputStream getImageInputStream() throws IOException;

    /**
     * @return data da última modificação do arquivo
     */
    public Date getModDate();

    public Date getCreationDate();

    public Date getAccessDate();

    public Date getChangeDate();

    public Object getExtraAttribute(String key);

    public Map<String, Object> getExtraAttributeMap();

    public IDataSource getDataSource();

    /**
     * @return Object containing the metadata of the item
     */
    public Metadata getMetadata();

}
