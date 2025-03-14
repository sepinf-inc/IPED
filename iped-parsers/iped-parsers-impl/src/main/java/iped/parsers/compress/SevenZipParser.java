package iped.parsers.compress;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.lang3.ArrayUtils;
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

import iped.parsers.util.RawISOConverter;
import iped.parsers.util.Util;
import iped.properties.ExtraProperties;
import iped.utils.EmptyInputStream;
import iped.utils.IOUtil;
import iped.utils.LocalizedFormat;
import net.sf.sevenzipjbinding.ExtractAskMode;
import net.sf.sevenzipjbinding.ExtractOperationResult;
import net.sf.sevenzipjbinding.IArchiveExtractCallback;
import net.sf.sevenzipjbinding.IInArchive;
import net.sf.sevenzipjbinding.ISequentialOutStream;
import net.sf.sevenzipjbinding.PropID;
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

    private static Logger LOGGER = LoggerFactory.getLogger(SevenZipParser.class);

    private static final String ISO9660 = "x-iso9660-image"; //$NON-NLS-1$
    private static final String UDF = "x-udf-image"; //$NON-NLS-1$
    public static final MediaType RAR = MediaType.application("x-rar-compressed"); //$NON-NLS-1$

    private static boolean inited = false;

    private static Set<MediaType> supportedTypes = getTypes();

    private static synchronized Set<MediaType> getTypes() {

        HashSet<MediaType> supportedTypes = new HashSet<MediaType>();

        supportedTypes.add(MediaType.application(ISO9660));
        supportedTypes.add(MediaType.application(UDF));
        supportedTypes.add(RAR);

        /*
         * 7zipJBinding-4.65 does not work with 7z files created by 7z-16.04 or higher.
         * 7zipJBinding-9.20 does not work correctly with some rare ISO files.
         */
        // supportedTypes.add(MediaType.application("x-7z-compressed"));
        // supportedTypes.add(MediaType.application("vnd.ms-htmlhelp"));
        // supportedTypes.add(MediaType.application("vnd.ms-cab-compressed"));
        // supportedTypes.add(MediaType.application("x-wim-image"));

        return supportedTypes;
    }

    private static synchronized void init7zNativeLibs() {
        if (inited) {
            return;
        }
        try {
            File javaTmp = new File(System.getProperty("java.io.tmpdir"));
            File tmpDir = new File(javaTmp, "7zip-" + new Random().nextLong());
            Files.createDirectories(tmpDir.toPath());
            // use a different tmp dir for each process, see #301
            SevenZip.initSevenZipFromPlatformJAR(tmpDir);

        } catch (Throwable t) {
            supportedTypes = Collections.emptySet();
            throw new RuntimeException(t);
        } finally {
            inited = true;
        }

    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext arg0) {
        return supportedTypes;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        init7zNativeLibs();

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

            ArrayList<Integer> folderList = new ArrayList<Integer>();
            ArrayList<Integer> fileList = new ArrayList<Integer>();
            for (int i = 0; i < simpleInArchive.getNumberOfItems(); i++) {
                ISimpleInArchiveItem item = simpleInArchive.getArchiveItem(i);
                if (item.isEncrypted()) {
                    listArchiveContent(inArchive, xhtml);
                    throw new EncryptedDocumentException();
                }
                if (item.isFolder())
                    folderList.add(i);
                else
                    fileList.add(i);
            }

            TreeMap<String, SubFile> fileMap = new TreeMap<>();
            MyExtractCallback extractCallback = new MyExtractCallback(simpleInArchive, tmp, handler, extractor, fileMap);

            // transverse folders first
            int[] folders = ArrayUtils.toPrimitive(folderList.toArray(new Integer[0]));
            inArchive.extract(folders, false, extractCallback);

            // extract folders in alphabetical order
            for (SubFile subFile : fileMap.values().toArray(new SubFile[0])) {
                try (InputStream is = new BufferedInputStream(subFile.file != null ? new FileInputStream(subFile.file) : new EmptyInputStream())) {
                    parseSubitem(is, subFile.item, xhtml, extractor, fileMap);
                }
            }

            // extract files
            extractCallback.extractFiles = true;
            int[] files = ArrayUtils.toPrimitive(fileList.toArray(new Integer[0]));
            inArchive.extract(files, false, extractCallback);

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
    
    private void listArchiveContent(IInArchive inArchive, XHTMLContentHandler xhtml) {
        int maxNumEntries = 1 << 24;
        List<String> entries = new ArrayList<String>();
        try {
            int numEntries = Math.min(maxNumEntries, inArchive.getNumberOfItems());
            DecimalFormat nf = LocalizedFormat.getDecimalInstance("#,##0");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < numEntries; i++) {
                sb.append((String) inArchive.getProperty(i, PropID.PATH));
                Boolean isFolder = (Boolean) inArchive.getProperty(i, PropID.IS_FOLDER);
                if (isFolder != null && isFolder.booleanValue()) {
                    sb.append(" [FOLDER]");
                } else {
                    Long size = (Long) inArchive.getProperty(i, PropID.SIZE);
                    if (size != null && size > 0) {
                        sb.append(" [").append(nf.format(size)).append(" bytes]");
                    }
                }
                entries.add(sb.toString());
                sb.delete(0, sb.length());
            }
        } catch (Exception e) {
        } finally {
            try {
                if (!entries.isEmpty()) {
                    Collections.sort(entries);
                    xhtml.startElement("pre");
                    for (String entry : entries) {
                        xhtml.characters(entry);
                        xhtml.newline();
                    }
                    xhtml.endElement("pre");
                }
            } catch (Exception e) {
            }
        }
    }

    private static class SubFile {
        File file;
        ISimpleInArchiveItem item;

        private SubFile(File file, ISimpleInArchiveItem item) {
            this.file = file;
            this.item = item;
        }
    }

    private static class MyExtractCallback implements IArchiveExtractCallback {

        ISimpleInArchive simpleInArchive;
        TemporaryResources tmp;
        TreeMap<String, SubFile> fileMap;
        ISimpleInArchiveItem item;
        ContentHandler handler;
        EmbeddedDocumentExtractor extractor;

        byte[] tmpBuf = new byte[32 * 1024 * 1024];
        int bufPos = 0;
        File tmpFile;
        boolean extractFiles = false;

        public MyExtractCallback(ISimpleInArchive simpleInArchive, TemporaryResources tmp, ContentHandler handler, EmbeddedDocumentExtractor extractor, TreeMap<String, SubFile> fileMap) {
            this.simpleInArchive = simpleInArchive;
            this.tmp = tmp;
            this.handler = handler;
            this.extractor = extractor;
            this.fileMap = fileMap;
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
                if (item.isFolder()) {
                    if (tmpFile == null && bufPos > 0) {
                        tmpFile = tmp.createTemporaryFile();
                        Files.copy(new ByteArrayInputStream(tmpBuf, 0, bufPos), tmpFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                    SubFile file = new SubFile(tmpFile, item);
                    fileMap.put(item.getPath(), file);
                    return;
                }
                // Do not remove if below, 7zJBiding seems to iterate over items not asked for
                if (extractFiles) {
                    if (tmpFile == null) {
                        parseSubitem(new ByteArrayInputStream(tmpBuf, 0, bufPos), item, handler, extractor, fileMap);
                    } else {
                        try (InputStream is = new BufferedInputStream(new FileInputStream(tmpFile))) {
                            parseSubitem(is, item, handler, extractor, fileMap);
                        }
                    }
                }
            } catch (Exception e) {
                throw new SevenZipException(e);

            } finally {
                tmpFile = null;
                bufPos = 0;
            }

        }

    }

    private static void parseSubitem(InputStream is, ISimpleInArchiveItem item, ContentHandler handler, EmbeddedDocumentExtractor extractor, TreeMap<String, SubFile> fileMap) throws SAXException, IOException {

        String parentPath = Util.getParentPath(item.getPath());
        if (parentPath != null && !fileMap.containsKey(parentPath)) {
            parseMissingSubFolder(new EmptyInputStream(), parentPath, handler, extractor, fileMap);
        }

        String subitemPath = ""; //$NON-NLS-1$
        try {
            final Metadata entrydata = new Metadata();
            subitemPath = item.getPath().replace("\\", "/"); //$NON-NLS-1$ //$NON-NLS-2$
            entrydata.set(TikaCoreProperties.RESOURCE_NAME_KEY, subitemPath);
            entrydata.set(ExtraProperties.ITEM_VIRTUAL_ID, item.getPath());
            entrydata.set(ExtraProperties.PARENT_VIRTUAL_ID, parentPath);
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

    private static void parseMissingSubFolder(InputStream is, String path, ContentHandler handler, EmbeddedDocumentExtractor extractor, TreeMap<String, SubFile> fileMap) throws SAXException, IOException {
        String parentPath = Util.getParentPath(path);
        if (parentPath != null && !fileMap.containsKey(parentPath)) {
            parseMissingSubFolder(new EmptyInputStream(), parentPath, handler, extractor, fileMap);
        }
        Metadata entrydata = new Metadata();
        String subitemPath = path.replace("\\", "/"); //$NON-NLS-1$ //$NON-NLS-2$
        entrydata.set(TikaCoreProperties.RESOURCE_NAME_KEY, subitemPath);
        entrydata.set(ExtraProperties.ITEM_VIRTUAL_ID, path);
        entrydata.set(ExtraProperties.PARENT_VIRTUAL_ID, parentPath);
        entrydata.set(ExtraProperties.EMBEDDED_FOLDER, "true");
        if (extractor.shouldParseEmbedded(entrydata)) {
            extractor.parseEmbedded(is, handler, entrydata, true);
        }
        fileMap.put(path, new SubFile(null, null));
    }

}
