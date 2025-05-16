/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de Evidências Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package iped.engine.task;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.EmptyParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.html.HtmlMapper;
import org.apache.tika.parser.html.IdentityHtmlMapper;
import org.apache.tika.utils.XMLReaderUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.configuration.Configurable;
import iped.data.ICaseData;
import iped.data.IItem;
import iped.data.IItemReader;
import iped.engine.config.CategoryToExpandConfig;
import iped.engine.config.Configuration;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.ExternalParsersConfig;
import iped.engine.config.LocalConfig;
import iped.engine.config.OCRConfig;
import iped.engine.config.ParsersConfig;
import iped.engine.config.ParsingTaskConfig;
import iped.engine.config.PluginConfig;
import iped.engine.config.SplitLargeBinaryConfig;
import iped.engine.core.Statistics;
import iped.engine.core.Worker;
import iped.engine.core.Worker.ProcessTime;
import iped.engine.data.CaseData;
import iped.engine.data.IPEDSource;
import iped.engine.data.Item;
import iped.engine.io.MetadataInputStreamFactory;
import iped.engine.io.ParsingReader;
import iped.engine.search.ItemSearcher;
import iped.engine.task.carver.CarverTask;
import iped.engine.task.index.IndexItem;
import iped.engine.tika.SyncMetadata;
import iped.engine.util.ItemInfoFactory;
import iped.engine.util.ParentInfo;
import iped.engine.util.TextCache;
import iped.engine.util.Util;
import iped.exception.IPEDException;
import iped.exception.ZipBombException;
import iped.io.IStreamSource;
import iped.parsers.browsers.ie.IndexDatParser;
import iped.parsers.compress.PackageParser;
import iped.parsers.compress.SevenZipParser;
import iped.parsers.database.EDBParser;
import iped.parsers.external.ExternalParser;
import iped.parsers.external.ExternalParsersFactory;
import iped.parsers.fork.EmbeddedDocumentParser;
import iped.parsers.fork.EmbeddedDocumentParser.NameTitle;
import iped.parsers.fork.ForkParser;
import iped.parsers.mail.LibpffPSTParser;
import iped.parsers.misc.MultipleParser;
import iped.parsers.misc.PDFTextParser;
import iped.parsers.ocr.OCRParser;
import iped.parsers.python.PythonParser;
import iped.parsers.registry.RegRipperParser;
import iped.parsers.standard.RawStringParser;
import iped.parsers.standard.StandardParser;
import iped.parsers.telegram.TelegramParser;
import iped.parsers.util.ComputeThumb;
import iped.parsers.util.EmbeddedItem;
import iped.parsers.util.EmbeddedParent;
import iped.parsers.util.IgnoreCorruptedCarved;
import iped.parsers.util.ItemInfo;
import iped.parsers.util.MetadataUtil;
import iped.parsers.util.OCROutputFolder;
import iped.parsers.util.PDFToImage;
import iped.parsers.whatsapp.WhatsAppParser;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.properties.MediaTypes;
import iped.search.IItemSearcher;
import iped.utils.EmptyInputStream;
import iped.utils.IOUtil;

/**
 * TAREFA DE PARSING DE ALGUNS TIPOS DE ARQUIVOS. ARMAZENA O TEXTO EXTRAÍDO,
 * CASO PEQUENO, PARA REUTILIZAR DURANTE INDEXAÇÃO, ASSIM O ARQUIVO NÃO É
 * DECODIFICADO NOVAMENTE. O PARSING É EXECUTADO EM OUTRA THREAD, SENDO POSSÍVEL
 * MONITORAR E RECUPERAR DE HANGS, ETC.
 *
 * É REALIZADO O PARSING NOS SEGUINTES CASOS: - ITENS DO TIPO CONTAINER, PARA
 * EXTRAÇÃO DE SUBITENS. - ITENS DE CARVING PARA IGNORAR CORROMPIDOS, CASO A
 * INDEXAÇÃO ESTEJA DESABILITADA. - CATEGORIAS QUE POSSAM CONTER ITENS CIFRADOS,
 * ASSIM PODEM SER ADICIONADOS A CATEGORIA ESPECÍFICA.
 *
 * O PARSING DOS DEMAIS ITENS É REALIADO DURANTE A INDEXAÇÃO, ASSIM ITENS
 * GRANDES NÃO TEM SEU TEXTO EXTRAÍDO ARMAZENADO EM MEMÓRIA, O QUE PODERIA
 * CAUSAR OOM.
 */
public class ParsingTask extends ThumbTask implements EmbeddedDocumentExtractor {

