package dpf.sp.gpinf.indexer.search;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.lang.ArrayUtils;

import dpf.sp.gpinf.indexer.util.Util;

public class MultiMarcadores implements Serializable {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	Map<Integer, Marcadores> map = new HashMap<Integer, Marcadores>();
	
	public MultiMarcadores(List<IPEDSource> sources){
		for(IPEDSource s : sources)
			map.put(s.getSourceId(), s.getMarcadores());
	}
	
	public Collection<Marcadores> getSingleBookmarks(){
		return map.values();
	}
	
	public int getTotalSelected(){
		int sum = 0;
		for(Marcadores m : map.values())
			sum += m.getTotalSelected();
		return sum;
	}
	
	public void clearSelected(){
		for(Marcadores m : map.values())
			m.clearSelected();
	}
	
	public void selectAll(){
		for(Marcadores m : map.values())
			m.selectAll();
	}
	
	public boolean isSelected(ItemId item){
		return map.get(item.getSourceId()).isSelected(item.getId());
	}
	
	public void setSelected(boolean value, ItemId item, IPEDSource ipedCase) {
		map.get(item.getSourceId()).setSelected(value, item.getId(), ipedCase);
	}

	public String getLabels(ItemId item) {
		return map.get(item.getSourceId()).getLabels(item.getId());
	}

	public final boolean hasLabel(ItemId item){
		return map.get(item.getSourceId()).hasLabel(item.getId());
	}
	
	private static final int[] getLabelIds(Marcadores m, Set<String> labelNames){
		int[] labelIds = new int[labelNames.size()];
	  	int i = 0;
	  	boolean hasLabel = false;
	  	for(String labelName : labelNames){
	  		labelIds[i] = m.getLabelId(labelName);
	  		if(labelIds[i++] != -1)
	  			hasLabel = true;
	  	}
	  	if(!hasLabel)
	  		return null;
	  	return labelIds;
	}
	
	public final boolean hasLabel(ItemId item, Set<String> labelNames){
		Marcadores m = map.get(item.getSourceId());
		int[] labelIds = getLabelIds(m, labelNames);
	  	return m.hasLabel(item.getId(), m.getLabelBits(labelIds));
	}
	
	public final boolean hasLabel(ItemId item, String labelName) {
		Marcadores m = map.get(item.getSourceId());
		int labelId = m.getLabelId(labelName);
		if(labelId == -1)
			return false;
		return m.hasLabel(item.getId(), labelId);
	}

	public void addLabel(ArrayList<ItemId> ids, String labelName) {
		HashMap<Integer, ArrayList<Integer>> itemsPerSource = getIdsPerSource(ids);
		for(Integer sourceId : itemsPerSource.keySet()){
			Marcadores m = map.get(sourceId);
			int labelId = m.getLabelId(labelName);
			if(labelId == -1)
				labelId = m.newLabel(labelName);
			m.addLabel(itemsPerSource.get(sourceId), labelId);
		}
	}
	
	private HashMap<Integer, ArrayList<Integer>> getIdsPerSource(ArrayList<ItemId> ids){
		HashMap<Integer, ArrayList<Integer>> itemsPerSource = new HashMap<Integer, ArrayList<Integer>>(); 
		for(ItemId item : ids){
			ArrayList<Integer> items = itemsPerSource.get(item.getSourceId());
			if(items == null){
				items = new ArrayList<Integer>();
				itemsPerSource.put(item.getSourceId(), items);
			}
			items.add(item.getId());
		}
		return itemsPerSource;
	}
	
	public void removeLabel(ArrayList<ItemId> ids, String labelName) {
		HashMap<Integer, ArrayList<Integer>> itemsPerSource = getIdsPerSource(ids);
		for(Integer sourceId : itemsPerSource.keySet()){
			Marcadores m = map.get(sourceId);
			int labelId = m.getLabelId(labelName);
			if(labelId != -1)
				m.removeLabel(itemsPerSource.get(sourceId), labelId);
		}

	}

	public void newLabel(String labelName) {
		for(Marcadores m : map.values())
			m.newLabel(labelName);
	}

	public void delLabel(String labelName) {
		for(Marcadores m : map.values()){
			int labelId = m.getLabelId(labelName); 
			m.delLabel(labelId);
		}
	}
	
	public void changeLabel(String oldLabel, String newLabel){
		for(Marcadores m : map.values())
			m.changeLabel(m.getLabelId(oldLabel), newLabel);
	}
	
	public TreeSet<String> getLabelMap(){
		TreeSet<String> labels = new TreeSet<String>();
		for(Marcadores m : map.values())
			labels.addAll(m.getLabelMap().values());
		return labels;
	}
	
