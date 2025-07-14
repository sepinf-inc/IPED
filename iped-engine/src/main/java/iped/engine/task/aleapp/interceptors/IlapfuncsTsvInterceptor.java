package iped.engine.task.aleapp.interceptors;

import static iped.engine.task.aleapp.AleappTask.ALEAPP_APPLICATION_PREFIX;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.apache.commons.lang3.StringUtils;
import org.apache.tika.mime.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.data.IItemReader;
import iped.engine.core.Worker.ProcessTime;
import iped.engine.data.Item;
import iped.engine.task.aleapp.AleappTask;
import iped.engine.task.aleapp.AleappTask.State;
import iped.engine.task.aleapp.AleappUtils;
import iped.engine.task.aleapp.CallInterceptor;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import jep.PyMethod;

/**
 * Replace the ilapfuncs.tsv function
 */
public class IlapfuncsTsvInterceptor extends CallInterceptor {

    protected static final Logger logger = LoggerFactory.getLogger(IlapfuncsTsvInterceptor.class);

    public IlapfuncsTsvInterceptor() {
        super("scripts.ilapfuncs", "scripts.ilapfuncs.tsv");
    }

    @SuppressWarnings("unchecked")
    @Override
    @PyMethod(varargs = true, kwargs = true)
    public Object call(Object[] args, Map<String, Object> kwargs) throws Exception {

        // get params from arguments: tsv(report_folder, data_headers, data_list, tsvname, source_file=None):
        List<String> dataHeaders = (List<String>) getArgumentValue("data_headers", 1, args, kwargs);
        List<List<Object>> dataList = (List<List<Object>>) getArgumentValue("data_list", 2, args, kwargs);
        String tsvName = (String) getArgumentValue("tsvname", 3, args, kwargs);
        String sourceFile = (String) getArgumentValue("source_file", 4, args, kwargs);

        if (dataList.isEmpty()) {
            return null;
        }

        State state = AleappTask.getState();

        // set hasChildren, so plugin will not be ignored in AleappTask.processPluginEvidence()
        state.getPluginItem().setHasChildren(true);

        // create node for tsv
        Item tsvNode = (Item) state.getPluginItem().createChildItem();
        tsvNode.setName(tsvName);
        tsvNode.setExtension("");
        tsvNode.setPath(state.getPluginItem().getPath() + "/" + tsvName);
        tsvNode.setIdInDataSource("");
        tsvNode.setHasChildren(true);
        tsvNode.setExtraAttribute(BasicProps.TREENODE, Boolean.valueOf(true));

        state.getWorker().processNewItem(tsvNode);

        // linkedItems for subItems
        String linkedItem = null;
        if (sourceFile != null) {
            IItemReader sourceFileItem = AleappUtils.findItemByPath(state.getCaseData(), sourceFile);
            if (sourceFileItem != null && sourceFileItem.getHash() != null) {
                linkedItem = BasicProps.HASH + ":\"" + sourceFileItem.getHash() + "\"";
            }
        }
        String finalLinkedItem = linkedItem;

        // media type
        String pluginName = state.getPluginItem().getMetadata().get(AleappTask.ALEAPP_PLUGIN_KEYNAME_META);
        MediaType mediaType = resolveMediaType(tsvName, pluginName);

        // create subItems
        IntStream.range(0, dataList.size()).forEach(index -> {

            String subItemName = tsvName + "-" + index;
            Item subItem = (Item) tsvNode.createChildItem();
            subItem.setMediaType(mediaType);
            subItem.setName(subItemName);
            subItem.setExtension("");
            subItem.setPath(tsvNode.getPath() + "/" + subItemName);
            subItem.setExtraAttribute(ExtraProperties.DECODED_DATA, true);
            subItem.setSubItem(true);
            subItem.setSubitemId(index);
            if (finalLinkedItem != null) {
                subItem.getMetadata().add(ExtraProperties.LINKED_ITEMS, finalLinkedItem);
            }

            // data as metadata
            List<Object> data = dataList.get(index);
            for (int i = 0; i < dataHeaders.size(); i++) {
                Object value = data.get(i);
                if (value != null) {
                    String header = dataHeaders.get(i);
                    subItem.getMetadata().set("aleapp:" + header, value.toString());
                }
            }

            state.getWorker().processNewItem(subItem, ProcessTime.LATER);
        });

        return null;
    }

    private MediaType resolveMediaType(String tsvName, String pluginModuleName) {

        String mimePluginName = pluginModuleName.toLowerCase().replace(".", "");

        if (StringUtils.containsIgnoreCase(mimePluginName, "facebook")) {
            mimePluginName = StringUtils.substringBefore(tsvName, "- ").toLowerCase();
        }

        if (StringUtils.containsIgnoreCase(pluginModuleName, "chrome")) {
            return MediaType.application(ALEAPP_APPLICATION_PREFIX + tsvNameToType(tsvName));
        } else if (StringUtils.containsIgnoreCase(tsvName, "Call")) {
            return MediaType.application(ALEAPP_APPLICATION_PREFIX + mimePluginName + "-call");
        } else if (StringUtils.containsIgnoreCase(tsvName, "Chat")) {
            return MediaType.application(ALEAPP_APPLICATION_PREFIX + mimePluginName + "-chat");
        } else if (StringUtils.containsIgnoreCase(tsvName, "Message")) {
            return MediaType.application(ALEAPP_APPLICATION_PREFIX + mimePluginName + "-message");
        } else if (StringUtils.containsAnyIgnoreCase(tsvName, "Activity", "Activities")) {
            return MediaType.application(ALEAPP_APPLICATION_PREFIX + mimePluginName + "-activity");
        } else if (StringUtils.containsIgnoreCase(tsvName, "Contact")) {
            return MediaType.application(ALEAPP_APPLICATION_PREFIX + mimePluginName + "-contact");
        } else if (StringUtils.containsIgnoreCase(tsvName, "Conversation")) {
            return MediaType.application(ALEAPP_APPLICATION_PREFIX + mimePluginName + "-conversation");
        } else if (StringUtils.containsIgnoreCase(tsvName, "Autofill")) {
            return MediaType.application(ALEAPP_APPLICATION_PREFIX + mimePluginName + "-autofill");
        } else {
            return MediaType.application(ALEAPP_APPLICATION_PREFIX + tsvNameToType(tsvName));
        }
    }

    private String tsvNameToType(String tsvName) {
        String type = StringUtils.substringBefore(tsvName, " (");
        type = type.replace(" - ", "-").replace(" ", "-").replace("--", "-");
        return type;
    }
}