    private static Logger LOGGER = LoggerFactory.getLogger(ParsingTask.class);

    public static final String ENCRYPTED = "encrypted"; //$NON-NLS-1$
    public static final String HAS_SUBITEM = "hasSubitem"; //$NON-NLS-1$
    public static final String NUM_SUBITEMS = "numSubItems"; //$NON-NLS-1$

    private static final int MAX_SUBITEM_DEPTH = 100;
    private static final String SUBITEM_DEPTH = "subitemDepth"; //$NON-NLS-1$

    private static final String PARENT_CONTAINER_HASH = "PARENT_CONTAINER_HASH";

    /**
     * Max number of containers expanded concurrently. Configured to be half the
     * number of workers or external parsing processes if enabled. See
     * https://github.com/sepinf-inc/IPED/issues/1358
     */
    private static int max_expanding_containers;

    public static AtomicLong totalText = new AtomicLong();
    private static final Map<String, Long> timesPerParser = new HashMap<String, Long>();

    private static Map<Integer, ZipBombStats> zipBombStatsMap = new ConcurrentHashMap<>();
    private static final Set<MediaType> typesToCheckZipBomb = getTypesToCheckZipbomb();

    private static AtomicInteger containersBeingExpanded = new AtomicInteger();
    private static AtomicBoolean tikaSAXPoolSizeSet = new AtomicBoolean(false);

    private CategoryToExpandConfig expandConfig;
    private ParsingTaskConfig parsingConfig;

    private IItem evidence;
    private ParseContext context;
    private boolean extractEmbedded;
    private volatile ParsingReader reader;
    private String firstParentPath = null;
    private volatile long subitemsTime;
    private Map<Object, ParentInfo> idToItemMap = new HashMap<>();
    private int numSubitems = 0;
    private StandardParser autoParser;
    private long minItemSizeToFragment;

    private static Set<MediaType> getTypesToCheckZipbomb() {
        HashSet<MediaType> set = new HashSet<>();
        set.addAll(PackageParser.SUPPORTED_TYPES);
        set.add(SevenZipParser.RAR);
        return set;
    }

    // this must be static or moved to its own class, see #539
    private static class ZipBombStats {

        private Long itemSize;
        private long childrenSize = 0;

        private ZipBombStats(Long itemSize) {
            this.itemSize = itemSize;
        }
    }

    public ParsingTask() {
        // no op
    }

    public ParsingTask(IItem evidence, StandardParser parser) {
        this.evidence = evidence;
        this.autoParser = parser;
    }

    public ParsingTask(Worker worker, StandardParser parser) {
        this.setWorker(worker);
        this.autoParser = parser;
    }

    @Override
    public boolean isEnabled() {
        return parsingConfig.isEnabled();
    }

    public ParseContext getTikaContext() {
        return getTikaContext(this.output, null);
    }

    public ParseContext getTikaContext(IPEDSource ipedsource) {
        return getTikaContext(ipedsource.getModuleDir(), ipedsource);
    }

    private ParseContext getTikaContext(File output, IPEDSource ipedsource) {
        // DEFINE CONTEXTO: PARSING RECURSIVO, ETC
        context = new ParseContext();
        context.set(Parser.class, this.autoParser);
        context.set(ICaseData.class, caseData);

        ItemInfo itemInfo = ItemInfoFactory.getItemInfo(evidence);
        context.set(ItemInfo.class, itemInfo);
        context.set(OCROutputFolder.class, new OCROutputFolder(output));

        if (CarverTask.ignoreCorrupted && caseData != null && !caseData.isIpedReport()) {
            context.set(IgnoreCorruptedCarved.class, new IgnoreCorruptedCarved());
        }

        // Tratamento p/ acentos de subitens de ZIP
        context.set(ArchiveStreamFactory.class, new ArchiveStreamFactory("Cp850")); //$NON-NLS-1$
        // Indexa conteudo de todos os elementos de HTMLs, como script, etc
        context.set(HtmlMapper.class, IdentityHtmlMapper.INSTANCE);

        context.set(IStreamSource.class, evidence);
        context.set(IItemReader.class, evidence);
        if (ipedsource != null) {
            context.set(IItemSearcher.class, new ItemSearcher(ipedsource));
        } else {
            context.set(IItemSearcher.class, (IItemSearcher) caseData.getCaseObject(IItemSearcher.class.getName()));
        }

        extractEmbedded = expandConfig.isToBeExpanded(itemInfo.getCategories()) || isToAlwaysExpand(caseData, evidence);
        if (extractEmbedded) {
            context.set(EmbeddedDocumentExtractor.class, this);
        } else
            context.set(EmbeddedDocumentExtractor.class, new EmbeddedDocumentParser(context));

        return context;
    }

