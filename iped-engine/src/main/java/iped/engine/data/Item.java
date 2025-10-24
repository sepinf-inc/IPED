package iped.engine.data;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.data.IHashValue;
import iped.data.IItem;
import iped.datasource.IDataSource;
import iped.engine.core.Statistics;
import iped.engine.io.ReferencedFile;
import iped.engine.lucene.analysis.CategoryTokenizer;
import iped.engine.preview.PreviewInputStreamFactory;
import iped.engine.task.index.IndexItem;
import iped.engine.tika.SyncMetadata;
import iped.engine.util.ParentInfo;
import iped.engine.util.TextCache;
import iped.engine.util.Util;
import iped.io.ISeekableInputStreamFactory;
import iped.io.SeekableInputStream;
import iped.utils.ByteArrayImageInputStream;
import iped.utils.EmptyInputStream;
import iped.utils.HashValue;
import iped.utils.IOUtil;
import iped.utils.LimitedSeekableInputStream;
import iped.utils.SeekableByteChannelImpl;
import iped.utils.SeekableFileInputStream;

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
public class Item implements IItem {

    private static Logger LOGGER = LoggerFactory.getLogger(Item.class);

    private static final String TIKA_OPEN_CONTAINER_KEY = "TIKA_OPEN_CONTAINER";

    private static Set<String> extraAttributeSet = Collections.synchronizedSet(new HashSet<String>());

    private static class Counter {

        private static int nextId = 0;

        public static synchronized int getNextId() {
            return nextId++;
        }

        public static synchronized int setStartID(int start) {
            return nextId = start;
        }
    }

    public static int getNextId() {
        return Counter.getNextId();
    }

    /**
     * @param start
     *            id inicial para itens adicionados após o processamento inicial
     */
    public static void setStartID(int start) {
        Counter.setStartID(start);
    }

    /** representa a evidência de origem (imagem dd, pasta) do item */
    private IDataSource dataSource;

    /**
     * Nome do arquivo.
     */
    private String name;

    /**
     * Extensão arquivo.
     */
    private String extension;

    private String path;

    /**
     * The detected file type ext.
     */
    private String type;

    private MediaType mediaType;

    private boolean deleted = false;

    private int id = -1;

    private Integer parentId;

    private Integer subitemId;

    private List<Integer> parentIds = new ArrayList<Integer>();

    private Map<String, Object> extraAttributes = new ConcurrentHashMap<String, Object>();

    /**
     * Temporaty attributes present only during processing flow.
     */
    private Map<String, Object> tempAttributes = new HashMap<>();

    /**
     * Data de criação do arquivo.
     */
    private Date creationDate;

    /**
     * Data da última alteração do arquivo.
     */
    private Date modificationDate;

    /**
     * Data do último acesso do arquivo.
     */
    private Date accessDate;

    /**
     * Data de última alteração do registro no sistema de arquivos.
     */
    private Date changeDate;

    /**
     * Tamanho do arquivo em bytes.
     */
    private Long length;

    /**
     * Nome e caminho relativo que o arquivo para visualização.
     */
    private File viewFile;

    private boolean hasPreview;

    private File previewBaseFolder;

    private String previewExt;

    private HashSet<String> categories = new HashSet<String>();

    private List<String> labels = new ArrayList<>();

    private Metadata metadata;

    private boolean timeOut = false;

    private boolean isSubItem = false, hasChildren = false;

    private boolean isDir = false, isRoot = false, sumVolume = true;

    private boolean toIgnore = false, addToCase = true, isToExtract = false, allowGetId = false;

    private boolean carved = false;

    private boolean isQueueEnd = false, parsed = false;

    private TextCache textCache;

    private String hash;

    private IHashValue hashValue;

    private File tmpFile, parentTmpFile;

    private ReferencedFile refTmpFile;

    private TemporaryResources tmpResources;

    private long startOffset = -1, parentOffset = -1;

    private String idInDataSource;

    private TikaInputStream tis;

    private byte[] thumb;

    private byte[] data;

    private ISeekableInputStreamFactory inputStreamFactory;

    private static final int BUF_LEN = 8 * 1024 * 1024;
    
    private static final int maxImageLength = 128 << 20;

    /**
     * Adiciona o item a uma categoria.
     *
     * @param category
     *            categoria a qual o item será adicionado
     */
    public void addCategory(String category) {
        this.categories.add(category);
    }

