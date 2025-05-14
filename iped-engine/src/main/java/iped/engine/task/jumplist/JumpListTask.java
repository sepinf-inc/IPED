package iped.engine.task.jumplist;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import iped.configuration.Configurable;
import iped.data.IItem;
import iped.engine.config.ConfigurationManager;
import iped.engine.search.QueryBuilder;
import iped.engine.task.AbstractTask;
import iped.parsers.lnk.LNKShortcutParser;
import iped.properties.ExtraProperties;

public class JumpListTask extends AbstractTask {

    private static final String AUTOMATIC_DESTINATIONS_SUFIX = ".automaticDestinations-ms";

    private static final String JUMPLIST_META_PREFIX = "jumpList:";

    private Configurable<Map<String, String>> jumpListAppIDsConfig;

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

        handleAutomaticDestinationsEntry(evidence);
    }

    private void handleAutomaticDestinationsEntry(IItem evidence) {
        if (evidence.getMediaType().equals(LNKShortcutParser.LNK_MEDIA_TYPE)) {
            String parentPath = StringUtils.removeEnd(evidence.getPath(), evidence.getName());
            parentPath = StringUtils.removeEnd(parentPath, "/");
            parentPath = StringUtils.removeEnd(parentPath, ">>");

            String parentName = StringUtils.substringAfterLast(parentPath, "/");

            if (StringUtils.endsWith(parentName, AUTOMATIC_DESTINATIONS_SUFIX)) {

                String appID = StringUtils.removeEnd(parentName, AUTOMATIC_DESTINATIONS_SUFIX).toLowerCase();
                evidence.getMetadata().set(JUMPLIST_META_PREFIX + "appID", appID);

                String appName = jumpListAppIDsConfig.getConfiguration().get(appID);
                if (appName != null) {
                    evidence.getMetadata().set(JUMPLIST_META_PREFIX + "appName", appName);
                }

                evidence.getMetadata().add(ExtraProperties.LINKED_ITEMS, QueryBuilder.escape(JUMPLIST_META_PREFIX + "id") + ":" + appID);
            }
        }
    }
}
