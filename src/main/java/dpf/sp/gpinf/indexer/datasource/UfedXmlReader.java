package dpf.sp.gpinf.indexer.datasource;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TimeZone;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.tika.mime.MediaType;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import dpf.mg.udi.gpinf.whatsappextractor.Util;
import dpf.sp.gpinf.indexer.parsers.util.ExtraProperties;
import dpf.sp.gpinf.indexer.util.SimpleHTMLEncoder;
import gpinf.dev.data.CaseData;
import gpinf.dev.data.DataSource;
import gpinf.dev.data.EvidenceFile;

public class UfedXmlReader extends DataSourceReader{
    
    private static String AVATAR_PATH_META = ExtraProperties.UFED_META_PREFIX + "contactphoto_extracted_path";
    
    File root;
    EvidenceFile rootItem;
    EvidenceFile decodedFolder;
    HashMap<String, EvidenceFile> pathToParent = new HashMap<>();

    public UfedXmlReader(CaseData caseData, File output, boolean listOnly) {
        super(caseData, output, listOnly);
    }

    @Override
    public boolean isSupported(File datasource) {
        File file = new File(datasource, "files");
        if(!file.exists())
            return false;
        
        file = new File(datasource, "pages");
        if(!file.exists())
            return false;
        
        if(getXmlFile(datasource) != null)
            return true;
        
        return false;
    }

    @Override
    public int read(File root) throws Exception {
        
        this.root = root;
        addRootItem();
        addVirtualDecodedFolder();
        
        File xml = getXmlFile(root);
        
        SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.setNamespaceAware(true);
        SAXParser saxParser = spf.newSAXParser();
        XMLReader xmlReader = saxParser.getXMLReader();
        xmlReader.setContentHandler(new XMLContentHandler());
        xmlReader.parse(xml.toURI().toString());
        
        return 0;
    }
    
    private File getXmlFile(File root) {
        File[] files = root.listFiles();
        if(files != null)
            for(File f : files)
                if(f.getName().toLowerCase().endsWith("report.xml") ||
                   f.getName().toLowerCase().endsWith("relatório.xml")) {
                    return f;
                }
        return null;
    }
    
    private void addRootItem() throws InterruptedException {
        
        if(listOnly)
            return;
        
        String evidenceName = getEvidenceName(root);
        if (evidenceName == null)
            evidenceName = root.getName();
        DataSource evidenceSource = new DataSource(root);
        evidenceSource.setName(evidenceName);
        
        rootItem = new EvidenceFile();
        rootItem.setRoot(true);
        rootItem.setDataSource(evidenceSource);
        rootItem.setPath(root.getName());
        rootItem.setName(root.getName());
        rootItem.setHasChildren(true);
        //rootItem.setLength(0L);
        rootItem.setHash("");
        
        pathToParent.put(rootItem.getPath(), rootItem);
        
        caseData.incDiscoveredEvidences(1);
        caseData.addEvidenceFile(rootItem);
    }
    
    private void addVirtualDecodedFolder() throws InterruptedException {
        
        if(listOnly)
            return;
        
        decodedFolder = new EvidenceFile();
        decodedFolder.setName("_DecodedData");
        decodedFolder.setParent(rootItem);
        decodedFolder.setPath(rootItem.getPath() + "/" + decodedFolder.getName());
        decodedFolder.setIsDir(true);
        decodedFolder.setHasChildren(true);
        decodedFolder.setHash("");
        
        pathToParent.put(decodedFolder.getPath(), decodedFolder);
        
        caseData.incDiscoveredEvidences(1);
        caseData.addEvidenceFile(decodedFolder);
    }
    
    private class XMLContentHandler implements ContentHandler{
        
        StringBuilder chars = new StringBuilder();
        
        HashMap<String,String> extractionInfoMap = new HashMap<String,String>(); 
        
        String df2Pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS";
        DateFormat df1 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        DateFormat df2 = new SimpleDateFormat(df2Pattern);
        
