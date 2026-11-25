package iped.engine.task.leapp.interceptors;

import java.util.Map;
import java.util.Objects;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import iped.engine.task.leapp.CallInterceptor;
import iped.engine.task.leapp.LeappContext;
import jep.PyMethod;

/**
 * Replaces ilapfuncs.check_in_embedded_media: plugins call it to register a media that is not a file of the extraction,
 * but data stored inside one (e.g. a picture kept in a database blob), and put the returned reference in a 'media'
 * typed column of their results.
 *
 * The original function writes the data to the LAVA media folder and database, not used here. Instead, the data is
 * kept in the context of the current plugin run and a reference to it is returned: when the row holding that
 * reference is turned into a subitem, PluginResultsProcessor creates a child item with the data.
 */
public class IlapfuncsEmbeddedMediaInterceptor extends CallInterceptor {

    public static final String REFERENCE_PREFIX = "iped-embedded-media:";

    public IlapfuncsEmbeddedMediaInterceptor() {
        super("scripts.ilapfuncs", "scripts.ilapfuncs.check_in_embedded_media");
    }

    @Override
    @PyMethod(varargs = true, kwargs = true)
    public Object call(Object[] args, Map<String, Object> kwargs) throws Exception {

        // check_in_embedded_media(source_file, data, name="", force_type=None, force_extension=None, ...)
        Object data = getArgumentValue("data", 1, args, kwargs);
        LeappContext context = LeappContext.get();

        // Jep hands python bytes over as byte[]; like the original function, no data means no media
        if (!(data instanceof byte[]) || ArrayUtils.isEmpty((byte[]) data) || context == null) {
            return null;
        }
        byte[] bytes = (byte[]) data;

        Object name = getArgumentValue("name", 2, args, kwargs);
        Object extension = getArgumentValue("force_extension", 4, args, kwargs);

        // the same data registered more than once (e.g. an avatar repeated in every row) gets the same reference
        String reference = REFERENCE_PREFIX + DigestUtils.sha1Hex(bytes);
        context.getEmbeddedMedia().putIfAbsent(reference, new LeappContext.EmbeddedMedia(bytes,
                Objects.toString(name, null), StringUtils.removeStart(Objects.toString(extension, null), ".")));

        return reference;
    }
}
