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
import java.util.Date;
import java.util.Map.Entry;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.tika.mime.MediaType;
import org.sleuthkit.datamodel.SleuthkitCase;

import dpf.sp.gpinf.indexer.analysis.CategoryTokenizer;
import dpf.sp.gpinf.indexer.process.task.HTMLReportTask;
import dpf.sp.gpinf.indexer.util.DateUtil;
import dpf.sp.gpinf.indexer.util.SeekableFileInputStream;
import dpf.sp.gpinf.indexer.util.SeekableInputStream;
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
	

	private static FieldType contentField = new FieldType();
	private static FieldType storedTokenizedNoNormsField = new FieldType();
	
	static {
		contentField.setIndexed(true);
		contentField.setOmitNorms(true);
		storedTokenizedNoNormsField.setIndexed(true);
		storedTokenizedNoNormsField.setOmitNorms(true);
		storedTokenizedNoNormsField.setStored(true);
	}
	
	public static Document Document(EvidenceFile evidence, Reader reader) {
		Document doc = new Document();

		String value = String.valueOf(evidence.getId());
		doc.add(new StringField(ID, value, Field.Store.YES));

		value = evidence.getFtkID();
		if (value != null)
			doc.add(new StringField(FTKID, value, Field.Store.YES));

		value = evidence.getSleuthId();
		if (value != null)
			doc.add(new StringField(SLEUTHID, value, Field.Store.YES));
		
		value = evidence.getParentId();
		if (value == null)
			if (evidence.getEmailPai() != null)
				value = String.valueOf(evidence.getEmailPai().getId());
		if (value != null)
			doc.add(new StringField(PARENTID, value, Field.Store.YES));
		
		doc.add(new Field(PARENTIDs, evidence.getParentIdsString(), storedTokenizedNoNormsField));

		
		value = evidence.getName();
		if (value == null)
			value = "";
		Field nameField = new TextField(NAME, value, Field.Store.YES);
		nameField.setBoost(1000.0f);
		doc.add(nameField);

		EvidenceFileType fileType = evidence.getType();
		if (fileType != null)
			value = fileType.getLongDescr();
		else
			value = "";
		doc.add(new Field(TYPE, value, storedTokenizedNoNormsField));
		
		Long length = evidence.getLength();
		if(length != null){
			LongField longfield = new LongField(LENGTH, length, Field.Store.YES);
			doc.add(longfield);
		}
		
		Date date = evidence.getCreationDate();
		if(date != null)
			value = DateUtil.dateToString(date);
		else
			value = "";
		doc.add(new StringField(CREATED, value, Field.Store.YES));

		date = evidence.getAccessDate();
		if(date != null)
			value = DateUtil.dateToString(date);
		else
			value = "";
		doc.add(new StringField(ACCESSED, value, Field.Store.YES));

		date = evidence.getModDate();
		if(date != null)
			value = DateUtil.dateToString(date);
		else
			value = "";
		doc.add(new StringField(MODIFIED, value, Field.Store.YES));

		value = evidence.getPath();
		if (value == null)
			value = "";
		doc.add(new Field(PATH, value, storedTokenizedNoNormsField));

		doc.add(new Field(EXPORT, evidence.getFileToIndex(), storedTokenizedNoNormsField));
		
		doc.add(new Field(CATEGORY, evidence.getCategories(), storedTokenizedNoNormsField));

		MediaType type = evidence.getMediaType();
		if (type != null)
			value = type.toString();
		else
			value = "";
		doc.add(new Field(CONTENTTYPE, value, storedTokenizedNoNormsField));

		doc.add(new StringField(TIMEOUT, Boolean.toString(evidence.isTimedOut()), Field.Store.YES));

		value = evidence.getHash();
		if (value != null)
			doc.add(new Field(HASH, value, storedTokenizedNoNormsField));

		value = Boolean.toString(evidence.isDuplicate());
		doc.add(new StringField(DUPLICATE, value, Field.Store.YES));

		value = Boolean.toString(evidence.isDeleted());
		doc.add(new StringField(DELETED, value, Field.Store.YES));
		
		value = Boolean.toString(evidence.hasChildren());
		doc.add(new StringField(HASCHILD, value, Field.Store.YES));
		
		value = Boolean.toString(evidence.isDir());
		doc.add(new StringField(ISDIR, value, Field.Store.YES));
		
		if(evidence.isRoot())
			doc.add(new StringField(ISROOT, "true", Field.Store.YES));
		
		value = Boolean.toString(evidence.isCarved());
		doc.add(new StringField(CARVED, value, Field.Store.YES));
		
		value = Boolean.toString(evidence.isSubItem());
        doc.add(new StringField(SUBITEM, value, Field.Store.YES));
		
		long off = evidence.getFileOffset();
		if(off != -1)
			doc.add(new StoredField(OFFSET, Long.toString(off)));

		if (reader != null)
			doc.add(new Field(CONTENT, reader, contentField));
		
		for(Entry<String, Object> entry : evidence.getExtraAttributeMap().entrySet()){
			Object eValue = entry.getValue();
			if(eValue instanceof Date){
				value = DateUtil.dateToString((Date)eValue);
				doc.add(new StringField(entry.getKey(), value, Field.Store.YES));
			}else{
				doc.add(new Field(entry.getKey(), eValue.toString(), storedTokenizedNoNormsField));
			}
		}

		return doc;
	}
	
	public static EvidenceFile getItem(Document doc, File outputBase, SleuthkitCase sleuthCase, boolean viewITem){
		
		try{
			EvidenceFile evidence;
			if(viewITem)
				evidence = getViewItem();
			else
				evidence = new EvidenceFile(){
				public File getFile(){
					if(super.getFile() == null)
						try {
							return getTempFile();
						} catch (IOException e) {
							e.printStackTrace();
							return null;
						}
					else
						return super.getFile();
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

			value = doc.get(IndexItem.EXPORT);
			if (value != null && !value.isEmpty())
				evidence.setFile(Util.getRelativeFile(outputBase.getParent(), value));

			else {
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
				
				File viewFile = Util.findFileFromHash(new File(outputBase, "view"), value);
				if(viewFile == null){
					File thumb = Util.getFileFromHash(new File(outputBase.getParent() + 
							HTMLReportTask.reportSubFolderName + "/" + HTMLReportTask.thumbsFolderName), value, "jpg");
					if(thumb.exists())
						viewFile = thumb;
				}
				if(viewFile != null)
					evidence.setViewFile(viewFile);
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
	
	private static EvidenceFile getViewItem(){
		EvidenceFile item = new EvidenceFile(){
			private static final long serialVersionUID = 1L;
			
			public File getFile(){
				return getViewFile();
			}
			public SeekableInputStream getStream() throws FileNotFoundException{
				return new SeekableFileInputStream(getViewFile());
			}
		};
		return item;
	}

}
