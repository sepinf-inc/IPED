/*
 * Copyright 2020-2020, João Vitor de Sá Hauck
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
package iped.parsers.telegram;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.ap.gpinf.interfacetelegram.PhotoData;

/**
 * @author ADMHauck
 * @author Wladimir Leite
 */
public class PostBoxCoding {

    private static final Logger logger = LoggerFactory.getLogger(PostBoxCoding.class);

    private static final int tInt32 = 0;
    private static final int tInt64 = 1;
    private static final int tBool = 2;
    private static final int tDouble = 3;
    private static final int tString = 4;
    private static final int tObject = 5;
    private static final int tInt32Array = 6;
    private static final int tInt64Array = 7;
    private static final int tObjectArray = 8;
    private static final int tObjectDictionary = 9;
    private static final int tBytes = 10;
    private static final int tNil = 11;
    private static final int tStringArray = 12;
    private static final int tBytesArray = 13;

    private static final int minTimestamp = 631152000;

    private byte[] data;

    private int offset;

    public PostBoxCoding() {
    }

    public PostBoxCoding(byte[] data) {
        setData(data);
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    private int readInt32() {
        return readInt32(true);
    }

    private long readInt64() {
        return readInt64(true);
    }

    private int readInt32(boolean bigEndian) {
        try {
            int i = 0;
            byte len = 4;
            for (int j = 0; j < len; j++) {
                int a = data[offset++] & 0xFF;
                if (bigEndian) {
                    i |= (a << (j * 8));
                } else {
                    i |= (a << ((len - j - 1) * 8));
                }
            }
            return i;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    private long readInt64(boolean bigEndian) {
        try {
            long i = 0;
            byte len = 8;
            for (int j = 0; j < len; j++) {
                long a = data[offset++] & 0xFF;
                if (bigEndian) {
                    i |= (a << (j * 8L));
                } else {
                    i |= (a << ((len - j - 1) * 8L));
                }
            }
            return i;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    private String readString() {
        int len = readInt32();
        return readString(len);
    }

    private String readKeyString() {
        int len = readNextByte() & 0xFF;
        return readString(len);
    }

    private String readString(int len) {
        if (offset + len > data.length || len == 0) {
            return null;
        }
        String str = new String(data, offset, len, StandardCharsets.UTF_8);
        offset += len;
        return str;
    }

    private static boolean testbit(int data, int bit) {
        return (data & (1 << bit)) != 0;
    }

    @SuppressWarnings("unused")
    private void readForwardInfo(byte forwardInfoFlags) {
        long forwardAuthorId = readInt64();
        int forwardDate = readInt32();
        if (testbit(forwardInfoFlags, 1)) {
            long sourceID = readInt64();
        }

        if (testbit(forwardInfoFlags, 2)) {
            long MessagePeerId = readInt64();
            int forwardSourceMessageNamespace = readInt32();
            int forwardSourceMessageId = readInt32();
        }

        if (testbit(forwardInfoFlags, 3)) {
            String authorSignature = readString();
        }

        if (testbit(forwardInfoFlags, 4)) {
            String psaType = readString();
        }

        if (testbit(forwardInfoFlags, 5)) {
            int flags = readInt32();
        }
    }

    private List<byte[]> readArray() {
        int nel = readInt32();
        ArrayList<byte[]> els = new ArrayList<>();
        for (int i = 0; i < nel; i++) {
            int size = readInt32();
            if (offset + size <= data.length) {
                els.add(Arrays.copyOfRange(data, offset, offset + size));
                offset += size;
            }
        }
        return els;
    }

    private List<byte[]> readArray(int size) {
        int nel = readInt32();
        ArrayList<byte[]> els = new ArrayList<>();
        for (int i = 0; i < nel; i++) {
            if (offset + size <= data.length) {
                els.add(Arrays.copyOfRange(data, offset, offset + size));
                offset += size;
            }
        }
        return els;
    }

    private long[] readInt64Array() {
        int nel = readInt32();
        long els[] = new long[nel];
        for (int i = 0; i < nel; i++) {
            long val = readInt64();
            els[i] = val;
        }
        return els;
    }

    private List<PhotoData> getPhotos(PostBoxObject[] arr) {
        List<PhotoData> photos = new ArrayList<>();

        for (PostBoxObject a : arr) {
            PostBoxObject photo = a.getPostBoxObject("r");
            if (photo != null) {
                long id = photo.getLong("i");
                long volume = photo.getLong("v");
                int local = photo.getInteger("l");
                int size = photo.getInteger("n");

                Photo f = null;
                if (id != 0) {
                    f = new Photo();
                    f.setName(String.valueOf(id));
                    f.setSize(size);
                }

                if (volume != 0 && local != 0) {
                    f = new Photo();
                    f.setName(volume + "_" + local);
                    f.setSize(size);
                }
                if (f != null) {
                    boolean seen = false;
                    for (PhotoData p : photos) {
                        if (p.getSize() == f.getSize() && p.getName().equals(f.getName())) {
                            seen = true;
                            break;
                        }
                    }
                    if (!seen) {
                        photos.add(f);
                    }
                }
            }
        }

        return photos;
    }

    private void readPeersIds(Message m, byte[] d) {
        if (m == null || d == null)
            return;
        PostBoxCoding peersDec = new PostBoxCoding(d);
        long peers[] = peersDec.readInt64Array();

        String message = m.getData();
        if (message == null) {
            message = "Id ";
        }
        boolean first = true;
        for (long peer : peers) {
            if (!first) {
                message += ", ";
            } else {
                first = false;
            }
            message += peer;
        }
        m.setData(message);
    }

    private byte readNextByte() {
        if (offset < data.length) {
            return data[offset++];
        }
        return 0;
    }

    private void readMedia(Message m) {
        PostBoxObject obj = readPostBoxObject(true);
        PostBoxObject media = obj.getPostBoxObject("_");

        if (media != null) {
            String phone = media.getString("pn");
            if (phone != null) {
                String aux = m.getData();
                aux += "Phone: " + phone;
                aux += media.getString("vc");
                m.setData(aux);
            }

            double lat = media.getDouble("la");
            if (lat != 0) {
                double lon = media.getDouble("lo");
                if (lon != 0) {
                    m.setLatitude(lat);
                    m.setLongitude(lon);
                    m.setMediaMime("geo");
                    return;
                }
            }

            List<PhotoData> files = new ArrayList<>();
            String mimeType = null;
            String url = media.getString("u");
            String linkTitle = media.getString("ti");
            PostBoxObject im = media.getPostBoxObject("im");
            int size = 0;
            int action = 0;
            if (im != null && url != null) {
                // link with image
                PostBoxObject[] sizes = im.getPostBoxObjectArray("r");
                if (sizes != null && sizes.length > 0) {
                    mimeType = "link/image";
                    logger.debug("url: {}", url);
                    files = getPhotos(sizes);
                }
            } else {
                PostBoxObject[] sizes = media.getPostBoxObjectArray("r");
                if (sizes != null && sizes.length > 0) {
                    // image
                    mimeType = "image";
                    files = getPhotos(sizes);

                } else {
                    // other documents
                    PostBoxObject data = media.getPostBoxObject("r");
                    if (data != null) {
                        long id = data.getLong("i");
                        if (id == 0) {
                            // case id = fileId
                            id = data.getLong("f");
                        }
                        long volume = data.getLong("v");
                        int local = data.getInteger("l");
                        size = data.getInteger("n");
                        String fname = data.getString("fn");

                        action = media.getInteger("_rawValue");
                        mimeType = media.getString("mt");

                        // byte[] thumb = media.getBytes("itd");

                        logger.debug("v: {}", volume);
                        logger.debug("l: {}", local);
                        logger.debug("n: {}", size);
                        logger.debug("action: {}", action);

                        if (fname != null) {
                            Photo f = new Photo();
                            logger.debug("name: {}", fname);
                            f.setName(fname);
                            f.setSize(size);
                            files.add(f);
                        }

                        if (id != 0) {
                            Photo f = new Photo();
                            logger.debug("name: {}", id);
                            f.setName(String.valueOf(id));
                            f.setSize(size);
                            files.add(f);
                        }

                        if (volume != 0 && local != 0) {
                            Photo f = new Photo();
                            f.setName(volume + "_" + local);
                            logger.debug("name: {}", f.getName());
                            f.setSize(size);
                            files.add(f);
                        }

                        if (action == 2 || action == 3) {
                            // add or remove users from group
                            byte[] d = media.getBytes("peerIds");
                            readPeersIds(m, d);
                        }
                    } else {
                        PostBoxObject[] options = media.getPostBoxObjectArray("os");
                        if (options != null) {
                            // telegram pool
                            String title = media.getString("t");
                            if (title != null) {
                                PoolData poolData = new PoolData(title);
                                for (PostBoxObject opt : options) {
                                    if (opt != null) {
                                        String o = opt.getString("t");
                                        if (o != null) {
                                            poolData.add(o);
                                        }
                                    }
                                }
                                m.setPoolData(poolData);
                            }
                        }
                    }
                }
            }

            // m.setThumb(thumb);
            logger.debug("mimeType: {}", mimeType);
            m.setMediaMime(mimeType);
            if (files.size() == 1) {
                m.setMediasize(files.get(0).getSize());
            }
            m.setNames(files);
            if (url != null) {
                m.setLink(true);
                if (mimeType == null) {
                    m.setMediaMime("link");
                }
                m.setUrl(url);
                m.setLinkTitle(linkTitle);
            }

            m.setType(MapTypeMSG.decodeMsg(action));
            m.setMediasize(size);
        }
    }

    @SuppressWarnings("unused")
    public void readMessage(byte[] key, byte[] data, Message m, HashMap<String, byte[]> mediaKey) {
        // Reference:
        // https://github.com/TelegramMessenger/Telegram-iOS/blob/master/submodules/Postbox/Sources/MessageHistoryTable.swift
        // Function readIntermediateEntry
        if (m == null) {
            return;
        }
        this.data = data;

        PostBoxCoding pk = new PostBoxCoding(key);
        long peerKey = pk.readInt64(false);
        int namespaceKey = pk.readInt32(false);
        int timestampKey = pk.readInt32(false);

        if (timestampKey < minTimestamp && namespaceKey > minTimestamp) {
            timestampKey = namespaceKey;
        }

        byte type = readNextByte();
        if (type == tInt32) {

            int stableId = readInt32();
            m.setId(stableId);

            int stableVersion = readInt32();
            byte dataFlags = readNextByte();

            if (testbit(dataFlags, 0)) {
                long globallyUniqueId = readInt64();
            }
            if (testbit(dataFlags, 1)) {
                int globalTags = readInt32();
            }
            if (testbit(dataFlags, 2)) {
                long groupingKey = readInt64();
            }
            if (testbit(dataFlags, 3)) {
                int groupInfo = readInt32();
            }
            if (testbit(dataFlags, 4)) {
                int localTagsValue = readInt32();
            }
            if (testbit(dataFlags, 5)) {
                long threadId = readInt64();
            }

            int flags = readInt32();

            int tags = readInt32();

            byte forwardInfoFlags = readNextByte();
            if (forwardInfoFlags != 0) {
                readForwardInfo(forwardInfoFlags);
            }
            byte hasAuthor = readNextByte();
            if (hasAuthor == 1) {
                long authorId = readInt64();
                m.setFrom(new Contact(authorId));
            }

            String txt = readString();
            m.setData(txt);

            List<byte[]> attrs = readArray();
            List<byte[]> embeddedMedia = readArray();
            List<byte[]> referenceMedia = readArray(12);

            boolean incoming = testbit(flags, 2) || testbit(flags, 8);
            m.setFromMe(!incoming);

            for (byte[] b : referenceMedia) {
                // convert from big endian to little endian
                Util.invertByteArray(b, 0, 4);
                Util.invertByteArray(b, 4, 8);

                byte[] mediaBytes = mediaKey.get(Hex.encodeHexString(b));
                if (mediaBytes != null) {
                    PostBoxCoding media = new PostBoxCoding(mediaBytes);
                    media.readMedia(m);
                }
            }

            for (byte[] b : embeddedMedia) {
                if (b != null) {
                    PostBoxCoding media = new PostBoxCoding(b);
                    media.readMedia(m);
                }
            }
        }

        m.setTimeStamp(Date.from(Instant.ofEpochSecond(timestampKey)));
    }

    public long readChatId() {
        long chatId = readInt64(false);
        return chatId;
    }

    public long readAccountId() {
        PostBoxObject obj = readPostBoxObject(true);
        PostBoxObject account = obj.getPostBoxObject("_");
        if (account != null) {
            long peerId = account.getLong("peerId");
            if (peerId != 0) {
                return peerId;
            }
        }
        return 0;
    }

    public void readContact(Contact c) {
        PostBoxObject obj = readPostBoxObject(true);
        PostBoxObject user = obj.getPostBoxObject("_");
        if (user != null) {
            c.setName(user.getString("fn"));
            c.setLastName(user.getString("ln"));
            c.setUsername(user.getString("un"));
            c.setPhone(user.getString("p"));
            String title = user.getString("t");
            if (title != null) {
                c.setGroup(true);
                c.setName(title);
            }
            PostBoxObject[] objs = user.getPostBoxObjectArray("ph");
            if (objs != null && objs.length > 0) {
                List<PhotoData> photos = new ArrayList<>();
                for (PostBoxObject o : objs) {
                    long n1 = o.getLong("v");
                    int n2 = o.getInteger("l");
                    if (n1 != 0 && n2 != 0) {
                        Photo p = new Photo();
                        p.setName(n1 + "_" + n2);
                        photos.add(p);
                    }
                }
                if (!photos.isEmpty()) {
                    c.setPhotos(photos);
                }
            }
        }
    }

    private PostBoxObject readPostBoxObject(boolean isRoot) {
        PostBoxObject obj = new PostBoxObject();
        int readLimit = data.length;
        if (!isRoot) {
            obj.hash = readInt32();
            int objLen = readInt32();
            readLimit = offset + objLen;
        }
        if (offset + 4 < readLimit && data[offset] == 0) {
            offset++;
            int objLen = readInt32();
            readLimit = offset + objLen;
        }
        while (offset < readLimit) {
            String key = readKeyString();
            if (key == null) {
                break;
            }
            int type = readNextByte() & 0xFF;
            Object val = null;
            int len = 0;
            switch (type) {
                case tInt32:
                    val = readInt32();
                    break;

                case tInt64:
                    val = readInt64();
                    break;

                case tBool:
                    val = readNextByte() != 0;
                    break;

                case tDouble:
                    // TODO: Check in real cases if it uses big or little endian.
                    long bits = readInt64();
                    val = Double.longBitsToDouble(bits);
                    break;

                case tString:
                    val = readString();
                    break;

                case tObject:
                    val = readPostBoxObject(false);
                    break;

                case tInt32Array:
                    len = readInt32();
                    int[] intArr = new int[len];
                    for (int i = 0; i < len; i++) {
                        intArr[i] = readInt32();
                    }
                    val = intArr;
                    break;

                case tInt64Array:
                    len = readInt32();
                    long[] lngArr = new long[len];
                    for (int i = 0; i < len; i++) {
                        lngArr[i] = readInt64();
                    }
                    val = lngArr;
                    break;

                case tObjectArray:
                    len = readInt32();
                    PostBoxObject[] objArr = new PostBoxObject[len];
                    for (int i = 0; i < len; i++) {
                        objArr[i] = readPostBoxObject(false);
                    }
                    val = objArr;
                    break;

                case tBytes:
                    len = readInt32();
                    byte[] bytes = new byte[len];
                    for (int i = 0; i < len; i++) {
                        bytes[i] = readNextByte();
                    }
                    val = bytes;
                    break;

                case tBytesArray:
                    len = readInt32();
                    byte[][] bytArr = new byte[len][];
                    for (int i = 0; i < len; i++) {
                        int arrLen = readInt32();
                        byte[] bi = bytArr[i] = new byte[arrLen];
                        for (int j = 0; j < arrLen; j++) {
                            bi[j] = readNextByte();
                        }
                    }
                    val = bytArr;
                    break;

                case tStringArray:
                    len = readInt32();
                    String[] strArr = new String[len];
                    for (int i = 0; i < len; i++) {
                        strArr[i] = readString();
                    }
                    val = strArr;
                    break;

                case tObjectDictionary:
                    len = readInt32();
                    Map<PostBoxObject, PostBoxObject> map = new HashMap<PostBoxObject, PostBoxObject>();
                    for (int i = 0; i < len; i++) {
                        PostBoxObject keyObj = readPostBoxObject(false);
                        PostBoxObject valObj = readPostBoxObject(false);
                        map.put(keyObj, valObj);
                    }
                    val = map;
                    break;

                case tNil:
                    break;

                default:
                    logger.warn("Unknown type while decoding PostBox {}", type);
                    break;
            }
            obj.fields.put(key, val);
        }
        return obj;
    }
}

class Photo implements PhotoData {
    private String name;
    private int size;

    public void setName(String name) {
        this.name = name;
    }

    public void setSize(int size) {
        this.size = size;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public long getSize() {
        return size;
    }
}
