package iped.engine.task.jumplist;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    @Override
    public void processTaskConfig(Path resource) throws IOException {

        try (BufferedReader reader = Files.newBufferedReader(resource)) {
            String line = reader.readLine();

            while ((line = reader.readLine()) != null) {
                if (line.trim().startsWith("#") || line.trim().isEmpty()) {
                    continue;
                }

                Matcher matcher = pattern.matcher(line);

                if (!matcher.find()) {
                    continue;
                }
                String appID = matcher.group(1).toLowerCase();

                if (!matcher.find()) {
                    continue;
                }
                String appName = matcher.group(1);

                appIDsMap.put(appID, appName);
            }
        }

    }

}
