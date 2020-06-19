package iped3;

import iped3.io.ISeekableInputStreamFactory;
import iped3.io.IItemBase;
import iped3.datasource.IDataSource;
import iped3.io.SeekableInputStream;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.channels.SeekableByteChannel;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

/**
 * Classe que define um arquivo de evidência, que é um arquivo do caso,
 * acompanhado de todas sua propriedades disponíveis. Algumas propriedades,
 * consideradas essenciais, tais como nome, tipo do arquivo e o link para onde
 * foi exportado, são consideradas atributos da classe. As outras "básicas" (que
 * já vem prontas para o pré-processamento) são guardades em uma lista de
 * propriedades.
 *
 * @author Wladimir Leite (GPINF/SP)
 * @author Nassif (GPINF/SP)
 */
public interface IItem extends IItemBase {

    /**
     * Adiciona o item a uma categoria.
     *
     * @param category
     *            categoria a qual o item será adicionado
     */
    void addCategory(String category);

    /**
     * Adiciona o id de um dos pais do item numa estrutura hierárquica
     *
     * @param parentId
     *            id de um dos pais
     */
    void addParentId(int parentId);

    /**
     * Adiciona uma lista de ids dos pais do item numa estrutura hierárquica
     *
     * @param parentIds
     *            lista de ids dos pais do item
     */
    void addParentIds(List<Integer> parentIds);

    /**
     * Libera recursos utilizados, como arquivos temporários e handles
     *
     * @throws IOException
     *             caso ocorra erro de IO
     */
    void dispose();

    /**
     * @return data do último acesso
     */
    @Override
    Date getAccessDate();

    /**
     * @return um BufferedInputStream com o conteúdo do item
     * @throws IOException
     */
    @Override
    BufferedInputStream getBufferedStream() throws IOException;

    /**
     *
     * @return o nome das categorias do item concatenadas
     */
    String getCategories();

    /**
     *
     * @return o conjunto de categorias do item
     */
    HashSet<String> getCategorySet();

    /**
     * @return data de criação do arquivo
     */
    @Override
    Date getCreationDate();

    IDataSource getDataSource();

    String getIdInDataSource();

    ISeekableInputStreamFactory getInputStreamFactory();
    
    String getParentIdInDataSource();

    /**
     * @return nome e caminho relativo ao caso com que o arquivo de evidência em si
     *         foi exportado
     */
    String getExportedFile();

    /**
     * Módulos de processamento podem setar atributos extras no item para armazenar
     * o resultado do processamento
     *
     * @param key
     *            o nome do atributo extra
     * @return o valor do atributo extra
     */
    Object getExtraAttribute(String key);
    
    Object getTempAttribute(String key);

    /**
     *
     * @return o mapa de atributos extras do item. Módulos de processamento podem
     *         setar atributos extras no item para armazenar o resultado do
     *         processamento
     */
    Map<String, Object> getExtraAttributeMap();

    /**
     *
     * @return o offset no item pai da onde o item foi recuperado (carving). Retorna
     *         -1 se o item não é proveniente de carving.
     */
    long getFileOffset();

    /**
     *
     * @return o caminho para o arquivo do item. Diferente de vazio apenas em
     *         reports e processamentos de pastas.
     */
    String getFileToIndex();

    /**
     *
     * @return o id do item no FTK3+ no caso de reports
     */
    Integer getFtkID();

    IHashValue getHashValue();

    /**
     *
     * @return os marcadores do item concatenados
     */
    List<String> getLabels();

    /**
     *
     * @return lista contendo os ids dos itens pai
     */
    List<Integer> getParentIds();

    /**
     *
     * @return ids dos itens pai concatenados com espaço
     */
    String getParentIdsString();

    /**
     *
     * @return o texto extraído do item armazenado pela tarefa de expansão para
     *         alguns containers com texto (eml, ppt, etc)
     */
    @Deprecated
    String getParsedTextCache();

    Reader getTextReader() throws IOException;

    Date getRecordDate();

    @Override
    SeekableByteChannel getSeekableByteChannel() throws IOException;

    /**
     * @return InputStream com o conteúdo do arquivo.
     */
    @Override
    SeekableInputStream getStream() throws IOException;

    /**
     * Usado em módulos que só possam processar um File e não um InputStream. Pode
     * impactar performance pois gera arquivo temporário.
     *
     * @return um arquivo temporário com o conteúdo do item.
     * @throws IOException
     */
    @Override
    File getTempFile() throws IOException;

    /**
     *
     * @return um TikaInputStream com o conteúdo do arquivo
     * @throws IOException
     */
    TikaInputStream getTikaStream() throws IOException;

    /**
     * @return o tipo de arquivo baseado na análise de assinatura
     */
    IEvidenceFileType getType();

    boolean hasTmpFile();

    /**
     * @return true se o item foi submetido a parsing
     */
    boolean isParsed();

    /**
     * @return true se é um item de fim de fila de processamento
     */
    boolean isQueueEnd();

    /**
     *
     * @return true se o item deve ser adicionado ao caso
     */
    boolean isToAddToCase();

    /**
     *
     * @return true se o item deve ser exportado
     */
    boolean isToExtract();

    /**
     *
     * @return true se o item deve ser ignorado pelas próximas tarefas de
     *         processamento e excluído do caso
     */
    boolean isToIgnore();

    boolean isToSumVolume();

    /**
     * Remove o item da categoria
     *
     * @param category
     *            categoria a ser removida
     */
    void removeCategory(String category);

    /**
     * @param accessDate
     *            nova data de último acesso
     */
    void setAccessDate(Date accessDate);

    /**
     * @param addToCase
     *            se o item deve ser adicionado ao caso
     */
    void setAddToCase(boolean addToCase);

