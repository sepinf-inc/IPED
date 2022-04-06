/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dpf.sp.gpinf.indexer.parsers;

import static org.apache.tika.metadata.HttpHeaders.CONTENT_TYPE;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;

import org.apache.commons.compress.PasswordRequiredException;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.StreamingNotSupportedException;
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream;
import org.apache.commons.compress.archivers.arj.ArjArchiveInputStream;
import org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream;
import org.apache.commons.compress.archivers.dump.DumpArchiveInputStream;
import org.apache.commons.compress.archivers.jar.JarArchiveInputStream;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.UnsupportedZipFeatureException;
import org.apache.commons.compress.archivers.zip.UnsupportedZipFeatureException.Feature;
import org.apache.commons.compress.archivers.zip.X000A_NTFS;
import org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipExtraField;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.CloseShieldInputStream;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped3.io.IStreamSource;
import iped3.util.ExtraProperties;
import dpf.sp.gpinf.indexer.parsers.util.Util;

/**
 * Parser for various packaging formats. Package entries will be written to the
 * XHTML event stream as &lt;div class="package-entry"&gt; elements that contain
 * the (optional) entry name as a &lt;h1&gt; element and the full structured
 * body content of the parsed entry.
 * 
 * @author Nassif (better handling of encrypted zips and processing of XPS and
 *         generic OOXML)
 */
public class PackageParser extends AbstractParser {

    /** Serial version UID */
    private static final long serialVersionUID = -5331043266963888708L;

    private static final MediaType ZIP = MediaType.APPLICATION_ZIP;
    private static final MediaType JAR = MediaType.application("java-archive"); //$NON-NLS-1$
    private static final MediaType AR = MediaType.application("x-archive"); //$NON-NLS-1$
    private static final MediaType CPIO = MediaType.application("x-cpio"); //$NON-NLS-1$
    private static final MediaType DUMP = MediaType.application("x-tika-unix-dump"); //$NON-NLS-1$
    private static final MediaType TAR = MediaType.application("x-tar"); //$NON-NLS-1$
    private static final MediaType SEVENZ = MediaType.application("x-7z-compressed"); //$NON-NLS-1$
    private static final MediaType ARJ = MediaType.application("x-arj"); //$NON-NLS-1$
    private static final MediaType OOXML = MediaType.application("x-tika-ooxml"); //$NON-NLS-1$

    public static final Set<MediaType> SUPPORTED_TYPES = MediaType.set(ZIP, JAR, AR, CPIO, DUMP, TAR, SEVENZ, ARJ,
            OOXML);

    static MediaType getMediaType(ArchiveInputStream stream) {
        if (stream instanceof JarArchiveInputStream) {
            return JAR;
        } else if (stream instanceof ZipArchiveInputStream) {
            return ZIP;
        } else if (stream instanceof ArArchiveInputStream) {
            return AR;
        } else if (stream instanceof CpioArchiveInputStream) {
            return CPIO;
        } else if (stream instanceof DumpArchiveInputStream) {
            return DUMP;
        } else if (stream instanceof TarArchiveInputStream) {
            return TAR;
        } else if (stream instanceof SevenZWrapper) {
            return SEVENZ;
        } else if (stream instanceof ArjArchiveInputStream) {
            return ARJ;
        } else {
            return MediaType.OCTET_STREAM;
        }
    }

    static boolean isZipArchive(MediaType type) {
        return type.equals(ZIP) || type.equals(JAR);
    }

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        // Ensure that the stream supports the mark feature
        if (!stream.markSupported()) {
            stream = new BufferedInputStream(stream);
        }
        stream.mark(1 << 24);

