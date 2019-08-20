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
package dpf.sp.gpinf.indexer.parsers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.fork.ForkParser2;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaMetadataKeys;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.EmptyParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.apache.tika.parser.txt.TXTParser;
import org.apache.tika.sax.ContentHandlerDecorator;
import org.apache.tika.sax.SecureContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.parsers.util.CorruptedCarvedException;
import dpf.sp.gpinf.indexer.parsers.util.IgnoreCorruptedCarved;
import dpf.sp.gpinf.indexer.parsers.util.ItemInfo;
import dpf.sp.gpinf.indexer.parsers.util.Messages;
import dpf.sp.gpinf.indexer.parsers.util.MetadataUtil;
import dpf.sp.gpinf.indexer.util.IOUtil;
import iped3.io.IStreamSource;

/**
 * Parser padrão do Indexador. Como o AutoDetectParser, detecta o tipo do
 * arquivo e delega o parsing para o parser apropriado. Porém aproveita o
 * CONTENT_TYPE caso informado via metadados, evitando nova detecção. Além disso
 * utiliza o RawStringParser como fallback ou no caso de alguma Exceção durante
 * o parsing padrão. Finalmente, escreve os metadados ao final (inclusive de
 * subitens).
 */
public class IndexerDefaultParser extends CompositeParser {

    private static Logger LOGGER = LoggerFactory.getLogger(IndexerDefaultParser.class);

    private static final long serialVersionUID = 1L;

    private static TikaConfig tikaConfig = TikaConfig.getDefaultConfig();

    public static int parsingErrors = 0;

    public static final String METADATA_HEADER = Messages.getString("IndexerDefaultParser.MetadataTitle"); //$NON-NLS-1$
    public static final String METADATA_FOOTER = "-----------------------------------"; //$NON-NLS-1$

    public static final String INDEXER_CONTENT_TYPE = "Indexer-Content-Type"; //$NON-NLS-1$
    public static final String INDEXER_TIMEOUT = "Indexer-Timeout-Occurred"; //$NON-NLS-1$
    public static final String PARSER_EXCEPTION = "parserException"; //$NON-NLS-1$
    public static final String ENCRYPTED_DOCUMENT = "encryptedDocument"; //$NON-NLS-1$

    public static final String FALLBACK_PARSER_PROP = "fallbackParser"; //$NON-NLS-1$
    public static final String ERROR_PARSER_PROP = "errorParser"; //$NON-NLS-1$
    public static final String ENTROPY_TEST_PROP = "entropyTest"; //$NON-NLS-1$

    private boolean fallbackParserEnabled = Boolean.valueOf(System.getProperty(FALLBACK_PARSER_PROP, "true"));
    private boolean errorParserEnabled = Boolean.valueOf(System.getProperty(ERROR_PARSER_PROP, "true"));
    private boolean entropyTestEnabled = Boolean.valueOf(System.getProperty(ENTROPY_TEST_PROP, "true"));

    private Parser errorParser;
    private final Detector detector;
    private boolean printMetadata = true;
    private boolean ignoreStyle = true;
    private boolean canUseForkParser = false;

    public IndexerDefaultParser() {
        super(tikaConfig.getMediaTypeRegistry(), ((CompositeParser) tikaConfig.getParser()).getAllComponentParsers());
        detector = tikaConfig.getDetector();
        if (fallbackParserEnabled) {
            this.setFallback(new RawStringParser(entropyTestEnabled));
        }
        if (errorParserEnabled) {
            this.setErrorParser(new RawStringParser(entropyTestEnabled));
        }
    }

    public void setCanUseForkParser(boolean shouldUseForkParser) {
        this.canUseForkParser = shouldUseForkParser;
    }

    public void setErrorParser(Parser parser) {
        this.errorParser = parser;
    }

    private static synchronized void incParsingErrors() {
        parsingErrors++;
    }

    public Parser getBestParser(Metadata metadata) {
        return super.getParser(metadata);
    }

    public void setPrintMetadata(boolean printMetadata) {
        this.printMetadata = printMetadata;
    }

    public void setIgnoreStyle(boolean ignoreStyle) {
        this.ignoreStyle = ignoreStyle;
    }

    public boolean hasSpecificParser(Metadata metadata) {
        Parser p = getLeafParser(metadata);
        return isSpecificParser(p);
    }

    public Parser getLeafParser(Metadata metadata) {
        Parser parser = getBestParser(metadata);
        while (parser instanceof CompositeParser || parser instanceof ParserDecorator) {
            if (parser instanceof CompositeParser)
                parser = getBestParser((CompositeParser) parser, metadata);
            else
                parser = ((ParserDecorator) parser).getWrappedParser();
        }
        return parser;

    }

    public static boolean isSpecificParser(Parser parser) {
        if (parser instanceof RawStringParser || parser instanceof TXTParser || parser instanceof EmptyParser)
            return false;
        else
            return true;
    }

