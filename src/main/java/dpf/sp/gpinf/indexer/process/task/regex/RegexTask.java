package dpf.sp.gpinf.indexer.process.task.regex;

import java.io.File;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import dk.brics.automaton.Automaton;
import dk.brics.automaton.AutomatonMatcher;
import dk.brics.automaton.BasicOperations;
import dk.brics.automaton.DatatypesAutomatonProvider;
import dk.brics.automaton.RegExp;
import dk.brics.automaton.RunAutomaton;
import dpf.sp.gpinf.indexer.Messages;
import dpf.sp.gpinf.indexer.analysis.FastASCIIFoldingFilter;
import dpf.sp.gpinf.indexer.process.Worker;
import dpf.sp.gpinf.indexer.process.task.AbstractTask;
import dpf.sp.gpinf.indexer.util.IPEDException;
import dpf.sp.gpinf.indexer.util.Util;
import gpinf.dev.data.EvidenceFile;

public class RegexTask extends AbstractTask{
    
    public static final String REGEX_PREFIX = "Regex:"; //$NON-NLS-1$
    
	private static final String REGEX_CONFIG = "RegexConfig.txt"; //$NON-NLS-1$
	
	private static final String KEYWORDS_CONFIG = "KeywordsToExport.txt"; //$NON-NLS-1$
	
	private static final String KEYWORDS_NAME = "KEYWORDS"; //$NON-NLS-1$
	
	private static final String ENABLE_PARAM = "enableRegexSearch"; //$NON-NLS-1$
	
	private static List<Regex> regexList;
	
	private static Regex regexFull;
	
	private static volatile boolean extractByKeywords = false;
	
	private boolean enabled = true;
	
	public static boolean isExtractByKeywordsOn(){
	    return extractByKeywords;
	}
	
	class Regex{
		
		String name;
		int prefix, sufix;
		Automaton automaton;
		RunAutomaton pattern;
		
		public Regex(String name, int prefix, int sufix, boolean ignoreCases, boolean ignoreDiacritics, String regex){
			this(name, new RegExp(regex).toAutomaton(new DatatypesAutomatonProvider()), ignoreCases, ignoreDiacritics);
			this.prefix = prefix;
            this.sufix = sufix;
		}
		
		public Regex(String name, Automaton automaton){
		    this(name, automaton, false, false);
		}
		
		public Regex(String name, Automaton aut, boolean ignoreCases, boolean ignoreDiacritics){
			if(ignoreCases)
			    aut = ignoreCases(aut);
			if(ignoreDiacritics)
                aut = ignoreDiacritics(aut);
			this.name = name;
			this.automaton = aut;
			this.pattern = new RunAutomaton(aut);
		}
	}
	
	private static Automaton ignoreCases(Automaton a) {
		Map<Character,Set<Character>> map = new HashMap<Character,Set<Character>>();
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
        Map<Character,Set<Character>> map = new HashMap<Character,Set<Character>>();
        for (char c1 = 'a'; c1 <= 'ÿ'; c1++) {
            Set<Character> ws = new HashSet<Character>();
            char[] input = {c1};
            char[] output = new char[1];
            FastASCIIFoldingFilter.foldToASCII(input, 0, output, 0, input.length);
            char c2 = output[0];
            ws.add(c1);
            ws.add(c2);
            map.put(c1, ws);
            map.put(c2, ws);
        }
        return a.subst(map);
    }
	
	@Override
	public boolean isEnabled() {
	    return enabled;
	}

