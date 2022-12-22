package iped.carvers.custom;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import iped.carvers.api.Hit;
import iped.carvers.api.InvalidCarvedObjectException;
import iped.carvers.standard.AbstractCarver;
import iped.data.IItem;
import iped.io.SeekableInputStream;
import iped.parsers.mft.MFTEntry;

public class MFTEntryCarver extends AbstractCarver {
    /**
     * A static map to avoid (or at least minimize) adding to the case multiple
     * repeated carved MFT entries.
     */
    private static final Set<MFTEntry> mftEntries = new HashSet<MFTEntry>();

    @Override
    protected String getCarvedNamePrefix() {
        return "Carved-MFT-Entry-";
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
        try (SeekableInputStream is = parentEvidence.getSeekableInputStream()) {
            is.seek(headerOffset.getOffset());
            byte[] bytes = new byte[MFTEntry.entryLength];
            int read = is.readNBytes(bytes, 0, MFTEntry.entryLength);
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
            if (entry.getName() == null || entry.getName().isEmpty()) {
                throw new InvalidCarvedObjectException("Invalid MFT entry file name.");
            }
            if (entry.getCreationDate() == null && entry.getLastModificationDate() == null
                    && entry.getLastAccessDate() == null) {
                throw new InvalidCarvedObjectException("Invalid MFT entry. No dates found.");
            }
            synchronized (mftEntries) {
                if (!mftEntries.add(entry)) {
                    throw new InvalidCarvedObjectException("MFT entry already processed.");
                }
            }
            // This is done after checking repeated entries, so records in the active MFT
            // are added to the known entries map.
            if (!parentEvidence.isDeleted() && (name.equalsIgnoreCase("$MFT") || name.equalsIgnoreCase("$MFTMirr")
                    || name.equalsIgnoreCase("$LogFile"))) {
                throw new InvalidCarvedObjectException("MFT entries should not be carved from " + name);
            }
        } catch (Exception e) {
            throw new InvalidCarvedObjectException(e);
        }
    }
}