    private Parser getBestParser(CompositeParser comp, Metadata metadata) {
        Map<MediaType, Parser> map = comp.getParsers();
        MediaType type = MediaType.parse(metadata.get(Metadata.CONTENT_TYPE));
        if (type != null)
            type = comp.getMediaTypeRegistry().normalize(type);
        while (type != null) {
            Parser parser = map.get(type);
            if (parser != null)
                return parser;
            type = comp.getMediaTypeRegistry().getSupertype(type);
        }
        return comp.getFallback();
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        // Utilizado para não terminar documento antes de escrever metadados
        NoEndBodyContentHandler noEndHandler = new NoEndBodyContentHandler(handler);

        TemporaryResources tmp = new TemporaryResources();
        TikaInputStream tis = TikaInputStream.get(stream, tmp);

        if (context.get(Parser.class) == null)
            context.set(Parser.class, this);

        ItemInfo itemInfo = context.get(ItemInfo.class);
        IStreamSource evidence = context.get(IStreamSource.class);
        File file = null;
        if (evidence == null && errorParser != null) {
            file = tis.getFile();
        } else {
            context.set(IStreamSource.class, null);
        }

        String filePath = null;
        if (itemInfo != null)
            filePath = itemInfo.getPath();
        long length = -1;

        String lengthStr = metadata.get(Metadata.CONTENT_LENGTH);
        if (lengthStr != null) {
            length = Long.parseLong(lengthStr);
        } else if (file != null) {
            lengthStr = Long.toString(file.length());
        }

        String contentType = null;
        try {
            // calcula content_type caso não seja conhecido
            contentType = metadata.get(INDEXER_CONTENT_TYPE);
            if (contentType == null) {
                try {
                    contentType = detector.detect(tis, metadata).toString();

                } catch (IOException e) {
                    LOGGER.warn("{} Error detecting file type: {} ({} bytes)\t\t{}", Thread.currentThread().getName(), //$NON-NLS-1$
                            filePath, length, e.toString());
                }
            }
            // System.out.println(contentType);
            metadata.set(Metadata.CONTENT_TYPE, contentType);
            metadata.set(INDEXER_CONTENT_TYPE, contentType);

            // TIKA-216: Zip bomb prevention
            SecureContentHandler sch = new SecureContentHandler(noEndHandler, tis);
            sch.setMaximumDepth(Integer.MAX_VALUE);
            sch.setMaximumPackageEntryDepth(100);
            try {
                if (length != 0) {
                    if (metadata.get(INDEXER_TIMEOUT) == null) {
                        if (canUseForkParser && ForkParser2.isEnabled() && hasSpecificParser(metadata))
                            ForkParser2.getForkParser().parse(tis, sch, metadata, context);
                        else
                            super.parse(tis, sch, metadata, context);
                    } else {
                        if (errorParser != null)
                            errorParser.parse(tis, sch, metadata, context);
                    }
                }

            } catch (SAXException e) {
                // Convert zip bomb exceptions to TikaExceptions
                try {
                    sch.throwIfCauseOf(e);
                    throw e;
                } catch (TikaException t) {
                    if (tis.getPosition() > 0)
                        throw t;
                }
            }

            // Parsing com rawparser no caso de exceções de parsing
        } catch (TikaException e) {
            if (!Thread.currentThread().isInterrupted()
                    && !(e.getCause() instanceof InterruptedException) /*
                                                                        * //Extrair strings dos tipos abaixo? &&
                                                                        * !contentType.equals("image/gif") &&
                                                                        * !contentType.equals("image/jpeg") &&
                                                                        * !contentType.equals("image/x-ms-bmp") &&
                                                                        * !contentType.equals("image/png") &&
                                                                        * !contentType.startsWith(
                                                                        * "application/vnd.openxmlformats-officedocument")
                                                                        * && !contentType.startsWith(
                                                                        * "application/vnd.oasis.opendocument") &&
                                                                        * !contentType.equals("application/zip") &&
                                                                        * !contentType.equals("application/pdf") &&
                                                                        * !contentType.equals("application/gzip")
                                                                        */) {
                String value;
                if ((e instanceof EncryptedDocumentException
                        || (value = metadata.get("pdf:encrypted")) != null && Boolean.valueOf(value)) //$NON-NLS-1$
                        || ((value = metadata.get("Security")) != null && Integer.valueOf(value) == 1) //$NON-NLS-1$
                        // TODO melhorar detecção de arquivos openoffice cifrados
                        || (contentType.contains("vnd.oasis.opendocument") && e.getCause() != null //$NON-NLS-1$
                                && (e.getCause().toString().contains("only DEFLATED entries can have EXT descriptor") //$NON-NLS-1$
                                        || e.getCause().toString().contains("MalformedByteSequenceException")))) { //$NON-NLS-1$

                    metadata.set(ENCRYPTED_DOCUMENT, "true"); //$NON-NLS-1$

                } else if (itemInfo != null && itemInfo.isCarved() && context.get(IgnoreCorruptedCarved.class) != null)
                    throw new CorruptedCarvedException(e);

                else {
                    if (e.getCause() instanceof IOException && IOUtil.isDiskFull((IOException) e.getCause()))
                        LOGGER.error("No space on temp folder to process {} ({} bytes)", filePath, lengthStr); //$NON-NLS-1$

                    incParsingErrors();
                    metadata.set(PARSER_EXCEPTION, "true"); //$NON-NLS-1$

                    LOGGER.warn("{} Parsing exception: {} ({} bytes)\t\t{}", Thread.currentThread().getName(), filePath, //$NON-NLS-1$
                            lengthStr, e.toString());
                    LOGGER.debug(Thread.currentThread().getName() + " Parsing exception: " + filePath, e); //$NON-NLS-1$

                    InputStream is = null;
                    if (errorParser != null)
                        try {
                            if (evidence != null) {
                                is = evidence.getStream();
                            } else {
                                is = TikaInputStream.get(file);
                            }
                            errorParser.parse(is, noEndHandler, metadata, context);

                        } finally {
                            if (is != null)
                                is.close();
                        }
                }

            }

        } finally {
            MetadataUtil.normalizeMetadata(metadata);

            if (printMetadata) {
                // Escreve metadados ao final do texto
                noEndHandler.newLine();
                noEndHandler.characters(METADATA_HEADER.toCharArray(), 0, METADATA_HEADER.length());
                noEndHandler.newLine();

                String[] names = metadata.names();
                Arrays.sort(names);
                for (String name : names) {
                    if (name != null && !name.equals(TikaMetadataKeys.RESOURCE_NAME_KEY)
                            && !name.equals("PLTE PLTEEntry") && !name.equals("Chroma Palette PaletteEntry") //$NON-NLS-1$ //$NON-NLS-2$
                            && !name.equals(Metadata.CONTENT_TYPE)) {
                        String text = name + ": "; //$NON-NLS-1$
                        for (String value : metadata.getValues(name)) {
                            text += value + " "; //$NON-NLS-1$
                        }
                        noEndHandler.characters(text.toCharArray(), 0, text.length());
                        noEndHandler.newLine();
                    }
                }
                noEndHandler.characters(METADATA_FOOTER.toCharArray(), 0, METADATA_FOOTER.length());
                noEndHandler.newLine();
            }

            noEndHandler.reallyEndDocument();

            tmp.close();
        }

    }