	public MultiSearchResult filtrarMarcadores(MultiSearchResult result, Set<String> labelNames) throws Exception{
		ArrayList<ItemId> selectedItems = new ArrayList<ItemId>();
	  	ArrayList<Float> scores = new ArrayList<Float>();
	  	int i = 0;
	  	HashMap<Integer, byte[]> labelBitsPerSource = new HashMap<Integer, byte[]>(); 
	  	for(ItemId item : result.getIterator()){
	  		Marcadores m = map.get(item.getSourceId());
	  		byte[] labelbits = labelBitsPerSource.get(item.getSourceId());
	  		if(labelbits == null){
	  			int[] labelIds = getLabelIds(m, labelNames);
	  			if(labelIds != null) labelbits = m.getLabelBits(labelIds);
	  			else labelbits = new byte[0];
	  			labelBitsPerSource.put(item.getSourceId(), labelbits);
	  		}
	  		if(labelbits.length != 0 && m.hasLabel(item.getId(), labelbits)){
	  			selectedItems.add(item);
	  			scores.add(result.getScore(i));
	  		}
	  		i++;
	  	}
	  	MultiSearchResult r = new MultiSearchResult(selectedItems.toArray(new ItemId[0]),
	  			ArrayUtils.toPrimitive(scores.toArray(new Float[0])));
	  	
		return r;
	  }
	  
	  public MultiSearchResult filtrarSemEComMarcadores(MultiSearchResult result, Set<String> labelNames) throws Exception{
		  ArrayList<ItemId> selectedItems = new ArrayList<ItemId>();
		  	ArrayList<Float> scores = new ArrayList<Float>();
		  	int i = 0;
		  	HashMap<Integer, byte[]> labelBitsPerSource = new HashMap<Integer, byte[]>(); 
		  	for(ItemId item : result.getIterator()){
		  		Marcadores m = map.get(item.getSourceId());
		  		byte[] labelbits = labelBitsPerSource.get(item.getSourceId());
		  		if(labelbits == null){
		  			int[] labelIds = getLabelIds(m, labelNames);
		  			if(labelIds != null) labelbits = m.getLabelBits(labelIds);
		  			else labelbits = new byte[0];
		  			labelBitsPerSource.put(item.getSourceId(), labelbits);
		  		}
		  		if(!m.hasLabel(item.getId()) || (labelbits.length != 0 && m.hasLabel(item.getId(), labelbits))){
		  			selectedItems.add(item);
		  			scores.add(result.getScore(i));
		  		}
		  		i++;
		  	}
		  	MultiSearchResult r = new MultiSearchResult(selectedItems.toArray(new ItemId[0]),
		  			ArrayUtils.toPrimitive(scores.toArray(new Float[0])));
		  	
			return r;
	  }
	  
	  public MultiSearchResult filtrarSemMarcadores(MultiSearchResult result){
		  
		    ArrayList<ItemId> selectedItems = new ArrayList<ItemId>();
		  	ArrayList<Float> scores = new ArrayList<Float>();
		  	int i = 0;
		  	for(ItemId item : result.getIterator()){
		  		if(!this.hasLabel(item)){
		  			selectedItems.add(item);
		  			scores.add(result.getScore(i));
		  		}
		  		i++;
		  	}
		  	MultiSearchResult r = new MultiSearchResult(selectedItems.toArray(new ItemId[0]),
		  			ArrayUtils.toPrimitive(scores.toArray(new Float[0])));
		  	
			return r;
	  }
	
	  public MultiSearchResult filtrarSelecionados(MultiSearchResult result) throws Exception {
		  	
		  	ArrayList<ItemId> selectedItems = new ArrayList<ItemId>();
		  	ArrayList<Float> scores = new ArrayList<Float>();
		  	int i = 0;
		  	for(ItemId item : result.getIterator()){
		  		if(map.get(item.getSourceId()).isSelected(item.getId())){
		  			selectedItems.add(item);
		  			scores.add(result.getScore(i));
		  		}
		  		i++;
		  	}
		  	MultiSearchResult r = new MultiSearchResult(selectedItems.toArray(new ItemId[0]),
		  			ArrayUtils.toPrimitive(scores.toArray(new Float[0])));
		  	
			return r;
	  }
	  
	  public void loadState(){
		  for(Marcadores m : map.values())
			  m.loadState();
	  }
	  
	  public void loadState(File file) throws ClassNotFoundException, IOException{
		  MultiMarcadores state = (MultiMarcadores) Util.readObject(file.getAbsolutePath());
		  this.map = state.map;
		  for(Marcadores marcador : this.map.values())
              marcador.updateCookie();
      }
	  
	  public void saveState(){
		  for(Marcadores m : map.values())
			  m.saveState();
	  }
	  
	  public void saveState(File file) throws IOException{
		  Util.writeObject(this, file.getAbsolutePath());
	  }
	  
	  public LinkedHashSet<String> getTypedWords(){
		  LinkedHashSet<String> searches = new LinkedHashSet<String>(); 
		  for(Marcadores m : map.values())
			  for(String s : m.getTypedWords())
				if(!searches.contains(s))
					searches.add(s);
		  return searches;
		}
	  
	  public void clearTypedWords(){
		  for(Marcadores m : map.values())
			  m.getTypedWords().clear();
	  }
	  
	  public void addToTypedWords(String texto) {
		  for(Marcadores m : map.values())
			  m.addToTypedWords(texto);
	   }

}
