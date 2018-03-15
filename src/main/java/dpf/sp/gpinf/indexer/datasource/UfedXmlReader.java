package dpf.sp.gpinf.indexer.datasource;

import java.io.File;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TimeZone;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import gpinf.dev.data.CaseData;
import gpinf.dev.data.DataSource;
import gpinf.dev.data.EvidenceFile;

public class UfedXmlReader extends DataSourceReader{
    
    File root;
    EvidenceFile rootItem;
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
        rootItem.setLength(0L);
        
        pathToParent.put(rootItem.getPath(), rootItem);
        
        caseData.addEvidenceFile(rootItem);
    }
    
    private class XMLContentHandler implements ContentHandler{
        
        EvidenceFile item;
        String nameAttr;
        StringBuilder chars = new StringBuilder();
        
        String df1Pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";
        DateFormat df1 = new SimpleDateFormat(df1Pattern);
        DateFormat df2 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
        
        @Override
        public void setDocumentLocator(Locator locator) {
            // TODO Auto-generated method stub
            
        }

        @Override
        public void startDocument() throws SAXException {
            
        }

        @Override
        public void endDocument() throws SAXException {
            
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
            parent.setLength(0L);
            parent.setParent(getParent(parentPath));
            
            pathToParent.put(parentPath, parent);
            
            try {
                caseData.addEvidenceFile(parent);
            } catch (InterruptedException e) {
                throw new SAXException(e);
            }
            
            return parent;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
            
            if(qName.equals("file")) {
                String len = atts.getValue("size");
                Long size = null;
                if(len != null)
                    size = Long.valueOf(len.trim());
                
                if(listOnly) {
                    caseData.incDiscoveredEvidences(1);
                    caseData.incDiscoveredVolume(size);
                    return;
                }
                
                item = new EvidenceFile();
                item.setLength(size);
                
                String path = atts.getValue("path");
                item.setPath(rootItem.getName() + path);
                
                String name = path.substring(path.lastIndexOf('/') + 1);
                item.setName(name);
                
                boolean deleted = "deleted".equalsIgnoreCase(atts.getValue("deleted"));
                item.setDeleted(deleted);
                
                boolean embedded = "true".equalsIgnoreCase(atts.getValue("embedded"));
                //item.setSubItem(embedded);
                
                item.setParent(getParent(path));
                
            }
            
            nameAttr = atts.getValue("name");
            chars = new StringBuilder();
                
        }
        
        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            
            if(listOnly)
                return;
            
            if(qName.equals("item")) {
                if("Tags".equals(nameAttr)) {
                    item.setCategory(chars.toString());
                    
                } else if("Local Path".equals(nameAttr)) {
                    File file = new File(root, chars.toString());
                    item.setExportedFile(file.getAbsolutePath());
                    item.setFile(file);
                    item.setLength(file.length());
                }
                
            }else if(qName.equals("timestamp")) {
                try {
                    String value = chars.toString().trim();
                    if(!value.isEmpty()) {
                        DateFormat df = df1;
                        if(df1Pattern.length() != value.length())
                            df = df2;
                        if(nameAttr.equals("CreationTime"))
                            item.setCreationDate(df.parse(value));
                        else if(nameAttr.equals("ModifyTime"))
                            item.setModificationDate(df.parse(value));
                        else if(nameAttr.equals("AccessTime"))
                            item.setAccessDate(df.parse(value));
                    }
                    
                } catch (ParseException e) {
                    throw new SAXException(e);
                }
            } else if(qName.equals("file"))
                try {
                    caseData.addEvidenceFile(item);
                    
                } catch (InterruptedException e) {
                    throw new SAXException(e);
                }
            
            chars = new StringBuilder();
            nameAttr = null;
            
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