        TemporaryResources tmp = new TemporaryResources();
        ArchiveInputStream ais = null;
        String encoding = null;
        try {
            ArchiveStreamFactory factory = context.get(ArchiveStreamFactory.class, new ArchiveStreamFactory());
            encoding = factory.getEntryEncoding();
            // At the end we want to close the archive stream to release
            // any associated resources, but the underlying document stream
            // should not be closed
            ais = factory.createArchiveInputStream(new CloseShieldInputStream(stream));

        } catch (StreamingNotSupportedException sne) {
            // Most archive formats work on streams, but a few need files
            if (sne.getFormat().equals(ArchiveStreamFactory.SEVEN_Z)) {
                // Rework as a file, and wrap
                stream.reset();
                TikaInputStream tstream = TikaInputStream.get(stream, tmp);

                // Pending a fix for COMPRESS-269, this bit is a little nasty
                try {
                    ais = new SevenZWrapper(new SevenZFile(tstream.getFile()));
                } catch (PasswordRequiredException e) {
                    throw new EncryptedDocumentException(e);
                }

            } else {
                tmp.close();
                throw new TikaException("Unknown non-streaming format " + sne.getFormat(), sne); //$NON-NLS-1$
            }
        } catch (ArchiveException e) {
            tmp.close();
            throw new TikaException("Unable to unpack document stream", e); //$NON-NLS-1$
        }

        MediaType type = getMediaType(ais);
        if (!type.equals(MediaType.OCTET_STREAM) && metadata.get(Metadata.CONTENT_TYPE) == null) {
            metadata.set(CONTENT_TYPE, type.toString());
        }