    private class NoEndBodyContentHandler extends ContentHandlerDecorator {

        public static final String XHTML = "http://www.w3.org/1999/xhtml"; //$NON-NLS-1$
        private boolean endDocument, endFrameset, endBody, endHtml, inStyle;

        public NoEndBodyContentHandler(ContentHandler handler) {
            super(handler);
        }

        public void newLine() throws SAXException {
            // super.startElement(XHTML, "br", "br", new AttributesImpl());
            // super.endElement(XHTML, "br", "br");
            super.characters("\n".toCharArray(), 0, 1); //$NON-NLS-1$
        }

        @Override
        public void startElement(String uri, String localName, String name, org.xml.sax.Attributes atts)
                throws SAXException {
            if (name.equals("style")) { //$NON-NLS-1$
                // Tag <style> foi aberta
                inStyle = true;
            } else {
                // Qualquer outra tag que é iniciada volta para o estado normal, para garantir
                // que nada será perdido
                // mesmo em arquivos mal formados (tag </style> ausente, por exemplo)
                inStyle = false;
            }
            super.startElement(uri, localName, name, atts);
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            // Filtra texto se está dentro de tag <style>
            if (!inStyle || !ignoreStyle)
                super.characters(ch, start, length);
        }

        @Override
        public void endElement(String uri, String local, String name) throws SAXException {
            // Qualquer fechamento também volta ao estado normal, para garantir que texto
            // não será perdido.
            inStyle = false;
            if (name.equals("frameset")) { //$NON-NLS-1$
                endFrameset = true;
            } else if (name.equals("body")) { //$NON-NLS-1$
                endBody = true;
            } else if (name.equals("html")) { //$NON-NLS-1$
                endHtml = true;
            } else {
                super.endElement(uri, local, name);
            }
        }

        @Override
        public void endDocument() throws SAXException {
            endDocument = true;
        }

        public void reallyEndDocument() throws SAXException {
            if (endFrameset) {
                super.endElement(XHTML, "frameset", "frameset"); //$NON-NLS-1$ //$NON-NLS-2$
            }
            if (endBody) {
                super.endElement(XHTML, "body", "body"); //$NON-NLS-1$ //$NON-NLS-2$
            }
            if (endHtml) {
                super.endElement(XHTML, "html", "html"); //$NON-NLS-1$ //$NON-NLS-2$
            }

            super.endDocument();
        }

        public boolean getEndDocumentWasCalled() {
            return endDocument;
        }
    }

}
