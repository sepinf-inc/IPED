package dpf.sp.gpinf.indexer.parsers;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.lang.ArrayUtils;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.parsers.util.RawISOConverter;
import dpf.sp.gpinf.indexer.parsers.util.Util;
import dpf.sp.gpinf.indexer.util.IOUtil;
import iped3.util.ExtraProperties;
import net.sf.sevenzipjbinding.ExtractAskMode;
import net.sf.sevenzipjbinding.ExtractOperationResult;
import net.sf.sevenzipjbinding.IArchiveExtractCallback;
import net.sf.sevenzipjbinding.IInArchive;
import net.sf.sevenzipjbinding.ISequentialOutStream;
import net.sf.sevenzipjbinding.SevenZip;
import net.sf.sevenzipjbinding.SevenZipException;
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream;
import net.sf.sevenzipjbinding.simple.ISimpleInArchive;
import net.sf.sevenzipjbinding.simple.ISimpleInArchiveItem;

public class SevenZipParser extends AbstractParser {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private static final Set<MediaType> SUPPORTED_TYPES = getTypes();
    private static Logger LOGGER = LoggerFactory.getLogger(SevenZipParser.class);

    private static final String ISO9660 = "x-iso9660-image"; //$NON-NLS-1$
    private static final String UDF = "x-udf-image"; //$NON-NLS-1$
    private static final String RAR = "x-rar-compressed"; //$NON-NLS-1$

    private static synchronized Set<MediaType> getTypes() {

        HashSet<MediaType> supportedTypes = new HashSet<MediaType>();

        try {
            File javaTmp = new File(System.getProperty("java.io.tmpdir"));
            File tmpDir = new File(javaTmp, "7zip-" + new Random().nextLong());
            Files.createDirectories(tmpDir.toPath());
            // use a different tmp dir for each process, see
            SevenZip.initSevenZipFromPlatformJAR(tmpDir);
            supportedTypes.add(MediaType.application(ISO9660));
            supportedTypes.add(MediaType.application(UDF));
            supportedTypes.add(MediaType.application(RAR));

            /*
             * 7zipJBinding-4.65 does not work with 7z files created by 7z-16.04 or higher.
             * 7zipJBinding-9.20 does not work correctly with some rare ISO files.
             */
            // supportedTypes.add(MediaType.application("x-7z-compressed"));

            // supportedTypes.add(MediaType.application("vnd.ms-htmlhelp"));
            // supportedTypes.add(MediaType.application("vnd.ms-cab-compressed"));
            // supportedTypes.add(MediaType.application("x-rar-compressed"));
            // supportedTypes.add(MediaType.application("x-vhd"));
            // supportedTypes.add(MediaType.application("x-wim-image"));

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return supportedTypes;
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext arg0) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        final EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                new ParsingEmbeddedDocumentExtractor(context));

