package iped.engine.task.regex;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Reader;
import java.io.Serializable;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.codec.digest.DigestUtils;
import org.nustaq.serialization.FSTConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dk.brics.automaton.Automaton;
import dk.brics.automaton.AutomatonMatcher;
import dk.brics.automaton.BasicOperations;
import dk.brics.automaton.DatatypesAutomatonProvider;
import dk.brics.automaton.RegExp;
import dk.brics.automaton.RunAutomaton;
import iped.configuration.Configurable;
import iped.data.IItem;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.ExportByKeywordsConfig;
import iped.engine.config.RegexTaskConfig;
import iped.engine.config.RegexTaskConfig.RegexEntry;
import iped.engine.data.Item;
import iped.engine.hashdb.HashDBDataSource;
import iped.engine.lucene.analysis.FastASCIIFoldingFilter;
import iped.engine.task.AbstractTask;
import iped.engine.task.HashDBLookupTask;
import iped.engine.task.HashTask;
import iped.engine.task.PhotoDNALookup;
import iped.engine.task.index.IndexItem;
import iped.properties.ExtraProperties;

public class RegexTask extends AbstractTask {

    public static final String REGEX_PREFIX = "Regex:"; //$NON-NLS-1$

    private static final String KEYWORDS_NAME = "KEYWORDS"; //$NON-NLS-1$

    private static final int MAX_RESULTS = 50000; // OOME protection for files with tons of hits

    private static Logger logger = LoggerFactory.getLogger(RegexTask.class);

    private static final File cacheFile = new File(System.getProperty("user.home"), ".iped/regexAutomata.cache");

    private static List<Regex> regexList;

    private static Regex regexFull;

    private static FSTConfiguration fastSerializer = FSTConfiguration.createDefaultConfiguration();

    private static final Set<String> ignoredKeys = new HashSet<String>();
    static {
        // Ignore these keys when reading item's properties to be searched (issue #1988)
        ignoredKeys.add(HashTask.HASH.EDONKEY.toString());
        ignoredKeys.add(HashTask.HASH.MD5.toString());
        ignoredKeys.add(HashTask.HASH.SHA1.toString());
        ignoredKeys.add(HashTask.HASH.SHA256.toString());
        ignoredKeys.add(HashTask.HASH.SHA512.toString());
        ignoredKeys.add(IndexItem.TRACK_ID);
        ignoredKeys.add(IndexItem.PARENT_TRACK_ID);
        ignoredKeys.add(IndexItem.CONTAINER_TRACK_ID);
        ignoredKeys.add(IndexItem.EVIDENCE_UUID);
        ignoredKeys.add(ExtraProperties.GLOBAL_ID);
        ignoredKeys.add(HashDBLookupTask.ATTRIBUTES_PREFIX + HashDBDataSource.ledMd5_512);
        ignoredKeys.add(HashDBLookupTask.ATTRIBUTES_PREFIX + HashDBDataSource.ledMd5_64k);
        ignoredKeys.add(HashDBLookupTask.ATTRIBUTES_PREFIX + HashDBDataSource.photoDna);
        ignoredKeys.add(PhotoDNALookup.PHOTO_DNA_HIT_PREFIX + HashTask.HASH.MD5.name());
        ignoredKeys.add(PhotoDNALookup.PHOTO_DNA_NEAREAST_HASH);
    }

    private char[] cbuf = new char[1 << 20];

    private static RegexValidator regexValidator;

    private RegexTaskConfig regexConfig;

    static class Regex implements Serializable {

        /**
         * 
         */
        private static final long serialVersionUID = 1L;

        String name;
        int prefix, sufix;
        Automaton automaton;
        RunAutomaton pattern;
        boolean ignoreCases;

        public Regex(String name, int prefix, int sufix, boolean ignoreCases, boolean ignoreDiacritics, String regex) {
            this(name, new RegExp(regex).toAutomaton(new DatatypesAutomatonProvider()), ignoreCases, ignoreDiacritics);
            this.prefix = prefix;
            this.sufix = sufix;
        }

        public Regex(String name, Automaton automaton) {
            this(name, automaton, false, false);
        }

        public Regex(String name, Automaton aut, boolean ignoreCases, boolean ignoreDiacritics) {
            if (ignoreCases) {
                aut = ignoreCases(aut);
            }
            if (ignoreDiacritics) {
                aut = ignoreDiacritics(aut);
            }
            this.ignoreCases = ignoreCases;
            this.name = name;
            this.automaton = aut;
            this.pattern = new RunAutomaton(aut);
        }
    }

