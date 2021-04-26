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


import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;
import java.util.TreeMap;

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
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import com.github.junrar.Archive;
import com.github.junrar.exception.RarException;
import com.github.junrar.rarfile.FileHeader;

import dpf.sp.gpinf.indexer.parsers.util.Util;
import iped3.util.ExtraProperties;

/**
 * RAR file parser. No support for Rar5 format as of 2020-10-11 because of
 * junrar limitation. Currently this parser is disabled and SevenZipParser
 * handles RAR files.
 * 
 * @author Nassif
 *
 */
public class RARParser extends AbstractParser {

    private static final long serialVersionUID = 6157727985054451501L;
    private static final Set<MediaType> SUPPORTED_TYPES = Collections
            .singleton(MediaType.application("x-rar-compressed")); //$NON-NLS-1$

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext arg0) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                new ParsingEmbeddedDocumentExtractor(context));

        TemporaryResources tmp = new TemporaryResources();
        Archive rar = null;
        boolean entryEncrypted = false;
        try {
            TreeMap<String, FileHeader> folderMap = new TreeMap<String, FileHeader>();
            TikaInputStream tis = TikaInputStream.get(stream, tmp);
            rar = new Archive(tis.getFile());
            if (rar.isEncrypted())
                throw new EncryptedDocumentException();

            FileHeader header = rar.nextFileHeader();
            while (header != null && !Thread.currentThread().isInterrupted()) {
                if (header.isEncrypted())
                    // entryEncrypted = true;
                    throw new EncryptedDocumentException();
                /*
                 * Adiciona diretórios a um treemap para serem percorridos em profundidade pois
                 * são retornados na ordem contrária pelo junrar
                 */
                if (header.isDirectory())
                    folderMap.put(header.getFileNameString(), header);
                header = rar.nextFileHeader();
            }
            // processa os diretórios em profundidade
            for (FileHeader dirheader : folderMap.values()) {
                String parent = Util.getParentPath(dirheader.getFileNameString());
                parseSubitem(rar, dirheader, parent, xhtml, extractor);
                if (Thread.currentThread().isInterrupted())
                    throw new InterruptedException();
            }
            folderMap.clear();
            rar.close();
            // processa os arquivos
            rar = new Archive(tis.getFile());
            do {
                header = rar.nextFileHeader();
                if (header != null && !header.isDirectory()) {
                    String parent = Util.getParentPath(header.getFileNameString());
                    parseSubitem(rar, header, parent, xhtml, extractor);
                }

            } while (header != null && !Thread.currentThread().isInterrupted());

        } catch (RarException | InterruptedException e) {
            throw new TikaException("RARParser Exception", e); //$NON-NLS-1$

        } finally {
            if (rar != null)
                rar.close();
            tmp.close();
            xhtml.endDocument();
            if (entryEncrypted)
                throw new EncryptedDocumentException();
        }

    }

    private void parseSubitem(Archive rar, FileHeader header, String parent, ContentHandler handler,
            EmbeddedDocumentExtractor extractor) throws RarException, IOException, SAXException {
        InputStream subFile = null;
        try {
            subFile = rar.getInputStream(header);
            Metadata entrydata = new Metadata();
            if (header.isDirectory())
                entrydata.set(ExtraProperties.EMBEDDED_FOLDER, "true"); //$NON-NLS-1$
            
            entrydata.set(Metadata.RESOURCE_NAME_KEY, header.getFileNameString().replace("\\", "/")); //$NON-NLS-1$ //$NON-NLS-2$
            entrydata.set(TikaCoreProperties.CREATED, header.getCTime());
            entrydata.set(TikaCoreProperties.MODIFIED, header.getMTime());
            entrydata.set(ExtraProperties.ACCESSED, header.getATime());
            entrydata.set(ExtraProperties.ITEM_VIRTUAL_ID, header.getFileNameString());
            entrydata.set(ExtraProperties.PARENT_VIRTUAL_ID, parent);
            
            if (extractor.shouldParseEmbedded(entrydata))
                extractor.parseEmbedded(subFile, handler, entrydata, true);

        } finally {
            if (subFile != null)
                subFile.close();
        }
    }

}