        TemporaryResources tmp = new TemporaryResources();
        RandomAccessFile randomAccessFile = null;
        IInArchive inArchive = null;
        try {
            File file = TikaInputStream.get(stream, tmp).getFile();

            String mimetype = metadata.get(Metadata.CONTENT_TYPE);
            if (mimetype.contains(ISO9660) || mimetype.contains(UDF))
                file = RawISOConverter.convertTo2048SectorISO(file, tmp);

            randomAccessFile = new RandomAccessFile(file.getAbsolutePath(), "r"); //$NON-NLS-1$
            inArchive = SevenZip.openInArchive(null, new RandomAccessFileInStream(randomAccessFile), "password"); //$NON-NLS-1$
            ISimpleInArchive simpleInArchive = inArchive.getSimpleInterface();
            // Armazena as pastas
            TreeMap<String, Integer> folderMap = new TreeMap<String, Integer>();
            ArrayList<Integer> itemsToExtract = new ArrayList<Integer>();
            for (int i = 0; i < simpleInArchive.getNumberOfItems(); i++) {
                ISimpleInArchiveItem item = simpleInArchive.getArchiveItem(i);
                if (item.isEncrypted())
                    throw new EncryptedDocumentException();
                if (item.isFolder())
                    folderMap.put(item.getPath(), i);
                else
                    itemsToExtract.add(i);
            }
            // Processa as pastas na ordem (em profundidade)
            MyExtractCallback extractCallback = new MyExtractCallback(simpleInArchive, context, xhtml, extractor, tmp);
            for (int i : folderMap.values()) {
                inArchive.extractSlow(i, extractCallback.getStream(i, null));
                extractCallback.setOperationResult(null);
            }
            folderMap.clear();
            // Processa os arquivos
            int[] items = ArrayUtils.toPrimitive(itemsToExtract.toArray(new Integer[0]));
            inArchive.extract(items, false, extractCallback);

        } catch (SevenZipException e1) {
            throw new TikaException(this.getClass().getSimpleName() + ": " + e1.getMessage(), e1); //$NON-NLS-1$

        } finally {
            try {
                inArchive.close();
            } catch (Exception e) {
            }

            IOUtil.closeQuietly(randomAccessFile);
            tmp.close();
            xhtml.endDocument();
        }

    }

    public class MyExtractCallback implements IArchiveExtractCallback {

        ISimpleInArchive simpleInArchive;
        ParseContext context;
        ContentHandler handler;
        EmbeddedDocumentExtractor extractor;
        TemporaryResources tmp;

        ISimpleInArchiveItem item;

        byte[] tmpBuf = new byte[32 * 1024 * 1024];
        int bufPos = 0;
        File tmpFile;

        public MyExtractCallback(ISimpleInArchive simpleInArchive, ParseContext context, ContentHandler handler,
                EmbeddedDocumentExtractor extractor, TemporaryResources tmp) {
            this.simpleInArchive = simpleInArchive;
            this.context = context;
            this.handler = handler;
            this.extractor = extractor;
            this.tmp = tmp;
        }

        @Override
        public ISequentialOutStream getStream(int index, ExtractAskMode extractAskMode) throws SevenZipException {

            if (Thread.currentThread().isInterrupted()) {
                throw new SevenZipException("Thread interrupted"); //$NON-NLS-1$
            }

            try {
                item = simpleInArchive.getArchiveItem(index);

            } catch (Exception e1) {
                e1.printStackTrace();
            }

            return new ISequentialOutStream() {
                public int write(byte[] data) throws SevenZipException {

                    // store small data in memory, otherwise use temp file
                    if (bufPos < tmpBuf.length) {
                        int len = Math.min(data.length, tmpBuf.length - bufPos);
                        System.arraycopy(data, 0, tmpBuf, bufPos, len);
                        bufPos += len;
                        return len;

                    } else {
                        try {
                            if (tmpFile == null) {
                                tmpFile = tmp.createTemporaryFile();
                                Files.write(tmpFile.toPath(), tmpBuf);
                            }
                            Files.write(tmpFile.toPath(), data, StandardOpenOption.APPEND);
                            return data.length; // Return amount of consumed data

                        } catch (IOException e) {
                            throw new SevenZipException(e);
                        }
                    }
                }
            };
        }

        @Override
        public void setCompleted(long arg0) throws SevenZipException {
        }

        @Override
        public void setTotal(long arg0) throws SevenZipException {
        }

        @Override
        public void prepareOperation(ExtractAskMode arg0) throws SevenZipException {
        }

        @Override
        public void setOperationResult(ExtractOperationResult arg0) throws SevenZipException {

            try {
                if (tmpFile == null) {
                    parseSubitem(new ByteArrayInputStream(tmpBuf, 0, bufPos));
                } else {
                    try (InputStream is = new FileInputStream(tmpFile)) {
                        parseSubitem(is);
                    }
                }

            } catch (Exception e) {
                throw new SevenZipException(e);

            } finally {
                tmpFile = null;
                bufPos = 0;
            }

        }

        private void parseSubitem(InputStream is) throws SAXException, IOException {

            String subitemPath = ""; //$NON-NLS-1$
            try {
                final Metadata entrydata = new Metadata();
                subitemPath = item.getPath().replace("\\", "/"); //$NON-NLS-1$ //$NON-NLS-2$
                entrydata.set(Metadata.RESOURCE_NAME_KEY, subitemPath);
                entrydata.set(ExtraProperties.ITEM_VIRTUAL_ID, item.getPath());
                entrydata.set(ExtraProperties.PARENT_VIRTUAL_ID, Util.getParentPath(item.getPath()));
                entrydata.set(TikaCoreProperties.CREATED, item.getCreationTime());
                entrydata.set(TikaCoreProperties.MODIFIED, item.getLastWriteTime());
                entrydata.set(ExtraProperties.ACCESSED, item.getLastAccessTime());

                if (item.isFolder())
                    entrydata.set(ExtraProperties.EMBEDDED_FOLDER, "true"); //$NON-NLS-1$

                if (extractor.shouldParseEmbedded(entrydata))
                    extractor.parseEmbedded(is, handler, entrydata, true);

            } catch (SevenZipException e) {
                LOGGER.warn("Error extracting subitem {} {}", subitemPath, e.getMessage()); //$NON-NLS-1$
            }
        }

    }

}
