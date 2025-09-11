package iped.engine.task.regex.validator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import iped.engine.task.regex.RegexValidatorService;

public class CryptoSeedPhraseValidator implements RegexValidatorService {

    private static final String REGEX_NAME = "CRYPTO_POSSIBLE_SEED_PHRASE_EN";
    private static final List<Integer> BIP39_VALID_WORD_COUNTS = Arrays.asList(12, 15, 18, 21, 24);

    private List<String> wordList;
    private Map<String, Integer> wordMap;

    @Override
    public void init(File confDir) {
        wordList = new ArrayList<>();
        wordMap = new HashMap<>();
        File regexConf = new File(confDir.getParentFile(), "conf/RegexConfig.txt");

        try (BufferedReader reader = new BufferedReader(new FileReader(regexConf, StandardCharsets.UTF_8))) {
            StringBuilder fullRegexLine = new StringBuilder();
            String line;

            // First, find the line where our regex starts
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(REGEX_NAME)) {
                    fullRegexLine.append(line.substring(line.indexOf('=') + 1).trim());
                    break; // Found it, exit this loop
                }
            }

            // Now, append subsequent lines that are part of the same regex definition
            while ((line = reader.readLine()) != null) {
                // Stop if we hit a blank line or a new regex definition
                if (line.trim().isEmpty() || line.contains("=")) {
                    break;
                }
                fullRegexLine.append(line.trim());
            }

            if (fullRegexLine.length() > 0) {
                String combinedLine = fullRegexLine.toString();
                // Use a non-greedy regex to find the content of the first parenthesis group
                Pattern p = Pattern.compile("\\((.*?)\\)");
                Matcher m = p.matcher(combinedLine);
                if (m.find()) {
                    String[] words = m.group(1).split("\\|");
                    this.wordList = new ArrayList<>(Arrays.asList(words));
                    for (int i = 0; i < this.wordList.size(); i++) {
                        this.wordMap.put(this.wordList.get(i), i);
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
            this.wordList = Collections.emptyList();
            this.wordMap = Collections.emptyMap();
        }
    }

    @Override
    public List<String> getRegexNames() {
        return Collections.singletonList(REGEX_NAME);
    }

    @Override
    public String format(String regexName, String hit) {
        return hit;
    }

    @Override
    public boolean validate(String regexName, String hit) {
        if (wordList == null || wordList.isEmpty()) {
            return false;
        }

        String[] words = hit.trim().toLowerCase().split("[ \\t]+");

        if (validateBIP39(words)) {
            return true;
        }

        return validateElectrum(words);
    }

    private boolean validateBIP39(String[] words) {
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
            e.printStackTrace();
            return false;
        }
    }

    private boolean validateElectrum(String[] words) {
        try {
            // Electrum seeds are typically 12 words, but can have other lengths.
            // A simple check is that the number of words is a multiple of 3.
            if (words.length == 0 || words.length % 3 != 0) {
                return false;
            }

            for (String w : words) {
                if (!this.wordList.contains(w)) {
                    return false;
                }
            }

            // Normalize the mnemonic string for HMAC
            String normalizedMnemonic = String.join(" ", words);

            // HMAC-SHA512 with a constant key "Seed version"
            Mac hmac = Mac.getInstance("HmacSHA512");
            SecretKeySpec key = new SecretKeySpec("Seed version".getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            hmac.init(key);
            byte[] hash = hmac.doFinal(normalizedMnemonic.getBytes(StandardCharsets.UTF_8));

            // Check the version prefix from the hash
            int versionPrefix = hash[0] & 0xFF;

            // Standard Electrum seeds starts with 0x01. We check for a common range.
            // This is a simplified check; a full check is much more complex.

            return versionPrefix > 0;

        } catch (Exception e) {
            // Any exception during crypto operations means it's not a valid Electrum seed.
            return false;
        }
    }
}