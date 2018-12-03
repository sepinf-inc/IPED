package dpf.sp.gpinf.indexer.datasource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.TreeMap;

import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.apache.commons.io.IOUtils;

import dpf.sp.gpinf.indexer.parsers.util.Util;
import dpf.sp.gpinf.indexer.util.IPEDException;
import dpf.sp.gpinf.indexer.util.SeekableFileInputStream;
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
            
            //items may be out of tree order? So store them in a sorted path structure
            TreeMap<String, FileHeader> folderMap = new TreeMap<String, FileHeader>();
            
            for (FileHeader header : ad1.getHeaderList()) {
                if (header != null  && !Thread.currentThread().isInterrupted()){
                    if(header.isEncrypted())
                        throw new IPEDException("Encrypted AD1 file!");

                    if(header.isDirectory()){
                        folderMap.put(header.getFilePath().replace("\\", "/"), header);
                    }
                }
            }
            
            //iterates over items in tree order
            HashMap<String, EvidenceFile> parentMap = new HashMap<>();
            for(FileHeader dirheader : folderMap.values()){
                String itemPath = dirheader.getFilePath().replace("\\", "/");
                EvidenceFile parent = parentMap.get(Util.getParentPath(itemPath));
                EvidenceFile item = createAndAddItem(ad1, dirheader, parent);
                parentMap.put(itemPath, item);
                if(Thread.currentThread().isInterrupted())
                    throw new InterruptedException();
            }
            folderMap.clear();

            //processa os arquivos          
            for (FileHeader header : ad1.getHeaderList()) {
                if (header != null && !header.isDirectory()  && !Thread.currentThread().isInterrupted()){
                    String itemPath = header.getFilePath().replace("\\", "/");
                    EvidenceFile parent = parentMap.get(Util.getParentPath(itemPath));
                    createAndAddItem(ad1, header, parent);
                }
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
    
    private EvidenceFile createAndAddItem(AD1Extractor ad1, FileHeader header, EvidenceFile parent) throws InterruptedException{
        
        if(listOnly) {
            caseData.incDiscoveredEvidences(1);
            caseData.incDiscoveredVolume(header.getFileSize());
            return null;
        }
        
        EvidenceFile item = new EvidenceFile();
        item.setDataSource(dataSource);
        if(parent != null) item.setParent(parent);
        else item.setParent(rootItem);
        item.setIsDir(header.isDirectory());
        item.setName(header.getFileName());
        item.setPath(rootItem.getName() + header.getFilePath());
        item.setLength(header.getFileSize());
        item.setModificationDate(header.getMTime());
        item.setAccessDate(header.getATime());
        item.setCreationDate(header.getCTime());
        item.setRecordDate(header.getRTime());
        
        item.setInputStreamFactory(inputStreamFactory);
        item.setIdInDataSource(header.getFilePath());
        
        caseData.addEvidenceFile(item);
        
        return item;
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
                        headerMap.put(fh.getFilePath(), fh);
                        
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