        ArrayList<XmlNode> nodeSeq = new ArrayList<>();
        ArrayList<EvidenceFile> itemSeq = new ArrayList<>();
        
        HashSet<String> elements = new HashSet<>();
        HashSet<String> types = new HashSet<>();
        
        HashMap<String, EvidenceFile> ids = new HashMap<>();
        
        private class XmlNode{
            String element;
            HashMap<String, String> atts = new HashMap<>();
            
            private XmlNode(String element, Attributes atts) {
                this.element = element;
                for(int i = 0; i < atts.getLength(); i++) {
                    this.atts.put(atts.getQName(i), atts.getValue(i));
                }
            }
        }
        
        HashSet<String> ignoreAttrs = new HashSet<>(Arrays.asList(
                "type",
                "path",
                "size",
                "deleted",
                "deleted_state"
                ));
        
        HashSet<String> ignoreNameAttrs = new HashSet<>(Arrays.asList(
                "Tags",
                "Local Path",
                "CreationTime",
                "ModifyTime",
                "AccessTime",
                "CoreFileSystemFileSystemNodeCreationTime",
                "CoreFileSystemFileSystemNodeModifyTime",
                "CoreFileSystemFileSystemNodeLastAccessTime",
                "UserMapping"
                ));
        
        HashSet<String> mergeInParentNode = new HashSet<>(Arrays.asList(
                "Party",
                "PhoneNumber",
                "EmailAddress",
                "Coordinate",
                "Organization",
                "UserID",
                "ContactPhoto",
                "StreetAddress"
                ));
        
        @Override
        public void setDocumentLocator(Locator locator) {
            // TODO Auto-generated method stub
            
        }

        @Override
        public void startDocument() throws SAXException {
            //TODO remover timezone da exibição? obter da linha de comando?
            df2.setTimeZone(TimeZone.getTimeZone("GMT"));
            df1.setTimeZone(TimeZone.getTimeZone("GMT"));
        }

        @Override
        public void endDocument() throws SAXException {
            /*
            for(String s : elements)
                System.out.println("element: " + s);
            for(String s : types)
                System.out.println("type: " + s);
            */
        }

        @Override
        public void startPrefixMapping(String prefix, String uri) throws SAXException {
            // TODO Auto-generated method stub
            
        }

        @Override
        public void endPrefixMapping(String prefix) throws SAXException {
            // TODO Auto-generated method stub
            
        }
        
