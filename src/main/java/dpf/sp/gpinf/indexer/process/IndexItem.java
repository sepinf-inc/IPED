/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de Evidências Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package dpf.sp.gpinf.indexer.process;

import gpinf.dev.data.EvidenceFile;
import gpinf.dev.filetypes.EvidenceFileType;
import gpinf.dev.filetypes.GenericFileType;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.lucene.collation.ICUCollationDocValuesField;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoubleDocValuesField;
import org.apache.lucene.document.DoubleField;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.FloatDocValuesField;
import org.apache.lucene.document.FloatField;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.util.BytesRef;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.sleuthkit.datamodel.SleuthkitCase;

import com.ibm.icu.text.Collator;

import dpf.sp.gpinf.indexer.analysis.CategoryTokenizer;
import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.process.task.HTMLReportTask;
import dpf.sp.gpinf.indexer.util.DateUtil;
import dpf.sp.gpinf.indexer.util.SeekableFileInputStream;
import dpf.sp.gpinf.indexer.util.SeekableInputStream;
import dpf.sp.gpinf.indexer.util.UTF8Properties;
import dpf.sp.gpinf.indexer.util.Util;

/**
 * Cria um org.apache.lucene.document.Document a partir das propriedades do itens
 * que será adicionado ao índice.
 */
public class IndexItem {
	
	public static final String ID = "id";
	public static final String FTKID = "ftkId";
	public static final String PARENTID = "parentId";
	public static final String PARENTIDs = "parentIds";
	public static final String SLEUTHID = "sleuthId";
	public static final String NAME = "nome";
	public static final String TYPE = "tipo";
	public static final String LENGTH = "tamanho";
	public static final String CREATED = "criacao";
	public static final String ACCESSED = "acesso";
	public static final String MODIFIED = "modificacao";
	public static final String PATH = "caminho";
	public static final String EXPORT = "export";
	public static final String CATEGORY = "categoria";
	public static final String HASH = "hash";
	public static final String ISDIR = "isDir";
	public static final String ISROOT = "isRoot";
	public static final String DELETED = "deletado";
	public static final String HASCHILD = "hasChildren";
	public static final String CARVED = "carved";
	public static final String SUBITEM = "subitem";
	public static final String OFFSET = "offset";
	public static final String DUPLICATE = "duplicate";
	public static final String TIMEOUT = "timeout";
	public static final String CONTENTTYPE = "contentType";
	public static final String CONTENT = "conteudo";
	public static final String TREENODE = "treeNode";
	
	public static String attrTypesFilename = "metadataTypes.txt";
	
	static HashSet<String> ignoredMetadata = new HashSet<String>();
	
	private static volatile boolean guessMetaTypes = false;
	
	private static Map<String, Class> typesMap = new ConcurrentHashMap<String, Class>();

	private static FieldType contentField = new FieldType();
	private static FieldType storedTokenizedNoNormsField = new FieldType();
	private static Collator collator;
	
