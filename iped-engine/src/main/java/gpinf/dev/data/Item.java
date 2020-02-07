package gpinf.dev.data;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
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

import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.analysis.CategoryTokenizer;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.config.SleuthKitConfig;
import dpf.sp.gpinf.indexer.datasource.SleuthkitReader;
import dpf.sp.gpinf.indexer.process.Statistics;
import dpf.sp.gpinf.indexer.process.task.ImageThumbTask;
import dpf.sp.gpinf.indexer.util.EmptyInputStream;
import dpf.sp.gpinf.indexer.util.HashValue;
import dpf.sp.gpinf.indexer.util.LimitedSeekableInputStream;
import dpf.sp.gpinf.indexer.util.ParentInfo;
import dpf.sp.gpinf.indexer.util.SeekableByteChannelImpl;
import dpf.sp.gpinf.indexer.util.SeekableFileInputStream;
import dpf.sp.gpinf.indexer.util.SleuthkitClient;
import dpf.sp.gpinf.indexer.util.SleuthkitInputStream;
import dpf.sp.gpinf.indexer.util.TextCache;
import dpf.sp.gpinf.indexer.util.Util;
import iped3.IItem;
import iped3.IEvidenceFileType;
import iped3.IHashValue;
import iped3.datasource.IDataSource;
import iped3.io.ISeekableInputStreamFactory;
import iped3.io.SeekableInputStream;
import iped3.sleuthkit.ISleuthKitItem;

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
public class Item implements ISleuthKitItem {

    private static Logger LOGGER = LoggerFactory.getLogger(Item.class);

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

    /**
     * Identificador utilizado para serialização da classe.
     */
    private static final long serialVersionUID = 98653214753695125L;

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
     * Tipo do arquivo.
     */
    private IEvidenceFileType type;

    private MediaType mediaType;

    private boolean deleted = false;

    private int id = -1;

    private Integer ftkID;

    private Integer parentId;

    private List<Integer> parentIds = new ArrayList<Integer>();

    private Map<String, Object> extraAttributes = new ConcurrentHashMap<String, Object>();

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
    private Date recordDate;

    /**
     * Tamanho do arquivo em bytes.
     */
    private Long length;

    /**
     * Nome e caminho relativo que o arquivo foi exportado.
     */
    private String exportedFile;

    /**
     * Nome e caminho relativo que o arquivo para visualização.
     */
    private File viewFile;

    private HashSet<String> categories = new HashSet<String>();

    private List<String> labels = new ArrayList<>();

    private Metadata metadata;

    private boolean timeOut = false;

    private boolean duplicate = false;

    private boolean isSubItem = false, hasChildren = false;

    private boolean isDir = false, isRoot = false, sumVolume = true;

    private boolean toIgnore = false, addToCase = true, isToExtract = false, deleteFile = false;

    /**
     * Configura deleção posterior do arquivo. Por ex, subitem que deva ser
     * processado e incluído no relatório, porém sem ter seu conteúdo exportado (ex:
     * gera thumb do vídeo e dps deleta o vídeo)
     *
     * @param deleteFile
     *            se deve ser deletado ao não
     */
    public void setDeleteFile(boolean deleteFile) {
        this.deleteFile = deleteFile;
    }

    private boolean carved = false;

    private boolean isQueueEnd = false, parsed = false;

    private TextCache textCache;

    private String hash;

    private IHashValue hashValue;

    private File file, tmpFile;

    private TemporaryResources tmpResources = new TemporaryResources();

    private Content sleuthFile;

    private long startOffset = -1, tempStartOffset = -1;

    private Integer sleuthId;

    private String idInDataSource;

    private TikaInputStream tis;

    private byte[] thumb;

    private ISeekableInputStreamFactory inputStreamFactory;

    static final int BUF_LEN = 8 * 1024 * 1024;

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
        try {
            tmpResources.close();
        } catch (Exception e) {
            // LOGGER.warn("{} {}", Thread.currentThread().getName(), e.toString());
        }
        try {
            if (textCache != null)
                textCache.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (isSubItem && file != null && (toIgnore || !addToCase || deleteFile)) {
            if (!file.delete()) {
                LOGGER.warn("{} Error deleting {}", Thread.currentThread().getName(), file.getAbsolutePath()); //$NON-NLS-1$
            }
        }
    }

    /**
     * @return data do último acesso
     */
    public Date getAccessDate() {
        return accessDate;
    }

