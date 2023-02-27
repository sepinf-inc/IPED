package iped.parsers.evtx.model;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

import iped.utils.DateUtil;

public class EvtxRecord {
    ByteBuffer bb;
    long id;
    Date writtenTime;
    int size;
    private EvtxFile evtxFile;
    private EvtxBinXml binXml;

    public EvtxRecord(EvtxFile evtxFile, ByteBuffer bb) throws EvtxParseException {
        this.evtxFile = evtxFile;
        this.bb = bb;
        this.size = bb.getInt();
        this.id = bb.getLong();
        long filetime = bb.getLong();
        long javatime = filetime - 0x19db1ded53e8000L;
        javatime /= 10000;
        this.writtenTime = new Date(javatime);
        binXml = new EvtxBinXml(evtxFile, this, bb);
    }

    public EvtxBinXml getBinXml() {
        return binXml;
    }

    public void setBinXml(EvtxBinXml binXml) {
        this.binXml = binXml;
    }

    public String getEventId() {
        ArrayList<EvtxElement> al = binXml.getElements();
        for (Iterator iterator = al.iterator(); iterator.hasNext();) {
            EvtxElement evtxElement = (EvtxElement) iterator.next();
            if (evtxElement.getName().equals("Event")) {
                ArrayList<EvtxElement> al2 = evtxElement.childElements;
                for (Iterator iterator2 = al2.iterator(); iterator2.hasNext();) {
                    EvtxElement evtxElement2 = (EvtxElement) iterator2.next();
                    if (evtxElement2.getName().equals("System")) {
                        ArrayList<EvtxElement> al3 = evtxElement2.childElements;
                        for (Iterator iterator3 = al3.iterator(); iterator3.hasNext();) {
                            EvtxElement evtxElement3 = (EvtxElement) iterator3.next();
                            if (evtxElement3.getName().equals("EventID") && evtxElement3.children.size() > 0) {
                                return evtxElement3.children.get(0).toString();
                            }
                        }
                    }
                }
            }
        }
        return "";
    }

