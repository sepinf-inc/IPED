package iped.engine.task;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.configuration.Configurable;
import iped.data.IItem;
import iped.engine.CmdLineArgs;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.LocalConfig;
import iped.engine.config.TempFileTaskConfig;
import iped.engine.data.Item;
import iped.utils.IOUtil;

/**
 * Tarefa para geração de arquivos temporários para os itens antes do
 * processamento. Caso indexTemp esteja em disco SSD e a imagem esteja
 * compactada (e01), pode aumentar consideravelmente a performance pois os itens
 * deixam de ser descompactados múltiplas vezes pela libewf, a qual não é thread
 * safe e sincroniza descompactações concorrentes, subaproveitando máquinas
 * multiprocessadas.
 *
 * @author Nassif
 *
 */
public class TempFileTask extends AbstractTask {

    private static Logger LOGGER = LoggerFactory.getLogger(TempFileTask.class);
    private long maxFileSize = 1L << 30;
    private boolean isEnabled = true;

    @Override
    public boolean isEnabled() {
        return isEnabled;
    }

    @Override
    public List<Configurable<?>> getConfigurables() {
        return Arrays.asList(new TempFileTaskConfig());
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {
        LocalConfig localConfig = configurationManager.findObject(LocalConfig.class);
        boolean indexTempOnSSD = localConfig.isIndexTempOnSSD();

        CmdLineArgs args = (CmdLineArgs) caseData.getCaseObject(CmdLineArgs.class.getName());
        isEnabled = indexTempOnSSD && !"fastmode".equals(args.getProfile()) && !"triage".equals(args.getProfile()) && !caseData.isIpedReport();

        if (isEnabled) {
            TempFileTaskConfig tempFileTaskConfig = configurationManager.findObject(TempFileTaskConfig.class);
            maxFileSize = tempFileTaskConfig.getMaxFileSize();
        }
    }

    @Override
    public void finish() throws Exception {
    }

    @Override
    protected void process(IItem evidence) throws Exception {

        if (!isEnabled) {
            return;
        }

        if (evidence instanceof Item) {
            if (((Item) evidence).cacheDataInMemory()) {
                return;
            }
        }

        Long len = evidence.getLength();
        if (len != null && len <= maxFileSize) {
            try {
                // skip items with File refs && carved items pointing to parent temp file
                if (!IOUtil.hasFile(evidence)
                        && (!(evidence instanceof Item) || !((Item) evidence).hasParentTmpFile())) {
                    if (!evidence.isSubItem()) { // should we create temp files for subitems compressed into the sqlite
                                                 // storages?
                        evidence.getTempFile();
                    }
                }
            } catch (IOException e) {
                LOGGER.warn("{} Error creating temp file {} {}", Thread.currentThread().getName(), evidence.getPath(), //$NON-NLS-1$
                        e.toString());
            }
        }
    }
}