	@Override
	public void init(Properties confParams, File confDir) throws Exception {
	    
	    String value = confParams.getProperty(ENABLE_PARAM);
	    if(value != null && !value.trim().isEmpty())
	        enabled = Boolean.valueOf(value.trim());
	    
		if(enabled && regexList == null){
			regexList = new ArrayList<Regex>();
			
			File confFile = new File(confDir, REGEX_CONFIG);
            String content = Util.readUTF8Content(confFile);
            for(String line : content.split("\n")){ //$NON-NLS-1$
	              line = line.trim();
	              if (line.startsWith("#") || line.isEmpty()) //$NON-NLS-1$
	                continue;
	              else{
	                  String[] values = line.split("=", 2); //$NON-NLS-1$
	                  if(values.length < 2)
	                      throw new IPEDException(Messages.getString("RegexTask.SeparatorNotFound.1") + REGEX_CONFIG + Messages.getString("RegexTask.SeparatorNotFound.2") + line); //$NON-NLS-1$ //$NON-NLS-2$
	                  String name = values[0].trim();
	                  String[] params = name.split(","); //$NON-NLS-1$
	                  String regexName = params[0].trim();
	                  int prefix = params.length > 1 ? Integer.valueOf(params[1].trim()) : 0;
	                  int sufix = params.length > 2 ? Integer.valueOf(params[2].trim()) : 0;
	                  boolean ignoreCase = params.length > 3 ? Boolean.valueOf(params[3].trim()) : true;
	                  String regex = replace(values[1].trim());
	                  regexList.add(new Regex(regexName, prefix, sufix, ignoreCase, false, regex));
	              }
			}
			
			confFile = new File(confDir, KEYWORDS_CONFIG);
			content = Util.readUTF8Content(confFile);
            for(String line : content.split("\n")){ //$NON-NLS-1$
                line = line.trim();
                if (line.startsWith("#") || line.isEmpty()) //$NON-NLS-1$
                  continue;
                else{
                  String regex = replace(line);
                  regexList.add(new Regex(KEYWORDS_NAME, 0, 0, true, true, regex));
                  extractByKeywords = true;
                  caseData.setContainsReport(true);
                }
            }
		    
			ArrayList<Automaton> automatonList = new ArrayList<Automaton>();
			for(Regex regex : regexList)
				automatonList.add(regex.automaton);
			Automaton automata = BasicOperations.union(automatonList);
			regexFull = new Regex("FULL", automata); //$NON-NLS-1$
		}
		
	}
	
	private static final String replace(String s){
	    return s.replace("\\t", "\t") //$NON-NLS-1$ //$NON-NLS-2$
	            .replace("\\r", "\r") //$NON-NLS-1$ //$NON-NLS-2$
	            .replace("\\n", "\n") //$NON-NLS-1$ //$NON-NLS-2$
	            .replace("\\f", "\f") //$NON-NLS-1$ //$NON-NLS-2$
	            .replace("\\s", "[ \t\r\n\f]"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Override
	public void finish() throws Exception {
		// TODO Auto-generated method stub
	}

	@Override
	protected void process(EvidenceFile evidence) throws Exception {
		
	    if(!enabled || evidence.getTextCache() == null)
			return;
		
		List<List<Object>> hitList = new ArrayList<List<Object>>();
		List<List<String>> fragList = new ArrayList<List<String>>();
		for(int i = 0; i < regexList.size(); i++){
			hitList.add(new ArrayList<Object>());
			fragList.add(new ArrayList<String>());
		}
		
		char[] cbuf= new char[1000000];
        int k = 0;
        try(Reader reader = evidence.getTextReader()){
            while(k != -1) {
                int off = 0; k = 0;
                while(k != -1 && (off += k) < cbuf.length)
                    k = reader.read(cbuf, off, cbuf.length - off);
                
                String text = new String(cbuf, 0, off);
                
                AutomatonMatcher fullMatcher = regexFull.pattern.newMatcher(text);
                while(fullMatcher.find()){
                    int start = fullMatcher.start();
                    int end = fullMatcher.end();
                    String hit = text.substring(start, end);
                    int i = 0;
                    for(Regex regex : regexList){
                        if(regex.pattern.run(hit)){
                            hit = hit.substring(regex.prefix, hit.length() - regex.sufix);
                            if(!RegexValidation.checkVerificationCode(regex, hit))
                                continue;
                            List<Object> hits = hitList.get(i);
                            hits.add(hit);
                            /*List<String> frags = fragList.get(i);
                            String frag = text.substring(Math.max(0, start - 50), Math.min(text.length(), end + 50));
                            if(frags.size() > 0) frag = "(...) " + frag;
                            frags.add(frag);
                            */
                        }
                        i++;
                    }
                }
                for(int i = 0; i < regexList.size(); i++){
                    if(hitList.get(i).size() > 0){
                        evidence.setExtraAttribute(REGEX_PREFIX + regexList.get(i).name, hitList.get(i));
                        //evidence.setExtraAttribute(REGEX_PREFIX + regexList.get(i).name + "_Frag", fragList.get(i));
                        if(regexList.get(i).name.equals(KEYWORDS_NAME))
                            evidence.setToExtract(true);
                    }
                }
            }
        }
	}

}
