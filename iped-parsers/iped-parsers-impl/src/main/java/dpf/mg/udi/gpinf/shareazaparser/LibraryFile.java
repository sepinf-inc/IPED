/*
 * Copyright 2015-2015, Fabio Melo Pfeifer
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
package dpf.mg.udi.gpinf.shareazaparser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import dpf.sp.gpinf.indexer.parsers.KnownMetParser;
import dpf.sp.gpinf.indexer.parsers.util.ChildPornHashLookup;
import iped3.io.IItemBase;
import iped3.search.IItemSearcher;

/**
 * @author Fabio Melo Pfeifer <pfeifer.fmp@dpf.gov.br>
 */
class LibraryFile extends ShareazaEntity {

    private final XMLElement metadata = new XMLElement();
    private final List<SharedSource> sharedSources = new ArrayList<>();
    private String name;
    private long index;
    private long size;
    private String time;
    private String shared;
    private long virtualSize;
    private long virtualBase;
    private String sha1;
    private String tiger;
    private String md5;
    private String ed2k;
    private String bth;
    private String verify;
    private String uri;
    private boolean metadataAuto;
    private String metadataTime;
    private int rating;
    private String comments;
    private String shareTags;
    private boolean metadataModified;
    private long hitsTotal;
    private long uploadsTotal;
    private boolean cachedPreview;
    private boolean bogus;
    private final LibraryFolder parentFolder;
    private HashSet<String> hashSetHits = new HashSet<>();

    public LibraryFile(LibraryFolder parentFolder) {
        super("LIBRARY FILE"); //$NON-NLS-1$
        this.parentFolder = parentFolder;
    }

    public String getInheritedShared() {
        String resp = shared;
        if (resp.equals("Unknown") && parentFolder != null) { //$NON-NLS-1$
            resp = parentFolder.getInheritedShared();
        }
        return resp;
    }

    @Override
    public void read(MFCParser ar, int version) throws IOException {
        name = ar.readString();
        index = ar.readUInt();

        if (version >= 17) {
            size = ar.readLong();
        } else {
            size = ar.readUInt();
        }

        time = ar.readEpochDateTime();

        if (version >= 5) {
            shared = Util.decodeTriState(ar.readInt());
        } else {
            if (ar.readByte() == 0) {
                shared = "True"; //$NON-NLS-1$
            } else {
                shared = "Unknown"; //$NON-NLS-1$
            }
        }

        if (version >= 21) {
            virtualSize = ar.readLong();
            if (virtualSize != 0) {
                virtualBase = ar.readLong();
            }
        }

        sha1 = ar.readHash(20);

        if (version >= 8) {
            tiger = ar.readHash(24);
        }
        if (version >= 11) {
            md5 = ar.readHash(16);
            ed2k = ar.readHash(16);
        }
        if (version >= 26) {
            bth = ar.readHash(20, "base32"); //$NON-NLS-1$
        }
        if (version >= 4) {
            verify = Util.decodeTriState(ar.readInt());
        }

        uri = ar.readString();
        if (uri.length() > 0) {
            if (version < 27) {
                metadataAuto = ar.readBool();
                if (!metadataAuto) {
                    metadataTime = ar.readEpochDateTime();
                }
            }
            metadata.read(ar);
        }

        if (version >= 13) {
            rating = ar.readInt();
            comments = ar.readString();
            if (version >= 16) {
                shareTags = ar.readString();
            }
            if (version >= 27) {
                metadataAuto = ar.readBool();
                metadataTime = ar.readEpochDateTime();
            } else {
                if (metadataAuto && (rating != 0 || comments.length() > 0)) {
                    metadataTime = ar.readEpochDateTime();
                }
            }
        }
        metadataModified = false;
        hitsTotal = ar.readUInt();
        uploadsTotal = ar.readUInt();
        if (version >= 14) {
            cachedPreview = ar.readBool();
        }
        if (version >= 20) {
            bogus = ar.readBool();
        }
        if (version >= 2) {
            int n = ar.readCount();
            for (int i = 0; i < n; i++) {
                SharedSource source = new SharedSource();
                source.read(ar, version);
                sharedSources.add(source);
            }
        }

    }