    /**
     * @return um BufferedInputStream com o conteúdo do item
     * @throws IOException
     */
    public BufferedInputStream getBufferedStream() throws IOException {

        int len = 8192;
        if (length != null && length > len) {
            if (length < BUF_LEN) {
                len = length.intValue();
            } else {
                len = BUF_LEN;
            }
        }

        return new BufferedInputStream(getStream(), len);
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
     * @return nome e caminho relativo ao caso com que o arquivo de evidência em si
     *         foi exportado
     */
    public String getExportedFile() {
        return exportedFile;
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
     * @return o arquivo com o conteúdo do item. Retorna não nulo apenas em
     *         processamentos de pastas, reports e no caso de subitens de
     *         containers. Consulte {@link #getTempFile()}} e {@link #getStream()}
     */
    public File getFile() {
        return file;
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
     * @return o caminho para o arquivo do item. Diferente de vazio apenas em
     *         reports e processamentos de pastas.
     */
    public String getFileToIndex() {
        if (exportedFile != null) {
            return exportedFile.trim();
        } else {
            return ""; //$NON-NLS-1$
        }
    }

    /**
     *
     * @return o id do item no FTK3+ no caso de reports
     */
    public Integer getFtkID() {
        return ftkID;
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
            id = getNextId();
        }

        return id;
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
     * @return o id do item pai. Tem o nome do caso prefixado no caso de reports do
     *         FTK3+
     */
    public Integer getParentId() {
        return parentId;
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
        String parents = ""; //$NON-NLS-1$
        for (Integer id : parentIds) {
            parents += id + " "; //$NON-NLS-1$
        }

        return parents;
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
        else
            return textCache.getTextReader();
    }

    /**
     * @return String com o caminho completo do item
     */
    public String getPath() {
        return path;
    }

    /**
     *
     * @return o objeto do Sleuthkit que representa o item
     */
    public Content getSleuthFile() {
        return sleuthFile;
    }

    /**
     *
     * @return o id do item no Sleuthkit
     */
    public Integer getSleuthId() {
        return sleuthId;
    }

    /**
     * @return InputStream com o conteúdo do arquivo.
     */
    public SeekableInputStream getStream() throws IOException {
        if (startOffset == -1 && file != null && file.isFile()) {
            return new SeekableFileInputStream(file);
        }

        // block 1 (referenciado abaixo)
        if (tmpFile == null && tis != null && tis.hasFile()) {
            tmpFile = tis.getFile();
        }

        if (tmpFile != null) {
            try {
                return new SeekableFileInputStream(tmpFile);

                // workaround para itens com erro de parsing cujo tmpFile foi setado por block1
                // acima ao
                // chamar getStream() antes de rodar strings e dps apagado ao fechar stream dps
                // do parsing
            } catch (IOException fnfe) {
                tmpFile = null;
            }
        }

        SeekableInputStream stream = null;
        if (file != null && file.isFile()) {
            try {
                stream = new SeekableFileInputStream(file);

                if (tempStartOffset != -1) {
                    return new LimitedSeekableInputStream(stream, tempStartOffset, length);
                }

                // workaround para itens carveados apontando para tmpFile do pai que foi apagado
                //Sometimes NPE is thrown, needs investigation...
            } catch (IOException | NullPointerException e) {
                file = null;
            }
        }

        if (stream == null && sleuthFile != null) {
            SleuthkitCase sleuthcase = SleuthkitReader.sleuthCase;
            SleuthKitConfig tskConfig = (SleuthKitConfig) ConfigurationManager.getInstance()
                    .findObjects(SleuthKitConfig.class).iterator().next();
            if (sleuthcase == null || !tskConfig.isRobustImageReading()) {
                stream = new SleuthkitInputStream(sleuthFile);
            } else {
                SleuthkitClient sleuthProcess = SleuthkitClient.get();
                stream = sleuthProcess.getInputStream((int) sleuthFile.getId(), path);
            }
        }

        if (stream == null && inputStreamFactory != null)
            stream = inputStreamFactory.getSeekableInputStream(idInDataSource);

        if (stream != null && startOffset != -1) {
            stream = new LimitedSeekableInputStream(stream, startOffset, length);
        }

        if (stream == null)
            return new EmptyInputStream();

        return stream;
    }

    @Override
    public SeekableByteChannel getSeekableByteChannel() throws IOException {
        return new SeekableByteChannelImpl(this.getStream());
    }

    /**
     * Usado em módulos que só possam processar um File e não um InputStream. Pode
     * impactar performance pois gera arquivo temporário.
     *
     * @return um arquivo temporário com o conteúdo do item.
     * @throws IOException
     */
    public File getTempFile() throws IOException {
        if (startOffset == -1 && file != null) {
            return file;
        }
        if (tmpFile == null) {
            if (tis != null && tis.hasFile()) {
                tmpFile = tis.getFile();
            } else {
                String ext = ".tmp"; //$NON-NLS-1$
                if (type != null && !type.toString().isEmpty()) {
                    ext = Util.getValidFilename("." + type.toString()); //$NON-NLS-1$
                }
                final Path path = Files.createTempFile("iped", ext); //$NON-NLS-1$
                tmpResources.addResource(new Closeable() {
                    public void close() throws IOException {
                        Files.delete(path);
                    }
                });

                try (InputStream in = getBufferedStream()) {
                    Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
                }
                tmpFile = path.toFile();
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

    public boolean hasFile() {
        return startOffset == -1 && file != null;
    }

    /**
     *
     * @return um TikaInputStream com o conteúdo do arquivo
     * @throws IOException
     */
    public TikaInputStream getTikaStream() throws IOException {

        if (startOffset == -1 && file != null && file.isFile()) {
            tis = TikaInputStream.get(file);
        } else {
            if (tmpFile == null && tis != null && tis.hasFile()) {
                tmpFile = tis.getFile();
            }
            if (tmpFile != null) {
                try {
                    tis = TikaInputStream.get(tmpFile);
                } catch (FileNotFoundException fnfe) {
                    tmpFile = null;
                }
            }
            if (tmpFile == null) {
                tis = TikaInputStream.get(getBufferedStream());
            }
        }

        tmpResources.addResource(tis);
        return tis;
    }

    /**
     * @return o tipo de arquivo baseado na análise de assinatura
     */
    public IEvidenceFileType getType() {
        return type;
    }

    public String getTypeExt() {
        if (type == null)
            return null;
        else
            return type.getLongDescr();
    }

    /**
     * Obtém o arquivo de visualização. Retorna nulo caso inexistente.
     *
     * @return caminho relativo ao caso do arquivo de visualização
     */
    public File getViewFile() {
        return viewFile;
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
     * @return true se o item é uma duplicata de outro, baseado no hash
     */
    public boolean isDuplicate() {
        return duplicate;
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

    public Date getRecordDate() {
        return recordDate;
    }

    public void setRecordDate(Date recordDate) {
        this.recordDate = recordDate;
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
     * Define se o item é duplicado
     *
     * @param duplicate
     *            se é duplicado
     */
    public void setDuplicate(boolean duplicate) {
        this.duplicate = duplicate;
    }

    /**
     * Define o caminho para o arquivo do item, no caso de processamento de pastas e
     * para subitens extraídos.
     *
     * @param exportedFile
     *            caminho para o arquivo do item
     */
    public void setExportedFile(String exportedFile) {
        this.exportedFile = exportedFile;
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
     * Define o arquivo referente ao item, caso existente
     *
     * @param file
     *            arquivo referente ao item
     */
    public void setFile(File file) {
        this.file = file;
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
     * Define o id do FTK3+, em casos de report
     *
     * @param ftkID
     *            id do FTK
     */
    public void setFtkID(Integer ftkID) {
        this.ftkID = ftkID;
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
        extension = (p < 0) ? "" : name.substring(p + 1).toLowerCase(); //$NON-NLS-1$
    }

    /**
     * @param parentId
     *            identificador do item pai
     */
    public void setParentId(Integer parentId) {
        this.parentId = parentId;
    }

    public void setParent(iped3.IItem parent) {
        int parentId = parent.getId();
        this.setParentId(parentId);
        this.addParentIds(parent.getParentIds());
        this.addParentId(parentId);
        this.setDataSource(parent.getDataSource());
    }

    public void setParent(ParentInfo parent) {
        int parentId = parent.getId();
        this.setParentId(parentId);
        this.addParentIds(parent.getParentIds());
        this.addParentId(parentId);
        this.setDataSource(parent.getDataSource());
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
        this.textCache = new TextCache();
        try {
            this.textCache.write(parsedText);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setParsedTextCache(TextCache textCache) {
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
     * @param sleuthFile
     *            objeto que representa o item no sleuthkit
     */
    public void setSleuthFile(Content sleuthFile) {
        this.sleuthFile = sleuthFile;
    }

    /**
     * @param sleuthId
     *            id do item no sleuthkit
     */
    public void setSleuthId(Integer sleuthId) {
        this.sleuthId = sleuthId;
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

    /**
     * @param type
     *            tipo de arquivo
     */
    public void setType(IEvidenceFileType type) {
        this.type = type;
    }

    /**
     * @param viewFile
     *            caminho do arquivo para visualização.
     */
    public void setViewFile(File viewFile) {
        this.viewFile = viewFile;
    }

    /**
     * Retorna String com os dados contidos no objeto.
     *
     * @return String listando as propriedades do arquivo.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("File: " + name); //$NON-NLS-1$
        sb.append("\n\t\tPath: ").append(getPath()); //$NON-NLS-1$
        if (type != null) {
            sb.append("\n\t\t").append("File type: ") //$NON-NLS-1$ //$NON-NLS-2$
                    .append(type.getLongDescr());
        }
        if (creationDate != null) {
            sb.append("\n\t\tCreation: ").append(creationDate.toString()); //$NON-NLS-1$
        }
        if (modificationDate != null) {
            sb.append("\n\t\tModification: ").append(modificationDate.toString()); //$NON-NLS-1$
        }
        if (accessDate != null) {
            sb.append("\n\t\tLast Accessed: ").append(accessDate.toString()); //$NON-NLS-1$
        }
        if (length != null) {
            sb.append("\n\t\tSize: ").append(length); //$NON-NLS-1$
        }
        return sb.toString();
    }

    public boolean isToSumVolume() {
        return sumVolume;
    }

    public void setSumVolume(boolean sumVolume) {
        this.sumVolume = sumVolume;
    }

    public void setTempStartOffset(long tempStartOffset) {
        this.tempStartOffset = tempStartOffset;
    }

    public Metadata getMetadata() {
        if (metadata == null) {
            metadata = new Metadata();
        }
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
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
        this.setExtraAttribute(ImageThumbTask.HAS_THUMB, true);
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
}
