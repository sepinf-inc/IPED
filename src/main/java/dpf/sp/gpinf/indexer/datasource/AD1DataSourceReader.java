package dpf.sp.gpinf.indexer.datasource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.TreeMap;

import dpf.sp.gpinf.indexer.util.IPEDException;
import dpf.sp.gpinf.indexer.util.SeekableInputStream;
import dpf.sp.gpinf.indexer.util.SeekableInputStreamFactory;
import gpinf.dev.data.CaseData;
import gpinf.dev.data.DataSource;
import gpinf.dev.data.EvidenceFile;
import sef.mg.laud.ad1extractor.AD1Extractor;
import sef.mg.laud.ad1extractor.FileHeader;

public class AD1DataSourceReader extends DataSourceReader {
    
    AD1InputStreamFactory inputStreamFactory;
    EvidenceFile rootItem;

    public AD1DataSourceReader(CaseData caseData, File output, boolean listOnly) {
        super(caseData, output, listOnly);
    }

    @Override
    public boolean isSupported(File datasource) {
        return datasource.getName().toLowerCase().endsWith(".ad1");
    }

    @Override
    public int read(File datasource) throws Exception {
        
        rootItem = addRootItem(datasource);
        
        try (AD1Extractor ad1 = new AD1Extractor(datasource)){
            if (ad1.isEncrypted())
                throw new IPEDException("Encrypted AD1 file!");
            
            inputStreamFactory = new AD1InputStreamFactory(datasource.toPath());
            
            for (FileHeader header : ad1.getRootHeaders()) {
                if(header.isEncrypted())
                    throw new IPEDException("Encrypted AD1 file!");
                
                if(Thread.currentThread().isInterrupted())
                    throw new InterruptedException();
                
                createAndAddItem(ad1, header, rootItem);
            }
            
        }
        
        return 0;
    }
    
    private EvidenceFile addRootItem(File root) throws InterruptedException {
        
        if(listOnly) {
            caseData.incDiscoveredEvidences(1);
            return null;
        }
        
        String evidenceName = getEvidenceName(root);
        if (evidenceName == null)
            evidenceName = root.getName();
        dataSource = new DataSource(root);
        dataSource.setName(evidenceName);
        
        EvidenceFile rootItem = new EvidenceFile();
        rootItem.setRoot(true);
        rootItem.setDataSource(dataSource);
        rootItem.setPath(evidenceName);
        rootItem.setName(evidenceName);
        rootItem.setHasChildren(true);
        rootItem.setLength(root.length());
        rootItem.setSumVolume(false);
        rootItem.setHash(""); //$NON-NLS-1$
        
        caseData.addEvidenceFile(rootItem);
        
        return rootItem;
    }
    
    private void createAndAddItem(AD1Extractor ad1, FileHeader header, EvidenceFile parent) throws InterruptedException{
        
        EvidenceFile item = new EvidenceFile();
        item.setDataSource(dataSource);
        if(parent != null) item.setParent(parent);
        item.setIsDir(header.isDirectory());
        item.setName(header.getFileName());
        if(rootItem != null) item.setPath(rootItem.getName() + header.getFilePath());
        item.setLength(header.getFileSize());
        item.setModificationDate(header.getMTime());
        item.setAccessDate(header.getATime());
        item.setCreationDate(header.getCTime());
        item.setRecordDate(header.getRTime());
        item.setDeleted(header.isDeleted());
        item.setHasChildren(header.hasChildren());
        
        item.setInputStreamFactory(inputStreamFactory);
        item.setIdInDataSource(Long.toString(header.object_address));
        
        if(listOnly) {
            caseData.incDiscoveredEvidences(1);
            caseData.incDiscoveredVolume(header.getFileSize());
        }else
            caseData.addEvidenceFile(item);
        
        for(FileHeader child : header.children)
            createAndAddItem(ad1, child, item);
        
    }
    
    public static class AD1InputStreamFactory extends SeekableInputStreamFactory{
        
        AD1Extractor ad1;
        TreeMap<String, FileHeader> headerMap = new TreeMap<String, FileHeader>();

        public AD1InputStreamFactory(Path dataSource) {
            super(dataSource);
        }
        
        private synchronized void init() throws IOException {
            if(ad1 == null)
                try {
                    ad1 = new AD1Extractor(dataSource.toFile());
                    for(FileHeader fh : ad1.getHeaderList())
                        headerMap.put(Long.toString(fh.object_address), fh);
                        
                } catch (Exception e) {
                    throw new IOException(e);
                }
        }

        @Override
        public SeekableInputStream getSeekableInputStream(String identifier) throws IOException {
            if(ad1 == null)
                init();
            
            FileHeader fh = headerMap.get(identifier);
            return ad1.getSeekableInputStream(fh);
        }
        
    }

}
