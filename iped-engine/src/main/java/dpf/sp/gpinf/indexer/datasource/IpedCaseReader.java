package dpf.sp.gpinf.indexer.datasource;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.gov.pf.labld.cases.IpedCase;
import br.gov.pf.labld.cases.IpedCase.IpedDatasource;
import br.gov.pf.labld.cases.IpedCase.IpedInput;
import gpinf.dev.data.DataSource;
import gpinf.dev.data.Item;
import iped3.ICaseData;

public class IpedCaseReader extends DataSourceReader {

    private static Logger LOGGER = LoggerFactory.getLogger(IpedCaseReader.class);

    private List<DataSourceReader> subReaders;

    public IpedCaseReader(ICaseData caseData, File output, boolean listOnly) {
        super(caseData, output, listOnly);
        initSubReaders();
    }

    private void initSubReaders() {
        subReaders = new ArrayList<>(3);

        SleuthkitReader sleuthkitReader = new SleuthkitReader(caseData, output, listOnly);
        UfedXmlReader ufedXmlReader = new UfedXmlReader(caseData, output, listOnly);
        FolderTreeReader folderTreeReader = new FolderTreeReader(caseData, output, listOnly);

        subReaders.add(sleuthkitReader);
        subReaders.add(ufedXmlReader);
        subReaders.add(folderTreeReader);
    }

    @Override
    public boolean isSupported(File datasource) {
        boolean isIpedCase = datasource.isFile() && datasource.getName().equals(IpedCase.IPEDCASE_JSON);
        if (isIpedCase) {
            try {
                IpedCase.loadFrom(datasource);
            } catch (IOException e) {
                LOGGER.error("Could not read " + datasource.getAbsolutePath(), e);
                return false;
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public int read(File file) throws Exception {

        IpedCase ipedCase = IpedCase.loadFrom(file);
        caseData.putCaseObject(IpedCase.class.getName(), ipedCase);

        dataSource = new DataSource(ipedCase.getCaseFile());

        List<Item> datasourceEvidences = null;
        if (!listOnly) {
            Item caseEvidence = createCaseEvidence(ipedCase);
            datasourceEvidences = createRootEvidences(ipedCase, caseEvidence);
        }

        List<IpedDatasource> datasources = ipedCase.getDatasources();
        for (int index = 0; index < datasources.size(); index++) {
            IpedDatasource ipedDatasource = datasources.get(index);
            Item datasourceEvidence = null;
            if (!listOnly) {
                datasourceEvidence = datasourceEvidences.get(index);
            }

            List<IpedInput> inputs = ipedDatasource.getInputs();
            for (IpedInput input : inputs) {
                File inputFile = new File(input.getPath());

                DataSourceReader dataSourceReader = getSuitableReader(inputFile);
                if (dataSourceReader == null) {
                    throw new IllegalArgumentException("Unsupported datasource file: " + inputFile);
                } else {
                    dataSourceReader.dataSource = dataSource;
                    dataSourceReader.read(inputFile, datasourceEvidence);
                }
            }
        }
        return 0;
    }

    private List<Item> createRootEvidences(IpedCase ipedCase, Item caseEvidence) {
        List<IpedDatasource> datasources = ipedCase.getDatasources();
        List<Item> result = new ArrayList<>(datasources.size());
        for (IpedDatasource ipedDatasource : datasources) {
            Item datasourceEvidence = createDatasourceEvidence(ipedDatasource, caseEvidence);
            result.add(datasourceEvidence);
        }
        return result;
    }

    private DataSourceReader getSuitableReader(File inputFile) {
        for (DataSourceReader reader : subReaders) {
            if (reader.isSupported(inputFile)) {
                return reader;
            }
        }
        return null;
    }

    private Item createDatasourceEvidence(IpedDatasource datasource, Item caseEvidence) {
        String evidenceName = datasource.getName();

        Item Item = new Item();
        Item.setDataSource(dataSource);
        Item.setIsDir(true);
        Item.setHasChildren(true);
        Item.setPath(caseEvidence.getPath() + File.separator + evidenceName);
        Item.setName(evidenceName);
        Item.setHash("");
        Item.setParent(caseEvidence);
        String type = datasource.getExtras().get("type");
        if (type != null) {
            Item.setExtraAttribute("X-EntityType", type);
        }
        String property = datasource.getExtras().get("property");
        if (property != null) {
            Item.getExtraAttributeMap().put("X-EntityProperty", property);
        }
        String value = datasource.getExtras().get("value");
        if (value != null) {
            Item.getExtraAttributeMap().put("X-EntityPropertyValue", value);
        }

        Item.getId();

        caseData.incDiscoveredEvidences(1);

        try {
            caseData.addItem(Item);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return Item;
    }

    private Item createCaseEvidence(IpedCase ipedCase) {
        String evidenceName = ipedCase.getName();

        Item Item = new Item();
        Item.setRoot(true);
        Item.setIsDir(true);
        Item.setHasChildren(true);
        Item.setDataSource(dataSource);
        Item.setPath(evidenceName);
        Item.setName(evidenceName);
        Item.setHash("");

        Item.getId();

        caseData.incDiscoveredEvidences(1);
        try {
            caseData.addItem(Item);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return Item;
    }

}