	static {
		contentField.setIndexed(true);
		contentField.setOmitNorms(true);
		storedTokenizedNoNormsField.setIndexed(true);
		storedTokenizedNoNormsField.setOmitNorms(true);
		storedTokenizedNoNormsField.setStored(true);
		
		collator = Collator.getInstance();
		collator.setStrength(Collator.PRIMARY);
		collator.freeze();
		
		ignoredMetadata.add(Metadata.CONTENT_TYPE);
		ignoredMetadata.add(Metadata.CONTENT_LENGTH);
		ignoredMetadata.add(Metadata.RESOURCE_NAME_KEY);
		ignoredMetadata.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE);
		ignoredMetadata.add(IndexerDefaultParser.INDEXER_TIMEOUT);
		ignoredMetadata.add(TikaCoreProperties.CONTENT_TYPE_HINT.getName());
		ignoredMetadata.add("File Name");
		ignoredMetadata.add("File Size");
	}
	
	public static Map<String, Class> getMetadataTypes(){
	    return typesMap;
	}
	
	public static void saveMetadataTypes(File confDir) throws IOException{
		File metadataTypesFile = new File(confDir, attrTypesFilename); 
		UTF8Properties props = new UTF8Properties();
		for(Entry<String, Class> e : typesMap.entrySet())
			props.setProperty(e.getKey(), e.getValue().getCanonicalName());
		props.store(metadataTypesFile);
	}
	
	public static void loadMetadataTypes(File confDir) throws IOException, ClassNotFoundException{
		
		if(IndexItem.getMetadataTypes().size() != 0)
			return;
		
		File metadataTypesFile = new File(confDir, attrTypesFilename);
		if(metadataTypesFile.exists()){
		    UTF8Properties props = new UTF8Properties();
			props.load(metadataTypesFile);
			for(String key : props.stringPropertyNames())
				typesMap.put(key, Class.forName(props.getProperty(key)));
		}
	}
	
	private static final ICUCollationDocValuesField getCollationDocValue(String field, String value){
		ICUCollationDocValuesField cdvf = new ICUCollationDocValuesField(field, collator);
		cdvf.setStringValue(value);
		return cdvf;
	}
	
	public static Document Document(EvidenceFile evidence, Reader reader) {
		Document doc = new Document();

		doc.add(new IntField(ID, evidence.getId(), Field.Store.YES));
		doc.add(new NumericDocValuesField(ID, evidence.getId()));

		String value = evidence.getFtkID();
		if (value != null){
			doc.add(new StringField(FTKID, value, Field.Store.YES));
			doc.add(new SortedDocValuesField(FTKID, new BytesRef(value)));
		}
			
		value = evidence.getSleuthId();
		if (value != null){
			doc.add(new IntField(SLEUTHID, Integer.parseInt(value), Field.Store.YES));
			doc.add(new NumericDocValuesField(SLEUTHID, Integer.parseInt(value)));
		}
			
		value = evidence.getParentId();
		if (value != null){
			try{
				doc.add(new IntField(PARENTID, Integer.parseInt(value), Field.Store.YES));
				doc.add(new NumericDocValuesField(PARENTID, Integer.parseInt(value)));
			}catch(Exception e){
				doc.add(new StringField(PARENTID, value, Field.Store.YES));
				doc.add(new SortedDocValuesField(PARENTID, new BytesRef(value)));
			}
		}
		
		doc.add(new Field(PARENTIDs, evidence.getParentIdsString(), storedTokenizedNoNormsField));
		doc.add(new SortedDocValuesField(PARENTIDs, new BytesRef(evidence.getParentIdsString())));
		
		value = evidence.getName();
		if (value == null)
			value = "";
		Field nameField = new TextField(NAME, value, Field.Store.YES);
		nameField.setBoost(1000.0f);
		doc.add(nameField);
		doc.add(getCollationDocValue(NAME, value));

		EvidenceFileType fileType = evidence.getType();
		if (fileType != null)
			value = fileType.getLongDescr();
		else
			value = "";
		doc.add(new Field(TYPE, value, storedTokenizedNoNormsField));
		doc.add(getCollationDocValue(TYPE, value));
		
		Long length = evidence.getLength();
		if(length != null){
			doc.add(new LongField(LENGTH, length, Field.Store.YES));
			doc.add(new NumericDocValuesField(LENGTH, length));
		}
		
		Date date = evidence.getCreationDate();
		if(date != null)
			value = DateUtil.dateToString(date);
		else
			value = "";
		doc.add(new StringField(CREATED, value, Field.Store.YES));
		doc.add(new SortedDocValuesField(CREATED, new BytesRef(value)));

		date = evidence.getAccessDate();
		if(date != null)
			value = DateUtil.dateToString(date);
		else
			value = "";
		doc.add(new StringField(ACCESSED, value, Field.Store.YES));
		doc.add(new SortedDocValuesField(ACCESSED, new BytesRef(value)));

		date = evidence.getModDate();
		if(date != null)
			value = DateUtil.dateToString(date);
		else
			value = "";
		doc.add(new StringField(MODIFIED, value, Field.Store.YES));
		doc.add(new SortedDocValuesField(MODIFIED, new BytesRef(value)));

		value = evidence.getPath();
		if (value == null)
			value = "";
		doc.add(new Field(PATH, value, storedTokenizedNoNormsField));
		//doc.add(new SortedDocValuesField(PATH, new BytesRef(value)));
		doc.add(getCollationDocValue(PATH, value));

		doc.add(new Field(EXPORT, evidence.getFileToIndex(), storedTokenizedNoNormsField));
		doc.add(new SortedDocValuesField(EXPORT, new BytesRef(evidence.getFileToIndex())));
		
		doc.add(new Field(CATEGORY, evidence.getCategories(), storedTokenizedNoNormsField));
		doc.add(getCollationDocValue(CATEGORY, evidence.getCategories()));

		MediaType type = evidence.getMediaType();
		if (type != null)
			value = type.toString();
		else
			value = "";
		doc.add(new Field(CONTENTTYPE, value, storedTokenizedNoNormsField));
		doc.add(new SortedDocValuesField(CONTENTTYPE, new BytesRef(value)));

		if(evidence.isTimedOut()){
			doc.add(new StringField(TIMEOUT, Boolean.TRUE.toString(), Field.Store.YES));
			doc.add(new SortedDocValuesField(TIMEOUT, new BytesRef(Boolean.TRUE.toString())));
		}

		value = evidence.getHash();
		if (value != null){
			doc.add(new Field(HASH, value, storedTokenizedNoNormsField));
			doc.add(new SortedDocValuesField(HASH, new BytesRef(value)));
		}

		value = Boolean.toString(evidence.isDuplicate());
		doc.add(new StringField(DUPLICATE, value, Field.Store.YES));
		doc.add(new SortedDocValuesField(DUPLICATE, new BytesRef(value)));

		value = Boolean.toString(evidence.isDeleted());
		doc.add(new StringField(DELETED, value, Field.Store.YES));
		doc.add(new SortedDocValuesField(DELETED, new BytesRef(value)));
		
		value = Boolean.toString(evidence.hasChildren());
		doc.add(new StringField(HASCHILD, value, Field.Store.YES));
		doc.add(new SortedDocValuesField(HASCHILD, new BytesRef(value)));
		
		value = Boolean.toString(evidence.isDir());
		doc.add(new StringField(ISDIR, value, Field.Store.YES));
		doc.add(new SortedDocValuesField(ISDIR, new BytesRef(value)));
		
		if(evidence.isRoot()){
			doc.add(new StringField(ISROOT, Boolean.TRUE.toString(), Field.Store.YES));
			doc.add(new SortedDocValuesField(ISROOT, new BytesRef(Boolean.TRUE.toString())));
		}
		
		value = Boolean.toString(evidence.isCarved());
		doc.add(new StringField(CARVED, value, Field.Store.YES));
		doc.add(new SortedDocValuesField(CARVED, new BytesRef(value)));
		
		value = Boolean.toString(evidence.isSubItem());
        doc.add(new StringField(SUBITEM, value, Field.Store.YES));
        doc.add(new SortedDocValuesField(SUBITEM, new BytesRef(value)));
		
		long off = evidence.getFileOffset();
		if(off != -1)
			doc.add(new StoredField(OFFSET, Long.toString(off)));

		if (reader != null)
			doc.add(new Field(CONTENT, reader, contentField));
		
		if(typesMap.size() == 0)
			guessMetaTypes = true;
		
		for(Entry<String, Object> entry : evidence.getExtraAttributeMap().entrySet()){
			if(!typesMap.containsKey(entry.getKey()))
				typesMap.put(entry.getKey(), entry.getValue().getClass());
			addExtraAttributeToDoc(doc, entry.getKey(), entry.getValue(), false);
		}
		
		Metadata metadata = evidence.getMetadata();
        if(metadata != null){
        	if(guessMetaTypes)
        		guessMetadataTypes(evidence.getMetadata());
        	else
                addMetadataToDoc(doc, evidence.getMetadata()); 
        }
		
		
		return doc;
	}
	
	private static void addExtraAttributeToDoc(Document doc, String key, Object oValue, boolean isMetadata){
		boolean isString = false;
	    if(oValue instanceof Date){
            String value = DateUtil.dateToString((Date)oValue);
            doc.add(new StringField(key, value, Field.Store.YES));
            doc.add(new SortedDocValuesField(key, new BytesRef(value)));
            
        }else if(oValue instanceof Byte){
            doc.add(new IntField(key, (Byte)oValue, Field.Store.YES));
            doc.add(new NumericDocValuesField(key, (Byte)oValue));
            
        }else if(oValue instanceof Integer){
            doc.add(new IntField(key, (Integer)oValue, Field.Store.YES));
            doc.add(new NumericDocValuesField(key, (Integer)oValue));
            
        }else if(oValue instanceof Long){
            doc.add(new LongField(key, (Long)oValue, Field.Store.YES));
            doc.add(new NumericDocValuesField(key, (Long)oValue));
            
        }else if(oValue instanceof Float){
            doc.add(new FloatField(key, (Float)oValue, Field.Store.YES));
            doc.add(new FloatDocValuesField(key, (Float)oValue));
            
        }else if(oValue instanceof Double){
            doc.add(new DoubleField(key, (Double)oValue, Field.Store.YES));
            doc.add(new DoubleDocValuesField(key, (Double)oValue));
            
        }else{
        	doc.add(new Field(key, oValue.toString(), storedTokenizedNoNormsField));
        	isString = true;
        }
	    
	    if(isMetadata || isString){
	    	String value = oValue.toString();
	        if(value.length() > 16000)
	            value = value.substring(value.length() - 16000);
	        doc.add(getCollationDocValue("_" + key, value));
	    }
	    
	}
	
	private static void addMetadataToDoc(Document doc, Metadata metadata){
	    for(String key : metadata.names()){
            if(key.contains("Unknown tag") || ignoredMetadata.contains(key))
                continue;
            String value = metadata.get(key).trim();
            if(metadata.getValues(key).length > 1){
            	StringBuilder strBuilder = new StringBuilder();
            	for(String val : metadata.getValues(key))
                    strBuilder.append(val + " ");
            	value = strBuilder.toString().trim();
            }
            if(value.isEmpty())
            	continue;
            
            Object oValue = value;
            Class type = typesMap.get(key); 
            if(type != null && !type.equals(String.class))
            	try{
            		if(type.equals(Integer.class))
                        oValue = Integer.valueOf(value);
                    else if(type.equals(Long.class))
                        oValue = Long.valueOf(value);
                    else if(type.equals(Float.class))
                        oValue = Float.valueOf(value);
                    else if(type.equals(Double.class))
                        oValue = Double.valueOf(value);
            		
            	}catch(NumberFormatException e){
            		//e.printStackTrace();
            		typesMap.put(key, String.class);
            		//oValue = null;
            	}
            
            //if(oValue != null)
            addExtraAttributeToDoc(doc, key, oValue, true);
            
        }
	}
	
	private static void guessMetadataTypes(Metadata metadata){
	    
	    for(String key : metadata.names()){
            if(key.contains("Unknown tag") || ignoredMetadata.contains(key))
                continue;
            if(metadata.getValues(key).length > 1){
                typesMap.put(key, String.class);
                continue;
            }
            String val = metadata.get(key);
            if(typesMap.get(key) == null || !typesMap.get(key).equals(String.class)){
                int type = 0;
                while(type <= 4)
                    try{
                        switch(type){
                        case 0:
                            if(typesMap.get(key) == null || typesMap.get(key).equals(Integer.class)){
                                Integer.parseInt(val);
                                typesMap.put(key, Integer.class);
                                break;
                            }
                        case 1:
                            if(typesMap.get(key) == null || typesMap.get(key).equals(Integer.class) 
                            || typesMap.get(key).equals(Long.class)){
                                Long.parseLong(val);
                                typesMap.put(key, Long.class);
                                break;
                            }
                        case 2:
                            if(typesMap.get(key) == null || typesMap.get(key).equals(Float.class)){
                                Float.parseFloat(val);
                                typesMap.put(key, Float.class);
                                break;
                            }
                        case 3:
                            if(typesMap.get(key) == null || typesMap.get(key).equals(Float.class) 
                            || typesMap.get(key).equals(Double.class)){
                                Double.parseDouble(val);
                                typesMap.put(key, Double.class);
                                break;
                            }
                        case 4:
                            typesMap.put(key, String.class);
                        }
                        type = 100;
                        
                    }catch(NumberFormatException e){
                        type++;
                    }
            }
                
                
        }
	    
	}
	
	public static EvidenceFile getItem(Document doc, File outputBase, SleuthkitCase sleuthCase, boolean viewItem){
		
		try{
			EvidenceFile evidence = new EvidenceFile(){
				public File getFile(){
					try {
						return getTempFile();
					} catch (IOException e) {
						e.printStackTrace();
						return null;
					}
				}
			};
			
			evidence.setName(doc.get(IndexItem.NAME));
			
			String value = doc.get(IndexItem.LENGTH);
			Long len = null;
			if (value != null && !value.isEmpty())
				len = Long.valueOf(value);
			evidence.setLength(len);
			
			value = doc.get(IndexItem.ID);
			if (value != null)
				evidence.setId(Integer.valueOf(value));
			
			//evidence.setLabels(state.getLabels(id));

			value = doc.get(IndexItem.PARENTID);
			if (value != null)
				evidence.setParentId(value);

			value = doc.get(IndexItem.TYPE);
			if (value != null)
				evidence.setType(new GenericFileType(value));

			value = doc.get(IndexItem.CATEGORY);
			if (value != null)
				for(String category : value.split(CategoryTokenizer.SEPARATOR + ""))
					evidence.addCategory(category);
			
			value = doc.get(IndexItem.ACCESSED);
			if (value != null && !value.isEmpty())
				evidence.setAccessDate(DateUtil.stringToDate(value));

			value = doc.get(IndexItem.CREATED);
			if (value != null && !value.isEmpty())
				evidence.setCreationDate(DateUtil.stringToDate(value));

			value = doc.get(IndexItem.MODIFIED);
			if (value != null && !value.isEmpty())
				evidence.setModificationDate(DateUtil.stringToDate(value));

			evidence.setPath(doc.get(IndexItem.PATH));

			boolean hasFile = false;
			value = doc.get(IndexItem.EXPORT);
			if (value != null && !value.isEmpty()){
				evidence.setFile(Util.getRelativeFile(outputBase.getParent(), value));
				hasFile = true;

			}else {
				value = doc.get(IndexItem.SLEUTHID);
				if (value != null && !value.isEmpty()) {
					evidence.setSleuthId(value);
					evidence.setSleuthFile(sleuthCase.getContentById(Long.valueOf(value)));
				}
			}

			value = doc.get(IndexItem.CONTENTTYPE);
			if (value != null)
				evidence.setMediaType(MediaType.parse(value));

			value = doc.get(IndexItem.TIMEOUT);
			if (value != null)
				evidence.setTimeOut(Boolean.parseBoolean(value));

			value = doc.get(IndexItem.HASH);
			if (value != null){
				value = value.toUpperCase();
				evidence.setHash(value);
				
				if(!value.isEmpty()){
					File viewFile = Util.findFileFromHash(new File(outputBase, "view"), value);
					if(viewFile == null && !hasFile && evidence.getSleuthId() == null)
						viewFile = Util.findFileFromHash(new File(outputBase.getParentFile(), 
								HTMLReportTask.reportSubFolderName + "/" + HTMLReportTask.thumbsFolderName), value);
					if(viewFile != null)
						evidence.setViewFile(viewFile);
					if(viewItem || (!hasFile && evidence.getSleuthId() == null)){
						evidence.setFile(viewFile);
						evidence.setTempFile(viewFile);
						evidence.setMediaType(null);
					}
					
				}
			}
			
			value = doc.get(IndexItem.DELETED);
			if (value != null)
				evidence.setDeleted(Boolean.parseBoolean(value));
			
			value = doc.get(IndexItem.ISDIR);
			if (value != null)
				evidence.setIsDir(Boolean.parseBoolean(value));
			
			value = doc.get(IndexItem.CARVED);
			if (value != null)
				evidence.setCarved(Boolean.parseBoolean(value));
			
			value = doc.get(IndexItem.SUBITEM);
			if (value != null)
				evidence.setSubItem(Boolean.parseBoolean(value));
			
			value = doc.get(IndexItem.HASCHILD);
			if (value != null)
				evidence.setHasChildren(Boolean.parseBoolean(value));
	        
	        value = doc.get(IndexItem.TIMEOUT);
	        if (value != null)
	        	evidence.setTimeOut(Boolean.parseBoolean(value));
			
			value = doc.get(IndexItem.OFFSET);
			if(value != null)
				evidence.setFileOffset(Long.parseLong(value));
			
			return evidence;
			
		}catch(Exception e){
			e.printStackTrace();
		}
		
		return null;
		
	}

}
