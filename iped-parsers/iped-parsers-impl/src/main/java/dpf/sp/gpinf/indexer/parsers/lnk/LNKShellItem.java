package dpf.sp.gpinf.indexer.parsers.lnk;

import java.util.ArrayList;
import java.util.List;

public class LNKShellItem {
    private String name;
    private int type;
    private List<String> listValues = new ArrayList<String>();

    private LNKShellItemFileEntry fileEntry = null;

    private LNKShellItemNetwork networkLocation = null;

    private boolean unparsed = false;

    public boolean isUnparsed() {
        return unparsed;
    }

    public void setUnparsed(boolean unparsed) {
        this.unparsed = unparsed;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getListValues() {
        return listValues;
    }

    public void addValue(String value) {
        listValues.add(value);
    }

    public String getStrValues() {
        if (listValues.size() == 0)
            return ""; //$NON-NLS-1$
        StringBuffer sb = new StringBuffer(listValues.get(0));
        for (int i = 1; i < listValues.size(); i++) {
            sb.append(", " + listValues.get(i)); //$NON-NLS-1$
        }
        return sb.toString();
    }

    public boolean hasFileEntry() {
        return (fileEntry != null);
    }

    public LNKShellItemFileEntry getFileEntry() {
        if (fileEntry == null)
            fileEntry = new LNKShellItemFileEntry();
        return fileEntry;
    }

    public void setFileEntry(long flSize, long modDate, int flAtt, String pName) {
        this.fileEntry = new LNKShellItemFileEntry();
        this.fileEntry.setFileSize(flSize);
        this.fileEntry.setModifiedDate(modDate);
        this.fileEntry.setFileAttributeFlags(flAtt);
        this.fileEntry.setPrimaryName(pName);
        this.fileEntry.setTipoShell(type);
    }

    public boolean hasNetworkLocation() {
        return (networkLocation != null);
    }

    public LNKShellItemNetwork getNetworkLocation() {
        return networkLocation;
    }

    public void setNetworkLocation(LNKShellItemNetwork networkLocation) {
        this.networkLocation = networkLocation;
    }

}