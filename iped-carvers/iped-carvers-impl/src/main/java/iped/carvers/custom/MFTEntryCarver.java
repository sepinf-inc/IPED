package iped.carvers.custom;

import java.io.IOException;

import org.apache.commons.codec.DecoderException;
import org.apache.tika.mime.MediaType;

import iped.carvers.api.CarverType;
import iped.carvers.api.Hit;
import iped.carvers.api.InvalidCarvedObjectException;
import iped.carvers.standard.AbstractCarver;
import iped.data.IItem;
import iped.io.SeekableInputStream;
import iped.parsers.mft.MFTEntry;

public class MFTEntryCarver extends AbstractCarver {
    public MFTEntryCarver() throws DecoderException {
        carverTypes = new CarverType[1];
        carverTypes[0] = new CarverType();
        carverTypes[0].addHeader("FILE0");
        carverTypes[0].setMimeType(MediaType.parse(MFTEntry.MIME_TYPE));
        carverTypes[0].setMaxLength(MFTEntry.entryLength);
        carverTypes[0].setMinLength(MFTEntry.entryLength);
        carverTypes[0].setName("MFT-ENTRY");
    }

    @Override
    public long getLengthFromHit(IItem parentEvidence, Hit header) throws IOException {
        long length = MFTEntry.entryLength;
        long evidenceLen = parentEvidence.getLength();
        if (header.getOffset() + length > evidenceLen) {
            length = evidenceLen - header.getOffset();
        }
        return length;
    }

    @Override
    public boolean isSpecificIgnoreCorrupted() {
        return true;
    }

    @Override
    public void validateCarvedObject(IItem parentEvidence, Hit headerOffset, long length)
            throws InvalidCarvedObjectException {
        if (length != MFTEntry.entryLength) {
            throw new InvalidCarvedObjectException("Invalid MFT entry length: " + length);
        }
        String name = parentEvidence.getName();
        if (name.equalsIgnoreCase("$MFT") || name.equalsIgnoreCase("$MFTMirr") || name.equalsIgnoreCase("$LogFile")) {
            throw new InvalidCarvedObjectException("MFT entries are not be carved from " + name);
        }
        try (SeekableInputStream is = parentEvidence.getSeekableInputStream()) {
            byte[] bytes = new byte[MFTEntry.entryLength];
            int read = 0;
            while (read < MFTEntry.entryLength) {
                int r = is.read(bytes, read, MFTEntry.entryLength - read);
                if (r == -1) {
                    break;
                }
                read += r;
            }
            if (length != MFTEntry.entryLength) {
                throw new InvalidCarvedObjectException("Incorrect number of bytes read from MFT entry: " + read);
            }
            MFTEntry entry = MFTEntry.parse(bytes);
            if (entry == null) {
                throw new InvalidCarvedObjectException("Invalid MFT entry.");
            }
            if (entry.getFixUpOffset() != 0x30) {
                throw new InvalidCarvedObjectException("Invalid MFT entry fixup offset: " + entry.getFixUpOffset());
            }
            if (entry.getAttrOffset() != 0x38) {
                throw new InvalidCarvedObjectException("Invalid MFT entry attributes offset: " + entry.getAttrOffset());
            }
            if (entry.getFlags() < 0 || entry.getFlags() > 3) {
                throw new InvalidCarvedObjectException("Invalid MFT entry flags: " + entry.getFlags());
            }
            if (entry.getUsedSize() < 0x40 || entry.getUsedSize() > length) {
                throw new InvalidCarvedObjectException("Invalid MFT entry used size: " + entry.getUsedSize());
            }
            if (entry.getTotalSize() != length) {
                throw new InvalidCarvedObjectException("Invalid MFT entry total size: " + entry.getTotalSize());
            }
            if (entry.getName() == null) {
                throw new InvalidCarvedObjectException("Invalid MFT entry file name.");
            }
            if (entry.getCreationDate() == null && entry.getLastModificationDate() == null
                    && entry.getLastAccessDate() == null) {
                throw new InvalidCarvedObjectException("Invalid MFT entry. No dates found.");
            }
        } catch (Exception e) {
            throw new InvalidCarvedObjectException(e);
        }
    }
}
