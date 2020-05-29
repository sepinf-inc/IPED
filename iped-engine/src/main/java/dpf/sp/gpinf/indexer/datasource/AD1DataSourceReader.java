package dpf.sp.gpinf.indexer.datasource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import dpf.sp.gpinf.indexer.util.IPEDException;
import dpf.sp.gpinf.indexer.util.SeekableInputStreamFactory;
import gpinf.dev.data.DataSource;
import gpinf.dev.data.Item;
import iped3.ICaseData;
import iped3.IItem;
import iped3.io.SeekableInputStream;
import sef.mg.laud.ad1extractor.AD1Extractor;
import sef.mg.laud.ad1extractor.FileHeader;

public class AD1DataSourceReader extends DataSourceReader {

    AD1InputStreamFactory inputStreamFactory;
    IItem rootItem;

    public AD1DataSourceReader(ICaseData caseData, File output, boolean listOnly) {
        super(caseData, output, listOnly);
    }

    @Override
    public boolean isSupported(File datasource) {
        return datasource.getName().toLowerCase().endsWith(".ad1");
    }

    @Override
    public int read(File datasource) throws Exception {

        rootItem = addRootItem(datasource);

        try (AD1Extractor ad1 = new AD1Extractor(datasource)) {
            if (ad1.isEncrypted())
                throw new IPEDException("Encrypted AD1 file!");

            inputStreamFactory = new AD1InputStreamFactory(datasource.toPath());

            FileHeader rootHeader = ad1.getRootHeader();

            if (rootHeader.isEncrypted())
                throw new IPEDException("Encrypted AD1 file!");

            createAndAddItemRecursive(ad1, rootHeader, rootItem);

        }

        return 0;
    }

    private IItem addRootItem(File root) throws InterruptedException {

        if (listOnly) {
            caseData.incDiscoveredEvidences(1);
            return null;
        }

        String evidenceName = getEvidenceName(root);
        dataSource = new DataSource(root);
        dataSource.setName(evidenceName);

        IItem rootItem = new Item();
        rootItem.setRoot(true);
        rootItem.setDataSource(dataSource);
        rootItem.setPath(evidenceName);
        rootItem.setName(evidenceName);
        rootItem.setHasChildren(true);
        rootItem.setLength(root.length());
        rootItem.setSumVolume(false);
        rootItem.setHash(""); //$NON-NLS-1$

        caseData.addItem(rootItem);

        return rootItem;
    }

    private void createAndAddItem(AD1Extractor ad1, FileHeader header, IItem parent)
            throws IOException, InterruptedException {

        IItem item = new Item();

        if (!listOnly) {
            item.setDataSource(dataSource);
            item.setParent(parent);
            item.setIsDir(header.isDirectory());
            item.setName(header.getFileName());
            item.setPath(rootItem.getName() + header.getFilePath());
            item.setLength(header.getFileSize());
            item.setModificationDate(header.getMTime());
            item.setAccessDate(header.getATime());
            item.setCreationDate(header.getCTime());
            item.setRecordDate(header.getRTime());
            item.setDeleted(header.isDeleted());
            item.setHasChildren(header.hasChildren());

            item.setInputStreamFactory(inputStreamFactory);
            item.setIdInDataSource(Long.toString(header.object_address));

            caseData.addItem(item);

        } else {
            caseData.incDiscoveredEvidences(1);
            caseData.incDiscoveredVolume(header.getFileSize());
        }

        FileHeader child = header.getChildHeader();
        if (child != null)
            createAndAddItemRecursive(ad1, child, item);

    }

    private void createAndAddItemRecursive(AD1Extractor ad1, FileHeader header, IItem parent)
            throws IOException, InterruptedException {

        createAndAddItem(ad1, header, parent);

        while ((header = header.getNextHeader()) != null)
            createAndAddItem(ad1, header, parent);
    }

    public static class AD1InputStreamFactory extends SeekableInputStreamFactory {

        AD1Extractor ad1;

        public AD1InputStreamFactory(Path dataSource) {
            super(dataSource);
        }

        private synchronized void init() throws IOException {
            if (ad1 == null)
                ad1 = new AD1Extractor(dataSource.toFile());
        }

        @Override
        public SeekableInputStream getSeekableInputStream(String identifier) throws IOException {
            if (ad1 == null)
                init();

            FileHeader fh = ad1.lerObjeto(Long.parseLong(identifier), null);
            return ad1.getSeekableInputStream(fh);
        }

    }

}