    public void setExtractEmbedded(boolean extractEmbedded) {
        this.extractEmbedded = extractEmbedded;
    }

    private void fillMetadata(IItem evidence) {
        fillMetadata(evidence, evidence.getMetadata());
    }

    public static void fillMetadata(IItem evidence, Metadata metadata) {
        Long len = evidence.getLength();
        if (len != null)
            metadata.set(Metadata.CONTENT_LENGTH, len.toString());
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, evidence.getName());
        if (evidence.getMediaType() != null) {
            metadata.set(Metadata.CONTENT_TYPE, evidence.getMediaType().toString());
            metadata.set(StandardParser.INDEXER_CONTENT_TYPE, evidence.getMediaType().toString());
        }
        if (evidence.isTimedOut()) {
            metadata.set(StandardParser.INDEXER_TIMEOUT, "true"); //$NON-NLS-1$
        }
    }

    private static boolean isToAlwaysExpand(CaseData caseData, IItem item) {
        if (caseData != null && caseData.isIpedReport()) {
            return false;
        }
        return WhatsAppParser.WA_USER_PLIST.equals(item.getMediaType())
                || TelegramParser.TELEGRAM_USER_CONF.equals(item.getMediaType());
    }

    @SuppressWarnings("resource")
    private void setEmptyTextCache(IItem evidence) {
        ((Item) evidence).setParsedTextCache(new TextCache());
    }

    public void process(IItem evidence) throws Exception {

        long start = System.nanoTime() / 1000;

        fillMetadata(evidence);

        Parser parser = autoParser.getLeafParser(evidence.getMetadata());
        if (parser instanceof EmptyParser) {
            setEmptyTextCache(evidence);
            return;
        }

        if (((Item) evidence).getTextCache() == null
                && ((evidence.getLength() == null || evidence.getLength() < minItemSizeToFragment)
                        || StandardParser.isSpecificParser(parser))) {
            ParsingTask task = null;
            try {
                task = new ParsingTask(worker, autoParser);
                task.parsingConfig = this.parsingConfig;
                task.expandConfig = this.expandConfig;
                task.safeProcess(evidence);

            } finally {
                String parserName = getParserName(parser, evidence.getMetadata().get(Metadata.CONTENT_TYPE));
                long st = task == null ? 0 : task.subitemsTime;
                long diff = System.nanoTime() / 1000 - start;
                if (diff < st) {
                    LOGGER.warn("{} Negative Parsing Time: {} {} Diff={} SubItemsTime={}",
                            Thread.currentThread().getName(), evidence.getPath(), parserName, diff, st);
                }
                synchronized (timesPerParser) {
                    timesPerParser.merge(parserName, diff - st, Long::sum);
                }
            }

        }

    }

    private String getParserName(Parser parser, String contentType) {
        if (parser instanceof ExternalParser)
            return ((ExternalParser) parser).getParserName();
        else if (parser instanceof PythonParser)
            return ((PythonParser) parser).getName(contentType);
        else if (parser instanceof MultipleParser)
            return ((MultipleParser) parser).getParserName();
        else
            return parser.getClass().getSimpleName();
    }

    public static boolean hasSpecificParser(StandardParser autoParser, IItem evidence) {
        return autoParser.hasSpecificParser(evidence.getMetadata());
    }

    private void safeProcess(IItem evidence) throws Exception {

        this.evidence = evidence;

        context = getTikaContext();

        if (this.extractEmbedded) {
            if (containersBeingExpanded.incrementAndGet() > max_expanding_containers) {
                containersBeingExpanded.decrementAndGet();
                super.reEnqueueItem(evidence);
                return;
            }
            // Don't expand subitem if its hash is equal to parent container hash, could lead to infinite recursion.
            // See https://github.com/sepinf-inc/IPED/issues/1814
            if (evidence.isSubItem() && evidence.getHash() != null && evidence.getHash().equals(evidence.getTempAttribute(PARENT_CONTAINER_HASH))) {
                return;
            }
        }

        TikaInputStream tis = null;
        try {
            tis = evidence.getTikaStream();

        } catch (IOException e) {
            LOGGER.warn("{} Error opening: {} {}", Thread.currentThread().getName(), evidence.getPath(), e.toString()); //$NON-NLS-1$
            if (this.extractEmbedded) {
                containersBeingExpanded.decrementAndGet();
            }
            return;
        }

        if (evidence.getHashValue() != null && evidence.getLength() != null && evidence.getLength() > 0) {
            try {
                File thumbFile = getThumbFile(evidence);
                if (!hasThumb(evidence, thumbFile)) {
                    context.set(ComputeThumb.class, new ComputeThumb());
                }
            } catch (Exception e1) {
                LOGGER.warn("Error checking item thumbnail: " + evidence.toString(), e1);
            }
        }

        Metadata metadata = evidence.getMetadata();

        if (typesToCheckZipBomb.contains(evidence.getMediaType())) {
            zipBombStatsMap.put(evidence.getId(), new ZipBombStats(evidence.getLength()));
        }

        try {
            reader = new ParsingReader(this.autoParser, tis, metadata, context);
            reader.startBackgroundParsing();

            TextCache textCache = new TextCache();
            textCache.setEnableDiskCache(parsingConfig.isStoreTextCacheOnDisk());
            char[] cbuf = new char[128 * 1024];
            int len = 0;
            while ((len = reader.read(cbuf)) != -1 && !Thread.currentThread().isInterrupted()) {
                textCache.write(cbuf, 0, len);
                // if(metadata.get(IndexerDefaultParser.PARSER_EXCEPTION) != null)
                // break;
            }

            ((Item) evidence).setParsedTextCache(textCache);
            evidence.setParsed(true);
            totalText.addAndGet(textCache.getSize());

        } catch (IOException e) {
            if (e.toString().contains("Write end dead"))
                LOGGER.error("{} Parsing thread ended without closing pipedWriter {} ({} bytes)", //$NON-NLS-1$
                        Thread.currentThread().getName(), evidence.getPath(), evidence.getLength());
            else
                throw e;

        } finally {
            // IOUtil.closeQuietly(tis);
            if (this.extractEmbedded) {
                containersBeingExpanded.decrementAndGet();
            }
            IOUtil.closeQuietly(reader);
            if (numSubitems > 0) {
                evidence.setExtraAttribute(NUM_SUBITEMS, numSubitems);
            }
            handleMetadata(evidence);
        }

    }

    private final void handleMetadata(IItem evidence) {
        // Ajusta metadados:
        Metadata metadata = evidence.getMetadata();
        if (metadata.get(StandardParser.ENCRYPTED_DOCUMENT) != null) {
            evidence.setExtraAttribute(ParsingTask.ENCRYPTED, "true"); //$NON-NLS-1$
            metadata.remove(StandardParser.ENCRYPTED_DOCUMENT);
        }

        String value = metadata.get(OCRParser.OCR_CHAR_COUNT);
        if (value != null) {
            int charCount = Integer.parseInt(value);
            evidence.setExtraAttribute(OCRParser.OCR_CHAR_COUNT, charCount);
            metadata.remove(OCRParser.OCR_CHAR_COUNT);
            if (charCount >= 100 && evidence.getMediaType().getType().equals("image")) { //$NON-NLS-1$
                evidence.setCategory(SetCategoryTask.SCANNED_CATEGORY);
            }
        }

        String base64Thumb = metadata.get(ExtraProperties.THUMBNAIL_BASE64);
        if (base64Thumb != null) {
            metadata.remove(ExtraProperties.THUMBNAIL_BASE64);
            evidence.setThumb(Base64.getDecoder().decode(base64Thumb));
            try {
                if (evidence.getHash() != null) {
                    File thumbFile = getThumbFile(evidence);
                    saveThumb(evidence, thumbFile);
                }
            } catch (Throwable t) {
                LOGGER.warn("Error saving thumb of " + evidence.toString(), t);
            } finally {
                updateHasThumb(evidence);
            }
        }

        String prevMediaType = evidence.getMediaType().toString();
        String parsedMediaType = metadata.get(StandardParser.INDEXER_CONTENT_TYPE);
        if (!prevMediaType.equals(parsedMediaType)) {
            MediaType mediaType = MediaType.parse(parsedMediaType);
            if (mediaType != null) {
                evidence.setMediaType(mediaType);
            }
        }

        if (Boolean.valueOf(metadata.get(BasicProps.HASCHILD))) {
            evidence.setHasChildren(true);
        }
        metadata.remove(BasicProps.HASCHILD);

        String compressRatio = evidence.getMetadata().get(EntropyTask.COMPRESS_RATIO);
        if (compressRatio != null) {
            evidence.getMetadata().remove(EntropyTask.COMPRESS_RATIO);
            evidence.setExtraAttribute(EntropyTask.COMPRESS_RATIO, Double.valueOf(compressRatio));
        }
        
        if (MediaTypes.isInstanceOf(evidence.getMediaType(), MediaTypes.UFED_MESSAGE_MIME)) {
            evidence.getMetadata().set(ExtraProperties.PARENT_VIEW_POSITION, String.valueOf(evidence.getId()));
        }

    }

    @Override
    public boolean shouldParseEmbedded(Metadata subitemMeta) {

        // do not extract images from html generated previews
        if (evidence != null && !MetadataUtil.isHtmlMediaType(evidence.getMediaType())
                && MetadataUtil.isHtmlSubType(evidence.getMediaType())) {
            String type = subitemMeta == null ? null : subitemMeta.get(Metadata.CONTENT_TYPE);
            if (type != null && type.startsWith("image")) //$NON-NLS-1$
                return false;
        }
        return true;
    }

    private String removePathPrefix(String name, boolean hasTitle) {
        if (!hasTitle) {
            int i = name.lastIndexOf('/');
            if (i != -1) {
                name = name.substring(i + 1);
            }
        }
        return name;
    }

    @Override
    public void parseEmbedded(InputStream inputStream, ContentHandler handler, Metadata metadata, boolean outputHtml)
            throws SAXException, IOException {

        if (!this.shouldParseEmbedded(metadata)) {
            return;
        }

        TemporaryResources tmp = new TemporaryResources();
        String subitemPath = null;
        try {
            ItemInfo itemInfo = context.get(ItemInfo.class);
            itemInfo.incChild();

            NameTitle nameTitle = EmbeddedDocumentParser.getNameTitle(metadata, itemInfo.getChild());
            boolean hasTitle = nameTitle.hasTitle;
            String name = removePathPrefix(nameTitle.name, hasTitle);

            String parentPath = itemInfo.getPath();
            if (firstParentPath == null) {
                firstParentPath = parentPath;
            }

            String parentId = metadata.get(ExtraProperties.PARENT_VIRTUAL_ID);
            metadata.remove(ExtraProperties.PARENT_VIRTUAL_ID);
            ParentInfo parentInfo = null;
            if (parentId != null)
                parentInfo = idToItemMap.get(parentId);
            if (parentInfo == null && context.get(EmbeddedParent.class) != null)
                parentInfo = new ParentInfo((IItem) context.get(EmbeddedParent.class).getObj());
            if (parentInfo == null)
                parentInfo = new ParentInfo(evidence);

            if (parentInfo.getId() != evidence.getId()) {
                parentPath = parentInfo.getPath();
                subitemPath = parentPath + "/" + name; //$NON-NLS-1$
            } else {
                subitemPath = parentPath + ">>" + name; //$NON-NLS-1$
            }

            Item subItem = new Item();
            subItem.setPath(subitemPath);
            subItem.setSubitemId(itemInfo.getChild());
            context.set(EmbeddedItem.class, new EmbeddedItem(subItem));

            subItem.setExtraAttribute(IndexItem.PARENT_TRACK_ID, parentInfo.getTrackId());
            subItem.setExtraAttribute(IndexItem.CONTAINER_TRACK_ID, Util.getTrackID(evidence));

            String embeddedPath = subitemPath.replace(firstParentPath + ">>", ""); //$NON-NLS-1$ //$NON-NLS-2$
            char[] nameChars = (embeddedPath + "\n\n").toCharArray(); //$NON-NLS-1$
            handler.characters(nameChars, 0, nameChars.length);

            if (!extractEmbedded) {
                return;
            }

            Integer depth = (Integer) evidence.getExtraAttribute(SUBITEM_DEPTH);
            if (depth == null)
                depth = 0;
            if (++depth > MAX_SUBITEM_DEPTH) {
                throw new ZipBombException(
                        "Max subitem depth of " + MAX_SUBITEM_DEPTH + " reached, possible zip bomb detected."); //$NON-NLS-1$ //$NON-NLS-2$
            }
            subItem.setExtraAttribute(SUBITEM_DEPTH, depth);

            // root has children
            evidence.setHasChildren(true);

            // see https://github.com/sepinf-inc/IPED/issues/1814
            subItem.setTempAttribute(PARENT_CONTAINER_HASH, evidence.getHash());

            // protection for future concurrent access, see #794
            metadata = new SyncMetadata(metadata);
            subItem.setMetadata(metadata);

            boolean updateInputStream = false;
            String contentTypeStr = metadata.get(StandardParser.INDEXER_CONTENT_TYPE);
            if (contentTypeStr != null) {
                MediaType type = MediaType.parse(contentTypeStr);
                subItem.setMediaType(type);
                if (caseData.containsReport() && MediaTypes.isMetadataEntryType(type)) {
                    subItem.setInputStreamFactory(new MetadataInputStreamFactory(subItem.getMetadata(), true));
                    metadata.remove(BasicProps.LENGTH);
                    if (inputStream == null || inputStream instanceof EmptyInputStream) {
                        updateInputStream = true;
                    }
                }
            }

            subItem.setName(name);
            if (hasTitle) {
                subItem.setExtension(""); //$NON-NLS-1$
            }

            subItem.setParent(parentInfo);

            // sometimes do not work, because parent may be already processed and
            // stored in database/index so setting it later has no effect
            // parent.setHasChildren(true);

            // parsers should set this property to let created items be displayed in file
            // tree
            if (Boolean.valueOf(metadata.get(BasicProps.HASCHILD))) {
                subItem.setHasChildren(true);
            }
            metadata.remove(BasicProps.HASCHILD);

            subItem.setHash(metadata.get(BasicProps.HASH));
            metadata.remove(BasicProps.HASH);

            Integer attachCount = metadata.getInt(ExtraProperties.MESSAGE_ATTACHMENT_COUNT);
            if (attachCount != null && attachCount > 0)
                subItem.setHasChildren(true);

            // indica se o conteiner tem subitens (mais específico que filhos genéricos)
            evidence.setExtraAttribute(HAS_SUBITEM, "true"); //$NON-NLS-1$

            if (Boolean.valueOf(metadata.get(ExtraProperties.EMBEDDED_FOLDER))) {
                subItem.setIsDir(true);
            }
            metadata.remove(ExtraProperties.EMBEDDED_FOLDER);

            subItem.setCreationDate(metadata.getDate(TikaCoreProperties.CREATED));
            subItem.setModificationDate(metadata.getDate(TikaCoreProperties.MODIFIED));
            subItem.setAccessDate(metadata.getDate(ExtraProperties.ACCESSED));

            removeMetadataAndDuplicates(metadata, TikaCoreProperties.CREATED);
            removeMetadataAndDuplicates(metadata, TikaCoreProperties.MODIFIED);
            removeMetadataAndDuplicates(metadata, ExtraProperties.ACCESSED);

            subItem.setDeleted(parentInfo.isDeleted());
            if (Boolean.valueOf(metadata.get(ExtraProperties.DELETED))) {
                subItem.setDeleted(true);
            }
            metadata.remove(ExtraProperties.DELETED);

            if (Boolean.valueOf(metadata.get(ExtraProperties.DECODED_DATA))) {
                subItem.setExtraAttribute(ExtraProperties.DECODED_DATA, true);
            }
            metadata.remove(ExtraProperties.DECODED_DATA);

            // causa problema de subitens corrompidos de zips carveados serem apagados,
            // mesmo sendo referenciados por outros subitens
            // subItem.setCarved(parent.isCarved());
            subItem.setSubItem(true);
            subItem.setSumVolume(false);

            InputStream is = !updateInputStream ? inputStream : subItem.getSeekableInputStream();
            try {
                ExportFileTask extractor = new ExportFileTask();
                extractor.setWorker(worker);
                extractor.extractFile(is, subItem, evidence.getLength());
            } finally {
                if (updateInputStream) {
                    IOUtil.closeQuietly(is);
                }
            }

            checkRecursiveZipBomb(subItem);

            if ("".equals(metadata.get(BasicProps.LENGTH))) {
                subItem.setLength(null);
            }

            // subitem is populated, store its info now
            String embeddedId = metadata.get(ExtraProperties.ITEM_VIRTUAL_ID);
            metadata.remove(ExtraProperties.ITEM_VIRTUAL_ID);

            // pausa contagem de timeout do pai antes de extrair e processar subitem
            if (reader.setTimeoutPaused(true)) {
                long start = System.nanoTime() / 1000;
                try {

                    ProcessTime time = ProcessTime.AUTO;

                    worker.processNewItem(subItem, time);
                    Statistics.get().incSubitemsDiscovered();
                    numSubitems++;

                } finally {
                    // Store time spent on subitems processing
                    subitemsTime += System.nanoTime() / 1000 - start;

                    // despausa contador de timeout do pai somente após processar subitem
                    reader.setTimeoutPaused(false);

                    // must do this after adding subitem to queue
                    if (embeddedId != null) {
                        idToItemMap.put(embeddedId, new ParentInfo(subItem));
                    }
                }
            }

        } catch (SAXException e) {
            // TODO Provavelmente PipedReader foi interrompido, interrompemos
            // aqui tb, deve ser melhorado...
            if (e.toString().contains("Error writing")) { //$NON-NLS-1$
                Thread.currentThread().interrupt();
            }

            LOGGER.warn("{} SAX error while extracting subitem {}\t\t{}", Thread.currentThread().getName(), subitemPath, //$NON-NLS-1$
                    e.toString());
            LOGGER.debug("SAX error extracting subitem " + subitemPath, (Throwable) e);

        } catch (ZipBombException e) {
            throw e;

        } catch (Exception e) {
            LOGGER.warn("{} Error while extracting subitem {}\t\t{}", Thread.currentThread().getName(), subitemPath, //$NON-NLS-1$
                    e.toString());
            LOGGER.debug("Error extracting subitem " + subitemPath, (Throwable) e);

        } finally {
            tmp.close();
        }

    }

    private void checkRecursiveZipBomb(Item subItem) throws ZipBombException {
        ZipBombException zipBombException = null;
        for (Integer id : subItem.getParentIds()) {
            ZipBombStats stats = zipBombStatsMap.get(id);
            if (stats == null) {
                continue;
            }
            if (subItem.getLength() != null) {
                synchronized (stats) {
                    stats.childrenSize += subItem.getLength();
                }
            }
            if (zipBombException == null && ZipBombException.isZipBomb(stats.itemSize, stats.childrenSize)) {
                zipBombException = new ZipBombException("Possible zipBomb detected: id=" + id + " size="
                        + stats.itemSize + " childrenSize=" + stats.childrenSize);
            }
        }
        if (zipBombException != null) {
            // dispose now because this item will not be added to processing queue
            subItem.dispose();
            throw zipBombException;
        }
    }

    private static void removeMetadataAndDuplicates(Metadata metadata, Property prop) {
        metadata.remove(prop.getName());
        Property[] props = prop.getSecondaryExtractProperties();
        if (props != null)
            for (Property p : props)
                metadata.remove(p.getName());
    }

    public List<Configurable<?>> getConfigurables() {
        return Arrays.asList(new ParsingTaskConfig(), new CategoryToExpandConfig(), new OCRConfig(),
                new ParsersConfig(), new ExternalParsersConfig());
    }

    @Override
    public void init(ConfigurationManager configurationManager) {

        parsingConfig = configurationManager.findObject(ParsingTaskConfig.class);
        expandConfig = configurationManager.findObject(CategoryToExpandConfig.class);

        SplitLargeBinaryConfig splitConfig = configurationManager.findObject(SplitLargeBinaryConfig.class);
        minItemSizeToFragment = splitConfig.getMinItemSizeToFragment();

        setupParsingOptions(configurationManager);

        this.autoParser = new StandardParser();

    }

    public static void setupParsingOptions(ConfigurationManager configurationManager) {

        ParsingTaskConfig parsingConfig = configurationManager.findObject(ParsingTaskConfig.class);
        ParsersConfig parserConfig = configurationManager.findObject(ParsersConfig.class);
        System.setProperty("tika.config", parserConfig.getTmpConfigFile().getAbsolutePath());

        // we have seen very large records in valid docs
        org.apache.poi.hpsf.CodePageString.setMaxRecordLength(512_000);

        // heavy Tika configuration
        if (!tikaSAXPoolSizeSet.getAndSet(true)) {
            try {
                XMLReaderUtils.setPoolSize(Runtime.getRuntime().availableProcessors());
            } catch (TikaException e) {
                e.printStackTrace();
            }
        }

        // most options below are set using sys props because they are also used by
        // child external processes

        if (parsingConfig.isEnableExternalParsing()) {
            ForkParser.setEnabled(true);
            PluginConfig pluginConfig = configurationManager.findObject(PluginConfig.class);
            ForkParser.setPluginDir(pluginConfig.getPluginFolder().getAbsolutePath());
            ForkParser.setPoolSize(parsingConfig.getNumExternalParsers());
            max_expanding_containers = parsingConfig.getNumExternalParsers() / 2;
            if (max_expanding_containers == 0) {
                // Abort. We must have at least 2 external parsing processes, 1 causes deadlock.
                throw new IPEDException("You must have a minimum of 2 external parsing processes! Adjust the '" + ParsingTaskConfig.NUM_EXTERNAL_PARSERS + "' option.");
            }
            ForkParser.setServerMaxHeap(parsingConfig.getExternalParsingMaxMem());
        } else {
            LocalConfig localConfig = configurationManager.findObject(LocalConfig.class);
            max_expanding_containers = Math.max(localConfig.getNumThreads() / 2, 1);
        }

        String appRoot = Configuration.getInstance().appRoot;
        ExternalParsersConfig extParsersConfig = configurationManager.findObject(ExternalParsersConfig.class);
        System.setProperty(ExternalParser.EXTERNAL_PARSERS_ROOT, appRoot);
        System.setProperty(ExternalParsersFactory.EXTERNAL_PARSER_PROP, extParsersConfig.getTmpConfigFilePath());
        System.setProperty(StandardParser.FALLBACK_PARSER_PROP, String.valueOf(parsingConfig.isParseUnknownFiles()));
        System.setProperty(StandardParser.ERROR_PARSER_PROP, String.valueOf(parsingConfig.isParseCorruptedFiles()));
        System.setProperty(StandardParser.ENTROPY_TEST_PROP,
                String.valueOf(configurationManager.getEnableTaskProperty(EntropyTask.ENABLE_PARAM)));
        System.setProperty(PDFTextParser.SORT_PDF_CHARS, String.valueOf(parsingConfig.isSortPDFChars()));
        System.setProperty(PDFTextParser.PROCESS_INLINE_IMAGES, String.valueOf(parsingConfig.isProcessImagesInPDFs()));
        System.setProperty(RawStringParser.MIN_STRING_SIZE, String.valueOf(parsingConfig.getMinRawStringSize()));
        System.setProperty(PythonParser.PYTHON_PARSERS_FOLDER, appRoot + "/scripts/parsers");

        if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
            System.setProperty(OCRParser.TOOL_PATH_PROP, appRoot + "/tools/tesseract"); //$NON-NLS-1$
            System.setProperty(EDBParser.TOOL_PATH_PROP, appRoot + "/tools/esedbexport/"); //$NON-NLS-1$
            System.setProperty(LibpffPSTParser.TOOL_PATH_PROP, appRoot + "/tools/pffexport/"); //$NON-NLS-1$
            System.setProperty(IndexDatParser.TOOL_PATH_PROP, appRoot + "/tools/msiecfexport/"); //$NON-NLS-1$
        }

        System.setProperty(RegRipperParser.TOOL_PATH_PROP, appRoot + "/tools/regripper/"); //$NON-NLS-1$

        OCRConfig ocrConfig = configurationManager.findObject(OCRConfig.class);
        setupOCROptions(ocrConfig);

        // do not open extra processes for OCR if ForkParser is enabled
        String value = parsingConfig.isEnableExternalParsing() ? Boolean.FALSE.toString() : ocrConfig.getExternalPdfToImgConv();
        System.setProperty(PDFToImage.EXTERNAL_CONV_PROP, value);
    }

    private static void setupOCROptions(OCRConfig ocrConfig) {
        if (ocrConfig.isOCREnabled()) {
            System.setProperty(OCRParser.ENABLE_PROP, "true");
            System.setProperty(OCRParser.LANGUAGE_PROP, ocrConfig.getOcrLanguage());
            System.setProperty(OCRParser.SKIP_KNOWN_FILES_PROP, String.valueOf(ocrConfig.isSkipKnownFiles()));
            System.setProperty(OCRParser.MIN_SIZE_PROP, ocrConfig.getMinFileSize2OCR());
            System.setProperty(OCRParser.MAX_SIZE_PROP, ocrConfig.getMaxFileSize2OCR());
            System.setProperty(OCRParser.PAGE_SEGMODE_PROP, ocrConfig.getPageSegMode());
            System.setProperty(PDFToImage.RESOLUTION_PROP, ocrConfig.getPdfToImgResolution());
            System.setProperty(PDFToImage.PDFLIB_PROP, ocrConfig.getPdfToImgLib());
            System.setProperty(PDFToImage.EXTERNAL_CONV_MAXMEM_PROP, ocrConfig.getExternalConvMaxMem());
            System.setProperty(PDFTextParser.MAX_CHARS_TO_OCR, ocrConfig.getMaxPdfTextSize2OCR());
            System.setProperty(OCRParser.PROCESS_NON_STANDARD_FORMATS_PROP, ocrConfig.getProcessNonStandard());
            System.setProperty(OCRParser.MAX_CONV_IMAGE_SIZE_PROP, ocrConfig.getMaxConvImageSize());
        }
    }

    @Override
    public void finish() throws Exception {
        if (totalText != null) {
            LOGGER.info("Total extracted text size: " + totalText.get()); //$NON-NLS-1$
            WhatsAppParser.clearStaticResources();
        }
        totalText = null;
    }

    public static void copyTimesPerParser(Map<String,Long> dest) {
        dest.clear();
        synchronized (timesPerParser) {
            dest.putAll(timesPerParser);
        }
    }
}
