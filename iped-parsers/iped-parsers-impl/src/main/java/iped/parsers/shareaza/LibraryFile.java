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
package iped.parsers.shareaza;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import iped.data.IItemReader;
import iped.parsers.util.ChildPornHashLookup;
import iped.parsers.util.P2PUtil;
import iped.search.IItemSearcher;

/**
 * @author Fabio Melo Pfeifer <pfeifer.fmp@dpf.gov.br>
 */
public class LibraryFile extends ShareazaEntity {

    private final XMLElement metadata = new XMLElement();
    private final List<SharedSource> sharedSources = new ArrayList<>();
    private String name;
    private int index;
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
    private List<String> hashSetHits;

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
        index = (int) ar.readUInt();

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

    public void printTableRow(XHTMLContentHandler html, String path, IItemSearcher searcher, Map<Integer, List<String>> albunsForFiles) throws SAXException {

        hashSetHits = ChildPornHashLookup.lookupHashAndMerge(md5, hashSetHits);
        hashSetHits = ChildPornHashLookup.lookupHashAndMerge(sha1, hashSetHits);

        AttributesImpl attributes = new AttributesImpl();
        if (md5 != null && !md5.isEmpty()) {
            attributes.addAttribute("", "name", "name", "CDATA", md5.toUpperCase());
        }
        if (hashSetHits != null && !hashSetHits.isEmpty()) {
            attributes.addAttribute("", "class", "class", "CDATA", "r");
        }
        html.startElement("tr", attributes);

        printTd(html, searcher, path, name, albunsForFiles.get(index), index, size, time, getInheritedShared(), virtualSize, virtualBase, sha1, tiger, md5, ed2k, bth, verify, uri, metadataAuto, metadataTime, metadataModified, rating,
                comments, shareTags, hitsTotal, uploadsTotal, cachedPreview, bogus);

        html.endElement("tr"); //$NON-NLS-1$
    }

    private void printTd(XHTMLContentHandler html, IItemSearcher searcher, Object... tdtext) throws SAXException {
        int col = 0;
        Boolean foundInCase = false;
        for (Object o : tdtext) {
            html.startElement("td"); //$NON-NLS-1$
            if (o != null) {
                if (col == 1) {
                    IItemReader item = P2PUtil.searchItemInCase(searcher, "md5", md5);
                    if (item != null) {
                        P2PUtil.printNameWithLink(html, item, name);
                        foundInCase = true;
                    } else {
                        html.characters(name);
                    }
                } else if (col == 2) {
                    @SuppressWarnings("unchecked")
                    List<String> albums = (List<String>) o;
                    boolean first = true;
                    for (String album : albums) {
                        if (!first) {
                            html.characters(" | ");
                        }
                        html.characters(album);
                        first = false;

                    }
                } else {
                    html.characters(o.toString());
                }
            }
            html.endElement("td"); //$NON-NLS-1$
            col++;
        }
        html.startElement("td"); //$NON-NLS-1$
        if (hashSetHits != null && !hashSetHits.isEmpty()) {
            html.characters(hashSetHits.toString());
        }
        html.endElement("td"); //$NON-NLS-1$
        html.startElement("td"); //$NON-NLS-1$
        html.characters(foundInCase.toString());
        html.endElement("td"); //$NON-NLS-1$
    }

    public boolean isHashDBHit() {
        return hashSetHits != null && !hashSetHits.isEmpty();
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

    public int getIndex() {
        return index;
    }

    public List<SharedSource> getSharedSources() {
        return sharedSources;
    }

    public String getName() {
        return name;
    }

    public String getTime() {
        return time;
    }

    public String getShared() {
        return shared;
    }

    public long getVirtualSize() {
        return virtualSize;
    }

    public long getVirtualBase() {
        return virtualBase;
    }

    public String getTiger() {
        return tiger;
    }

    public String getEd2k() {
        return ed2k;
    }

    public String getBth() {
        return bth;
    }

    public String getVerify() {
        return verify;
    }

    public String getUri() {
        return uri;
    }

    public boolean isMetadataAuto() {
        return metadataAuto;
    }

    public String getMetadataTime() {
        return metadataTime;
    }

    public int getRating() {
        return rating;
    }

    public String getComments() {
        return comments;
    }

    public String getShareTags() {
        return shareTags;
    }

    public boolean isMetadataModified() {
        return metadataModified;
    }

    public long getHitsTotal() {
        return hitsTotal;
    }

    public long getUploadsTotal() {
        return uploadsTotal;
    }

    public boolean isCachedPreview() {
        return cachedPreview;
    }

    public boolean isBogus() {
        return bogus;
    }

    public LibraryFolder getParentFolder() {
        return parentFolder;
    }

    public List<String> getHashSetHits() {
        return hashSetHits == null ? Collections.emptyList() : hashSetHits;
    }

    public void setIndex(int index) {
        this.index = index;
    }

}
