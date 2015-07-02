package dpf.sp.gpinf.indexer.process.task;

import gpinf.dev.data.EvidenceFile;
import gpinf.dev.data.FileGroup;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;

import dpf.sp.gpinf.indexer.IndexFiles;
import dpf.sp.gpinf.indexer.process.Worker;
import dpf.sp.gpinf.indexer.process.task.HashTask.HashValue;
import dpf.sp.gpinf.indexer.util.Log;

/**
 * Tarefa de consulta a base de hashes do LED. Pode ser removida no futuro e ser integrada a tarefa de KFF.
 * A vantagem de ser independente é que a base de hashes pode ser atualizada facilmente, apenas apontando para
 * a nova base, sem necessidade de importação.
 * 
 * @author Nassif
 *
 */
public class LedKFFTask extends AbstractTask {

    private static String ledCategory = "Hash com Alerta (PI)";
    private static Object lock = new Object();
    private static HashValue[] hashArray;
    private static final String taskName = "Consulta Base de Hashes do LED";

    public LedKFFTask(Worker worker) {
        super(worker);
    }

    @Override
    public void init(Properties confParams, File confDir) throws Exception {

        synchronized (lock) {
            if (hashArray != null) return;

            this.caseData.addBookmark(new FileGroup(ledCategory, "", ""));
            String hash = confParams.getProperty("hash");
            String ledWkffPath = confParams.getProperty("ledWkffPath");
            if (ledWkffPath == null || hash == null)
            	return;
            File wkffDir = new File(ledWkffPath);
            if (!wkffDir.exists()) throw new Exception("Caminho para base de hashes do LED inválido!");

            int column = -1;
            hash = hash.toLowerCase();
            if (hash.equals("md5")) column = 0;
            else if (hash.equals("sha-1") || hash.equals("sha1")) column = 3;
            else {
                Log.warning(taskName, "Apenas hashes md5 e sha-1 disponíveis na base do LED.");
                Log.warning(taskName, "Tipo de hash configurado: " + hash + ".");
                Log.warning(taskName, "Tarefa DESABILITADA!");
                hashArray = new HashValue[0];
                return;
            }

            IndexFiles.getInstance().firePropertyChange("mensagem", "", "Carregando base de hashes do LED...");

            ArrayList<HashValue> hashList = new ArrayList<HashValue>();
            for (File wkffFile : wkffDir.listFiles()) {
                BufferedReader reader = new BufferedReader(new FileReader(wkffFile));
                String line = reader.readLine();
                while ((line = reader.readLine()) != null) {
                    String[] hashes = line.split(" \\*");
                    hashList.add(new HashValue(hashes[column].trim()));
                }
                reader.close();
            }
            hashArray = hashList.toArray(new HashValue[0]);
            hashList = null;
            Arrays.sort(hashArray);
            Log.info(taskName, "Hashes carregados: " + hashArray.length);
        }

    }

    @Override
    public void finish() throws Exception {
        hashArray = null;

    }

    @Override
    protected void process(EvidenceFile evidence) throws Exception {

        String hash = evidence.getHash();
        if (hash != null && hashArray != null) {
            if (Arrays.binarySearch(hashArray, new HashValue(hash)) >= 0) evidence.addCategory(ledCategory);

        }

    }

}
