package iped.engine.datasource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import iped.data.ICaseData;
import iped.data.IItem;
import iped.engine.core.Manager;
import iped.engine.data.DataSource;
import iped.engine.data.Item;
import iped.engine.datasource.ad1.AD1Extractor;
import iped.engine.datasource.ad1.FileHeader;
import iped.exception.IPEDException;
import iped.io.SeekableInputStream;
import iped.utils.SeekableInputStreamFactory;

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
    public void read(File datasource) throws Exception {

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
        rootItem.setIdInDataSource("");

        Manager.getInstance().addItemToQueue(rootItem);

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
            item.setChangeDate(header.getRTime());
            item.setDeleted(header.isDeleted());
            item.setHasChildren(header.hasChildren());

            item.setInputStreamFactory(inputStreamFactory);
            item.setIdInDataSource(Long.toString(header.object_address));

            Manager.getInstance().addItemToQueue(item);

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
            super(dataSource.toUri());
        }

        private synchronized void init() throws IOException {
            if (ad1 == null)
                ad1 = new AD1Extractor(Paths.get(dataSource).toFile());
        }

        @Override
        public SeekableInputStream getSeekableInputStream(String identifier) throws IOException {
            if (ad1 == null)
                init();

            FileHeader fh = ad1.readObject(Long.parseLong(identifier), null);
            return ad1.getSeekableInputStream(fh);
        }

    }

}
