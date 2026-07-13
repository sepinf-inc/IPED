package iped.engine.task.jumplist;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.tika.mime.MediaType;

import iped.configuration.Configurable;
import iped.data.IItem;
import iped.engine.config.ConfigurationManager;
import iped.engine.search.QueryBuilder;
import iped.engine.task.AbstractTask;
import iped.properties.ExtraProperties;
import iped.properties.MediaTypes;

public class JumpListTask extends AbstractTask {

    public static final MediaType AUTOMATIC_DESTINATIONS_MIME = MediaType.application("x-customdestinations");
    public static final MediaType CUSTOM_DESTINATIONS_MIME = MediaType.application("x-automaticdestinations");

    public static final MediaType AUTOMATIC_DESTINATIONS_ENTRY_MIME = MediaType.application("x-customdestinations-entry");
    public static final MediaType CUSTOM_DESTINATIONS_ENTRY_MIME = MediaType.application("x-automaticdestinations-entry");

    private static final String AUTOMATIC_DESTINATIONS_SUFIX = ".automaticDestinations-ms";
    private static final String CUSTOM_DESTINATIONS_SUFIX = ".customDestinations-ms";

    public static final String JUMPLIST_META_PREFIX = "jumpList:";
    public static final String JUMPLIST_PROGRAM_APP_IDS = JUMPLIST_META_PREFIX + "ids";

    private static final MediaType EXE_MIME = MediaType.application("x-msdownload");

    private Configurable<ConcurrentMap<String, String>> jumpListAppIDsConfig;

    @Override
    public List<Configurable<?>> getConfigurables() {
        return Arrays.asList(new AppIDsConfig());
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {
        jumpListAppIDsConfig = configurationManager.findObject(AppIDsConfig.class);
    }

    @Override
    public void finish() throws Exception {
    }

    @Override
    protected void process(IItem evidence) throws Exception {

        processAutomaticDestinationsEntry(evidence);
        processExecutables(evidence);
    }

    private void processAutomaticDestinationsEntry(IItem evidence) {

        if (evidence.getMediaType().equals(AUTOMATIC_DESTINATIONS_ENTRY_MIME)
                || evidence.getMediaType().equals(CUSTOM_DESTINATIONS_ENTRY_MIME)) {

            if (evidence.getMetadata().get(JUMPLIST_META_PREFIX + "appID") != null) {
                return;
            }

            String parentPath = StringUtils.removeEnd(evidence.getPath(), evidence.getName());
            parentPath = StringUtils.removeEnd(parentPath, "/");
            parentPath = StringUtils.removeEnd(parentPath, ">>");

            String parentName = StringUtils.substringAfterLast(parentPath, "/");

            if (StringUtils.endsWithAny(parentName, AUTOMATIC_DESTINATIONS_SUFIX, CUSTOM_DESTINATIONS_SUFIX)) {

                String appID = StringUtils.removeEnd(parentName, AUTOMATIC_DESTINATIONS_SUFIX);
                appID = StringUtils.removeEnd(appID, CUSTOM_DESTINATIONS_SUFIX);
                appID = appID.toLowerCase();
                evidence.getMetadata().set(JUMPLIST_META_PREFIX + "appID", appID);

                String appName = jumpListAppIDsConfig.getConfiguration().get(appID);
                if (appName != null) {
                    evidence.getMetadata().set(JUMPLIST_META_PREFIX + "appName", appName);
                }

                String linkQuery = QueryBuilder.escape(JUMPLIST_PROGRAM_APP_IDS) + ":" + appID;
                evidence.getMetadata().add(ExtraProperties.LINKED_ITEMS, linkQuery);
            }
        }
    }

    private void processExecutables(IItem evidence) {

        if (MediaTypes.isInstanceOf(evidence.getMediaType(), EXE_MIME) && !evidence.isCarved() && !evidence.isDeleted() && !evidence.isSubItem()) {

            List<String> appIDs = AppIDCalculator.calculateAppIDs(evidence.getPath());

            if (evidence.getMetadata().get(JUMPLIST_PROGRAM_APP_IDS) == null) {
                for (String appID : appIDs) {

                    // add appID
                    evidence.getMetadata().add(JUMPLIST_PROGRAM_APP_IDS, appID);

                    // increment the known appIDs map
                    jumpListAppIDsConfig.getConfiguration().putIfAbsent(appID, evidence.getName());
                }
            }
        }
    }
}
