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

import iped.engine.task.regex.BasicAbstractRegexValidatorService;

/**
 * Validator for BIP-39 Portuguese seed phrases.
 *
 * @author Rui Sant'Ana Junior
 */
public class CryptoPTSeedPhraseValidatorService extends BasicAbstractRegexValidatorService {

    private static final String REGEX_NAME = "CRYPTO_POSSIBLE_SEED_PHRASE_PT";
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

            while ((line = reader.readLine()) != null) {
                if (line.startsWith(REGEX_NAME)) {
                    fullRegexLine.append(line.substring(line.indexOf('=') + 1).trim());
                    break;
                }
            }

            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty() || line.contains("=")) {
                    break;
                }
                fullRegexLine.append(line.trim());
            }

            if (fullRegexLine.length() > 0) {
                String combinedLine = fullRegexLine.toString();
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
    public String format(String hit) {
        return hit;
    }

    @Override
    protected boolean validate(String hit) {
        if (wordList == null || wordList.isEmpty()) {
            return false;
        }
        String[] words = hit.trim().toLowerCase().split("\\s+");
        return validateBIP39(words);
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
}
