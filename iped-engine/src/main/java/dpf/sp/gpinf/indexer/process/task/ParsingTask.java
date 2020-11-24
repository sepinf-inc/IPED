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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
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
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.html.HtmlMapper;
import org.apache.tika.parser.html.IdentityHtmlMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.ap.gpinf.telegramextractor.TelegramParser;
import dpf.mg.udi.gpinf.whatsappextractor.WhatsAppParser;
import dpf.sp.gpinf.carver.CarverTask;
import dpf.sp.gpinf.indexer.config.AdvancedIPEDConfig;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.io.ParsingReader;
import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.parsers.OCRParser;
import dpf.sp.gpinf.indexer.parsers.external.ExternalParser;
import dpf.sp.gpinf.indexer.parsers.util.EmbeddedItem;
import dpf.sp.gpinf.indexer.parsers.util.EmbeddedParent;
import dpf.sp.gpinf.indexer.parsers.util.IgnoreCorruptedCarved;
import dpf.sp.gpinf.indexer.parsers.util.ItemInfo;
import dpf.sp.gpinf.indexer.parsers.util.MetadataUtil;
import dpf.sp.gpinf.indexer.parsers.util.OCROutputFolder;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.process.ItemSearcher;
import dpf.sp.gpinf.indexer.process.Worker;
import dpf.sp.gpinf.indexer.process.Worker.ProcessTime;
import dpf.sp.gpinf.indexer.search.IPEDSource;
import dpf.sp.gpinf.indexer.util.ItemInfoFactory;
import dpf.sp.gpinf.indexer.util.ParentInfo;
import dpf.sp.gpinf.indexer.util.TextCache;
import dpf.sp.gpinf.indexer.util.Util;
import gpinf.dev.data.Item;
import iped3.IItem;
import iped3.io.IItemBase;
import iped3.io.IStreamSource;
import iped3.search.IItemSearcher;
import iped3.util.BasicProps;
import iped3.util.ExtraProperties;

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
public class ParsingTask extends AbstractTask implements EmbeddedDocumentExtractor {

    private static Logger LOGGER = LoggerFactory.getLogger(ParsingTask.class);

    public static final String EXPAND_CONFIG = "CategoriesToExpand.txt"; //$NON-NLS-1$
    public static final String ENABLE_PARSING = "enableFileParsing"; //$NON-NLS-1$
    public static final String ENCRYPTED = "encrypted"; //$NON-NLS-1$
    public static final String HAS_SUBITEM = "hasSubitem"; //$NON-NLS-1$
    public static final String NUM_SUBITEMS = "numSubItems"; //$NON-NLS-1$

    private static final int MAX_SUBITEM_DEPTH = 100;
    private static final String SUBITEM_DEPTH = "subitemDepth"; //$NON-NLS-1$

    private static boolean expandContainers = false;
    private static boolean enableFileParsing = true;

    public static AtomicInteger subitensDiscovered = new AtomicInteger();
    private static HashSet<String> categoriesToExpand = new HashSet<String>();
    public static AtomicLong totalText = new AtomicLong();
    public static Map<String, AtomicLong> times = Collections.synchronizedMap(new TreeMap<String, AtomicLong>());

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

    public ParsingTask() {
        this.autoParser = new IndexerDefaultParser();
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
        return enableFileParsing;
    }

