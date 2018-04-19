package dpf.sp.gpinf.indexer.datasource;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
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

import org.apache.tika.metadata.Message;
import org.apache.tika.mime.MediaType;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import dpf.sp.gpinf.indexer.parsers.ufed.UFEDChatParser;
import dpf.sp.gpinf.indexer.parsers.util.ExtraProperties;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.util.MetadataInputStreamFactory;
import dpf.sp.gpinf.indexer.util.SimpleHTMLEncoder;
import dpf.sp.gpinf.indexer.util.Util;
import gpinf.dev.data.CaseData;
import gpinf.dev.data.DataSource;
import gpinf.dev.data.EvidenceFile;

public class UfedXmlReader extends DataSourceReader{
    
    private static final String[] HEADER_STRINGS = {"project id", "extractionType", "sourceExtractions"}; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    
    private static String AVATAR_PATH_META = ExtraProperties.UFED_META_PREFIX + "contactphoto_extracted_path";
    private static String ATTACH_PATH_META = ExtraProperties.UFED_META_PREFIX + "attachment_extracted_path";
    private static String EMAIL_ATTACH_KEY = ExtraProperties.UFED_META_PREFIX + "email_attach_names";
    
    public static final String UFED_MIME_PREFIX = "x-ufed-";
    public static final String UFED_EMAIL_MIME = "message/x-ufed-email";
    
    File root;
    EvidenceFile rootItem;
    EvidenceFile decodedFolder;
    HashMap<String, EvidenceFile> pathToParent = new HashMap<>();

    public UfedXmlReader(CaseData caseData, File output, boolean listOnly) {
        super(caseData, output, listOnly);
    }

    @Override
    public boolean isSupported(File datasource) {
        
        File xmlReport = getXmlReport(datasource);
        if(xmlReport != null)
            return true;
        
        return false;
    }
    
