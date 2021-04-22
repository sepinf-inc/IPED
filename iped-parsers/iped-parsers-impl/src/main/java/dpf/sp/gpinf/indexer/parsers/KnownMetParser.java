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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
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
import org.xml.sax.helpers.AttributesImpl;

import dpf.sp.gpinf.indexer.parsers.util.ChildPornHashLookup;
import dpf.sp.gpinf.indexer.parsers.util.ExportFolder;
import dpf.sp.gpinf.indexer.parsers.util.Messages;
import gpinf.emule.KnownMetEntry;
import iped3.io.IItemBase;
import iped3.search.IItemSearcher;
import iped3.util.BasicProps;
import iped3.util.ExtraProperties;

/**
 * Parser para arquivos known.met do e-Mule, que armazena arquivos conhecidos,
 * com dados de compartilhamento e transmissão.
 * 
 * @author Wladimir
 */
public class KnownMetParser extends AbstractParser {
    private static final long serialVersionUID = 5039027273156031902L;

    public static final String EDONKEY = "edonkey";

    public static final String EMULE_MIME_TYPE = "application/x-emule"; //$NON-NLS-1$
    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MediaType.parse(EMULE_MIME_TYPE));

    private static final String[] header = new String[] { Messages.getString("KnownMetParser.Seq"), //$NON-NLS-1$
            Messages.getString("KnownMetParser.Name"), //$NON-NLS-1$
            Messages.getString("KnownMetParser.Hash"), //$NON-NLS-1$
            Messages.getString("KnownMetParser.LastModDate"), //$NON-NLS-1$
            Messages.getString("KnownMetParser.LastPubDate"), //$NON-NLS-1$
            Messages.getString("KnownMetParser.LastShareDate"), //$NON-NLS-1$
            Messages.getString("KnownMetParser.Size"), //$NON-NLS-1$
            Messages.getString("KnownMetParser.Requests"), //$NON-NLS-1$
            Messages.getString("KnownMetParser.AcceptedRequests"), //$NON-NLS-1$
            Messages.getString("KnownMetParser.BytesSent"), //$NON-NLS-1$
            Messages.getString("KnownMetParser.TempFile"), //$NON-NLS-1$
            Messages.getString("KnownMetParser.FoundInPedoHashDB"), //$NON-NLS-1$
            Messages.getString("KnownMetParser.FoundInCase")}; //$NON-NLS-1$

    private static final String strYes = Messages.getString("KnownMetParser.Yes"); //$NON-NLS-1$
    
    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        final DecimalFormat nf = new DecimalFormat("#,##0"); //$NON-NLS-1$
        final DateFormat df = new SimpleDateFormat(Messages.getString("KnownMetParser.DataFormat")); //$NON-NLS-1$
        df.setTimeZone(TimeZone.getTimeZone("GMT+0")); //$NON-NLS-1$

        metadata.set(HttpHeaders.CONTENT_TYPE, EMULE_MIME_TYPE);
        metadata.remove(TikaMetadataKeys.RESOURCE_NAME_KEY);

        List<KnownMetEntry> l = gpinf.emule.KnownMetParser.parseToList(stream);
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
                ".dt {border-collapse: collapse; font-family: Arial, sans-serif; width: 1800px; margin-right: 32px; margin-bottom: 32px; } " //$NON-NLS-1$
                        + ".rh { font-weight: bold; text-align: center; background-color:#AAAAEE; vertical-align: middle; } " //$NON-NLS-1$
                        + ".ra { vertical-align: middle; } " //$NON-NLS-1$
                        + ".rb { background-color:#E7E7F0; vertical-align: middle; } " //$NON-NLS-1$
                        + ".rr { background-color:#E77770; vertical-align: middle; } " //$NON-NLS-1$
                        + ".s { border: solid; border-width: thin; padding: 3px; text-align: center; vertical-align: middle; word-wrap: break-word; width: 80px; } " //$NON-NLS-1$
                        + ".e { border: solid; border-width: thin; padding: 3px; text-align: center; vertical-align: middle; word-wrap: break-word; width: 140px; font-family: monospace; } " //$NON-NLS-1$
                        + ".a { border: solid; border-width: thin; padding: 3px; text-align: center; vertical-align: middle; word-wrap: break-word; width: 110px; } " //$NON-NLS-1$
                        + ".b { border: solid; border-width: thin; padding: 3px; text-align: left; vertical-align: middle; word-wrap: break-word; word-break: break-all; width: 420px; } " //$NON-NLS-1$
                        + ".c { border: solid; border-width: thin; padding: 3px; text-align: right; vertical-align: middle; word-wrap: break-word;  width: 110px; }"); //$NON-NLS-1$
        xhtml.endElement("style"); //$NON-NLS-1$
        xhtml.newline();

        xhtml.startElement("p");
        xhtml.characters(Messages.getString("P2P.FoundInPedoHashDB"));
        xhtml.endElement("p");
        xhtml.newline();
        
        xhtml.startElement("table", "class", "dt"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        int cont = 1;
        List<String> cells = new ArrayList<String>();
        long totReq = 0;
        long accReq = 0;
        long bytTrf = 0;
        int hashDBHits = 0;
        String[] colClass = new String[header.length];
        Arrays.fill(colClass, "a"); //$NON-NLS-1$
        colClass[0] = "s"; //$NON-NLS-1$
        colClass[1] = "b"; //$NON-NLS-1$
        for (int i = -1; i <= l.size(); i++) {
            cells.clear();
            String trClass = ""; //$NON-NLS-1$
            KnownMetEntry e = null;
            IItemBase item = null;
            if (i == -1) {
                for (int j = 0; j < header.length; j++) {
                    cells.add(header[j]);
                }
                trClass = "rh"; //$NON-NLS-1$
            } else if (i == l.size()) {
                cells.add(Messages.getString("KnownMetParser.Totals")); //$NON-NLS-1$
                cells.add(" "); //$NON-NLS-1$
                cells.add(" "); //$NON-NLS-1$
                cells.add(" "); //$NON-NLS-1$
                cells.add(" "); //$NON-NLS-1$
                cells.add(" "); //$NON-NLS-1$
                cells.add(" "); //$NON-NLS-1$
                cells.add(toFormatStr(nf, totReq));
                cells.add(toFormatStr(nf, accReq));
                cells.add(toFormatStr(nf, bytTrf));
                cells.add(" "); //$NON-NLS-1$
                cells.add(" "); //$NON-NLS-1$
                cells.add(" "); //$NON-NLS-1$
                trClass = "rh"; //$NON-NLS-1$
            } else {
                colClass[2] = "e";
                Arrays.fill(colClass, 6, 10, "c");
                if (i % 2 == 0)
                    trClass = "ra"; //$NON-NLS-1$
                else
                    trClass = "rb"; //$NON-NLS-1$
                e = l.get(i);
                cells.add(String.valueOf(cont++));
                cells.add(e.getName());
                String hash = e.getHash();
                metadata.add(ExtraProperties.SHARED_HASHES, hash);
                HashSet<String> hashSets = new HashSet<>();
                hashSets.addAll(ChildPornHashLookup.lookupHash(EDONKEY, hash));
                item = searchItemInCase(searcher, EDONKEY, e.getHash());
                if(item != null) {
                    hashSets.addAll(ChildPornHashLookup.lookupHash(item.getHash()));
                }
                if (!hashSets.isEmpty()) {
                    hashDBHits++;
                    trClass = "rr"; //$NON-NLS-1$
                }
                cells.add(hash.substring(0, hash.length() / 2) + " " + hash.substring(hash.length() / 2)); //$NON-NLS-1$
                cells.add(e.getLastModified() == null ? " " : df.format(e.getLastModified())); //$NON-NLS-1$
                cells.add(e.getLastPublishedKad() == null ? " " : df.format(e.getLastPublishedKad())); //$NON-NLS-1$
                cells.add(e.getLastShared() == null ? " " : df.format(e.getLastShared())); //$NON-NLS-1$
                cells.add(toFormatStr(nf, e.getFileSize()));
                cells.add(toFormatStr(nf, e.getTotalRequests()));
                cells.add(toFormatStr(nf, e.getAcceptedRequests()));
                cells.add(toFormatStr(nf, e.getBytesTransfered()));
                cells.add(e.getPartName() == null ? " " : e.getPartName()); //$NON-NLS-1$
                cells.add(!hashSets.isEmpty() ? hashSets.toString() : ""); // $NON-NLS-1$
                cells.add(" "); //$NON-NLS-1$
                totReq += toSum(e.getTotalRequests());
                accReq += toSum(e.getAcceptedRequests());
                bytTrf += toSum(e.getBytesTransfered());
            }

            xhtml.startElement("tr", "class", trClass); //$NON-NLS-1$ //$NON-NLS-2$
            for (int j = 0; j < cells.size(); j++) {
                xhtml.startElement("td", "class", colClass[j]); //$NON-NLS-1$ //$NON-NLS-2$
                if (i < 0 || i >= l.size())
                    xhtml.startElement("b"); //$NON-NLS-1$
                if (j != 1 || e == null)
                    xhtml.characters(cells.get(j));
                else {
                    if (item != null) {
                        printNameWithLink(xhtml, item, e.getName());
                        cells.set(cells.size() - 1, strYes);
                    } else {
                        xhtml.characters(e.getName());
                    }
                }
                if (i < 0 || i >= l.size())
                    xhtml.endElement("b"); //$NON-NLS-1$
                xhtml.endElement("td"); //$NON-NLS-1$
            }
            xhtml.endElement("tr"); //$NON-NLS-1$
            xhtml.newline();
        }

        if (hashDBHits > 0)
            metadata.set(ExtraProperties.CSAM_HASH_HITS, Integer.toString(hashDBHits));

        xhtml.endElement("table"); //$NON-NLS-1$
        xhtml.endDocument();
    }

    public static IItemBase searchItemInCase(IItemSearcher searcher, String hashAlgo, String hash) {
        if (searcher == null) {
            return null;
        }
        List<IItemBase> items = searcher.search(hashAlgo + ":" + hash); //$NON-NLS-1$
        if (items == null || items.isEmpty()) {
            return null;
        }
        return items.get(0);
    }

    public static void printNameWithLink(XHTMLContentHandler xhtml, IItemBase item, String name) throws SAXException {
        String hashPath = getPathFromHash(new File("../../../../", ExportFolder.getExportPath()), //$NON-NLS-1$
                item.getHash(), item.getExt());

        AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute("", "onclick", "onclick", "CDATA", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                "app.open(\"" + BasicProps.HASH + ":" + item.getHash() + "\")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        attributes.addAttribute("", "href", "href", "CDATA", hashPath); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        xhtml.startElement("a", attributes); //$NON-NLS-1$
        xhtml.characters(name);
        xhtml.endElement("a"); //$NON-NLS-1$
    }

    private static String getPathFromHash(File baseDir, String hash, String ext) {
        if (hash == null || hash.length() < 2)
            return ""; //$NON-NLS-1$
        StringBuilder path = new StringBuilder();
        hash = hash.toUpperCase();
        path.append(hash.charAt(0)).append('/');
        path.append(hash.charAt(1)).append('/');
        path.append(hash).append('.').append(ext);
        File result = new File(baseDir, path.toString());
        return result.getPath();
    }

    private long toSum(long v) {
        return v == -1 ? 0 : v;
    }

    private String toFormatStr(NumberFormat nf, long v) {
        return v == -1 ? "-" : nf.format(v); //$NON-NLS-1$
    }
}