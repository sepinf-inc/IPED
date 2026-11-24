package iped.engine.task.leapp.aleapp;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.mime.MediaType;

import iped.configuration.Configurable;
import iped.data.IItem;
import iped.engine.config.ALeappConfig;
import iped.engine.config.ConfigurationManager;
import iped.engine.task.leapp.AbstractLeappTask;
import iped.engine.task.leapp.LeappTool;
import iped.engine.task.leapp.UfdrDecodingMode;
import iped.engine.task.leapp.UfedModelRule;
import iped.parsers.android.backup.AndroidBackupParser;

/**
 * Runs ALEAPP plugins over the Android extractions of the case. The integration itself is in
 * {@link AbstractLeappTask}: here is only what is specific to ALEAPP and to Android extractions.
 */
public class AleappTask extends AbstractLeappTask {

    public static final MediaType ALEAPP_CASE_MEDIATYPE = AleappTool.INSTANCE.mediaType(LeappTool.CASE_SUBTYPE);
    public static final MediaType ALEAPP_CATEGORY_MEDIATYPE = AleappTool.INSTANCE.mediaType(LeappTool.CATEGORY_SUBTYPE);
    public static final MediaType ALEAPP_PLUGIN_RESULTS_MEDIATYPE = AleappTool.INSTANCE.mediaType(LeappTool.PLUGIN_RESULTS_SUBTYPE);
    public static final MediaType ALEAPP_DEVICE_INFO_MEDIATYPE = AleappTool.INSTANCE.mediaType(LeappTool.DEVICE_INFO_SUBTYPE);

    private static final String ZIP_EXT = "zip";

    private static final Set<String> DUMP_ROOT_FOLDER_NAMES = Set.of("Dump", "backup");

    // top level folders of an android file system, used to tell if ALEAPP should run over a extraction (see isAndroidFileSystem)
    private static final Set<String> ANDROID_ROOT_FOLDER_NAMES = Set.of("data", "system");

    private static final String EXTRACTION_TYPE_ANDROID_BACKUP = "android-backup";
    private static final String EXTRACTION_TYPE_ZIP = "zip";
    private static final String EXTRACTION_TYPE_DUMP = "dump";

    private ALeappConfig config;

    @Override
    protected LeappTool getTool() {
        return AleappTool.INSTANCE;
    }

    @Override
    public List<Configurable<?>> getConfigurables() {
        return Arrays.asList(new ALeappConfig());
    }

    @Override
    public boolean isEnabled() {
        return config.isEnabled();
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {
        config = (ALeappConfig) configurationManager.findObject(ALeappConfig.class);
        super.init(configurationManager);
    }

    @Override
    protected File getToolFolder() {
        return config.getAleappFolder();
    }

    @Override
    protected boolean isPluginIncluded(String moduleName) {
        return config.isPluginIncluded(moduleName);
    }

    @Override
    protected UfdrDecodingMode getUfdrDecodingMode() {
        return config.getUfdrDecodingToUse();
    }

    @Override
    protected List<UfedModelRule> getUfedModels(String moduleName, String artifactName) {
        return config.getUfedModels(moduleName, artifactName);
    }

    @Override
    protected boolean isExtractionRoot(IItem evidence) {

        if (AndroidBackupParser.SUPPORTED_TYPES.contains(evidence.getMediaType())) {
            return true;
        }

        // in a ufdr the device tree does not hang from the ufdr item itself, but from its file system
        // item, so the latter is the extraction root (see isUfdrFileSystem)
        if (isUfdrFileSystem(evidence)) {
            return config.getUfdrDecodingToUse() != UfdrDecodingMode.NONE;
        }

        if (evidence.isRoot()) {
            // if evidence is root, its realname can be changed via -dname parameter, so we
            // need to get it from other source.
            return DUMP_ROOT_FOLDER_NAMES.contains(evidence.getDataSource().getSourceFile().getName());
        }

        if (!evidence.isDir() || !DUMP_ROOT_FOLDER_NAMES.contains(evidence.getName())) {
            return false;
        }

        // a folder with one of these names inside an extraction (e.g. "Dump/data/backup") is not another extraction
        String[] ancestors = StringUtils.splitByWholeSeparator(
                StringUtils.replace(StringUtils.substringBeforeLast(evidence.getPath(), "/"), ">>", "/"), "/");
        return Arrays.stream(ancestors).noneMatch(DUMP_ROOT_FOLDER_NAMES::contains);
    }

    @Override
    protected String getExtractionType(IItem rootEvidence) {

        if (AndroidBackupParser.SUPPORTED_TYPES.contains(rootEvidence.getMediaType())) {
            return EXTRACTION_TYPE_ANDROID_BACKUP;
        }
        if (isUfdrFileSystem(rootEvidence)) {
            return EXTRACTION_TYPE_UFDR;
        }

        String realExt = rootEvidence.getExt();
        if (rootEvidence.isRoot()) {
            String realName = rootEvidence.getDataSource().getSourceFile().getName();
            realExt = FilenameUtils.getExtension(realName);
        }
        return ZIP_EXT.equalsIgnoreCase(realExt) ? EXTRACTION_TYPE_ZIP : EXTRACTION_TYPE_DUMP;
    }

    /**
     * Checks if the extraction root really holds an Android extraction the plugins can decode, applying the checks each
     * extraction type needs.
     */
    @Override
    protected boolean isSupportedExtraction(IItem caseEvidence, String extractionType) {

        switch (extractionType) {

        case EXTRACTION_TYPE_ANDROID_BACKUP:
            // expanded as a tar, holding a file system of its own ("apps/<package>", "shared/0"...)
            return true;

        case EXTRACTION_TYPE_UFDR:
            return isAndroidFileSystem(caseEvidence);

        case EXTRACTION_TYPE_DUMP:
        case EXTRACTION_TYPE_ZIP:
            return isAndroidFileSystem(caseEvidence) && isInsideRealDump(caseEvidence);

        default:
            throw new IllegalStateException("Unexpected extraction type: " + extractionType);
        }
    }

    /**
     * Checks if the case evidence really corresponds to an Android dump, by looking for a folder every Android device
     * has ("/data/data/com.android.vending"). Avoids running plugins over unrelated zips/folders that just happen to
     * match the extraction root naming rules.
     */
    private boolean isInsideRealDump(IItem caseEvidence) {
        return findExtractionItem(caseEvidence, "/data/data/com.android.vending") != null;
    }

    /**
     * Checks if the extraction root holds an Android file system, by looking for at least one of its top level folders
     * ({@link #ANDROID_ROOT_FOLDER_NAMES}).
     * A ufdr can hold an iOS, KeyChain or KeyStore extractions, whose file system no plugin can decode.
     */
    private boolean isAndroidFileSystem(IItem caseEvidence) {
        // anyMatch short-circuits: the next folder is only searched if the previous one was not found
        return ANDROID_ROOT_FOLDER_NAMES.stream()
                .anyMatch(folder -> findExtractionItem(caseEvidence, "/" + folder) != null);
    }
}