    /**
     * Define se é um item de carving
     *
     * @param carved
     *            se é item de carving
     */
    void setCarved(boolean carved);

    /**
     * Redefine a categoria do item
     *
     * @param category
     *            nova categoria
     */
    void setCategory(String category);

    /**
     * @param creationDate
     *            nova data de criação do arquivo
     */
    void setCreationDate(Date creationDate);

    void setDataSource(IDataSource evidence);

    /**
     * Configura deleção posterior do arquivo. Por ex, subitem que deva ser
     * processado e incluído no relatório, porém sem ter seu conteúdo exportado (ex:
     * gera thumb do vídeo e dps deleta o vídeo)
     *
     * @param deleteFile
     *            se deve ser deletado ao não
     */
    void setDeleteFile(boolean deleteFile);

    /**
     * Define se o item é apagado
     *
     * @param deleted
     *            se é apagado
     */
    void setDeleted(boolean deleted);

    /**
     * Define se o item é duplicado
     *
     * @param duplicate
     *            se é duplicado
     */
    void setDuplicate(boolean duplicate);

    /**
     * Define o caminho para o arquivo do item, no caso de processamento de pastas e
     * para subitens extraídos.
     *
     * @param exportedFile
     *            caminho para o arquivo do item
     */
    void setExportedFile(String exportedFile);

    /**
     * Define a extensão do item.
     *
     * @param ext
     *            extensão
     */
    void setExtension(String ext);

    /**
     * Define um atributo extra para o item
     *
     * @param key
     *            nome do atributo
     * @param value
     *            valor do atributo
     */
    void setExtraAttribute(String key, Object value);
    
    void setTempAttribute(String key, Object value);

    /**
     * Define o arquivo referente ao item, caso existente
     *
     * @param file
     *            arquivo referente ao item
     */
    void setFile(File file);

    /**
     * Define o offset onde itens de carving são encontrados no item pai
     *
     * @param fileOffset
     *            offset do item
     */
    void setFileOffset(long fileOffset);

    /**
     * Define o id do FTK3+, em casos de report
     *
     * @param ftkID
     *            id do FTK
     */
    void setFtkID(Integer ftkID);

    /**
     * Define se o item tem filhos, como subitens ou itens de carving
     *
     * @param hasChildren
     *            se tem filhos
     */
    void setHasChildren(boolean hasChildren);

    /**
     * Define o hash do item
     *
     * @param hash
     *            hash do item
     */
    void setHash(String hash);

    /**
     * @param id
     *            identificador do item
     */
    void setId(int id);

    /**
     * Define se o item é um diretório.
     *
     * @param isDir
     *            se é diretório
     */
    void setIsDir(boolean isDir);

    /**
     * Define os marcadores do item
     *
     * @param labels
     *            marcadores concatenados
     */
    void setLabels(List<String> labels);

    /**
     * @param length
     *            tamanho do arquivo
     */
    void setLength(Long length);

    /**
     * Define o mediaType do item baseado na assinatura
     *
     * @param mediaType
     *            internet mediaType
     */
    void setMediaType(MediaType mediaType);

    void setMetadata(Metadata metadata);

    /**
     * @param modificationDate
     *            data da última modificação do arquivo
     */
    void setModificationDate(Date modificationDate);

    /**
     * @param name
     *            nome do arquivo
     */
    void setName(String name);

    void setParent(IItem parent);

    /**
     * @param parentId
     *            identificador do item pai
     */
    void setParentId(Integer parentId);

    /**
     * @param parsed
     *            se foi realizado parsing do item
     */
    void setParsed(boolean parsed);

    /**
     * @param parsedTextCache
     *            texto extraído após o parsing
     */
    @Deprecated
    void setParsedTextCache(String parsedTextCache);

    /**
     * @param path
     *            caminho do item
     */
    void setPath(String path);

    /**
     * @param isQueueEnd
     *            se é um item especial de fim de fila
     */
    void setQueueEnd(boolean isQueueEnd);

    void setRecordDate(Date recordDate);

    /**
     * @param isRoot
     *            se o item é raiz
     */
    void setRoot(boolean isRoot);

    /**
     * @param isSubItem
     *            se o item é um subitem
     */
    void setSubItem(boolean isSubItem);

    void setSumVolume(boolean sumVolume);

    void setTempFile(File tempFile);

    void setTempStartOffset(long tempStartOffset);

    /**
     * @param timeOut
     *            se o parsing do item ocasionou timeout
     */
    void setTimeOut(boolean timeOut);

    /**
     * @param isToExtract
     *            se o item deve ser extraído
     */
    void setToExtract(boolean isToExtract);

    /**
     * @param toIgnore
     *            se o item deve ser ignorado pela tarefas de processamento
     *            seguintes e excluído do caso
     */
    void setToIgnore(boolean toIgnore);

    /**
     * @param toIgnore
     *            se o item deve ser ignorado pela tarefas de processamento
     *            seguintes e excluído do caso
     */
    void setToIgnore(boolean toIgnore, boolean updateStats);

    /**
     * @param type
     *            tipo de arquivo
     */
    void setType(IEvidenceFileType type);

    /**
     * @param viewFile
     *            caminho do arquivo para visualização.
     */
    void setViewFile(File viewFile);

    void setInputStreamFactory(ISeekableInputStreamFactory inputStreamFactory);

    void setIdInDataSource(String string);
    
    void setParentIdInDataSource(String string);
    
    void setThumb(byte[] thumb);

    void setImageSimilarityFeatures(byte[] imageSimilarityFeatures);

    /**
     * @return returns the created evidenceFile.
     */
    IItem createChildItem();

    /**
     * Retorna String com os dados contidos no objeto.
     *
     * @return String listando as propriedades do arquivo.
     */
    @Override
    String toString();

}
