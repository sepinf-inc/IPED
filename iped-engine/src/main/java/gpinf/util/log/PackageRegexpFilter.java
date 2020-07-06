package gpinf.util.log;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.filter.AbstractFilter;

@Plugin(name = "PackageRegexpFilter", category = "Core", elementType = "filter", printObject = true)
public class PackageRegexpFilter extends AbstractFilter {

    /**
     *
     */
    private static final long serialVersionUID = -8773522832741324758L;
    private Pattern pattern;

    private PackageRegexpFilter(final String regex, final Result onMatch, final Result onMismatch) {
        super(onMatch, onMismatch);
        pattern = Pattern.compile(regex);
    }

    @Override
    public Result filter(final LogEvent event) {
        String name = event.getLoggerName();
        Matcher m = pattern.matcher(name);
        if (m.matches()) {
            return onMatch;
        }
        return onMismatch;
    }

    @PluginFactory
    public static PackageRegexpFilter createFilter(@PluginAttribute("regex") final String regex,
            @PluginAttribute("OnMatch") final String match, @PluginAttribute("OnMismatch") final String mismatch) {
        return new PackageRegexpFilter(regex, Result.toResult(match), Result.toResult(mismatch));
    }

}
