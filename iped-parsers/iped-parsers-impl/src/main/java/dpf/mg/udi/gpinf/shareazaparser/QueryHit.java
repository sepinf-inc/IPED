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

/**
 * @author Fabio Melo Pfeifer <pfeifer.fmp@dpf.gov.br>
 */
class QueryHit extends ShareazaEntity {

    private String searchId;
    private int protocolId = 0;
    private String clientId;
    private String address;
    private int port;
    private long speed;
    private String sSpeed;
    private String sCode;
    private boolean push = false;
    private boolean busy = false;
    private boolean stable = false;
    private boolean measured = false;
    private int upslots;
    private int upqueue;
    private boolean chat = false;
    private boolean browsehost = false;

    private String sha1;
    private String tiger;
    private String ed2k;
    private String bth;
    private String md5;
    private String url;
    private String name;
    private long index = 0;
    private boolean bSize = false;

    private long size;
    private long hitSources;
    private long partial;
    private boolean preview = false;
    private String sPreview;
    private boolean collection = false;
    private String schemaURI;
    /* private String schemaPlural; */
    private final XMLElement xml = new XMLElement();
    private int rating;
    private String comments;

    private boolean matched = false;
    private boolean exactMatch = false;
    private boolean bogus = false;
    private boolean download = false;
    private String nick;

    public QueryHit() {
        super("QUERY HIT"); //$NON-NLS-1$
    }

    @Override
    public void read(MFCParser ar, int version) throws IOException {
        searchId = Util.encodeGUID(ar.readBytes(16));
        if (version >= 9) {
            protocolId = ar.readInt();
        }
        clientId = Util.encodeGUID(ar.readBytes(16));
        address = Util.encodeInAddr(ar.readBytes(4));
        port = ar.readUShort();
        speed = ar.readUInt();
        sSpeed = ar.readString();
        sCode = ar.readString();

        push = ar.readBool();
        busy = ar.readBool();
        stable = ar.readBool();
        measured = ar.readBool();
        upslots = ar.readInt();
        upqueue = ar.readInt();
        chat = ar.readBool();
        browsehost = ar.readBool();

        sha1 = ar.readHash(20);
        tiger = ar.readHash(24);
        ed2k = ar.readHash(16);

        if (version >= 13) {
            bth = ar.readHash(20, "base32"); //$NON-NLS-1$
            md5 = ar.readHash(16);
        }

        url = ar.readString();
        name = ar.readString();
        index = ar.readUInt();
        bSize = ar.readBool();

        if (version >= 10) {
            size = ar.readLong();
        } else {
            size = ar.readUInt();
        }

        hitSources = ar.readUInt();
        partial = ar.readUInt();
        preview = ar.readBool();
        sPreview = ar.readString();

        if (version >= 11) {
            collection = ar.readBool();
        }

        schemaURI = ar.readString();
        /* schemaPlural = */
        ar.readString();
        if (schemaURI.length() > 0) {
            xml.read(ar);
        }

        rating = ar.readInt();
        comments = ar.readString();

        matched = ar.readBool();
        if (version >= 12) {
            exactMatch = ar.readBool();
        }

        bogus = ar.readBool();
        download = ar.readBool();
        if (version >= 15) {
            nick = ar.readString();
        }

    }

    @Override
    protected void writeImpl(ShareazaOutputGenerator f) {
        f.out("Search ID: " + searchId); //$NON-NLS-1$
        f.out("Protocol ID: " + protocolId); //$NON-NLS-1$
        f.out("Client ID" + clientId); //$NON-NLS-1$
        f.out("Address: %s:%d", address, port); //$NON-NLS-1$
        f.out("Speed: %d (%s)", speed, sSpeed); //$NON-NLS-1$
        f.out("Code: " + sCode); //$NON-NLS-1$
        f.out("Push: " + push); //$NON-NLS-1$
        f.out("Busy: " + busy); //$NON-NLS-1$
        f.out("Stable: " + stable); //$NON-NLS-1$
        f.out("Measured: " + measured); //$NON-NLS-1$
        f.out("UPSlots: %d  UPQueue: %d", upslots, upqueue); //$NON-NLS-1$
        f.out("Chat: " + chat); //$NON-NLS-1$
        f.out("BrowseHost: " + browsehost); //$NON-NLS-1$
        f.out("SHA1: " + sha1); //$NON-NLS-1$
        f.out("Tiger: " + tiger); //$NON-NLS-1$
        f.out("ED2k: " + ed2k); //$NON-NLS-1$
        f.out("BTH: " + bth); //$NON-NLS-1$
        f.out("MD5: " + md5); //$NON-NLS-1$
        f.out("URL: " + url); //$NON-NLS-1$
        f.out("Name: " + name); //$NON-NLS-1$
        f.out("Index: " + index); //$NON-NLS-1$
        f.out("bSize: " + bSize); //$NON-NLS-1$
        f.out("Size: " + size); //$NON-NLS-1$
        f.out("Hit Sources: " + hitSources); //$NON-NLS-1$
        f.out("Partial: " + partial); //$NON-NLS-1$
        f.out("Has Preview: " + preview); //$NON-NLS-1$
        f.out("Preview: " + sPreview); //$NON-NLS-1$
        f.out("Collection: " + collection); //$NON-NLS-1$
        f.out("SchemaURI: " + schemaURI); //$NON-NLS-1$
        xml.write(f);
        f.out("Rating: " + rating); //$NON-NLS-1$
        f.out("Comments: " + comments); //$NON-NLS-1$
        f.out("Matched: " + matched); //$NON-NLS-1$
        f.out("Exact Match: " + exactMatch); //$NON-NLS-1$
        f.out("Bogus: " + bogus); //$NON-NLS-1$
        f.out("Download: " + download); //$NON-NLS-1$
        f.out("Nick: " + nick); //$NON-NLS-1$
    }

}