    /**
     * Adiciona o id de um dos pais do item numa estrutura hierárquica
     *
     * @param parentId
     *            id de um dos pais
     */
    public void addParentId(int parentId) {
        this.parentIds.add(parentId);
    }

    /**
     * Adiciona uma lista de ids dos pais do item numa estrutura hierárquica
     *
     * @param parentIds
     *            lista de ids dos pais do item
     */
    public void addParentIds(List<Integer> parentIds) {
        this.parentIds.addAll(parentIds);
    }

    /**
     * Libera recursos utilizados, como arquivos temporários e handles
     *
     * @throws IOException
     *             caso ocorra erro de IO
     */
    public void dispose() {
        this.dispose(true);
    }

    public void dispose(boolean clearTextCache) {
        if (tmpResources != null) {
            try {
                tmpResources.close();
            } catch (Exception e) {
                LOGGER.warn("Error closing resources of " + getPath(), e);
            }
            tmpResources = null;
        }
        data = null;
        tmpFile = null;
        tis = null;
        parentTmpFile = null;
        try {
            if (textCache != null && clearTextCache) {
                textCache.close();
                textCache = null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * @return data do último acesso
     */
    public Date getAccessDate() {
        return accessDate;
    }

    private int getBestBufferSize() {
        int len = 8192;
        if (length != null && length > len) {
            if (length < BUF_LEN) {
                len = length.intValue();
            } else {
                len = BUF_LEN;
            }
        }
        return len;
    }

    /**
     * @return um BufferedInputStream com o conteúdo do item
     * @throws IOException
     */
    public BufferedInputStream getBufferedInputStream() throws IOException {
        if (data != null) {
            return new BufferedInputStream(new ByteArrayInputStream(data));
        }
        return new BufferedInputStream(getSeekableInputStream(), getBestBufferSize());
    }

    public ImageInputStream getImageInputStream() throws IOException {
        if (data != null) {
            return new ByteArrayImageInputStream(data);
        }
        if (tmpFile != null) {
            return new FileImageInputStream(tmpFile);
        }
        File file = IOUtil.getFile(this);
        if (file != null) {
            return new FileImageInputStream(file);
        }
        if (length == null || length <= maxImageLength) {
            BufferedInputStream bis = getBufferedInputStream();
            return new MemoryCacheImageInputStream(bis) {
                @Override
                public void close() throws IOException {
                    // This BufferedInputStream was created to be used by the returned
                    // ImageInputStream, so it should be closed when the IIS is closed.
                    IOUtil.closeQuietly(bis);
                    super.close();
                }
            };
        }

        // If the file is too large, then create a temporary File, instead of using the
        // InputStream to avoid excessive memory usage or even OOME (see issue #2033).
        file = getTempFile();
        return new FileImageInputStream(file);
    }

    /**
     *
     * @return o nome das categorias do item concatenadas
     */
    public String getCategories() {
        String names = ""; //$NON-NLS-1$
        int i = 0;
        for (String bookmark : categories) {
            names += bookmark;
            if (++i < categories.size()) {
                names += CategoryTokenizer.SEPARATOR;
            }
        }
        return names;
    }

    /**
     *
     * @return o conjunto de categorias do item
     */
    public HashSet<String> getCategorySet() {
        return categories;
    }

    /**
     * @return data de criação do arquivo
     */
    public Date getCreationDate() {
        return creationDate;
    }

    /**
     *
     * @return a extensão original do item
     */
    public String getExt() {
        return extension;
    }

    /**
     * Módulos de processamento podem setar atributos extras no item para armazenar
     * o resultado do processamento
     *
     * @param key
     *            o nome do atributo extra
     * @return o valor do atributo extra
     */
    public Object getExtraAttribute(String key) {
        return extraAttributes.get(key);
    }

    /**
     *
     * @return o mapa de atributos extras do item. Módulos de processamento podem
     *         setar atributos extras no item para armazenar o resultado do
     *         processamento
     */
    public Map<String, Object> getExtraAttributeMap() {
        return this.extraAttributes;
    }

    public static Set<String> getAllExtraAttributes() {
        return extraAttributeSet;
    }

    /**
     *
     * @return o offset no item pai da onde o item foi recuperado (carving). Retorna
     *         -1 se o item não é proveniente de carving.
     */
    public long getFileOffset() {
        return startOffset;
    }

    /**
     *
     * @return o hash do arquivo, caso existente.
     */
    public String getHash() {
        return hash;
    }

    public IHashValue getHashValue() {
        if (hash == null || hash.isEmpty()) {
            return null;
        }
        if (hashValue == null) {
            hashValue = new HashValue(hash);
        }
        return hashValue;
    }

    /**
     *
     * @return o id do item
     */
    public int getId() {
        if (id == -1) {
            if (!allowGetId) {
                /**
                 * id may change when resuming processing. Currently this could happen when
                 * adding items to processing queue. So id can just be read after its value is
                 * set to the previous processing id or if a previous id is not found.
                 */
                Exception e = new Exception("Cannot use ID before adding item to queue");
                LOGGER.error("", e);
            }
            synchronized (Counter.class) {
                if (id == -1) {
                    id = getNextId();
                }
            }
        }
        return id;
    }

    /**
     * Set to true if ID could be retrieved after being set to its final value.
     * 
     * @param allowGetId
     */
    public void setAllowGetId(boolean allowGetId) {
        this.allowGetId = allowGetId;
    }

    /**
     *
     * @return lista de marcadores do item
     */
    public List<String> getLabels() {
        return labels;
    }

    /**
     * @return tamanho do arquivo em bytes
     */
    public Long getLength() {
        return length;
    }

    /**
     *
     * @return o mediaType do arquivo, resultado da análise de assinatura
     */
    public MediaType getMediaType() {
        return mediaType;
    }

    /**
     * @return data da última modificação do arquivo
     */
    public Date getModDate() {
        return modificationDate;
    }

    /**
     * @return nome do arquivo
     */
    public String getName() {
        return name;
    }

    /**
     *
     * @return o id do item pai.
     */
    public Integer getParentId() {
        return parentId;
    }

    public Integer getSubitemId() {
        return subitemId;
    }

    /**
     *
     * @return lista contendo os ids dos itens pai
     */
    public List<Integer> getParentIds() {
        return parentIds;
    }

    /**
     *
     * @return ids dos itens pai concatenados com espaço
     */
    public String getParentIdsString() {
        StringBuilder parents = new StringBuilder(); // $NON-NLS-1$
        int i = 0;
        for (Integer id : parentIds) {
            parents.append(id);
            if (++i < parentIds.size()) {
                parents.append(" ");
            }
        }
        return parents.toString();
    }

    /**
     *
     * @return o texto extraído do item armazenado pela tarefa de expansão para
     *         alguns containers com texto (eml, ppt, etc)
     */
    @Deprecated
    public String getParsedTextCache() {
        if (textCache == null)
            return null;
        StringBuilder sb = new StringBuilder();
        try (Reader reader = textCache.getTextReader()) {
            int tot = 0, i = 0;
            char[] cbuf = new char[64 * 1024];
            while ((tot += i) < 10000000 && (i = reader.read(cbuf)) != -1) {
                sb.append(cbuf, 0, i);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return sb.toString();
    }

    public TextCache getTextCache() {
        return textCache;
    }

    public Reader getTextReader() throws IOException {
        if (textCache == null)
            return null;
        else {
            textCache.setSourceItem(this);
            return textCache.getTextReader();
        }
    }

    /**
     * @return String com o caminho completo do item
     */
    public String getPath() {
        return path;
    }

    /**
     * @return InputStream com o conteúdo do arquivo.
     */
    @Override
    public SeekableInputStream getSeekableInputStream() throws IOException {

        if (data != null) {
            return new SeekableFileInputStream(new SeekableInMemoryByteChannel(data));
        }

        // block 1 (referenciado abaixo)
        if (tmpFile == null && tis != null && tis.hasFile()) {
            tmpFile = tis.getFile();
        }

        if (tmpFile != null) {
            try {
                return new SeekableFileInputStream(tmpFile);

                // workaround para itens com erro de parsing cujo tmpFile foi setado por block1
                // acima ao chamar getStream() antes de rodar strings e dps apagado ao fechar
                // stream dps do parsing
            } catch (IOException fnfe) {
                tmpFile = null;
            }
        }

        // optimization for carved files
        if (parentTmpFile != null && parentOffset != -1) {
            try {
                return new LimitedSeekableInputStream(new SeekableFileInputStream(parentTmpFile), parentOffset, length);

                // workaround para itens carveados apontando para tmpFile do pai que foi apagado
                // Sometimes NPE is thrown, needs investigation...
            } catch (IOException | NullPointerException e) {
                parentTmpFile = null;
            }
        }

        SeekableInputStream stream = null;

        if (inputStreamFactory != null && idInDataSource != null) {
            stream = inputStreamFactory.getSeekableInputStream(idInDataSource);
        }

        if (stream != null && startOffset != -1 && !(inputStreamFactory instanceof PreviewInputStreamFactory)) {
            stream = new LimitedSeekableInputStream(stream, startOffset, length);
        }

        if (stream == null)
            return new EmptyInputStream();

        return stream;
    }

    @Override
    public SeekableByteChannel getSeekableByteChannel() throws IOException {
        if (data != null) {
            return new SeekableInMemoryByteChannel(data);
        }
        return new SeekableByteChannelImpl(this.getSeekableInputStream());
    }

    /**
     *  Cache data in memory for small items to avoid:
     *  1. multiple decompression of data from compressed evidences
     *  2. multiple reads from evidences in network shares
     *  3. writing small temp files in temp dir, when possible
     *  4. decrease heavy IO calls into kernel space 
     *  
     * @return true if data was cached on memory, false otherwise
     */
    public boolean cacheDataInMemory() {
        if (length == null || length > BUF_LEN) {
            return false;
        }
        if (data != null) {
            return true;
        }
        try (InputStream is = this.getSeekableInputStream()) {
            data = new byte[length.intValue()];
            int i, offset = 0;
            while (offset < data.length && (i = is.read(data, offset, data.length - offset)) != -1) {
                offset += i;
            }
            return true;
        } catch (IOException e) {
            // ignore
        }
        return false;
    }

    /**
     * Usado em módulos que só possam processar um File e não um InputStream. Pode
     * impactar performance pois gera arquivo temporário.
     *
     * @return um arquivo temporário com o conteúdo do item.
     * @throws IOException
     */
    @Override
    public File getTempFile() throws IOException {
        if (IOUtil.hasFile(this)) {
            return IOUtil.getFile(this);
        }
        if (tmpFile == null) {
            if (tis != null && tis.hasFile()) {
                tmpFile = tis.getFile();
            } else {
                try (SeekableInputStream sis = getSeekableInputStream()) {
                    if (sis instanceof SeekableFileInputStream) {
                        File file = ((SeekableFileInputStream) sis).getFile();
                        if (file != null && IOUtil.isTemporaryFile(file)) {
                            tmpFile = file;
                        }
                    }
                    if (tmpFile == null) {
                        String ext = ".tmp"; //$NON-NLS-1$
                        if (type != null && !type.toString().isEmpty()) {
                            ext = Util.getValidFilename("." + type.toString()); //$NON-NLS-1$
                        }
                        Path path = Files.createTempFile("iped", ext); //$NON-NLS-1$
                        if (data != null) {
                            Files.write(path, data);
                        } else {
                            Files.copy(new BufferedInputStream(sis, getBestBufferSize()), path, StandardCopyOption.REPLACE_EXISTING);
                        }
                        tmpFile = path.toFile();
                    }
                    refTmpFile = new ReferencedFile(tmpFile);
                    addTmpResource(refTmpFile);
                }
            }
        }
        return tmpFile;
    }

    public void setTempFile(File tempFile) {
        tmpFile = tempFile;
    }

    public boolean hasTmpFile() {
        return tmpFile != null;
    }

    /**
     *
     * @return um TikaInputStream com o conteúdo do arquivo
     * @throws IOException
     */
    public TikaInputStream getTikaStream() throws IOException {

        File file = null;
        if (IOUtil.hasFile(this) && (file = IOUtil.getFile(this)).isFile()) {
            tis = TikaInputStream.get(file.toPath());
        } else {
            if (tmpFile == null && tis != null && tis.hasFile()) {
                tmpFile = tis.getFile();
            }
            // reset tis, it may have been set (and consumed) by a previous call of this
            // method
            tis = null;
            if (tmpFile != null) {
                try {
                    tis = TikaInputStream.get(tmpFile.toPath());
                } catch (IOException fnfe) {
                    tmpFile = null;
                }
            }
            if (tis == null && data != null) {
                tis = TikaInputStream.get(data);
            }
            if (tis == null) {
                tis = TikaInputStream.get(getBufferedInputStream());
            }
        }
        addTmpResource(tis);

        // set the TikaInputStream openContainer with the object previously set in item.setOpenContainer(object)
        Object openContainer = getTempAttribute(TIKA_OPEN_CONTAINER_KEY);
        if (openContainer != null) {
            tis.setOpenContainer(openContainer);
        }
        return tis;
    }

    /**
     * This method is used to pass a generic object to TikaInputStream in parsers.
     * In the parser, the generic object can be fetch by calling TikaInputStream.getOpenContainer()
     */
    public void setOpenContainer(Object openContainer) {
        setTempAttribute(TIKA_OPEN_CONTAINER_KEY, openContainer);
    }

    @Override
    public String getType() {
        return type;
    }

    /**
     * Obtém o arquivo de visualização. Retorna nulo caso inexistente.
     *
     * @return caminho relativo ao caso do arquivo de visualização
     */
    public File getViewFile() {
        return viewFile;
    }

    @Override
    public boolean hasPreview() {
        return hasPreview;
    }

    @Override
    public File getPreviewBaseFolder() {
        return previewBaseFolder;
    }

    @Override
    public String getPreviewExt() {
        return previewExt;
    }

    /**
     *
     * @return true se o item tem filhos, como subitens ou itens carveados
     */
    public boolean hasChildren() {
        return hasChildren;
    }

    /**
     *
     * @return true se o item é proveniente de carving
     */
    public boolean isCarved() {
        return carved;
    }

    /**
     *
     * @return true se o item está apagado
     */
    public boolean isDeleted() {
        return deleted;
    }

    /**
     *
     * @return true se o item é um diretório
     */
    public boolean isDir() {
        return isDir;
    }

    /**
     * @return true se o item foi submetido a parsing
     */
    public boolean isParsed() {
        return parsed;
    }

    /**
     * @return true se é um item de fim de fila de processamento
     */
    public boolean isQueueEnd() {
        return isQueueEnd;
    }

    /**
     * @param id
     *            identificador do item
     */
    public void setId(int id) {
        this.id = id;
    }

    /**
     * @return true se é um item raiz
     */
    public boolean isRoot() {
        return isRoot;
    }

    /**
     * @return true se é um subitem de um container
     */
    public boolean isSubItem() {
        return isSubItem;
    }

    /**
     * @return true se o parsing do item ocasionou timeout
     */
    public boolean isTimedOut() {
        return timeOut;
    }

    /**
     *
     * @return true se o item deve ser exportado
     */
    public boolean isToExtract() {
        return isToExtract;
    }

    /**
     *
     * @return true se o item deve ser ignorado pelas próximas tarefas de
     *         processamento e excluído do caso
     */
    public boolean isToIgnore() {
        return toIgnore;
    }

    /**
     *
     * @return true se o item deve ser adicionado ao caso
     */
    public boolean isToAddToCase() {
        return addToCase;
    }

    /**
     * Remove o item da categoria
     *
     * @param category
     *            categoria a ser removida
     */
    public void removeCategory(String category) {
        categories.remove(category);
    }

    /**
     * @param accessDate
     *            nova data de último acesso
     */
    public void setAccessDate(Date accessDate) {
        this.accessDate = accessDate;
    }

    public Date getChangeDate() {
        return changeDate;
    }

    public void setChangeDate(Date changeDate) {
        this.changeDate = changeDate;
    }

    /**
     * Define se é um item de carving
     *
     * @param carved
     *            se é item de carving
     */
    public void setCarved(boolean carved) {
        this.carved = carved;
    }

    /**
     * Redefine a categoria do item
     *
     * @param category
     *            nova categoria
     */
    public void setCategory(String category) {
        categories = new HashSet<String>();
        if (category != null)
            categories.add(category);
    }

    /**
     * @param creationDate
     *            nova data de criação do arquivo
     */
    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }

    /**
     * Define se o item é apagado
     *
     * @param deleted
     *            se é apagado
     */
    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    /**
     * Define a extensão do item.
     *
     * @param ext
     *            extensão
     */
    public void setExtension(String ext) {
        extension = ext;
    }

    /**
     * Define um atributo extra para o item
     *
     * @param key
     *            nome do atributo
     * @param value
     *            valor do atributo
     */
    public void setExtraAttribute(String key, Object value) {
        this.extraAttributes.put(key, value);
        extraAttributeSet.add(key);
    }

    /**
     * Define o offset onde itens de carving são encontrados no item pai
     *
     * @param fileOffset
     *            offset do item
     */
    public void setFileOffset(long fileOffset) {
        this.startOffset = fileOffset;
    }

    /**
     * Define se o item tem filhos, como subitens ou itens de carving
     *
     * @param hasChildren
     *            se tem filhos
     */
    public void setHasChildren(boolean hasChildren) {
        this.hasChildren = hasChildren;
    }

    /**
     * Define o hash do item
     *
     * @param hash
     *            hash do item
     */
    public void setHash(String hash) {
        this.hash = hash;
    }

    /**
     * Define se o item é um diretório.
     *
     * @param isDir
     *            se é diretório
     */
    public void setIsDir(boolean isDir) {
        this.isDir = isDir;
    }

    /**
     * Define os marcadores do item
     *
     * @param labels
     *            lista de marcadores
     */
    public void setLabels(List<String> labels) {
        this.labels = labels;
    }

    /**
     * @param length
     *            tamanho do arquivo
     */
    public void setLength(Long length) {
        this.length = length;
    }

    /**
     * Define o mediaType do item baseado na assinatura
     *
     * @param mediaType
     *            internet mediaType
     */
    public void setMediaType(MediaType mediaType) {
        this.mediaType = mediaType;
    }

    public void setMediaTypeStr(String mediaType) {
        this.mediaType = MediaType.parse(mediaType);
    }

    /**
     * @param modificationDate
     *            data da última modificação do arquivo
     */
    public void setModificationDate(Date modificationDate) {
        this.modificationDate = modificationDate;
    }

    /**
     * @param name
     *            nome do arquivo
     */
    public void setName(String name) {
        this.name = name;
        int p = name.lastIndexOf("."); //$NON-NLS-1$
        extension = (p < 0) ? "" : name.substring(p + 1).toLowerCase().trim(); //$NON-NLS-1$
    }

    public void setSubitemId(Integer subitemId) {
        this.subitemId = subitemId;
    }

    /**
     * @param parentId
     *            identificador do item pai
     */
    public void setParentId(Integer parentId) {
        this.parentId = parentId;
    }

    public void setParent(iped.data.IItem parent) {
        int parentId = parent.getId();
        this.setParentId(parentId);
        this.addParentIds(parent.getParentIds());
        this.addParentId(parentId);
        this.setDataSource(parent.getDataSource());
        String parenttrackID = (String) parent.getExtraAttribute(IndexItem.TRACK_ID);
        if (parenttrackID == null) {
            throw new RuntimeException(IndexItem.TRACK_ID
                    + " cannot be null. It is populated after enqueuing the item: " + parent.getPath());
        }
        this.setExtraAttribute(IndexItem.PARENT_TRACK_ID, parenttrackID);
    }

    public void setParent(ParentInfo parent) {
        int parentId = parent.getId();
        this.setParentId(parentId);
        this.addParentIds(parent.getParentIds());
        this.addParentId(parentId);
        this.setDataSource(parent.getDataSource());
        this.setExtraAttribute(IndexItem.PARENT_TRACK_ID, parent.getTrackId());
    }

    /**
     * @param parsed
     *            se foi realizado parsing do item
     */
    public void setParsed(boolean parsed) {
        this.parsed = parsed;
    }

    /**
     * @param parsedTextCache
     *            texto extraído após o parsing
     */
    @Deprecated
    public void setParsedTextCache(String parsedText) {
        TextCache textCache = new TextCache();
        try {
            textCache.write(parsedText);
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.setParsedTextCache(textCache);
    }

    public void setParsedTextCache(TextCache textCache) {
        if (this.textCache != null) {
            try {
                this.textCache.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        this.textCache = textCache;
    }

    /**
     * @param path
     *            caminho do item
     */
    public void setPath(String path) {
        this.path = path;
    }

    /**
     * @param isQueueEnd
     *            se é um item especial de fim de fila
     */
    public void setQueueEnd(boolean isQueueEnd) {
        this.isQueueEnd = isQueueEnd;
    }

    /**
     * @param isRoot
     *            se o item é raiz
     */
    public void setRoot(boolean isRoot) {
        this.isRoot = isRoot;
    }

    /**
     * @param isSubItem
     *            se o item é um subitem
     */
    public void setSubItem(boolean isSubItem) {
        this.isSubItem = isSubItem;
    }

    /**
     * @param timeOut
     *            se o parsing do item ocasionou timeout
     */
    public void setTimeOut(boolean timeOut) {
        this.timeOut = timeOut;
    }

    /**
     * @param isToExtract
     *            se o item deve ser extraído
     */
    public void setToExtract(boolean isToExtract) {
        this.isToExtract = isToExtract;
    }

    /**
     * @param toIgnore
     *            se o item deve ser ignorado pela tarefas de processamento
     *            seguintes e excluído do caso
     */
    public void setToIgnore(boolean toIgnore) {
        setToIgnore(toIgnore, true);
    }

    /**
     * @param toIgnore
     *            se o item deve ser ignorado pela tarefas de processamento
     *            seguintes e excluído do caso
     */
    public void setToIgnore(boolean toIgnore, boolean updateStats) {
        this.toIgnore = toIgnore;
        if (updateStats && toIgnore)
            Statistics.get().incIgnored();
    }

    /**
     * @param addToCase
     *            se o item deve ser adicionado ao caso
     */
    public void setAddToCase(boolean addToCase) {
        this.addToCase = addToCase;
    }

    @Override
    public void setType(String type) {
        this.type = type;
    }

    /**
     * @param viewFile
     *            caminho do arquivo para visualização.
     */
    public void setViewFile(File viewFile) {
        this.viewFile = viewFile;
    }

    @Override
    public void setHasPreview(boolean value) {
        this.hasPreview = value;
    }

    @Override
    public void setPreviewExt(String previewExt) {
        this.previewExt = previewExt;
    }

    public void setPreviewBaseFolder(File previewBaseFolder) {
        this.previewBaseFolder = previewBaseFolder;
    }

    /**
     * Retorna String com os dados contidos no objeto.
     *
     * @return String listando as propriedades do arquivo.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Item: ").append(getPath()); //$NON-NLS-1$
        if (type != null) {
            sb.append(" type: ").append(type); //$NON-NLS-1$
        }
        if (length != null) {
            sb.append(" size: ").append(length); //$NON-NLS-1$
        }
        return sb.toString();
    }

    public boolean isToSumVolume() {
        return sumVolume;
    }

    public void setSumVolume(boolean sumVolume) {
        this.sumVolume = sumVolume;
    }

    public boolean hasParentTmpFile() {
        return parentTmpFile != null && parentOffset != -1;
    }

    public void setParentTmpFile(File parentTmpFile, Item parent) {
        this.parentTmpFile = parentTmpFile;
        if (parent.refTmpFile != null) {
            parent.refTmpFile.increment();
            addTmpResource(parent.refTmpFile);
        }
    }

    private void addTmpResource(Closeable c) {
        if (tmpResources == null) {
            tmpResources = new TemporaryResources(); 
        }
        tmpResources.addResource(c);
    }

    public void setParentOffset(long parentOffset) {
        this.parentOffset = parentOffset;
    }

    public Metadata getMetadata() {
        if (metadata == null) {
            metadata = new SyncMetadata();
        }
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        if (metadata instanceof SyncMetadata)
            this.metadata = metadata;
        else
            throw new IllegalArgumentException("Just SyncMetadata instances should be set in Item metadata.");
    }

    public IDataSource getDataSource() {
        return dataSource;
    }

    public void setDataSource(IDataSource evidence) {
        this.dataSource = evidence;
    }

    @Override
    public byte[] getThumb() {
        return thumb;
    }

    public void setThumb(byte[] thumb) {
        this.thumb = thumb;
    }

    public ISeekableInputStreamFactory getInputStreamFactory() {
        return inputStreamFactory;
    }

    public void setInputStreamFactory(ISeekableInputStreamFactory inputStreamFactory) {
        this.inputStreamFactory = inputStreamFactory;
    }

    public String getIdInDataSource() {
        return idInDataSource;
    }

    public void setIdInDataSource(String idInDataSource) {
        this.idInDataSource = idInDataSource;
    }

    @Override
    public IItem createChildItem() {
        IItem child = new Item();
        child.setParent(this);
        child.setDeleted(this.isDeleted());

        return child;
    }

    public Object getTempAttribute(String key) {
        return tempAttributes.get(key);
    }

    public void setTempAttribute(String key, Object value) {
        tempAttributes.put(key, value);
    }
}
