package iped.bfac.config;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

/**
 * Configuration manager for BFAC client.
 * Uses the same configuration folder as bfac_cli_frontend (~/.bfac/)
 * to allow sharing credentials between both clients.
 */
public class BfacConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String BFAC_FOLDER_NAME = ".bfac";
    private static final String CREDENTIALS_FILE = "credentials.json";
    private static final String CONFIG_FILE = "config.json";

    private static BfacConfig instance;

    private final Path bfacFolder;
    private JsonObject credentials;
    private JsonObject config;

    private BfacConfig() {
        String userHome = System.getProperty("user.home");
        this.bfacFolder = Paths.get(userHome, BFAC_FOLDER_NAME);
        ensureFolderExists();
    }

    public static synchronized BfacConfig getInstance() {
        if (instance == null) {
            instance = new BfacConfig();
        }
        return instance;
    }

    private void ensureFolderExists() {
        try {
            Files.createDirectories(bfacFolder);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create BFAC config folder: " + bfacFolder, e);
        }
    }

    /**
     * Gets the credentials from the credentials.json file.
     * @return JsonObject with credentials or empty object if file doesn't exist
     */
    public JsonObject getCredentials() {
        if (credentials == null) {
            credentials = loadJsonFile(CREDENTIALS_FILE);
        }
        return credentials;
    }

    /**
     * Saves credentials to the credentials.json file.
     * @param credentials JsonObject containing access_token and token_type
     */
    public void saveCredentials(JsonObject credentials) {
        this.credentials = credentials;
        saveJsonFile(CREDENTIALS_FILE, credentials);
    }

    /**
     * Gets the access token from stored credentials.
     * @return The access token or null if not available
     */
    public String getAccessToken() {
        JsonObject creds = getCredentials();
        if (creds.has("access_token")) {
            return creds.get("access_token").getAsString();
        }
        return null;
    }

    /**
     * Checks if there are valid stored credentials.
     * @return true if access token is available
     */
    public boolean hasStoredCredentials() {
        String token = getAccessToken();
        return token != null && !token.isEmpty();
    }

    /**
     * Clears stored credentials (logout).
     */
    public void clearCredentials() {
        this.credentials = new JsonObject();
        saveJsonFile(CREDENTIALS_FILE, this.credentials);
    }

    /**
     * Gets the configuration from the config.json file.
     * @return JsonObject with configuration or empty object if file doesn't exist
     */
    public JsonObject getConfig() {
        if (config == null) {
            config = loadJsonFile(CONFIG_FILE);
        }
        return config;
    }

    /**
     * Saves configuration to the config.json file.
     * @param config JsonObject containing configuration
     */
    public void saveConfig(JsonObject config) {
        this.config = config;
        saveJsonFile(CONFIG_FILE, config);
    }

    /**
     * Gets the stored username from configuration.
     * @return The username or null if not stored
     */
    public String getStoredUsername() {
        JsonObject cfg = getConfig();
        if (cfg.has("username")) {
            return cfg.get("username").getAsString();
        }
        return null;
    }

    /**
     * Saves the username to configuration.
     * @param username The username to store
     */
    public void saveUsername(String username) {
        JsonObject cfg = getConfig();
        cfg.addProperty("username", username);
        saveConfig(cfg);
    }

    private JsonObject loadJsonFile(String filename) {
        File file = bfacFolder.resolve(filename).toFile();
        if (!file.exists() || file.length() == 0) {
            return new JsonObject();
        }
        try (FileReader reader = new FileReader(file)) {
            JsonObject obj = GSON.fromJson(reader, JsonObject.class);
            return obj != null ? obj : new JsonObject();
        } catch (IOException e) {
            return new JsonObject();
        }
    }

    private void saveJsonFile(String filename, JsonObject data) {
        ensureFolderExists();
        File file = bfacFolder.resolve(filename).toFile();
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(data, writer);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save " + filename, e);
        }
    }

    /**
     * Gets the path to the BFAC configuration folder.
     * @return Path to ~/.bfac/
     */
    public Path getBfacFolder() {
        return bfacFolder;
    }
}
