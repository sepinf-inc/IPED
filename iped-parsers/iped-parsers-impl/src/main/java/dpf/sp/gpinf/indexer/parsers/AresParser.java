/*
 * Copyright 2015-2015, Wladimir Leite
 * 
 * This file is part of Indexador e Processador de Evidencias Digitais (IPED).
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
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaMetadataKeys;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.parsers.util.ChildPornHashLookup;
import dpf.sp.gpinf.indexer.parsers.util.Messages;
import gpinf.ares.AresEntry;
import iped3.io.IItemBase;
import iped3.search.IItemSearcher;
import iped3.util.ExtraProperties;
//ShareL.dat e ShareH.dat???
/**
 * Parser para arquivos ShareL.dat e ShareL.dat do Ares Galaxy.
 * 
 * @author Wladimir
 */
public class AresParser extends AbstractParser {
    private static final long serialVersionUID = -8593145271649456168L;

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MediaType.application("x-ares-galaxy")); //$NON-NLS-1$
    public static final String ARES_MIME_TYPE = "application/x-ares-galaxy"; //$NON-NLS-1$
    private static final String[] header = new String[] { Messages.getString("AresParser.Seq"), //$NON-NLS-1$
            Messages.getString("AresParser.Title"), Messages.getString("AresParser.Path"), //$NON-NLS-1$ //$NON-NLS-2$
            Messages.getString("AresParser.HashSha1"), Messages.getString("AresParser.FileDate"), //$NON-NLS-1$ //$NON-NLS-2$
            Messages.getString("AresParser.Size"), Messages.getString("AresParser.Shared"), //$NON-NLS-1$ //$NON-NLS-2$
            Messages.getString("AresParser.Corrupted"), Messages.getString("AresParser.Artist"), //$NON-NLS-1$ //$NON-NLS-2$
            Messages.getString("AresParser.Album"), Messages.getString("AresParser.Category"), //$NON-NLS-1$ //$NON-NLS-2$
            Messages.getString("AresParser.URL"), Messages.getString("AresParser.Comments"), //$NON-NLS-1$ //$NON-NLS-2$
            Messages.getString("AresParser.FoundInPedoHashDB"), Messages.getString("AresParser.FoundInCase")}; //$NON-NLS-1$ //$NON-NLS-2$

    private static final String strYes = Messages.getString("AresParser.Yes"); //$NON-NLS-1$
    private static final String strNo = Messages.getString("AresParser.No"); //$NON-NLS-1$
    
    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        final DecimalFormat nf = new DecimalFormat("#,##0"); //$NON-NLS-1$
        final DateFormat df = new SimpleDateFormat(Messages.getString("AresParser.DateFormat")); //$NON-NLS-1$
        df.setTimeZone(TimeZone.getTimeZone("GMT+0")); //$NON-NLS-1$

        metadata.set(HttpHeaders.CONTENT_TYPE, ARES_MIME_TYPE);
        metadata.remove(TikaMetadataKeys.RESOURCE_NAME_KEY);

        List<AresEntry> l = gpinf.ares.AresParser.parseToList(stream);
        if (l == null)
            return;
        metadata.set(ExtraProperties.P2P_REGISTRY_COUNT, String.valueOf(l.size()));
        if (l.isEmpty())
            return;

        IItemSearcher searcher = context.get(IItemSearcher.class);

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        xhtml.startElement("style"); //$NON-NLS-1$
        xhtml.characters(
                ".dt { border-collapse: collapse; font-family: Arial, sans-serif; width: 2400px; margin-right: 32px; margin-bottom: 32px; } " //$NON-NLS-1$
                        + ".rh { font-weight: bold; text-align: center; background-color:#AAAAEE; } " //$NON-NLS-1$
                        + ".ra { vertical-align: middle; } " //$NON-NLS-1$
                        + ".rb { background-color:#E7E7F0; vertical-align: middle; } " //$NON-NLS-1$
                        + ".rr { background-color:#E77770; vertical-align: middle; } " //$NON-NLS-1$
                        + ".s { border: solid; border-width: thin; padding: 3px; text-align: center; vertical-align: middle; word-wrap: break-word; width: 80px; } " //$NON-NLS-1$
                        + ".e { border: solid; border-width: thin; padding: 3px; text-align: center; vertical-align: middle; word-wrap: break-word; width: 150px; font-family: monospace; } " //$NON-NLS-1$
                        + ".a { border: solid; border-width: thin; padding: 3px; text-align: center; vertical-align: middle; word-wrap: break-word; width: 110px; } " //$NON-NLS-1$
                        + ".b { border: solid; border-width: thin; padding: 3px; text-align: left; vertical-align: middle; word-wrap: break-word; word-break: break-all; width: 450px; } " //$NON-NLS-1$
                        + ".z { border: solid; border-width: thin; padding: 3px; text-align: left; vertical-align: middle; word-wrap: break-word; word-break: break-all; width: 160px; } " //$NON-NLS-1$
                        + ".c { border: solid; border-width: thin; padding: 3px; text-align: right; vertical-align: middle; word-wrap: break-word;  width: 110px; } "); //$NON-NLS-1$
        xhtml.endElement("style"); //$NON-NLS-1$
        xhtml.newline();

        xhtml.startElement("p");
        xhtml.characters(Messages.getString("P2P.FoundInPedoHashDB"));
        xhtml.endElement("p");
        xhtml.newline();

        xhtml.startElement("table", "class", "dt"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        int cont = 1;
        List<String> cells = new ArrayList<String>();

        boolean[] present = new boolean[header.length];
        present[0] = present[6] = present[7]  = present[13]  = present[14] =  true;
        for (int i = 1; i < header.length; i++) {
            boolean found = false;
            for (AresEntry e : l) {
                if (i == 1 && isUsed(e.getTitle()))
                    found = true;
                if (i == 2 && isUsed(e.getPath()))
                    found = true;
                if (i == 3 && isUsed(e.getHash()))
                    found = true;
                if (i == 4 && e.getDate() != null)
                    found = true;
                if (i == 5 && e.getFileSize() != 0)
                    found = true;
                if (i == 8 && isUsed(e.getArtist()))
                    found = true;
                if (i == 9 && isUsed(e.getAlbum()))
                    found = true;
                if (i == 10 && isUsed(e.getCategory()))
                    found = true;
                if (i == 11 && isUsed(e.getUrl()))
                    found = true;
                if (i == 12 && isUsed(e.getComment()))
                    found = true;
                if (found)
                    break;
            }
            if (found)
                present[i] = true;
        }

        int hashAlertHits = 0;
        String[] colClass = new String[header.length];
        Arrays.fill(colClass, "a"); //$NON-NLS-1$        
        colClass[0] = "s"; //$NON-NLS-1$
        
        for (int i = -1; i < l.size(); i++) {
            cells.clear();
            String trClass = ""; //$NON-NLS-1$
            AresEntry e = null;
            if (i == -1) {
                for (int j = 0; j < header.length; j++) {
                    cells.add(header[j]);
                }
                trClass = "rh"; //$NON-NLS-1$
            } else {
                if (i % 2 == 0)
                    trClass = "ra"; //$NON-NLS-1$
                else
                    trClass = "rb"; //$NON-NLS-1$
                e = l.get(i);
                cells.add(String.valueOf(cont++));
                cells.add(e.getTitle());
                cells.add(e.getPath());
                String hash = e.getHash();
                if (e.isShared())
                    metadata.add(ExtraProperties.SHARED_HASHES, hash);
                List<String> hashSets = ChildPornHashLookup.lookupHash("sha-1", hash);
                if (!hashSets.isEmpty()) {
                    hashAlertHits++;
                    trClass = "rr"; //$NON-NLS-1$
                }
                cells.add(hash.substring(0, hash.length() / 2) + " " + hash.substring(hash.length() / 2)); //$NON-NLS-1$
                cells.add(e.getDate() == null ? "-" : df.format(e.getDate())); //$NON-NLS-1$
                cells.add(e.getFileSize() == 0 ? "-" : nf.format(e.getFileSize())); //$NON-NLS-1$
                cells.add(e.isShared() ? strYes : strNo);
                cells.add(e.isCorrupted() ?  strYes : strNo);
                cells.add(e.getArtist());
                cells.add(e.getAlbum());
                cells.add(e.getCategory());
                cells.add(e.getUrl());
                cells.add(e.getComment());
                cells.add(!hashSets.isEmpty() ? hashSets.toString() : ""); // $NON-NLS-1$
                cells.add(" "); //$NON-NLS-1$

                colClass[1] = colClass[2] = "b"; //$NON-NLS-1$
                colClass[3] = "e";
                colClass[5] = "c";
                Arrays.fill(colClass, 8, 13, "z");
            }

            xhtml.startElement("tr", "class", trClass); //$NON-NLS-1$ //$NON-NLS-2$
            for (int j = 0; j < cells.size(); j++) {
                if (present[j]) {
                    xhtml.startElement("td", "class", colClass[j]); //$NON-NLS-1$ //$NON-NLS-2$
                    if (i < 0 || i >= l.size())
                        xhtml.startElement("b"); //$NON-NLS-1$
                    String s = cells.get(j);
                    if (s == null || s.isEmpty())
                        s = " "; //$NON-NLS-1$
                    if (j != 1 || e == null)
                        xhtml.characters(s);
                    else {
                        IItemBase item = KnownMetParser.searchItemInCase(searcher, "sha-1", e.getHash());
                        if (item != null) {
                            KnownMetParser.printNameWithLink(xhtml, item, s);
                            cells.set(cells.size() - 1, strYes);
                        } else {
                            xhtml.characters(s);
                        }
                    }
                    
                    if (i < 0 || i >= l.size())
                        xhtml.endElement("b"); //$NON-NLS-1$
                    xhtml.endElement("td"); //$NON-NLS-1$
                }
            }
            xhtml.endElement("tr"); //$NON-NLS-1$
            xhtml.newline();
        }

        if (hashAlertHits > 0)
            metadata.set(ExtraProperties.CSAM_HASH_HITS, Integer.toString(hashAlertHits));

        xhtml.endElement("table"); //$NON-NLS-1$
        xhtml.endDocument();
    }

    private boolean isUsed(String s) {
        return s != null && !s.isEmpty();
    }
}