    @Override
    protected void writeImpl(ShareazaOutputGenerator f) {
        f.out("Name: " + name); //$NON-NLS-1$
        f.out("Index: " + index); //$NON-NLS-1$
        f.out("Size: " + size); //$NON-NLS-1$
        f.out("Time: " + time); //$NON-NLS-1$
        f.out("Shared: " + getInheritedShared()); //$NON-NLS-1$
        f.out("Virtual Size: %d, Virtual Base: %d", virtualSize, virtualBase); //$NON-NLS-1$
        f.out("SHA1: " + sha1); //$NON-NLS-1$
        f.out("Tiger: " + tiger); //$NON-NLS-1$
        f.out("MD5: " + md5); //$NON-NLS-1$
        f.out("ED2K: " + ed2k); //$NON-NLS-1$
        f.out("BTH: " + bth); //$NON-NLS-1$
        f.out("Verify: " + verify); //$NON-NLS-1$
        f.out("URI: " + uri); //$NON-NLS-1$
        f.out("Metadata Auto: %s, Metadata Time: %s, Metadata Modified: %s", "" + metadataAuto, metadataTime, //$NON-NLS-1$ //$NON-NLS-2$
                "" + metadataModified); //$NON-NLS-1$
        metadata.write(f);
        f.out("Rating: " + rating); //$NON-NLS-1$
        f.out("Comments: " + comments); //$NON-NLS-1$
        f.out("Share Tages: " + shareTags); //$NON-NLS-1$
        f.out("Hits Total: " + hitsTotal); //$NON-NLS-1$
        f.out("Uploads Total: " + uploadsTotal); //$NON-NLS-1$
        f.out("Cached Preview: " + cachedPreview); //$NON-NLS-1$
        f.out("Bogus: " + bogus); //$NON-NLS-1$
        for (SharedSource source : sharedSources) {
            source.write(f);
        }
    }

    public boolean isShared() {
        return "True".equals(getInheritedShared()); //$NON-NLS-1$
    }

    public void printTableRow(XHTMLContentHandler html, String path, IItemSearcher searcher) throws SAXException {

        hashSetHits.addAll(ChildPornHashLookup.lookupHash(md5));
        hashSetHits.addAll(ChildPornHashLookup.lookupHash(sha1));

        AttributesImpl attributes = new AttributesImpl();
        if (md5 != null && !md5.isEmpty()) {
            attributes.addAttribute("", "name", "name", "CDATA", md5.toUpperCase());
        }
        if (!hashSetHits.isEmpty()) {
            attributes.addAttribute("", "class", "class", "CDATA", "r");
        }
        html.startElement("tr", attributes);

        printTd(html, searcher, path, name, index, size, time, getInheritedShared(), virtualSize, virtualBase, sha1,
                tiger, md5, ed2k, bth, verify, uri, metadataAuto, metadataTime, metadataModified, rating, comments,
                shareTags, hitsTotal, uploadsTotal, cachedPreview, bogus);

        html.endElement("tr"); //$NON-NLS-1$
    }

    private void printTd(XHTMLContentHandler html, IItemSearcher searcher, Object... tdtext) throws SAXException {
        int col = 0;
        Boolean foundInCase = false;
        for (Object o : tdtext) {
            html.startElement("td"); //$NON-NLS-1$
            if (o != null) {
                if (col != 1) {
                    html.characters(o.toString());
                } else {
                    IItemBase item = KnownMetParser.searchItemInCase(searcher, "md5", md5);
                    if (item != null) {
                        KnownMetParser.printNameWithLink(html, item, name);
                        foundInCase = true;
                    } else {
                        html.characters(name);
                    }
                }
            }
            html.endElement("td"); //$NON-NLS-1$
            col++;
        }
        html.startElement("td"); //$NON-NLS-1$
        if (!hashSetHits.isEmpty()) {
            html.characters(hashSetHits.toString());
        }
        html.endElement("td"); //$NON-NLS-1$
        html.startElement("td"); //$NON-NLS-1$
        html.characters(foundInCase.toString());
        html.endElement("td"); //$NON-NLS-1$
    }

    public boolean isHashDBHit() {
        return !hashSetHits.isEmpty();
    }

    public long getSize() {
        return size;
    }

    public String getMd5() {
        return md5;
    }

    public String getSha1() {
        return sha1;
    }

}