        private EvidenceFile getParent(String path) throws SAXException {
            int idx = path.lastIndexOf('/');
            if(idx < 1)
                return rootItem;
            
            String parentPath = path.substring(0, idx);
            EvidenceFile parent = pathToParent.get(parentPath);
            if(parent != null)
                return parent;
            
            parent = new EvidenceFile();
            parent.setName(parentPath.substring(parentPath.lastIndexOf('/') + 1));
            parent.setPath(parentPath);
            parent.setHasChildren(true);
            parent.setIsDir(true);
            //parent.setLength(0L);
            parent.setHash("");
            parent.setParent(getParent(parentPath));
            
            pathToParent.put(parentPath, parent);
            
            try {
                caseData.incDiscoveredEvidences(1);
                caseData.addEvidenceFile(parent);
                
            } catch (InterruptedException e) {
                throw new SAXException(e);
            }
            
            return parent;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
            
            XmlNode node = new XmlNode(qName, atts);
            nodeSeq.add(node);
            
            if(!listOnly)
                elements.add(qName);
            
            if(qName.equals("extractionInfo")) {
                String id = atts.getValue("id");
                String name = atts.getValue("name"); 
                extractionInfoMap.put(id, name);
                
            }else if(qName.equals("file")) {
                String len = atts.getValue("size");
                Long size = null;
                if(len != null)
                    size = Long.valueOf(len.trim());
                
                if(listOnly) {
                    caseData.incDiscoveredEvidences(1);
                    caseData.incDiscoveredVolume(size);
                    return;
                }
                
                EvidenceFile item = new EvidenceFile();
                
                item.setLength(size);
                
                String fs = "/" + atts.getValue("fs");
                String path = rootItem.getName() + fs + atts.getValue("path");
                item.setPath(path);
                
                String name = path.substring(path.lastIndexOf('/') + 1);
                item.setName(name);
                
                item.setParent(getParent(path));
                
                boolean deleted = "deleted".equalsIgnoreCase(atts.getValue("deleted"));
                item.setDeleted(deleted);
                
                fillCommonMeta(item, atts);
                itemSeq.add(item);
                
            }else if(qName.equals("model")){
                XmlNode prevNode = nodeSeq.get(nodeSeq.size() - 2); 
                if(prevNode.element.equals("modelType")) {
                    if(listOnly) {
                        caseData.incDiscoveredEvidences(1);
                        return;
                    }
                    
                    EvidenceFile item = new EvidenceFile();
                    
                    String type = atts.getValue("type");
                    String name = type + "_" + atts.getValue("id");
                    item.setName(name);
                    String path = decodedFolder.getPath() + "/" + type + "/" + name;
                    item.setPath(path);
                    item.setParent(getParent(path));
                    item.setCategory(type);
                    item.setMediaType(MediaType.application("x-ufed-" + type));
                    item.setHash("");
                                        
                    boolean deleted = "deleted".equalsIgnoreCase(atts.getValue("deleted_state"));
                    item.setDeleted(deleted);
                    
                    fillCommonMeta(item, atts);
                    itemSeq.add(item);
                    
                }else if(prevNode.element.equals("modelField") || prevNode.element.equals("multiModelField")) {
                    
                    String type = atts.getValue("type");
                    if(listOnly) {
                        if(!mergeInParentNode.contains(type))
                            caseData.incDiscoveredEvidences(1);
                        return;
                    }
                    
                    EvidenceFile item = new EvidenceFile();
                    EvidenceFile parent = itemSeq.get(itemSeq.size() - 1);
                    
                    String name = type + "_" + atts.getValue("id");
                    String prevNameAtt = prevNode.atts.get("name");
                    if("Location".equals(type) && ("FromPoint".equals(prevNameAtt) || "ToPoint".equals(prevNameAtt)))
                        name = prevNameAtt + "_" + name;
                    item.setName(name);
                    item.setPath(parent.getPath() + "/" + name);
                    item.setCategory(type);
                    item.setMediaType(MediaType.application("x-ufed-" + type));
                    item.setHash("");
                    
                    item.setParent(parent);
                    if(!mergeInParentNode.contains(type))
                        parent.setHasChildren(true);
                    
                    boolean deleted = "deleted".equalsIgnoreCase(atts.getValue("deleted_state"));
                    item.setDeleted(deleted);
                    
                    fillCommonMeta(item, atts);
                    itemSeq.add(item);
                }
            }
            
            chars = new StringBuilder();
                
        }
        
        private void fillCommonMeta(EvidenceFile item, Attributes atts) {
            if("StreetAddress".equals(atts.getValue("type")))
                return;
            String extractionName = extractionInfoMap.get(atts.getValue("extractionId"));
            item.getMetadata().add(ExtraProperties.UFED_META_PREFIX + "extractionName", extractionName);
            
            for(int i = 0; i < atts.getLength(); i++) {
                String attName = atts.getQName(i);
                if(!ignoreAttrs.contains(attName)) {
                    String value = atts.getValue(i);
                    item.getMetadata().add(ExtraProperties.UFED_META_PREFIX + attName, value);
                }
            }
        }
        
        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            
            XmlNode currentNode = nodeSeq.remove(nodeSeq.size() - 1);
            
            if(listOnly)
                return;
            
            String nameAttr = currentNode.atts.get("name");
            EvidenceFile item = null;
            if(itemSeq.size() > 0)
                item = itemSeq.get(itemSeq.size() - 1);
            