        // Use the delegate parser to parse the contained document
        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                new ParsingEmbeddedDocumentExtractor(context));

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        String nameKey = metadata.get(Metadata.RESOURCE_NAME_KEY);
        boolean isCarved = nameKey != null ? nameKey.startsWith("Carved") : false; //$NON-NLS-1$
        BooleanWrapper encrypted = new BooleanWrapper();
        HashSet<String> parentMap = new HashSet<>();
        ArrayList<String> zipSubitemList = new ArrayList<>();
        try {
            ArchiveEntry entry = ais.getNextEntry();
            while (entry != null) {
                EntryInputStreamFactory factory = getEISFactory(ais, entry);
                handleEntry(factory, entry, isCarved, encrypted, parentMap, zipSubitemList, context, extractor, xhtml);
                entry = ais.getNextEntry();
            }
        } catch (UnsupportedZipFeatureException zfe) {
            // If it's an encrypted document of unknown password, report as such
            if (zfe.getFeature() == Feature.ENCRYPTION) {
                throw new EncryptedDocumentException(zfe);
            }
            if (ais instanceof ZipArchiveInputStream && zfe.getFeature() == Feature.DATA_DESCRIPTOR) {
                parseZipFile(stream, xhtml, context, tmp, parentMap, zipSubitemList, isCarved, encrypted, encoding);
            } else
                throw new TikaException("UnsupportedZipFeature", zfe); //$NON-NLS-1$

        } catch (PasswordRequiredException pre) {
            throw new EncryptedDocumentException(pre);

        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("Unexpected record signature")) { //$NON-NLS-1$
                metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING, e.toString());
            } else
                throw e;

        } finally {
            IOUtils.closeQuietly(ais);
            tmp.close();
            xhtml.endDocument();

            if (encrypted.bool)
                throw new EncryptedDocumentException();
        }
    }

    private void handleEntry(EntryInputStreamFactory factory, ArchiveEntry entry, boolean isCarved,
            BooleanWrapper encrypted, HashSet<String> parentMap, List<String> zipSubitemList, ParseContext context,
            EmbeddedDocumentExtractor extractor, XHTMLContentHandler xhtml)
            throws TikaException, SAXException, IOException {

        String name = getEntryName(entry, isCarved);
        String parent = getParent(name, parentMap, extractor, context, xhtml);
        if (!entry.isDirectory()) {
            if (!(factory.getSource() instanceof ZipFile) || !zipSubitemList.contains(name)) {
                parseEntry(parent, context, factory, entry, encrypted, extractor, xhtml);
            }
            // remember subitems if it's a zip stream to skip them in a possible 2nd parse
            if (factory.getSource() instanceof ZipArchiveInputStream) {
                zipSubitemList.add(name);
            }
        } else {
            if (!parentMap.contains(name)) {
                parseEntry(parent, context, factory, entry, encrypted, extractor, xhtml);
                parentMap.add(name);
            }
        }
        if (Thread.currentThread().isInterrupted())
            throw new TikaException("Parsing Interrupted"); //$NON-NLS-1$
    }

    private void parseZipFile(InputStream stream, XHTMLContentHandler xhtml, ParseContext context,
            TemporaryResources tmp, HashSet<String> parentMap, List<String> zipSubitemList, boolean isCarved,
            BooleanWrapper encrypted, String encoding) throws IOException, SAXException, TikaException {

        try (InputStream is = getNewInputStream(new CloseShieldInputStream(stream), context);
                ZipFile zipFile = new ZipFile(TikaInputStream.get(is, tmp).getFile(), encoding)) {

            EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class);
            Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry zipEntry = entries.nextElement();
                EntryInputStreamFactory factory = getEISFactory(zipFile, zipEntry);
                handleEntry(factory, zipEntry, isCarved, encrypted, parentMap, zipSubitemList, context, extractor, xhtml);
            }
        }
    }

    private InputStream getNewInputStream(InputStream stream, ParseContext context) throws IOException {
        try {
            stream.reset();
            return stream;

        } catch (IOException e) {
            IStreamSource streamFactory = context.get(IStreamSource.class);
            if (streamFactory != null) {
                return streamFactory.getSeekableInputStream();
            }
            throw new IOException("Fail to create a new InputStream: no IStreamSource in context");
        }
    }

    private interface EntryInputStreamFactory {

        boolean canReadEntryData();

        InputStream getInputStream() throws IOException;

        Object getSource();

    }

    private EntryInputStreamFactory getEISFactory(ArchiveInputStream ais, ArchiveEntry entry) {
        return new EntryInputStreamFactory() {
            @Override
            public boolean canReadEntryData() {
                return ais.canReadEntryData(entry);
            }

            @Override
            public InputStream getInputStream() throws IOException {
                return new CloseShieldInputStream(ais);
            }

            @Override
            public Object getSource() {
                return ais;
            }
        };
    }

    private EntryInputStreamFactory getEISFactory(ZipFile zipFile, ZipArchiveEntry zipEntry) {
        return new EntryInputStreamFactory() {
            @Override
            public boolean canReadEntryData() {
                return zipFile.canReadEntryData(zipEntry);
            }

            @Override
            public InputStream getInputStream() throws IOException {
                return zipFile.getInputStream(zipEntry);
            }

            @Override
            public Object getSource() {
                return zipFile;
            }
        };
    }

    private String getEntryName(ArchiveEntry entry) throws TikaException, UnsupportedEncodingException {
        return getEntryName(entry, false);
    }

    private String getEntryName(ArchiveEntry entry, boolean isCarved)
            throws TikaException, UnsupportedEncodingException {
        String name = entry.getName();
        if (name != null) {
            if (name.endsWith("/")) //$NON-NLS-1$
                name = name.substring(0, name.length() - 1);
            if (isCarved) {
                int MAX_LEN = 1024;
                if (name.length() > MAX_LEN)
                    throw new TikaException("Zip entry name too long"); //$NON-NLS-1$

                byte[] bytes = name.getBytes("windows-1252"); //$NON-NLS-1$
                for (byte b : bytes)
                    if (b >= 0 && b < 0x20 && b != 0x09)
                        throw new TikaException("Invalid char in zip entry"); //$NON-NLS-1$

            }
        }
        return name;
    }

    private String getParent(String path, HashSet<String> parentMap, EmbeddedDocumentExtractor extractor,
            ParseContext context, XHTMLContentHandler xhtml) throws SAXException, IOException {
        String parentPath = Util.getParentPath(path);
        if (parentPath == null)
            return null;
        if (!parentMap.contains(parentPath)) {
            String grandParent = getParent(parentPath, parentMap, extractor, context, xhtml);
            createParent(parentPath, grandParent, extractor, xhtml);
            parentMap.add(parentPath);
        }
        return parentPath;
    }

    private void createParent(String name, String parent, EmbeddedDocumentExtractor extractor,
            XHTMLContentHandler xhtml) throws SAXException, IOException {
        Metadata entrydata = new Metadata();
        entrydata.set(Metadata.RESOURCE_NAME_KEY, name);
        entrydata.set(ExtraProperties.EMBEDDED_FOLDER, "true"); //$NON-NLS-1$
        entrydata.set(ExtraProperties.ITEM_VIRTUAL_ID, name);
        entrydata.set(ExtraProperties.PARENT_VIRTUAL_ID, parent);
        extractor.parseEmbedded(new ByteArrayInputStream(new byte[0]), xhtml, entrydata, true);
    }

    private class BooleanWrapper {
        boolean bool = false;
    }

    private void parseEntry(String parent, ParseContext context, EntryInputStreamFactory factory, ArchiveEntry entry,
            BooleanWrapper entryEncrypted, EmbeddedDocumentExtractor extractor, XHTMLContentHandler xhtml)
            throws SAXException, IOException, TikaException {
        String name = getEntryName(entry);
        Metadata entrydata = new Metadata();
        entrydata.set(TikaCoreProperties.MODIFIED, entry.getLastModifiedDate());
        if (entry.getSize() != -1) {
            entrydata.set(Metadata.CONTENT_LENGTH, Long.toString(entry.getSize()));
        }
        if (name != null && name.length() > 0) {
            entrydata.set(Metadata.RESOURCE_NAME_KEY, name);
            entrydata.set(Metadata.EMBEDDED_RELATIONSHIP_ID, name);
            entrydata.set(ExtraProperties.ITEM_VIRTUAL_ID, name); // $NON-NLS-1$
        }
        if (entry instanceof ZipArchiveEntry) {
            ZipArchiveEntry zae = (ZipArchiveEntry) entry;
            if (!factory.canReadEntryData()) {
                if (zae.getGeneralPurposeBit().usesEncryption())
                    entryEncrypted.bool = true;
                
                // do not write to the handler if UnsupportedZipFeatureException.Feature.DATA_DESCRIPTOR
                // is met, we will catch this exception and read the zip archive once again
                boolean usesDataDescriptor = zae.getGeneralPurposeBit().usesDataDescriptor();
                if (usesDataDescriptor && zae.getMethod() == ZipEntry.STORED) {
                    throw new UnsupportedZipFeatureException(UnsupportedZipFeatureException.Feature.DATA_DESCRIPTOR, zae);
                }
            }
            for (ZipExtraField zef : zae.getExtraFields()) {
                if (zef instanceof X000A_NTFS) {
                    entrydata.set(ExtraProperties.ACCESSED, ((X000A_NTFS) zef).getAccessJavaTime());
                    entrydata.set(TikaCoreProperties.CREATED, ((X000A_NTFS) zef).getCreateJavaTime());
                }
                if (zef instanceof X5455_ExtendedTimestamp) {
                    entrydata.set(ExtraProperties.ACCESSED, ((X5455_ExtendedTimestamp) zef).getAccessJavaTime());
                    entrydata.set(TikaCoreProperties.CREATED, ((X5455_ExtendedTimestamp) zef).getCreateJavaTime());
                }
            }
        }
        entrydata.set(ExtraProperties.PARENT_VIRTUAL_ID, parent); // $NON-NLS-1$
        if (entry.isDirectory())
            entrydata.set(ExtraProperties.EMBEDDED_FOLDER, "true"); //$NON-NLS-1$
        if (extractor.shouldParseEmbedded(entrydata)) {
            // For detectors to work, we need a mark/reset supporting
            // InputStream, so wrap with a TikaInputStream
            TemporaryResources tmp = new TemporaryResources();
            try (InputStream is = factory.getInputStream()) {
                TikaInputStream tis = TikaInputStream.get(is, tmp);
                extractor.parseEmbedded(tis, xhtml, entrydata, true);
            } finally {
                tmp.dispose();
            }
        }
    }

    // Pending a fix for COMPRESS-269, we have to wrap ourselves
    private static class SevenZWrapper extends ArchiveInputStream {
        private SevenZFile file;

        private SevenZWrapper(SevenZFile file) {
            this.file = file;
        }

        @Override
        public int read() throws IOException {
            return file.read();
        }

        @Override
        public int read(byte[] b) throws IOException {
            return file.read(b);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return file.read(b, off, len);
        }

        @Override
        public ArchiveEntry getNextEntry() throws IOException {
            return file.getNextEntry();
        }

        @Override
        public void close() throws IOException {
            file.close();
        }
    }

}