    public String getEventProviderName() {
        ArrayList<EvtxElement> al = binXml.getElements();
        for (Iterator iterator = al.iterator(); iterator.hasNext();) {
            EvtxElement evtxElement = (EvtxElement) iterator.next();
            if (evtxElement.getName().equals("Event")) {
                ArrayList<EvtxElement> al2 = evtxElement.childElements;
                for (Iterator iterator2 = al2.iterator(); iterator2.hasNext();) {
                    EvtxElement evtxElement2 = (EvtxElement) iterator2.next();
                    if (evtxElement2.getName().equals("System")) {
                        ArrayList<EvtxElement> al3 = evtxElement2.childElements;
                        for (Iterator iterator3 = al3.iterator(); iterator3.hasNext();) {
                            EvtxElement evtxElement3 = (EvtxElement) iterator3.next();
                            if (evtxElement3.getName().equals("Provider")) {
                                ArrayList<EvtxAttribute> attrs = evtxElement3.getAttributes();
                                if (attrs != null) {
                                    for (Iterator iterator4 = attrs.iterator(); iterator4.hasNext();) {
                                        EvtxAttribute evtxAttribute = (EvtxAttribute) iterator4.next();
                                        if (evtxAttribute.getName().equals("Name")) {
                                            return evtxAttribute.getValueAsString();
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return "";
    }

    public long getEventRecordId() {
        return id;
    }

    public String getEventDateTime() {
        ArrayList<EvtxElement> al = binXml.getElements();
        for (Iterator iterator = al.iterator(); iterator.hasNext();) {
            EvtxElement evtxElement = (EvtxElement) iterator.next();
            if (evtxElement.getName().equals("Event")) {
                ArrayList<EvtxElement> al2 = evtxElement.childElements;
                for (Iterator iterator2 = al2.iterator(); iterator2.hasNext();) {
                    EvtxElement evtxElement2 = (EvtxElement) iterator2.next();
                    if (evtxElement2.getName().equals("System")) {
                        ArrayList<EvtxElement> al3 = evtxElement2.childElements;
                        for (Iterator iterator3 = al3.iterator(); iterator3.hasNext();) {
                            EvtxElement evtxElement3 = (EvtxElement) iterator3.next();
                            if (evtxElement3.getName().equals("TimeCreated")) {
                                Object o = ((EvtxOptionalSubstitution) evtxElement3.getAttributeByName("SystemTime")).getValue();
                                if (o != null && (o instanceof Date)) {
                                    return DateUtil.dateToString((Date) o);
                                }
                                return null;
                            }
                        }
                    }
                }
            }
        }
        return "";
    }

    public String getEventSystemComputer() {
        ArrayList<EvtxElement> al = binXml.getElements();
        for (Iterator iterator = al.iterator(); iterator.hasNext();) {
            EvtxElement evtxElement = (EvtxElement) iterator.next();
            if (evtxElement.getName().equals("Event")) {
                ArrayList<EvtxElement> al2 = evtxElement.childElements;
                for (Iterator iterator2 = al2.iterator(); iterator2.hasNext();) {
                    EvtxElement evtxElement2 = (EvtxElement) iterator2.next();
                    if (evtxElement2.getName().equals("System")) {
                        ArrayList<EvtxElement> al3 = evtxElement2.childElements;
                        for (Iterator iterator3 = al3.iterator(); iterator3.hasNext();) {
                            EvtxElement evtxElement3 = (EvtxElement) iterator3.next();
                            if (evtxElement3.getName().equals("Computer")) {
                                return evtxElement3.children.toString();
                            }
                        }
                    }
                }
            }
        }
        return "";
    }

    public HashMap<String, String> getEventData() {
        HashMap<String, String> result = new HashMap<String, String>();
        ArrayList<EvtxElement> al = binXml.getElements();
        for (Iterator iterator = al.iterator(); iterator.hasNext();) {
            EvtxElement evtxElement = (EvtxElement) iterator.next();
            if (evtxElement.getName().equals("Event")) {
                ArrayList<Object> al2 = evtxElement.children;
                for (Iterator iterator2 = al2.iterator(); iterator2.hasNext();) {
                    Object o = iterator2.next();
                    EvtxElement evtxElement2 = null;
                    if (o instanceof EvtxElement) {
                        evtxElement2 = (EvtxElement) o;
                    }
                    if (o instanceof EvtxOptionalSubstitution && ((EvtxOptionalSubstitution) o).getValue() instanceof EvtxElement) {
                        evtxElement2 = (EvtxElement) ((EvtxOptionalSubstitution) o).getValue();
                    }
                    if (evtxElement2 != null && evtxElement2.getName().equals("EventData")) {
                        ArrayList<EvtxElement> al3 = evtxElement2.childElements;
                        for (Iterator iterator3 = al3.iterator(); iterator3.hasNext();) {
                            EvtxElement evtxElement3 = (EvtxElement) iterator3.next();
                            if (evtxElement3.getName().equals("Data")) {
                                ArrayList<EvtxAttribute> attrs = evtxElement3.getAttributes();
                                if (attrs != null) {
                                    for (Iterator iterator4 = attrs.iterator(); iterator4.hasNext();) {
                                        EvtxAttribute evtxAttribute = (EvtxAttribute) iterator4.next();
                                        if (evtxAttribute.getName().equals("Name") && evtxElement3.children.size() > 0) {
                                            result.put(evtxAttribute.value.toString(), evtxElement3.children.get(0).toString());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    public long getId() {
        return id;
    }

    public EvtxElement getElement(EvtxElement base, String path) {
        int pos = path.indexOf("/");
        String first;
        String remainder = null;
        if (pos > -1) {
            first = path.substring(0, pos);
            remainder = path.substring(pos + 1);
        } else {
            first = path;
        }
        for (Iterator iterator = base.childElements.iterator(); iterator.hasNext();) {
            EvtxElement evtxElement = (EvtxElement) iterator.next();
            if (evtxElement.getName().equals(first)) {
                if (remainder == null || remainder.equals("")) {
                    return evtxElement;
                } else {
                    return getElement(evtxElement, remainder);
                }
            }
        }
        return null;
    }

    public String getElementValue(String path) {
        if (path.startsWith("Event/")) {
            path = path.substring("Event/".length());
        }
        ArrayList<EvtxElement> al = binXml.getElements();
        for (Iterator iterator = al.iterator(); iterator.hasNext();) {
            EvtxElement evtxElement = (EvtxElement) iterator.next();
            if (evtxElement.getName().equals("Event")) {
                EvtxElement evtxElement2 = getElement(evtxElement, path);
                return evtxElement2.children.get(0).toString();
            }
        }

        return "";
    }

    public EvtxElement getElement(String path) {
        if (path.startsWith("Event/")) {
            path = path.substring("Event/".length());
        }
        ArrayList<EvtxElement> al = binXml.getElements();
        for (Iterator iterator = al.iterator(); iterator.hasNext();) {
            EvtxElement evtxElement = (EvtxElement) iterator.next();
            if (evtxElement.getName().equals("Event")) {
                EvtxElement evtxElement2 = getElement(evtxElement, path);
                return evtxElement2;
            }
        }

        return null;
    }

    public String getEventProviderGUID() {
        ArrayList<EvtxElement> al = binXml.getElements();
        for (Iterator iterator = al.iterator(); iterator.hasNext();) {
            EvtxElement evtxElement = (EvtxElement) iterator.next();
            if (evtxElement.getName().equals("Event")) {
                ArrayList<EvtxElement> al2 = evtxElement.childElements;
                for (Iterator iterator2 = al2.iterator(); iterator2.hasNext();) {
                    EvtxElement evtxElement2 = (EvtxElement) iterator2.next();
                    if (evtxElement2.getName().equals("System")) {
                        ArrayList<EvtxElement> al3 = evtxElement2.childElements;
                        for (Iterator iterator3 = al3.iterator(); iterator3.hasNext();) {
                            EvtxElement evtxElement3 = (EvtxElement) iterator3.next();
                            if (evtxElement3.getName().equals("Provider")) {
                                ArrayList<EvtxAttribute> attrs = evtxElement3.getAttributes();
                                if (attrs != null) {
                                    for (Iterator iterator4 = attrs.iterator(); iterator4.hasNext();) {
                                        EvtxAttribute evtxAttribute = (EvtxAttribute) iterator4.next();
                                        if (evtxAttribute.getName().toUpperCase().equals("GUID")) {
                                            return evtxAttribute.getValueAsString();
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return "";
    }

}
