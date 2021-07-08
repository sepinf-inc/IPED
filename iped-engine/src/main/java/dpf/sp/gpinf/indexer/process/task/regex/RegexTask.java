package dpf.sp.gpinf.indexer.process.task.regex;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Reader;
import java.io.Serializable;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dk.brics.automaton.Automaton;
import dk.brics.automaton.AutomatonMatcher;
import dk.brics.automaton.BasicOperations;
import dk.brics.automaton.DatatypesAutomatonProvider;
import dk.brics.automaton.RegExp;
import dk.brics.automaton.RunAutomaton;
import dpf.sp.gpinf.indexer.analysis.FastASCIIFoldingFilter;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.config.ExportByKeywordsConfig;
import dpf.sp.gpinf.indexer.config.RegexTaskConfig;
import dpf.sp.gpinf.indexer.config.RegexTaskConfig.RegexEntry;
import dpf.sp.gpinf.indexer.process.task.AbstractTask;
import gpinf.dev.data.Item;
import iped3.IItem;
import macee.core.Configurable;

public class RegexTask extends AbstractTask {

    public static final String REGEX_PREFIX = "Regex:"; //$NON-NLS-1$

    private static final String KEYWORDS_NAME = "KEYWORDS"; //$NON-NLS-1$

    private static final int MAX_RESULTS = 50000; // OOME protection for files with tons of hits

    private static Logger logger = LoggerFactory.getLogger(RegexTask.class);

    private static final File cacheFile = new File(System.getProperty("user.home"), ".indexador/regexAutomata.cache");

    private static List<Regex> regexList;

    private static Regex regexFull;

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
        logger.info("Loaded {} regexes from configuration.", regexConfig.getRegexList().size());

        ExportByKeywordsConfig exportConfig = configurationManager.findObject(ExportByKeywordsConfig.class);

        if (regexConfig.isEnabled() && regexList == null) {

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

            initValidators(new File(output, "conf"));
        }

    }

    private void writeCache(RegexTaskConfig regexConfig, ExportByKeywordsConfig exportConfig) throws IOException {
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(cacheFile));
                ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            byte[] md5 = getMd5FromConfigs(regexConfig, exportConfig);
            oos.write(md5);
            oos.writeObject(regexList);
            oos.writeObject(regexFull);
        }
    }

    private boolean loadCache(RegexTaskConfig regexConfig, ExportByKeywordsConfig exportConfig)
            throws IOException, ClassNotFoundException {
        if (!cacheFile.exists()) {
            return false;
        }
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(cacheFile));
                ObjectInputStream ois = new ObjectInputStream(bis)) {
            byte[] md5 = getMd5FromConfigs(regexConfig, exportConfig);
            byte[] cacheMd5 = new byte[16];
            ois.readFully(cacheMd5);
            if (!new String(md5).equals(new String(cacheMd5))) {
                return false;
            }
            regexList = (List<Regex>) ois.readObject();
            regexFull = (Regex) ois.readObject();
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
                String hit = text.substring(start, end);
                int i = 0;
                for (Regex regex : regexList) {
                    if (regex.pattern.run(hit)) {
                        hit = hit.substring(regex.prefix, hit.length() - regex.sufix);
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
