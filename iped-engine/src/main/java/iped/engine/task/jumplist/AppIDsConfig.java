package iped.engine.task.jumplist;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import iped.engine.config.AbstractTaskConfig;

public class AppIDsConfig extends AbstractTaskConfig<ConcurrentMap<String, String>> {

    private static final long serialVersionUID = 8409433427758336695L;

    public static final String CONFIG_FILE = "AppIDs.txt";

    private Pattern pattern = Pattern.compile("\"([^\"]*)\"");

    private ConcurrentMap<String, String> appIDsMap = new ConcurrentHashMap<>();

    @Override
    public ConcurrentMap<String, String> getConfiguration() {
        return appIDsMap;
    }

    @Override
    public void setConfiguration(ConcurrentMap<String, String> config) {
        appIDsMap = config;
    }

    @Override
    public String getTaskEnableProperty() {
        return null;
    }

    @Override
    public String getTaskConfigFileName() {
        return CONFIG_FILE;
    }

    /**
     * The AppID is a 64-bit number written in hexadecimal. It may be found with or
     * without leading zeros and in upper or lower case, so it must be normalized
     * before being compared.
     *
     * @return the AppID in lower case and without leading zeros.
     */
    public static String normalize(String appID) {
        return StringUtils.stripStart(StringUtils.lowerCase(appID), "0");
    }

    @Override
    public void processTaskConfig(Path resource) throws IOException {

        try (BufferedReader reader = Files.newBufferedReader(resource)) {
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.trim().startsWith("#") || line.trim().isEmpty()) {
                    continue;
                }

                Matcher matcher = pattern.matcher(line);

                if (!matcher.find()) {
                    continue;
                }
                String appID = normalize(matcher.group(1));

                if (!matcher.find()) {
                    continue;
                }
                String appName = matcher.group(1).trim();

                appIDsMap.put(appID, appName);
            }
        }

    }

}