    private static Automaton ignoreCases(Automaton a) {
        Map<Character, Set<Character>> map = new HashMap<Character, Set<Character>>();
        for (char c1 = 'a'; c1 <= 'ÿ'; c1++) {
            Set<Character> ws = new HashSet<Character>();
            char c2 = Character.toUpperCase(c1);
            ws.add(c1);
            ws.add(c2);
            map.put(c1, ws);
            map.put(c2, ws);
        }
        return a.subst(map);
    }

    private static Automaton ignoreDiacritics(Automaton a) {
        Map<Character, Set<Character>> map = new HashMap<Character, Set<Character>>();
        for (char c1 = 'a'; c1 <= 'ÿ'; c1++) {
            char[] input = { c1 };
            char[] output = new char[4];
            FastASCIIFoldingFilter.foldToASCII(input, 0, output, 0, input.length);
            char c2 = output[0];
            Set<Character> ws = map.get(c2);
            if (ws == null) {
                ws = new HashSet<Character>();
            }
            ws.add(c1);
            ws.add(c2);
            map.put(c1, ws);
            map.put(c2, ws);
        }
        return a.subst(map);
    }

    @Override
    public boolean isEnabled() {
        return regexConfig.isEnabled();
    }

    public List<Configurable<?>> getConfigurables() {
        return Arrays.asList(new RegexTaskConfig());
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {

        regexConfig = configurationManager.findObject(RegexTaskConfig.class);
        ExportByKeywordsConfig exportConfig = configurationManager.findObject(ExportByKeywordsConfig.class);

        if (regexConfig.isEnabled() && regexList == null) {

            logger.info("Loaded {} regexes from configuration.", regexConfig.getRegexList().size());

            if (loadCache(regexConfig, exportConfig)) {
                logger.info("Regex cache loaded from {}", cacheFile.getAbsolutePath());
            } else {
                regexList = new ArrayList<Regex>();
                for (RegexEntry e : regexConfig.getRegexList()) {
                    regexList.add(new Regex(e.getRegexName(), e.getPrefix(), e.getSuffix(), e.isIgnoreCase(), false,
                            e.getRegex()));
                }
                int num = regexList.size();
                logger.info("Created {} automata for each regex configured.", num);

                if (exportConfig.isEnabled()) {
                    for (String keyword : exportConfig.getKeywords()) {
                        String regex = RegexTaskConfig.replace(keyword);
                        regexList.add(new Regex(KEYWORDS_NAME, 0, 0, true, true, regex));
                    }
                }
                logger.info("Created {} automata for each keyword to export configured.", regexList.size() - num);

                ArrayList<Automaton> automatonList = new ArrayList<Automaton>();
                for (Regex regex : regexList) {
                    automatonList.add(regex.automaton);
                }
                Automaton automata = BasicOperations.union(automatonList);
                regexFull = new Regex("FULL", automata); //$NON-NLS-1$
                logger.info("Created the unique automaton for all regexes.");

                writeCache(regexConfig, exportConfig);
                logger.info("Regex cache saved to {}", cacheFile.getAbsolutePath());
            }

            initValidators(new File(output, "scripts"));
        }

    }

    private void writeCache(RegexTaskConfig regexConfig, ExportByKeywordsConfig exportConfig) throws IOException {
        cacheFile.getParentFile().mkdirs();
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(cacheFile));
                DataOutputStream dos = new DataOutputStream(bos)) {
            byte[] md5 = getMd5FromConfigs(regexConfig, exportConfig);
            byte[] list = fastSerializer.asByteArray(regexList);
            byte[] full = fastSerializer.asByteArray(regexFull);
            dos.write(md5);
            dos.writeInt(list.length);
            dos.write(list);
            dos.writeInt(full.length);
            dos.write(full);
        }
    }

    private boolean loadCache(RegexTaskConfig regexConfig, ExportByKeywordsConfig exportConfig)
            throws IOException, ClassNotFoundException {
        if (!cacheFile.exists()) {
            return false;
        }
        try (DataInputStream dis = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(cacheFile.toPath())))) {
            byte[] md5 = getMd5FromConfigs(regexConfig, exportConfig);
            byte[] cacheMd5 = new byte[16];
            dis.readFully(cacheMd5);
            if (!new String(md5).equals(new String(cacheMd5))) {
                return false;
            }
            int listLen = dis.readInt();
            byte[] list = new byte[listLen];
            dis.readFully(list);
            regexList = (List<Regex>) fastSerializer.asObject(list);
            int fullLen = dis.readInt();
            byte[] full = new byte[fullLen];
            dis.readFully(full);
            regexFull = (Regex) fastSerializer.asObject(full);
            return true;
        }
    }

    private byte[] getMd5FromConfigs(RegexTaskConfig regexConfig, ExportByKeywordsConfig exportConfig)
            throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(regexConfig);
            oos.writeObject(exportConfig);
        }
        return DigestUtils.md5(baos.toByteArray());
    }

    private synchronized void initValidators(File confDir) {
        if (regexValidator == null) {
            regexValidator = new RegexValidator();
            regexValidator.init(confDir);
        }
    }

    @Override
    public void finish() throws Exception {
        regexFull = null;
        regexList = null;
    }

    protected void process(IItem item) throws Exception {

        Item evidence = (Item) item;

        if (evidence.getTextCache() == null || !evidence.isToAddToCase())
            return;

        try (Reader reader = evidence.getTextReader()) {
            processRegex(evidence, reader);
        }

        processRegex(evidence, new StringReader(evidence.getName()));
        
        processRegex(evidence, getExtraAttributeReader(evidence));
    }

    private Reader getExtraAttributeReader(IItem item) {
        StringBuilder sb = new StringBuilder();
        for (String key : item.getExtraAttributeMap().keySet().toArray(new String[0])) {
            if (!key.startsWith(REGEX_PREFIX) && !ignoredKeys.contains(key)) {
                Object val = item.getExtraAttribute(key);
                sb.append(key).append(": ").append(val.toString()).append('\n');
            }
        }
        return new StringReader(sb.toString());
    }

    @SuppressWarnings("unchecked")
    private void processRegex(IItem evidence, Reader reader) throws IOException {

        int k = 0;
        long totalOffset = 0;
        while (k != -1) {
            int off = 0;
            k = 0;
            while (k != -1 && (off += k) < cbuf.length)
                k = reader.read(cbuf, off, cbuf.length - off);

            String text = new String(cbuf, 0, off);

            List<Map<String, RegexHits>> hitList = new ArrayList<Map<String, RegexHits>>();
            for (int i = 0; i < regexList.size(); i++) {
                hitList.add(new HashMap<>());
            }

            AutomatonMatcher fullMatcher = regexFull.pattern.newMatcher(text);
            while (fullMatcher.find()) {
                int start = fullMatcher.start();
                int end = fullMatcher.end();
                String fullHit = text.substring(start, end);
                int i = 0;
                for (Regex regex : regexList) {
                    if (regex.pattern.run(fullHit)) {
                        String hit = fullHit.substring(regex.prefix, fullHit.length() - regex.sufix);
                        if (regex.ignoreCases)
                            hit = hit.toLowerCase();
                        if (regexValidator.validate(regex, hit)) {
                            if (regexConfig.isFormatRegexMatches()) {
                                hit = regexValidator.format(regex, hit);
                            }
                            Map<String, RegexHits> hitMap = hitList.get(i);
                            RegexHits hits = hitMap.get(hit);
                            if (hits == null) {
                                hits = new RegexHits(hit);
                                hitMap.put(hit, hits);
                            }
                            hits.addOffset(totalOffset + start + regex.prefix);
                        }
                    }
                    i++;
                }
            }
            for (int i = 0; i < regexList.size(); i++) {
                if (hitList.get(i).size() > 0) {
                    String key = REGEX_PREFIX + regexList.get(i).name;
                    Collection<RegexHits> prevHits = (Collection<RegexHits>) evidence.getExtraAttribute(key);
                    Map<String, RegexHits> hitsMap = hitList.get(i);
                    if (prevHits == null || prevHits.isEmpty() || !(prevHits.iterator().next() instanceof RegexHits)) {
                        evidence.setExtraAttribute(key, hitsMap.values());
                    } else {
                        if (prevHits.size() >= MAX_RESULTS) {
                            evidence.setExtraAttribute("maxHitsReached" + key, "true");
                        } else {
                            for (RegexHits hits : prevHits) {
                                RegexHits prev = hitsMap.get(hits.getHit());
                                if (prev != null) {
                                    prev.addAll(hits.getOffsets());
                                } else {
                                    hitsMap.put(hits.getHit(), hits);
                                }
                            }
                            evidence.setExtraAttribute(key, hitsMap.values());
                        }
                    }

                    if (regexList.get(i).name.equals(KEYWORDS_NAME))
                        evidence.setToExtract(true);
                }
            }
            totalOffset += off;
        }
    }

}