            XmlNode parentNode = null;
            if(nodeSeq.size() > 0)
                parentNode = nodeSeq.get(nodeSeq.size() - 1);
            
            if(qName.equals("item")) {
                if("Tags".equals(nameAttr)) {
                    item.setCategory(chars.toString());
                    
                } else if("Local Path".equals(nameAttr)) {
                    File file = new File(root, chars.toString());
                    item.setExportedFile(file.getAbsolutePath());
                    item.setFile(file);
                    item.setLength(file.length());
                    
                } else if(!ignoreNameAttrs.contains(nameAttr) && !nameAttr.toLowerCase().startsWith("exif"))
                    if(item != null && !chars.toString().trim().isEmpty())
                        item.getMetadata().add(ExtraProperties.UFED_META_PREFIX + nameAttr, chars.toString().trim());
                
            }else if(qName.equals("timestamp")) {
                try {
                    String value = chars.toString().trim();
                    if(!value.isEmpty()) {
                        DateFormat df = df2;
                        if(df2Pattern.length() - 2 != value.length())
                            df = df1;
                        if(nameAttr.equals("CreationTime"))
                            item.setCreationDate(df.parse(value));
                        else if(nameAttr.equals("ModifyTime"))
                            item.setModificationDate(df.parse(value));
                        else if(nameAttr.equals("AccessTime"))
                            item.setAccessDate(df.parse(value));
                        else
                            item.getMetadata().add(ExtraProperties.UFED_META_PREFIX + nameAttr, value);
                    }
                    
                } catch (ParseException e) {
                    throw new SAXException(e);
                }
            } else if(qName.equals("value")) {
                if(parentNode.element.equals("field") || parentNode.element.equals("multiField")) 
                    if(!ignoreNameAttrs.contains(parentNode.atts.get("name"))){
                        String meta = ExtraProperties.UFED_META_PREFIX + parentNode.atts.get("name");
                        String type = currentNode.atts.get("type");
                        String value = chars.toString().trim();
                        boolean added = false;
                        if(type.equals("TimeStamp"))
                            try {
                                item.getMetadata().add(meta, df1.format(df1.parse(value)));
                                added = true;
                            } catch (ParseException e) {
                                e.printStackTrace();
                            }
                        if(!added)
                            item.getMetadata().add(meta, value);
                    }    
            } else if(qName.equals("targetid") && parentNode.element.equals("jumptargets")){
                item.getMetadata().add(ExtraProperties.UFED_META_PREFIX + parentNode.element, chars.toString().trim());
            
            } else if(qName.equals("file")) {
                itemSeq.remove(itemSeq.size() - 1);
                try {
                    caseData.addEvidenceFile(item);
                } catch (InterruptedException e) {
                    throw new SAXException(e);
                }
                    
            } else if(qName.equals("model") && (
                    parentNode.element.equals("modelType") ||
                    parentNode.element.equals("modelField") || 
                    parentNode.element.equals("multiModelField"))) {
                
                itemSeq.remove(itemSeq.size() - 1);
                String type = currentNode.atts.get("type");
                if("Contact".equals(type) || "UserAccount".equals(type)) {
                    File viewFile = createContactPreview(item);
                    if(viewFile != null) {
                        item.setExportedFile(viewFile.getAbsolutePath());
                        item.setFile(viewFile);
                        item.setLength(viewFile.length());
                        item.setHash(null);
                    }
                }
                if(mergeInParentNode.contains(type)) {
                    EvidenceFile parentItem = itemSeq.get(itemSeq.size() - 1);
                    if("Party".equals(type)) {
                        String role = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Role");
                        if(role == null || role.equals("General"))
                            role = "Participants";
                        role = "Party:" + role;
                        String identifier = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Identifier");
                        String name = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Name");
                        String value = name == null || name.equals(identifier) ? identifier : name + " (" + identifier + ")";
                        parentItem.getMetadata().add(ExtraProperties.UFED_META_PREFIX + role, value);
                        
                    }else if("PhoneNumber".equals(type) || "EmailAddress".equals(type)) {
                        String category = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Category");
                        String value = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Value");
                        if(value != null && !value.trim().isEmpty()) {
                            if(category != null)
                                value += " (" + category + ")";
                            parentItem.getMetadata().add(ExtraProperties.UFED_META_PREFIX + type, value);
                        }
                        
                    }else if("Coordinate".equals(type)) {
                        String lat = ExtraProperties.UFED_META_PREFIX + "Latitude";
                        String lon = ExtraProperties.UFED_META_PREFIX + "Longitude";
                        parentItem.getMetadata().add(lat, item.getMetadata().get(lat));
                        parentItem.getMetadata().add(lon, item.getMetadata().get(lon));
                        
                    }else if("Organization".equals(type)) {
                        String value = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Name");
                        if(value != null) {
                            String position = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Position");
                            if(position != null)
                                value += " (" + position + ")";
                            parentItem.getMetadata().add(ExtraProperties.UFED_META_PREFIX + type, value);
                        }
                    }else if("UserID".equals(type)) {
                        String value = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Value");
                        parentItem.getMetadata().add(ExtraProperties.UFED_META_PREFIX + type, value);
                        
                    }else if("ContactPhoto".equals(type)) {
                        String avatarPath = item.getMetadata().get(AVATAR_PATH_META);
                        if(avatarPath != null)
                            parentItem.getMetadata().add(AVATAR_PATH_META, new File(root, avatarPath).getAbsolutePath());
                    }else if("StreetAddress".equals(type)) {
                        for(String meta : item.getMetadata().names()) {
                            String[] vals = item.getMetadata().getValues(meta);
                            for(String val : vals)
                                parentItem.getMetadata().add(meta, val);
                        }
                    }
                }else
                    try {   
                        caseData.addEvidenceFile(item);
                        
                    } catch (InterruptedException e) {
                        throw new SAXException(e);
                    }
            }
            