    private File getXmlReport(File root) {
        File[] files = root.listFiles();
        if(files != null)
            for(File file : files)
                if(file.getName().toLowerCase().endsWith(".xml"))
                    try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), "UTF-8")){
                        char[] cbuf = new char[1024];
                        int off = 0, i = 0;
                        while(off < cbuf.length && (i = reader.read(cbuf, off, cbuf.length - off)) != -1)
                            off += i;
                        String header = new String(cbuf, 0, off); 
                        for(String str : HEADER_STRINGS)
                            if(!header.contains(str))
                                return null;
                        
                        return file;
                        
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
        
        return null;
    }

    @Override
    public int read(File root) throws Exception {
        
        this.root = root;
        addRootItem();
        addVirtualDecodedFolder();
        File xml = getXmlReport(root);
        
        SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.setNamespaceAware(true);
        SAXParser saxParser = spf.newSAXParser();
        XMLReader xmlReader = saxParser.getXMLReader();
        xmlReader.setContentHandler(new XMLContentHandler());
        xmlReader.parse(xml.toURI().toString());
        
        return 0;
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
                    item.setMediaType(MediaType.application(UFED_MIME_PREFIX + type));
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
                    item.setMediaType(MediaType.application(UFED_MIME_PREFIX + type));
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
                if("Tags".equals(nameAttr) && "Configuration".equals(chars.toString())) {
                    item.setCategory(chars.toString());
                    
                } else if("Local Path".equals(nameAttr)) {
                    File file = new File(root, chars.toString());
                    String relativePath = Util.getRelativePath(output, file);
                    item.setExportedFile(relativePath);
                    item.setFile(file);
                    item.setLength(file.length());
                    
                } else if(!ignoreNameAttrs.contains(nameAttr) && !nameAttr.toLowerCase().startsWith("exif"))
                    if(item != null && !chars.toString().trim().isEmpty())
                        item.getMetadata().add(ExtraProperties.UFED_META_PREFIX + nameAttr, chars.toString().trim());
                
            }else if(qName.equals("timestamp")) {
                try {
                    String value = chars.toString().trim();
                    if(!value.isEmpty()) {
                        DateFormat df = df1;
                        if(df2Pattern.length() - 2 == value.length())
                            df = df2;
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
                if(parentNode.element.equals("field") || parentNode.element.equals("multiField")) {
                    String parentNameAttr = parentNode.atts.get("name");
                    if(!ignoreNameAttrs.contains(parentNameAttr)){
                        String meta = ExtraProperties.UFED_META_PREFIX + parentNameAttr;
                        String type = currentNode.atts.get("type");
                        String value = chars.toString().trim();
                        DateFormat df = df1;
                        if(df2Pattern.length() - 2 == value.length())
                            df = df2;
                        if(type.equals("TimeStamp"))
                            try {
                                item.getMetadata().add(meta, df.format(df.parse(value)));
                            } catch (ParseException e) {
                                e.printStackTrace();
                            }
                        else
                            item.getMetadata().add(meta, value);
                    }
                }   
            } else if(qName.equals("targetid") && parentNode.element.equals("jumptargets")){
                item.getMetadata().add(ExtraProperties.UFED_META_PREFIX + parentNode.element, chars.toString().trim());
            
            } else if(qName.equals("file")) {
                itemSeq.remove(itemSeq.size() - 1);
                try {
                    caseData.addEvidenceFile(item);
                } catch (Exception e) {
                    throw new SAXException(e);
                }
                    
            } else if(qName.equals("model") && (
                    parentNode.element.equals("modelType") ||
                    parentNode.element.equals("modelField") || 
                    parentNode.element.equals("multiModelField"))) {
                
                itemSeq.remove(itemSeq.size() - 1);
                String type = currentNode.atts.get("type");
                if("Contact".equals(type) || "UserAccount".equals(type)) {
                    createContactPreview(item);
                    
                }else if("Email".equals(type)) {
                    createEmailPreview(item);
                    
                }else if("Attachment".equals(type)) {
                    handleAttachment(item);
                    EvidenceFile parentItem = itemSeq.get(itemSeq.size() - 1);
                    if(parentItem.getMediaType().toString().contains("email"))
                        parentItem.getMetadata().add(EMAIL_ATTACH_KEY, item.getName());
                }else if("Chat".equals(type)) {
                    String name = "Chat";
                    String source = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Source");
                    if(source != null)
                        name += "_" + source;
                    String[] parties = item.getMetadata().getValues(ExtraProperties.UFED_META_PREFIX + "Participants");
                    if(parties != null && parties.length > 2) {
                        name += "_Group_" + item.getName().split("_")[1];
                    }else if(parties != null && parties.length > 0){
                        name += "_" + parties[0];
                        if(parties.length > 1)
                            name += "_" + parties[1];
                    }
                    updateName(item, name);
                    item.setExtraAttribute(IndexItem.TREENODE, "true");
                }
                if("InstantMessage".equals(type) || "Email".equals(type)) {
                    String date = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "TimeStamp");
                    item.getMetadata().remove(ExtraProperties.UFED_META_PREFIX + "TimeStamp");
                    item.getMetadata().set(ExtraProperties.MESSAGE_DATE, date);
                    
                    String subject = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Subject");
                    item.getMetadata().remove(ExtraProperties.UFED_META_PREFIX + "Subject");
                    item.getMetadata().set(ExtraProperties.MESSAGE_SUBJECT, subject);
                    
                    String body = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Body");
                    item.getMetadata().remove(ExtraProperties.UFED_META_PREFIX + "Body");
                    if(body == null) {
                        body = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Snippet");
                        item.getMetadata().remove(ExtraProperties.UFED_META_PREFIX + "Snippet");
                    }
                    item.getMetadata().set(ExtraProperties.MESSAGE_BODY, body);
                }
                if(mergeInParentNode.contains(type)) {
                    EvidenceFile parentItem = itemSeq.get(itemSeq.size() - 1);
                    if("Party".equals(type)) {
                        String role = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Role");
                        String parentNameAttr = parentNode.atts.get("name");
                        if(role == null || role.equals("General"))
                            role = parentNameAttr;
                        if(role.equals("To") && (parentNameAttr.equals("Bcc") || parentNameAttr.equals("Cc")))
                            role = parentNameAttr;
                        if(role.equals("Parties"))
                            role = "Participants";
                        
                        String identifier = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Identifier");
                        String name = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Name");
                        String value = name == null || name.equals(identifier) ? identifier : 
                            identifier == null ? name : name + "(" + identifier + ")";
                        if("From".equalsIgnoreCase(role))
                            parentItem.getMetadata().add(Message.MESSAGE_FROM, value);
                        else if("To".equalsIgnoreCase(role))
                            parentItem.getMetadata().add(Message.MESSAGE_TO, value);
                        else if("Cc".equalsIgnoreCase(role))
                            parentItem.getMetadata().add(Message.MESSAGE_CC, value);
                        else if("Bcc".equalsIgnoreCase(role))
                            parentItem.getMetadata().add(Message.MESSAGE_BCC, value);
                        else
                            parentItem.getMetadata().add(ExtraProperties.UFED_META_PREFIX + role, value);
                        
                        String isOwner = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "IsPhoneOwner");
                        if(Boolean.valueOf(isOwner) && parentItem.getMediaType().toString().contains("chat"))
                            parentItem.getMetadata().add(UFEDChatParser.META_PHONE_OWNER, value);
                        
                        if(Boolean.valueOf(isOwner) && "From".equals(role))
                            parentItem.getMetadata().add(UFEDChatParser.META_FROM_OWNER, Boolean.TRUE.toString());
                        
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
                        if(item.getFile() == null)
                            item.setInputStreamFactory(new MetadataInputStreamFactory(item.getMetadata()));
                        
                        caseData.addEvidenceFile(item);
                        
                    } catch (InterruptedException e) {
                        throw new SAXException(e);
                    }
            }
            
            chars = new StringBuilder();
            nameAttr = null;
            
        }
        
        private void updateName(EvidenceFile item, String newName) {
            item.setName(newName);
            item.setPath(item.getPath().substring(0, item.getPath().lastIndexOf('/') + 1) + newName);
        }
        
        private void handleAttachment(EvidenceFile item) {
            String name = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Filename");
            if(name != null)
                updateName(item, name);
            item.setMediaType(null);
            item.setHash(null);
            String extracted_path = item.getMetadata().get(ATTACH_PATH_META);
            if(extracted_path != null) {
                File file = new File(root, extracted_path);
                if(file.exists()) {
                    String relativePath = Util.getRelativePath(output, file);
                    item.setExportedFile(relativePath);
                    item.setFile(file);
                    item.setLength(file.length());
                }
            }
        }
        
        private File createEmailPreview(EvidenceFile email) {
            File file = new File(output, "view/emails/view-" + email.getId() + ".html");
            file.getParentFile().mkdirs();
            try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"))){
                bw.write("<!DOCTYPE html>\n"
                + "<html>\n" //$NON-NLS-1$
                + "<head>\n" //$NON-NLS-1$
                + "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n" //$NON-NLS-1$
                +"</head>\n");
                bw.write("<body>");
                //bw.write("<body style=\"background-color:white;text-align:left;font-family:arial;color:black;font-size:14px;margin:5px;\">\n"); //$NON-NLS-1$
                
                String[] ufedMetas = {
                        ExtraProperties.UFED_META_PREFIX + "Subject",
                        Message.MESSAGE_FROM, 
                        Message.MESSAGE_TO, 
                        Message.MESSAGE_CC, 
                        Message.MESSAGE_BCC, 
                        ExtraProperties.UFED_META_PREFIX + "TimeStamp"
                        };
                String[] printHeaders = {"Subject", "From", "To", "Cc", "Bcc", "Date"};
                for(int i = 0; i < printHeaders.length; i++) {
                    String[] values = email.getMetadata().getValues(ufedMetas[i]);
                    if(values.length > 0) {
                        bw.write("<b>" + printHeaders[i] + ":</b>");
                        for(String value : values)
                            bw.write(" " + SimpleHTMLEncoder.htmlEncode(value));
                        bw.write("<br>");
                    }
                }
                
                String[] attachNames = email.getMetadata().getValues(EMAIL_ATTACH_KEY);
                if(attachNames != null && attachNames.length > 0) {
                    bw.write("<b>" + "Attachments" + " (" + attachNames.length + "):</b><br>");
                    for(String attach : attachNames) {
                        bw.write(SimpleHTMLEncoder.htmlEncode(attach) + "<br>");
                    }
                }
                
                bw.write("<hr>");
                
                String bodyMeta = ExtraProperties.UFED_META_PREFIX + "Body";
                String body = email.getMetadata().get(bodyMeta);
                email.getMetadata().remove(bodyMeta);
                if(body == null)
                    body = email.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Snippet");
                if(body != null)
                    bw.write(body);
                
                bw.write("</body></html>");                
                
            } catch (IOException e) {
                e.printStackTrace();
            }
            email.setMediaType(MediaType.parse(UFED_EMAIL_MIME));
            String relativePath = Util.getRelativePath(output, file);
            email.setExportedFile(relativePath);
            email.setFile(file);
            email.setLength(file.length());
            email.setHash(null);
            
            return file;
        }
        
        private File createContactPreview(EvidenceFile contact) {
            
            String name = contact.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Name");
            if(name == null)
                name = contact.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Username");
            if(name != null) {
                name = contact.getName().substring(0, contact.getName().indexOf('_') + 1) + name;
                updateName(contact, name);
            }
            
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
                    bw.write("<img src=\"data:image/jpg;base64," + dpf.mg.udi.gpinf.whatsappextractor.Util.encodeBase64(bytes) + "\" width=\"150\"/><br>\n");
                    contact.setThumb(bytes);
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
                    bw.write("<br>");
                }
                bw.write("</body></html>");
                
            } catch (IOException e) {
                e.printStackTrace();
            }
            String relativePath = Util.getRelativePath(output, file);
            contact.setExportedFile(relativePath);
            contact.setFile(file);
            contact.setLength(file.length());
            contact.setHash(null);
            
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
