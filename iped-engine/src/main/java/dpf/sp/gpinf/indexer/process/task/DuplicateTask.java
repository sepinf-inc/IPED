package dpf.sp.gpinf.indexer.process.task;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexNotFoundException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.SortedDocValues;

import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.config.EnableTaskProperty;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.util.HashValue;
import dpf.sp.gpinf.indexer.util.SlowCompositeReaderWrapper;
import iped3.IHashValue;
import iped3.IItem;
import macee.core.Configurable;

/**
 * Tarefa de verificação de arquivos duplicados. Ignora o arquivo caso
 * configurado.
 *
 */
public class DuplicateTask extends AbstractTask {

    public static String HASH_MAP = HashTask.class.getSimpleName() + "HashMap"; //$NON-NLS-1$

    private static final String ENABLE_PARAM = "ignoreDuplicates"; //$NON-NLS-1$

    private HashMap<IHashValue, IHashValue> hashMap;

    private static boolean ignoreDuplicates = false;

    public static boolean isIgnoreDuplicatesEnabled() {
        return ignoreDuplicates;
    }

    @Override
    public List<Configurable> getConfigurables() {
        return Arrays.asList(new EnableTaskProperty(ENABLE_PARAM));
    }

    public void process(IItem evidence) {

        // Verificação de duplicados
        IHashValue hashValue = evidence.getHashValue();
        if (hashValue != null) {
            synchronized (hashMap) {
                if (!hashMap.containsKey(hashValue)) {
                    hashMap.put(hashValue, hashValue);
                } else {
                    evidence.setDuplicate(true);
                }

            }
        }

        if (ignoreDuplicates && evidence.isDuplicate() && !evidence.isDir() && !evidence.isRoot()
                && !caseData.isIpedReport()) {
            evidence.setToIgnore(true);
        }

    }

    @Override
    public void init(Properties confProps, File confDir) throws Exception {

        ignoreDuplicates = ConfigurationManager.getEnableTaskProperty(ENABLE_PARAM);

        hashMap = (HashMap<IHashValue, IHashValue>) caseData.getCaseObject(HASH_MAP);
        if (hashMap == null) {
            hashMap = new HashMap<IHashValue, IHashValue>();
            caseData.putCaseObject(HASH_MAP, hashMap);

            try (IndexReader reader = DirectoryReader.open(worker.writer, true, true)) {
                LeafReader aReader = SlowCompositeReaderWrapper.wrap(reader);
                SortedDocValues sdv = aReader.getSortedDocValues(IndexItem.HASH);
                if (sdv != null) {
                    for (int ord = 0; ord < sdv.getValueCount(); ord++) {
                        String hash = sdv.lookupOrd(ord).utf8ToString();
                        if (hash != null && !hash.isEmpty()) {
                            IHashValue hValue = new HashValue(hash);
                            hashMap.put(hValue, hValue);
                        }
                    }
                }
            } catch (IndexNotFoundException e) {
                // ignore
            }
        }

    }

    @Override
    public void finish() throws Exception {
        hashMap.clear();
    }

}
