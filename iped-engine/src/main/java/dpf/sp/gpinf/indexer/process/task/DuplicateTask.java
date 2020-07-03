package dpf.sp.gpinf.indexer.process.task;

import java.io.File;
import java.util.HashMap;
import java.util.Properties;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexNotFoundException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.SlowCompositeReaderWrapper;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.store.FSDirectory;

import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.process.Worker;
import dpf.sp.gpinf.indexer.util.ConfiguredFSDirectory;
import dpf.sp.gpinf.indexer.util.HashValue;
import iped3.IItem;
import iped3.IHashValue;

/**
 * Tarefa de verificação de arquivos duplicados. Ignora o arquivo caso
 * configurado.
 *
 */
public class DuplicateTask extends AbstractTask {

    public static String HASH_MAP = HashTask.class.getSimpleName() + "HashMap"; //$NON-NLS-1$

    private HashMap<IHashValue, IHashValue> hashMap;

    public static boolean ignoreDuplicates = false;

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

        String value = confProps.getProperty("ignoreDuplicates"); //$NON-NLS-1$
        if (value != null) {
            value = value.trim();
        }
        if (value != null && !value.isEmpty()) {
            ignoreDuplicates = Boolean.valueOf(value);
        }

        hashMap = (HashMap<IHashValue, IHashValue>) caseData.getCaseObject(HASH_MAP);
        if (hashMap == null) {
            hashMap = new HashMap<IHashValue, IHashValue>();
            caseData.putCaseObject(HASH_MAP, hashMap);

            try(IndexReader reader = DirectoryReader.open(worker.writer, true)){
                AtomicReader aReader = SlowCompositeReaderWrapper.wrap(reader);
                SortedDocValues sdv = aReader.getSortedDocValues(IndexItem.HASH);
                if(sdv != null) {
                    for(int ord = 0; ord < sdv.getValueCount(); ord++) {
                        String hash = sdv.lookupOrd(ord).utf8ToString();
                        if (hash != null && !hash.isEmpty()) {
                            IHashValue hValue = new HashValue(hash);
                            hashMap.put(hValue, hValue);
                        }
                    }
                }
            }catch(IndexNotFoundException e) {
                //ignore
            }
        }

    }

    @Override
    public void finish() throws Exception {
        hashMap.clear();
    }

}
