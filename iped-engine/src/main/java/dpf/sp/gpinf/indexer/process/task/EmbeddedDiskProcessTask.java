package dpf.sp.gpinf.indexer.process.task;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.tika.mime.MediaType;

import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.config.EnableTaskProperty;
import dpf.sp.gpinf.indexer.datasource.SleuthkitReader;
import gpinf.dev.data.Item;
import iped3.IItem;
import iped3.util.MediaTypes;
import macee.core.Configurable;

public class EmbeddedDiskProcessTask extends AbstractTask {

    private static final String ENABLE_PARAM = "processEmbeddedDisks";

    private static final String outputFolder = "embeddedDisks";

    private static Set<MediaType> supportedMimes = MediaType.set(MediaTypes.VMDK, MediaTypes.VHD, MediaTypes.RAW_IMAGE,
            MediaTypes.E01_IMAGE);

    private static Set<File> exportedDisks = Collections.synchronizedSet(new HashSet<>());

    private boolean enabled = true;

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public List<Configurable<?>> getConfigurables() {
        return Arrays.asList(new EnableTaskProperty(ENABLE_PARAM));
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {
        enabled = configurationManager.getEnableTaskProperty(ENABLE_PARAM);
    }

    @Override
    public void finish() throws Exception {
        if (caseData.containsReport()) {
            exportedDisks.stream().forEach(f -> f.delete());
        }

    }

    public static boolean isSupported(IItem item) {
        return item.getLength() != null && item.getLength() > 0 && !item.isRoot()
                && supportedMimes.contains(item.getMediaType())
                && item.getExtraAttribute(BaseCarveTask.FILE_FRAGMENT) == null;
    }

    @Override
    protected void process(IItem item) throws Exception {

        if (!isSupported(item)) {
            return;
        }

        File imageFile = null;
        if (item.hasFile()) {
            imageFile = item.getFile();
        } else {
            File exportDir = new File(this.output, outputFolder);
            exportDir.mkdirs();
            imageFile = new File(exportDir, item.getId() + "." + item.getTypeExt());
            try (InputStream is = item.getBufferedStream()) {
                Files.copy(is, imageFile.toPath());
            }
            exportedDisks.add(imageFile);
        }


        try (SleuthkitReader reader = new SleuthkitReader(true, caseData, output)) {
            reader.read(imageFile, (Item) item);
            int numSubitems = reader.getItemCount();
            if (numSubitems > 0) {
                item.setHasChildren(true);
                item.setExtraAttribute(ParsingTask.HAS_SUBITEM, Boolean.TRUE.toString());
                item.setExtraAttribute(ParsingTask.NUM_SUBITEMS, numSubitems);
            }
        }

    }

}