    public static void setExpandContainers(boolean enabled) {
        expandContainers = enabled;
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
        org.apache.poi.util.IOUtils.setByteArrayMaxOverride(-1);

        context.set(IStreamSource.class, evidence);
        context.set(IItemBase.class, evidence);
        if (ipedsource != null) {
            context.set(IItemSearcher.class, new ItemSearcher(ipedsource));
        } else {
            context.set(IItemSearcher.class, (IItemSearcher) caseData.getCaseObject(IItemSearcher.class.getName()));
        }

        extractEmbedded = isToBeExpanded(itemInfo.getCategories()) || isToAlwaysExpand(evidence);
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
        metadata.set(Metadata.RESOURCE_NAME_KEY, evidence.getName());
        if (evidence.getMediaType() != null) {
            metadata.set(Metadata.CONTENT_TYPE, evidence.getMediaType().toString());
            metadata.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, evidence.getMediaType().toString());
        }
        if (evidence.isTimedOut()) {
            metadata.set(IndexerDefaultParser.INDEXER_TIMEOUT, "true"); //$NON-NLS-1$
        }
    }

    public static void load(File file) throws FileNotFoundException, IOException {
        categoriesToExpand = new HashSet<String>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8")); //$NON-NLS-1$
        String line = reader.readLine();
        while ((line = reader.readLine()) != null) {
            if (line.trim().startsWith("#") || line.trim().isEmpty()) { //$NON-NLS-1$
                continue;
            }
            categoriesToExpand.add(line.trim());
        }
        reader.close();
    }

    private static boolean isToAlwaysExpand(IItem item) {
        return WhatsAppParser.WA_USER_PLIST.equals(item.getMediaType())
                || WhatsAppParser.WA_USER_XML.equals(item.getMediaType()) 
                || TelegramParser.TELEGRAM_USER_CONF.equals(item.getMediaType())
                || TelegramParser.TELEGRAM_DB_IOS.equals(item.getMediaType());
    }

    private static boolean isToBeExpanded(Collection<String> categories) {

        if (!expandContainers) {
            return false;
        }

        boolean result = false;
        for (String category : categories) {
            if (categoriesToExpand.contains(category)) {
                result = true;
                break;
            }
        }
        return result;
    }

    public static int getSubitensDiscovered() {
        return subitensDiscovered.get();
    }

    public void process(IItem evidence) throws IOException {

        if (!enableFileParsing) {
            return;
        }

        long start = System.nanoTime() / 1000;

        fillMetadata(evidence);

        Parser parser = autoParser.getLeafParser(evidence.getMetadata());

        AtomicLong time = times.get(getParserName(parser));
        if (time == null) {
            time = new AtomicLong();
            times.put(getParserName(parser), time);
        }

        AdvancedIPEDConfig advancedConfig = (AdvancedIPEDConfig) ConfigurationManager.getInstance()
                .findObjects(AdvancedIPEDConfig.class).iterator().next();
        if (((Item) evidence).getTextCache() == null
                && ((evidence.getLength() == null || evidence.getLength() < advancedConfig.getMinItemSizeToFragment())
                        || IndexerDefaultParser.isSpecificParser(parser))) {
            try {
                depth++;
                ParsingTask task = new ParsingTask(worker, autoParser);
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

    private String getParserName(Parser parser) {
        if (parser instanceof ExternalParser)
            return ((ExternalParser) parser).getParserName();
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
        Metadata metadata = evidence.getMetadata();

        reader = new ParsingReader(this.autoParser, tis, metadata, context);
        reader.startBackgroundParsing();

        try {
            AdvancedIPEDConfig advancedConfig = (AdvancedIPEDConfig) ConfigurationManager.getInstance()
                    .findObjects(AdvancedIPEDConfig.class).iterator().next();

            TextCache textCache = new TextCache();
            textCache.setEnableDiskCache(advancedConfig.isStoreTextCacheOnDisk());
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

    private static final void handleMetadata(IItem evidence) {
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

        String base64Thumb = metadata.get(ExtraProperties.USER_THUMB);
        if (base64Thumb != null) {
            evidence.setThumb(Base64.getDecoder().decode(base64Thumb));
            metadata.remove(ExtraProperties.USER_THUMB);
            evidence.setExtraAttribute(ImageThumbTask.HAS_THUMB, Boolean.TRUE.toString());
        }

        String hashSetStatus = metadata.get(KFFTask.KFF_STATUS);
        if (hashSetStatus != null) {
            evidence.setExtraAttribute(KFFTask.KFF_STATUS, hashSetStatus);
            metadata.remove(KFFTask.KFF_STATUS);
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

            Util.generatePersistentId(parentInfo.getPersistentId(), subItem);
            subItem.setExtraAttribute(IndexItem.CONTAINER_PERSISTENT_ID, Util.getPersistentId(evidence));

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
                throw new IOException(
                        "Max subitem depth of " + MAX_SUBITEM_DEPTH + "reached, possible zip bomb detected."); //$NON-NLS-1$ //$NON-NLS-2$
            }
            subItem.setExtraAttribute(SUBITEM_DEPTH, depth);

            // root has children
            evidence.setHasChildren(true);

            subItem.setMetadata(metadata);

            String contentTypeStr = metadata.get(IndexerDefaultParser.INDEXER_CONTENT_TYPE);
            if (contentTypeStr != null) {
                subItem.setMediaType(MediaType.parse(contentTypeStr));
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

            if (metadata.get(ExtraProperties.PST_EMAIL_HAS_ATTACHS) != null)
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

            // causa problema de subitens corrompidos de zips carveados serem apagados,
            // mesmo sendo referenciados por outros subitens
            // subItem.setCarved(parent.isCarved());
            subItem.setSubItem(true);
            subItem.setSumVolume(false);

            ExportFileTask extractor = new ExportFileTask();
            extractor.setWorker(worker);
            extractor.extractFile(inputStream, subItem, evidence.getLength());

            // subitem is populated, store its info now
            String embeddedId = metadata.get(ExtraProperties.ITEM_VIRTUAL_ID);
            metadata.remove(ExtraProperties.ITEM_VIRTUAL_ID);
            if (embeddedId != null)
                idToItemMap.put(embeddedId, new ParentInfo(subItem));

            // pausa contagem de timeout do pai antes de extrair e processar subitem
            if (reader.setTimeoutPaused(true)) {
                try {
                    long start = System.nanoTime() / 1000;
                    // When external parsing is on, items MUST be sent to queue, or errors will
                    // occur
                    ProcessTime time = ForkParser2.enabled ? ProcessTime.LATER : ProcessTime.AUTO;
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

        } catch (Exception e) {
            LOGGER.warn("{} Error while extracting subitem {}\t\t{}", Thread.currentThread().getName(), subitemPath, //$NON-NLS-1$
                    e.toString());
            LOGGER.debug("Error extracting subitem " + subitemPath, (Throwable) e);

        } finally {
            tmp.close();
        }

    }

    private static void removeMetadataAndDuplicates(Metadata metadata, Property prop) {
        metadata.remove(prop.getName());
        Property[] props = prop.getSecondaryExtractProperties();
        if (props != null)
            for (Property p : props)
                metadata.remove(p.getName());
    }

    @Override
    public void init(Properties confProps, File confDir) throws Exception {

        load(new File(confDir, EXPAND_CONFIG));

        String value = confProps.getProperty("expandContainers"); //$NON-NLS-1$
        if (value != null) {
            value = value.trim();
        }
        if (value != null && !value.isEmpty()) {
            expandContainers = Boolean.valueOf(value);
        }

        value = confProps.getProperty(ENABLE_PARSING);
        if (value != null & !value.trim().isEmpty()) {
            enableFileParsing = Boolean.valueOf(value.trim());
        }

    }

    @Override
    public void finish() throws Exception {
        if (totalText != null)
            LOGGER.info("Total extracted text size: " + totalText.get()); //$NON-NLS-1$
        totalText = null;

    }

}