            chars = new StringBuilder();
            nameAttr = null;
            
        }
        
        private File createContactPreview(EvidenceFile contact) {
            
            File file = new File(output, "view/contacts/view-" + contact.getId() + ".html");
            file.getParentFile().mkdirs();
            try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"))){
                
                bw.write("<!DOCTYPE html>\n"
                + "<html>\n" //$NON-NLS-1$
                + "<head>\n" //$NON-NLS-1$
                + "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n" //$NON-NLS-1$
                +"</head>\n"
                +"<body>\n");
                
                String avatarPath = contact.getMetadata().get(AVATAR_PATH_META);
                if(avatarPath != null) {
                    contact.getMetadata().remove(AVATAR_PATH_META);
                    byte[] bytes = Files.readAllBytes(new File(avatarPath).toPath());
                    bw.write("<img src=\"data:image/jpg;base64," + Util.encodeBase64(bytes) + "\" width=\"150\"/><br>\n");
                }
                String[] metas = contact.getMetadata().names();
                Arrays.sort(metas);
                for(String meta : metas) {
                    bw.write(SimpleHTMLEncoder.htmlEncode(meta) + ": ");
                    String[] vals = contact.getMetadata().getValues(meta);
                    for(int i = 0; i < vals.length; i++) {
                        bw.write(SimpleHTMLEncoder.htmlEncode(vals[i]));
                        if(i != vals.length - 1)
                            bw.write(" | ");
                    }
                    bw.write("<br>\n");
                }
                bw.write("</body></html>");
                
            } catch (IOException e) {
                e.printStackTrace();
            }
            return file;
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            if(listOnly)
                return;
            chars.append(ch, start, length);
        }

        @Override
        public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
            // TODO Auto-generated method stub
            
        }

        @Override
        public void processingInstruction(String target, String data) throws SAXException {
            // TODO Auto-generated method stub
            
        }

        @Override
        public void skippedEntity(String name) throws SAXException {
            // TODO Auto-generated method stub
            
        }
        
    }

}
