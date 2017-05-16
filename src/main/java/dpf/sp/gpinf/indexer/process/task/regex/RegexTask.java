package dpf.sp.gpinf.indexer.process.task.regex;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
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

import dpf.sp.gpinf.indexer.process.Worker;
import dpf.sp.gpinf.indexer.process.task.AbstractTask;
import dpf.sp.gpinf.indexer.util.IPEDException;
import gpinf.dev.data.EvidenceFile;

public class RegexTask extends AbstractTask{
    
    public static final String REGEX_PREFIX = "Regex_";
    
	private static final String REGEX_CONFIG = "RegexConfig.txt";
	
	private static List<Regex> regexList;
	
	private static Regex regexFull;
	
	class Regex{
		
		String name;
		int prefix, sufix;
		Automaton automaton;
		RunAutomaton pattern;
		
		public Regex(String name, int prefix, int sufix, String regex){
			this(name, new RegExp(regex).toAutomaton(new DatatypesAutomatonProvider()));
			this.prefix = prefix;
            this.sufix = sufix;
		}
		
		public Regex(String name, Automaton automaton){
			this.name = name;
			this.automaton = ignoreCases(automaton);
			this.pattern = new RunAutomaton(automaton);
		}
	}
	
	public RegexTask(Worker worker){
		super(worker);
	}
	
	private static Automaton ignoreCases(Automaton a) {
		Map<Character,Set<Character>> map = new HashMap<Character,Set<Character>>();
		for (char c1 = 'a'; c1 <= 'z'; c1++) {
			Set<Character> ws = new HashSet<Character>();
			char c2 = Character.toUpperCase(c1);
			//System.out.println(c1 + " " + c2);
			ws.add(c1);
			ws.add(c2);
			map.put(c1, ws);
			map.put(c2, ws);
		}
		return a.subst(map);
	}

	@Override
	public void init(Properties confParams, File confDir) throws Exception {
		if(regexList == null){
			regexList = new ArrayList<Regex>();
			
			File confFile = new File(confDir, REGEX_CONFIG);
			try(BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(confFile), "UTF-8"))){
			    //ignore first line with utf-8 header and comment
			    String line = reader.readLine();
	            while ((line = reader.readLine()) != null) {
	              if (line.trim().startsWith("#") || line.trim().isEmpty())
	                continue;
	              else{
	                  String[] values = line.split("=", 2);
	                  if(values.length < 2)
	                      throw new IPEDException("Caracter '=' não encontrado em " + REGEX_CONFIG + " linha: " + line);
	                  String name = values[0].trim();
	                  String[] params = name.split(",");
	                  String regexName = params[0].trim();
	                  int prefix = params.length > 1 ? Integer.valueOf(params[1].trim()) : 0;
	                  int sufix = params.length > 2 ? Integer.valueOf(params[2].trim()) : 0;
	                  String regex = replace(values[1].trim());
	                  regexList.add(new Regex(regexName, prefix, sufix, regex));
	              }
	            }
			}
		    
			ArrayList<Automaton> automatonList = new ArrayList<Automaton>();
			for(Regex regex : regexList)
				automatonList.add(regex.automaton);
			Automaton automata = BasicOperations.union(automatonList);
			regexFull = new Regex("FULL", automata);
		}
		
	}
	
	private static final String replace(String s){
	    return s.replace("\\t", "\t")
	            .replace("\\r", "\r")
	            .replace("\\n", "\n")
	            .replace("\\f", "\f")
	            .replace("\\s", "( |\t|\r|\n|\f)");
	}

	@Override
	public void finish() throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void process(EvidenceFile evidence) throws Exception {
		
		if(evidence.getParsedTextCache() == null)
			return;
		String text = evidence.getParsedTextCache();
		
		List<List<Object>> hitList = new ArrayList<List<Object>>();
		List<List<String>> fragList = new ArrayList<List<String>>();
		for(int i = 0; i < regexList.size(); i++){
			hitList.add(new ArrayList<Object>());
			fragList.add(new ArrayList<String>());
		}
		
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
			}
		}
	}

}
