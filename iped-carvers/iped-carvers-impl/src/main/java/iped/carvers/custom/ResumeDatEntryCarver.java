package iped.carvers.custom;

import java.io.IOException;

import iped.carvers.api.CarverType;
import iped.carvers.api.Hit;
import iped.carvers.standard.AbstractCarver;
import iped.data.IItem;
import iped.io.SeekableInputStream;

public class ResumeDatEntryCarver extends AbstractCarver {
    private static final int maxNameBlockLen = 512;
    private static final int minNameBlockLen = 8;
    private static final int maxSeedtimeBlockLen = 16;

    @Override
    public boolean isSpecificIgnoreCorrupted() {
        return true;
    }

    @Override
    protected String getCarvedNamePrefix() {
        return "Carved-Resume-Entry-";
    }

    @Override
    protected String getCarvedNameSuffix() {
        return ".dat";
    }

    @Override
    public IItem carveFromFooter(IItem parentEvidence, Hit footer) throws IOException {
        String parentType = parentEvidence.getMediaType().toString();
        if (parentType.toLowerCase().contains("torrent")) {
            return null;
        }

        Hit header = headersWaitingFooters.pollLast();
        if (header != null) {

            // Try to move the header backwards, to include the entry name (no tag)
            long start = Math.max(0, header.getOffset() - maxNameBlockLen);
            int read = (int) (header.getOffset() - start);
            if (read <= minNameBlockLen) {
                return null;
            }

            byte[] bytes = null;
            try (SeekableInputStream is = parentEvidence.getSeekableInputStream()) {
                is.seek(start);
                bytes = is.readNBytes(read);
            } catch (Exception e) {
                return null;
            }
            if (bytes == null) {
                return null;
            }

            int back = -1;
            for (int i = bytes.length - 1; i >= 0; i--) {
                if ((bytes[i] & 255) == ':') {
                    int len = bytes.length - i - 1;
                    char[] s = String.valueOf(len).toCharArray();
                    for (int j = 0; j < s.length; j++) {
                        if ((bytes[i - s.length + j] & 255) != s[j]) {
                            return null;
                        }
                    }
                    back = bytes.length - i + s.length;
                    break;
                }
            }
            if (back == -1) {
                return null;
            }
            header = new Hit(header.getSignature(), header.getOffset() - back);

            // Try to move the footer forward, to include the end of the last tag
            bytes = null;
            start = footer.getOffset() + footer.getSignature().getLength();
            read = (int) Math.min(start + maxSeedtimeBlockLen, parentEvidence.getLength() - start);
            try (SeekableInputStream is = parentEvidence.getSeekableInputStream()) {
                is.seek(start);
                bytes = is.readNBytes(read);
            } catch (Exception e) {
                return null;
            }
            if (bytes == null) {
                return null;
            }

            int forward = -1;
            for (int i = 0; i < bytes.length; i++) {
                char c = (char) (bytes[i] & 255);
                if (c == 'e' || c == 'i' || c == 'd') {
                    forward = i + 1;
                }
                if (c == ':') {
                    break;
                }
            }

            if (forward == -1) {
                return null;
            }

            long len = footer.getOffset() + footer.getSignature().getLength() - header.getOffset() + forward;
            CarverType typeCarved = header.getSignature().getCarverType();
            if (len < typeCarved.getMinLength() || len > typeCarved.getMaxLength()) {
                return null;
            }
            IItem item = carveFromHeader(parentEvidence, header, len);
            return item;
        }
        return null;
    }

    @Override
    public long getLengthFromHit(IItem parentEvidence, Hit headerOffset) throws IOException {
        return 0;
    }
}
