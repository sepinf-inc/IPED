package iped.engine.task.regex.validator;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.engine.config.ConfigurationManager; // Import ConfigurationManager
import iped.engine.config.RegexTaskConfig; // Import RegexTaskConfig
import iped.engine.config.RegexTaskConfig.RegexEntry; // Import RegexEntry
import iped.engine.task.regex.RegexValidatorService;

/**
 * Dynamically validates BIP-39 and Electrum seed phrases for any language
 * defined in the configuration.
 *
 * @author Rui Sant'Ana Junior
 */
public class CryptoSeedPhraseValidatorService implements RegexValidatorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CryptoSeedPhraseValidatorService.class);
    private static final String REGEX_PREFIX = "CRYPTO_POSSIBLE_SEED_PHRASE_";
    private static final List<Integer> BIP39_VALID_WORD_COUNTS = Arrays.asList(12, 15, 18, 21, 24);

    private final List<String> discoveredRegexNames = new ArrayList<>();
    private final Map<String, Map<String, Integer>> wordMaps = new HashMap<>();

    @Override
    public void init(File confDir) {
        // Obtain RegexTaskConfig from ConfigurationManager
        ConfigurationManager configurationManager = ConfigurationManager.get();
        RegexTaskConfig regexTaskConfig = configurationManager.findObject(RegexTaskConfig.class);

        if (regexTaskConfig == null) {
            LOGGER.error("RegexTaskConfig not found in ConfigurationManager. CryptoSeedPhraseValidatorService cannot initialize.");
            return;
        }

        // Iterate through the RegexEntry objects provided by RegexTaskConfig
        for (RegexEntry entry : regexTaskConfig.getRegexList()) {
            String regexName = entry.getRegexName();
            if (regexName.startsWith(REGEX_PREFIX)) {
                discoveredRegexNames.add(regexName);

                String regexPatternString = entry.getRegex(); // Get the regex string from RegexEntry

                if (regexPatternString != null && !regexPatternString.isEmpty()) {
                    Map<String, Integer> currentWordMap = new HashMap<>();
                    Pattern p = Pattern.compile("\\((.*?)\\)");
                    Matcher m = p.matcher(regexPatternString); // Match against the regex string
                    if (m.find()) {
                        String[] words = m.group(1).split("\\|");
                        for (int k = 0; k < words.length; k++) {
                            currentWordMap.put(words[k], k);
                        }
                    }
                    wordMaps.put(regexName, currentWordMap);
                } else {
                    LOGGER.warn("Regex pattern string is empty or null for regexName: {}", regexName);
                }
            }
        }
        if (discoveredRegexNames.isEmpty()) {
            LOGGER.warn("No crypto seed phrase regexes found in RegexConfig.txt with prefix: {}", REGEX_PREFIX);
        }
    }

    @Override
    public List<String> getRegexNames() {
        return discoveredRegexNames;
    }

    @Override
    public String format(String regexName, String hit) {
        return hit;
    }

    @Override
    public boolean validate(String regexName, String hit) {
        Map<String, Integer> currentWordMap = wordMaps.get(regexName);
        if (currentWordMap == null || currentWordMap.isEmpty()) {
            return false;
        }

        String[] words = hit.trim().toLowerCase().split("\\s+");

        if (validateBIP39(words, currentWordMap)) {
            return true;
        }

        if (regexName.endsWith("_EN")) {
            return validateElectrum(words);
        }

        return false;
    }

    private boolean validateBIP39(String[] words, Map<String, Integer> wordMap) {
        if (!BIP39_VALID_WORD_COUNTS.contains(words.length)) {
            return false;
        }

        StringBuilder bits = new StringBuilder();
        for (String word : words) {
            Integer index = wordMap.get(word);
            if (index == null) {
                return false;
            }
            bits.append(String.format("%11s", Integer.toBinaryString(index)).replace(' ', '0'));
        }

        String bitsStr = bits.toString();
        int ent = (bitsStr.length() * 32) / 33;
        int cs = bitsStr.length() - ent;

        String entropyBits = bitsStr.substring(0, ent);
        String checksumBits = bitsStr.substring(ent);

        byte[] entropy;
        try {
            int len = entropyBits.length() / 8;
            entropy = new byte[len];
            for (int i = 0; i < len; i++) {
                String byteString = entropyBits.substring(i * 8, (i + 1) * 8);
                entropy[i] = (byte) Integer.parseInt(byteString, 2);
            }
        } catch (NumberFormatException e) {
            return false;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(entropy);

            StringBuilder hashBits = new StringBuilder();
            for (byte b : hash) {
                hashBits.append(String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0'));
            }

            String expectedChecksum = hashBits.substring(0, cs);
            return checksumBits.equals(expectedChecksum);
        } catch (NoSuchAlgorithmException e) {
            LOGGER.error("SHA-256 algorithm not found", e);
            return false;
        }
    }

    private boolean validateElectrum(String[] words) {
        try {
            if (words.length == 0 || words.length % 3 != 0) {
                return false;
            }

            String normalizedMnemonic = String.join(" ", words);

            Mac hmac = Mac.getInstance("HmacSHA512");
            SecretKeySpec key = new SecretKeySpec("Seed version".getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            hmac.init(key);
            byte[] hash = hmac.doFinal(normalizedMnemonic.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b & 0xFF));
            }
            String hexHash = sb.toString();

            int length = Character.digit(hexHash.charAt(0), 16) + 2;
            if (hexHash.length() < length)
                return false;

            String prefixHex = hexHash.substring(0, length);
            int version = Integer.parseInt(prefixHex, 16);

            return version == 0x01 || version == 0x100 || version == 0x101 || version == 0x102 || version == 0x201;

        } catch (NoSuchAlgorithmException e) {
            LOGGER.error("HmacSHA512 algorithm not found", e);
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
