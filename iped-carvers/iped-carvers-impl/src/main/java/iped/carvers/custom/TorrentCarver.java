package iped.carvers.custom;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.dampcake.bencode.BencodeInputStream;

import iped.carvers.api.Hit;
import iped.carvers.standard.DefaultCarver;
import iped.data.IItem;
import iped.io.SeekableInputStream;
import iped.utils.IOUtil;

public class TorrentCarver extends DefaultCarver {
    @Override
    public boolean isSpecificIgnoreCorrupted() {
        return true;
    }

    @Override
    public long getLengthFromHit(IItem parentEvidence, Hit header) throws IOException {
        SeekableInputStream is = null;
        BencodeInputStream bis = null;
        long off = header.getOffset();
        try {
            is = parentEvidence.getSeekableInputStream();
            is.seek(header.getOffset());
            bis = new BencodeInputStream(is, StandardCharsets.UTF_8, true);
            Map<String, Object> dict = bis.readDictionary();
            if (isValid(dict)) {
                long len = is.position() - off;
                if (len <= 0) {
                    len = -1;
                } else {
                    Long parentLen = parentEvidence.getLength();
                    if (parentLen != null && off + len > parentLen) {
                        len = parentLen - off;
                    }
                }
                return len;
            } else {
                return -1;
            }
        } catch (IOException e) {
            long len = is.position() - off;
            return len;
        } finally {
            IOUtil.closeQuietly(bis);
            IOUtil.closeQuietly(is);
        }
    }

    public boolean isValid(Map<String, Object> dict) {
        Object info = dict.get("info");
        if (info != null && info instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> infoMap = (Map<String, Object>) info;
            return infoMap.containsKey("files") || infoMap.containsKey("name");
        }
        return false;
    }
}
