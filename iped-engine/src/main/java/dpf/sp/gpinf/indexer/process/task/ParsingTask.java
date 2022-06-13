/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de EvidÃªncias Digitais (IPED).
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
package dpf.sp.gpinf.indexer.process.task;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.fork.EmbeddedDocumentParser;
import org.apache.tika.fork.EmbeddedDocumentParser.NameTitle;
import org.apache.tika.fork.ForkParser2;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.ap.gpinf.telegramextractor.TelegramParser;
import dpf.inc.sepinf.python.PythonParser;
import dpf.mg.udi.gpinf.whatsappextractor.WhatsAppParser;
import dpf.sp.gpinf.carver.CarverTask;
import dpf.sp.gpinf.indexer.config.CategoryToExpandConfig;
import dpf.sp.gpinf.indexer.config.Configuration;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.config.ExternalParsersConfig;
import dpf.sp.gpinf.indexer.config.LocalConfig;
import dpf.sp.gpinf.indexer.config.OCRConfig;
import dpf.sp.gpinf.indexer.config.ParsersConfig;
import dpf.sp.gpinf.indexer.config.ParsingTaskConfig;
import dpf.sp.gpinf.indexer.config.PluginConfig;
import dpf.sp.gpinf.indexer.config.SplitLargeBinaryConfig;
import dpf.sp.gpinf.indexer.io.MetadataInputStreamFactory;
import dpf.sp.gpinf.indexer.io.ParsingReader;
import dpf.sp.gpinf.indexer.parsers.EDBParser;
import dpf.sp.gpinf.indexer.parsers.IndexDatParser;
import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.parsers.LibpffPSTParser;
import dpf.sp.gpinf.indexer.parsers.MultipleParser;
import dpf.sp.gpinf.indexer.parsers.OCRParser;
import dpf.sp.gpinf.indexer.parsers.PDFOCRTextParser;
import dpf.sp.gpinf.indexer.parsers.PackageParser;
import dpf.sp.gpinf.indexer.parsers.RawStringParser;
import dpf.sp.gpinf.indexer.parsers.RegistryParser;
import dpf.sp.gpinf.indexer.parsers.SevenZipParser;
import dpf.sp.gpinf.indexer.parsers.external.ExternalParser;
import dpf.sp.gpinf.indexer.parsers.external.ExternalParsersFactory;
import dpf.sp.gpinf.indexer.parsers.util.ComputeThumb;
import dpf.sp.gpinf.indexer.parsers.util.EmbeddedItem;
import dpf.sp.gpinf.indexer.parsers.util.EmbeddedParent;
import dpf.sp.gpinf.indexer.parsers.util.IgnoreCorruptedCarved;
import dpf.sp.gpinf.indexer.parsers.util.ItemInfo;
import dpf.sp.gpinf.indexer.parsers.util.MetadataUtil;
import dpf.sp.gpinf.indexer.parsers.util.OCROutputFolder;
import dpf.sp.gpinf.indexer.parsers.util.PDFToImage;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.process.Worker;
import dpf.sp.gpinf.indexer.process.Worker.ProcessTime;
import dpf.sp.gpinf.indexer.search.IPEDSource;
import dpf.sp.gpinf.indexer.search.ItemSearcher;
import dpf.sp.gpinf.indexer.tika.SyncMetadata;
import dpf.sp.gpinf.indexer.util.EmptyInputStream;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.ItemInfoFactory;
import dpf.sp.gpinf.indexer.util.ParentInfo;
import dpf.sp.gpinf.indexer.util.TextCache;
import dpf.sp.gpinf.indexer.util.Util;
import gpinf.dev.data.CaseData;
import gpinf.dev.data.Item;
import iped3.IItem;
import iped3.IItemBase;
import iped3.configuration.Configurable;
import iped3.exception.ZipBombException;
import iped3.io.IStreamSource;
import iped3.search.IItemSearcher;
import iped3.util.BasicProps;
import iped3.util.ExtraProperties;
import iped3.util.MediaTypes;

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

    public static AtomicInteger subitensDiscovered = new AtomicInteger();
    public static AtomicLong totalText = new AtomicLong();
    public static Map<String, AtomicLong> times = Collections.synchronizedMap(new TreeMap<String, AtomicLong>());

    private static Map<Integer, ZipBombStats> zipBombStatsMap = new ConcurrentHashMap<>();
    private static final Set<MediaType> typesToCheckZipBomb = getTypesToCheckZipbomb();

    private CategoryToExpandConfig expandConfig;
    private ParsingTaskConfig parsingConfig;

    private IItem evidence;
    private ParseContext context;
    private boolean extractEmbedded;
    private volatile ParsingReader reader;
    private String firstParentPath = null;
    private Map<Integer, Long> timeInDepth = new ConcurrentHashMap<>();
    private volatile int depth = 0;
    private Map<Object, ParentInfo> idToItemMap = new HashMap<>();
    private int numSubitems = 0;
    private IndexerDefaultParser autoParser;

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

    public ParsingTask(IItem evidence, IndexerDefaultParser parser) {
        this.evidence = evidence;
        this.autoParser = parser;
    }

    public ParsingTask(Worker worker, IndexerDefaultParser parser) {
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
        // we have seen very large records in valid docs
        org.apache.poi.hpsf.CodePageString.setMaxRecordLength(512_000);

        context.set(IStreamSource.class, evidence);
        context.set(IItemBase.class, evidence);
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
            metadata.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, evidence.getMediaType().toString());
        }
        if (evidence.isTimedOut()) {
            metadata.set(IndexerDefaultParser.INDEXER_TIMEOUT, "true"); //$NON-NLS-1$
        }
    }

    private static boolean isToAlwaysExpand(CaseData caseData, IItem item) {
        if (caseData != null && caseData.isIpedReport()) {
            return false;
        }
        return WhatsAppParser.WA_USER_PLIST.equals(item.getMediaType())
                || WhatsAppParser.WA_USER_XML.equals(item.getMediaType()) 
                || TelegramParser.TELEGRAM_USER_CONF.equals(item.getMediaType());
    }

    public static int getSubitensDiscovered() {
        return subitensDiscovered.get();
    }

    @SuppressWarnings("resource")
    private void setEmptyTextCache(IItem evidence) {
        ((Item) evidence).setParsedTextCache(new TextCache());
    }

	public void process(IItem evidence) throws IOException {

        long start = System.nanoTime() / 1000;

        fillMetadata(evidence);

        Parser parser = autoParser.getLeafParser(evidence.getMetadata());
        if (parser instanceof EmptyParser) {
            setEmptyTextCache(evidence);
            return;
        }

        String parserName = getParserName(parser, evidence.getMetadata().get(Metadata.CONTENT_TYPE));
        AtomicLong time = times.get(parserName);
        if (time == null) {
            time = new AtomicLong();
            times.put(parserName, time);
        }

        SplitLargeBinaryConfig splitConfig = ConfigurationManager.get()
                .findObject(SplitLargeBinaryConfig.class);
        if (((Item) evidence).getTextCache() == null
                && ((evidence.getLength() == null || evidence.getLength() < splitConfig.getMinItemSizeToFragment())
                        || IndexerDefaultParser.isSpecificParser(parser))) {
            try {
                depth++;
                ParsingTask task = new ParsingTask(worker, autoParser);
                task.parsingConfig = this.parsingConfig;
                task.expandConfig = this.expandConfig;
                task.depth = depth;
                task.timeInDepth = timeInDepth;
                task.safeProcess(evidence);

            } finally {
                depth--;
                long diff = System.nanoTime() / 1000 - start;
                Long subitemsTime = timeInDepth.remove(depth + 1);
                if (subitemsTime == null)
                    subitemsTime = 0L;
                time.addAndGet(diff - subitemsTime);
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

    public static boolean hasSpecificParser(IndexerDefaultParser autoParser, IItem evidence) {
        return autoParser.hasSpecificParser(evidence.getMetadata());
    }

    private void safeProcess(IItem evidence) throws IOException {

        this.evidence = evidence;

        TikaInputStream tis = null;
        try {
            tis = evidence.getTikaStream();

        } catch (IOException e) {
            LOGGER.warn("{} Error opening: {} {}", Thread.currentThread().getName(), evidence.getPath(), e.toString()); //$NON-NLS-1$
            return;
        }

        context = getTikaContext();
        if (evidence.getHashValue() != null) {
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

        reader = new ParsingReader(this.autoParser, tis, metadata, context);
        reader.startBackgroundParsing();

        try {
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
            reader.close();
            if (numSubitems > 0) {
                evidence.setExtraAttribute(NUM_SUBITEMS, numSubitems);
            }
            handleMetadata(evidence);
        }

    }

    private final void handleMetadata(IItem evidence) {
        // Ajusta metadados:
        Metadata metadata = evidence.getMetadata();
        if (metadata.get(IndexerDefaultParser.ENCRYPTED_DOCUMENT) != null) {
            evidence.setExtraAttribute(ParsingTask.ENCRYPTED, "true"); //$NON-NLS-1$
            metadata.remove(IndexerDefaultParser.ENCRYPTED_DOCUMENT);
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
                File thumbFile = getThumbFile(evidence);
                saveThumb(evidence, thumbFile);
            } catch (Throwable t) {
                LOGGER.warn("Error saving thumb of " + evidence.toString(), t);
            } finally {
                updateHasThumb(evidence);
            }
        }

        String prevMediaType = evidence.getMediaType().toString();
        String parsedMediaType = metadata.get(IndexerDefaultParser.INDEXER_CONTENT_TYPE);
        if (!prevMediaType.equals(parsedMediaType)) {
            evidence.setMediaType(MediaType.parse(parsedMediaType));
        }

        if (Boolean.valueOf(metadata.get(BasicProps.HASCHILD))) {
            metadata.remove(BasicProps.HASCHILD);
            evidence.setHasChildren(true);
        }

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

            // protection for future concurrent access, see #794
            metadata = new SyncMetadata(metadata);
            subItem.setMetadata(metadata);

            boolean updateInputStream = false;
            String contentTypeStr = metadata.get(IndexerDefaultParser.INDEXER_CONTENT_TYPE);
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
            if (metadata.get(BasicProps.HASCHILD) != null) {
                metadata.remove(BasicProps.HASCHILD);
                subItem.setHasChildren(true);
            }

            subItem.setHash(metadata.get(BasicProps.HASH));
            metadata.remove(BasicProps.HASH);

            Integer attachCount = metadata.getInt(ExtraProperties.MESSAGE_ATTACHMENT_COUNT);
            if (attachCount != null && attachCount > 0)
                subItem.setHasChildren(true);

            // indica se o conteiner tem subitens (mais específico que filhos genéricos)
            evidence.setExtraAttribute(HAS_SUBITEM, "true"); //$NON-NLS-1$

            if (metadata.get(ExtraProperties.EMBEDDED_FOLDER) != null) {
                metadata.remove(ExtraProperties.EMBEDDED_FOLDER);
                subItem.setIsDir(true);
            }

            subItem.setCreationDate(metadata.getDate(TikaCoreProperties.CREATED));
            subItem.setModificationDate(metadata.getDate(TikaCoreProperties.MODIFIED));
            subItem.setAccessDate(metadata.getDate(ExtraProperties.ACCESSED));

            removeMetadataAndDuplicates(metadata, TikaCoreProperties.CREATED);
            removeMetadataAndDuplicates(metadata, TikaCoreProperties.MODIFIED);
            removeMetadataAndDuplicates(metadata, ExtraProperties.ACCESSED);

            subItem.setDeleted(parentInfo.isDeleted());
            if (metadata.get(ExtraProperties.DELETED) != null) {
                metadata.remove(ExtraProperties.DELETED);
                subItem.setDeleted(true);
            }

            if (Boolean.valueOf(metadata.get(ExtraProperties.DECODED_DATA))) {
                subItem.setExtraAttribute(ExtraProperties.DECODED_DATA, true);
                metadata.remove(ExtraProperties.DECODED_DATA);
            }

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
                try {
                    long start = System.nanoTime() / 1000;
                    // If external parsing is on, items are sent to queue to avoid deadlock
                    // ProcessTime time = ForkParser2.enabled ? ProcessTime.LATER :
                    // ProcessTime.AUTO;

                    // Unfortunatelly AUTO value causes issues with JEP (python lib) too,
                    // because items could be processed by Workers in a different thread (parsing
                    // thread), instead of Worker default thread. So we are using LATER, which sends
                    // items to queue and is a bit slower when expanding lots of containers at the
                    // same time (causes a lot of IO instead mixing IO with CPU used to process
                    // subitems)
                    ProcessTime time = ProcessTime.LATER;
                    worker.processNewItem(subItem, time);
                    subitensDiscovered.incrementAndGet();
                    numSubitems++;

                    long diff = (System.nanoTime() / 1000) - start;
                    Long prevTime = timeInDepth.get(depth);
                    if (prevTime == null)
                        prevTime = 0L;
                    timeInDepth.put(depth, prevTime + diff);

                } finally {
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

        setupParsingOptions(configurationManager);

        this.autoParser = new IndexerDefaultParser();

    }

    public static void setupParsingOptions(ConfigurationManager configurationManager) {

        ParsingTaskConfig parsingConfig = configurationManager.findObject(ParsingTaskConfig.class);
        ParsersConfig parserConfig = configurationManager.findObject(ParsersConfig.class);
        System.setProperty("tika.config", parserConfig.getTmpConfigFile().getAbsolutePath());

        // most options below are set using sys props because they are also used by
        // child external processes

        if (parsingConfig.isEnableExternalParsing()) {
            ForkParser2.setEnabled(true);
            PluginConfig pluginConfig = configurationManager.findObject(PluginConfig.class);
            ForkParser2.setPluginDir(pluginConfig.getPluginFolder().getAbsolutePath());
            ForkParser2.setPoolSize(parsingConfig.getNumExternalParsers());
            ForkParser2.setServerMaxHeap(parsingConfig.getExternalParsingMaxMem());
            // do not open extra processes for OCR if ForkParser is enabled
            System.setProperty(PDFToImage.EXTERNAL_CONV_PROP, "false");
        }

        String appRoot = Configuration.getInstance().appRoot;
        ExternalParsersConfig extParsersConfig = configurationManager.findObject(ExternalParsersConfig.class);
        System.setProperty(ExternalParser.EXTERNAL_PARSERS_ROOT, appRoot);
        System.setProperty(ExternalParsersFactory.EXTERNAL_PARSER_PROP, extParsersConfig.getTmpConfigFilePath());
        System.setProperty(IndexerDefaultParser.FALLBACK_PARSER_PROP, String.valueOf(parsingConfig.isParseUnknownFiles()));
        System.setProperty(IndexerDefaultParser.ERROR_PARSER_PROP, String.valueOf(parsingConfig.isParseCorruptedFiles()));
        System.setProperty(IndexerDefaultParser.ENTROPY_TEST_PROP,
                String.valueOf(configurationManager.getEnableTaskProperty(EntropyTask.ENABLE_PARAM)));
        System.setProperty(PDFOCRTextParser.SORT_PDF_CHARS, String.valueOf(parsingConfig.isSortPDFChars()));
        System.setProperty(PDFOCRTextParser.PROCESS_INLINE_IMAGES, String.valueOf(parsingConfig.isProcessImagesInPDFs()));
        System.setProperty(RawStringParser.MIN_STRING_SIZE, String.valueOf(parsingConfig.getMinRawStringSize()));
        System.setProperty(PythonParser.PYTHON_PARSERS_FOLDER, appRoot + "/conf/parsers");

        if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
            System.setProperty(OCRParser.TOOL_PATH_PROP, appRoot + "/tools/tesseract"); //$NON-NLS-1$
            System.setProperty(EDBParser.TOOL_PATH_PROP, appRoot + "/tools/esedbexport/"); //$NON-NLS-1$
            System.setProperty(LibpffPSTParser.TOOL_PATH_PROP, appRoot + "/tools/pffexport/"); //$NON-NLS-1$
            System.setProperty(IndexDatParser.TOOL_PATH_PROP, appRoot + "/tools/msiecfexport/"); //$NON-NLS-1$
        }

        LocalConfig localConfig = configurationManager.findObject(LocalConfig.class);
        if (localConfig.getRegRipperFolder() != null) {
            System.setProperty(RegistryParser.TOOL_PATH_PROP, appRoot + "/" + localConfig.getRegRipperFolder()); //$NON-NLS-1$
        }

        setupOCROptions(configurationManager.findObject(OCRConfig.class));

    }

    private static void setupOCROptions(OCRConfig ocrConfig) {
        if (ocrConfig.isOCREnabled()) {
            System.setProperty(OCRParser.ENABLE_PROP, "true");
            System.setProperty(OCRParser.LANGUAGE_PROP, ocrConfig.getOcrLanguage());
            System.setProperty(OCRParser.MIN_SIZE_PROP, ocrConfig.getMinFileSize2OCR());
            System.setProperty(OCRParser.MAX_SIZE_PROP, ocrConfig.getMaxFileSize2OCR());
            System.setProperty(OCRParser.PAGE_SEGMODE_PROP, ocrConfig.getPageSegMode());
            System.setProperty(PDFToImage.RESOLUTION_PROP, ocrConfig.getPdfToImgResolution());
            System.setProperty(PDFToImage.PDFLIB_PROP, ocrConfig.getPdfToImgLib());
            System.setProperty(PDFToImage.EXTERNAL_CONV_PROP, ocrConfig.getExternalPdfToImgConv());
            System.setProperty(PDFToImage.EXTERNAL_CONV_MAXMEM_PROP, ocrConfig.getExternalConvMaxMem());
            System.setProperty(PDFOCRTextParser.MAX_CHARS_TO_OCR, ocrConfig.getMaxPdfTextSize2OCR());
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

}
