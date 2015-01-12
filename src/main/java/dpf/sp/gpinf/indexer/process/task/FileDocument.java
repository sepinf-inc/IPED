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
package dpf.sp.gpinf.indexer.process.task;

import gpinf.dev.data.EvidenceFile;
import gpinf.dev.filetypes.EvidenceFileType;

import java.io.Reader;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.tika.mime.MediaType;

/*
 * Cria um org.apache.lucene.document.Document a partir das propriedades do itens, para ser adicionado ao índice.
 */
public class FileDocument {

	//public static String[] field = { "Nome", "Tipo", "Tamanho", "Categoria", "Criação", "Modificação", "Acesso", "Caminho" };

	private static FieldType contentField = new FieldType();
	private static FieldType storedTokenizedNoNormsField = new FieldType();
	
	static {
		contentField.setIndexed(true);
		contentField.setOmitNorms(true);
		
		storedTokenizedNoNormsField.setIndexed(true);
		storedTokenizedNoNormsField.setOmitNorms(true);
		storedTokenizedNoNormsField.setStored(true);
	}
	
	/*private static final ThreadLocal<Document> docs =
        new ThreadLocal<Document>() {
            @Override protected Document initialValue() {
                return new Document();
        }
    };
    
    static class Fields{
    	Field id, ftkid, sleuthid, parentid, nome, len, tipo, categoria, created, modified, accessed,
    		path, export, mimetype, timeout, hash, primary, del, carved, contents; 
    }
    
    private static final ThreadLocal<Fields> fields =
        new ThreadLocal<Fields>() {
            @Override protected Fields initialValue() {
                Fields f = new Fields();
                String value = "";
                docs.get().add(f.id = new StringField("id", value, Field.Store.YES));
                docs.get().add(f.parentid = new StringField("parentId", value, Field.Store.YES));
                docs.get().add(f.ftkid = new StringField("ftkId", value, Field.Store.YES));
                docs.get().add(f.sleuthid = new StringField("sleuthId", value, Field.Store.YES));
                
                docs.get().add(f.nome = new TextField("nome", value, Field.Store.YES));
                f.nome.setBoost(1000.0f);
                
                docs.get().add(f.len = new StoredField("tamanho", value));
                docs.get().add(f.created = new StoredField("criacao", value));
                docs.get().add(f.accessed = new StoredField("acesso", value));
                docs.get().add(f.modified = new StoredField("modificacao", value));
                docs.get().add(f.hash = new StoredField("hash", value));
                docs.get().add(f.del = new StoredField("deletado", value));
                docs.get().add(f.timeout = new StoredField("timeout", value));
                
                docs.get().add(f.tipo = new Field("tipo", value, storedTokenizedNoNormsField));
                docs.get().add(f.categoria = new Field("categoria", value, storedTokenizedNoNormsField));
                docs.get().add(f.path = new Field("caminho", value, storedTokenizedNoNormsField));
                docs.get().add(f.export = new Field("export", value, storedTokenizedNoNormsField));
                docs.get().add(f.mimetype = new Field("content_type", value, storedTokenizedNoNormsField));
                
                docs.get().add(f.primary = new StringField("primary", value, Field.Store.YES));
                docs.get().add(f.carved = new StringField("carved", value, Field.Store.YES));
                docs.get().add(f.contents = new Field("conteudo", new StringReader(""), contentField));
                //TODO falta fileOffset

                return f;
        }
    };*/
	

    /*
     * Teste reaproveitando instâncias de Document e Field, conforme sugerido pela documentação do Lucene
     * para aumentar desempenho. Porém não houve melhoria, deve ser melhor testado.
     */
	/*public static Document Document2(EvidenceFile evidence, Reader reader, SimpleDateFormat df) {
		Fields f = fields.get();
		String value = String.valueOf(evidence.getId());
		f.id.setStringValue(value);
		
		value = evidence.getFtkID();
		if (value == null) value = "";
		f.ftkid.setStringValue(value);

		value = evidence.getSleuthId();
		if (value == null) value = "";
		f.sleuthid.setStringValue(value);
		
		
		value = evidence.getParentId();
		if (value == null)
			if (evidence.getEmailPai() != null)
				value = String.valueOf(evidence.getEmailPai().getId());
			else value = "";
		f.parentid.setStringValue(value);
		
		value = evidence.getName();
		if (value == null) value = "";
		f.nome.setStringValue(value);
		
		
		EvidenceFileType fileType = evidence.getType();
		if (fileType != null)
			value = fileType.getLongDescr();
		else
			value = "";
		f.tipo.setStringValue(value);

		
		Long length = evidence.getLength();
		if (length != null)
			value = length.toString();
		else
			value = "";
		f.len.setStringValue(value);

		Date date = evidence.getCreationDate();
		if (date != null)
			value = df.format(date);
		else
			value = "";
		f.created.setStringValue(value);

		date = evidence.getAccessDate();
		if (date != null)
			value = df.format(date);
		else
			value = "";
		f.accessed.setStringValue(value);

		date = evidence.getModDate();
		if (date != null)
			value = df.format(date);
		else
			value = "";
		f.modified.setStringValue(value);

		value = evidence.getPath();
		if (value == null)	value = "";
		f.path.setStringValue(value);

		f.export.setStringValue(evidence.getFileToIndex());

		f.categoria.setStringValue(evidence.getCategories());

		MediaType type = evidence.getMediaType();
		if (type != null)
			value = type.toString();
		else
			value = "";
		f.mimetype.setStringValue(value);

		f.timeout.setStringValue(Boolean.toString(evidence.timeOut));
		

		value = evidence.getHash();
		if (value == null) value = "";
		f.hash.setStringValue(value);


		value = Boolean.toString(evidence.isPrimaryHash());
		f.primary.setStringValue(value);


		value = Boolean.toString(evidence.isDeleted());
		f.del.setStringValue(value);

		
		value = Boolean.toString(evidence.isCarved());
		f.carved.setStringValue(value);
		

		Document doc = docs.get();
		//if (reader != null){
			f.contents.setReaderValue(reader);
		//	doc.add(f.contents);
		//}else
		//	doc.removeField(f.contents.name());
		
		return doc;
	}
	*/
	
	public static Document Document(EvidenceFile evidence, Reader reader, SimpleDateFormat df) {
		Document doc = new Document();

		String value = String.valueOf(evidence.getId());
		doc.add(new StringField("id", value, Field.Store.YES));

		value = evidence.getFtkID();
		if (value != null)
			doc.add(new StringField("ftkId", value, Field.Store.YES));

		value = evidence.getSleuthId();
		if (value != null)
			doc.add(new StringField("sleuthId", value, Field.Store.YES));

		value = evidence.getParentSleuthId();
		if (value != null)
			doc.add(new StringField("parentSleuthId", value, Field.Store.YES));
		
		value = evidence.getParentId();
		if (value == null)
			if (evidence.getEmailPai() != null)
				value = String.valueOf(evidence.getEmailPai().getId());
		if (value != null)
			doc.add(new StringField("parentId", value, Field.Store.YES));

		
		value = evidence.getName();
		if (value == null)
			value = "";
		Field nameField = new TextField("nome", value, Field.Store.YES);
		nameField.setBoost(1000.0f);
		doc.add(nameField);

		
		EvidenceFileType fileType = evidence.getType();
		if (fileType != null)
			value = fileType.getLongDescr();
		else
			value = "";
		doc.add(new Field("tipo", value, storedTokenizedNoNormsField));

		
		Long length = evidence.getLength();
		if (length != null)
			value = length.toString();
		else
			value = "";
		doc.add(new StoredField("tamanho", value));

		/*
		 * long length = -1; if(evidence.getLength() != null) length =
		 * evidence.getLength(); LongField longfield = new LongField("tamanho",
		 * length, Field.Store.YES); doc.add(longfield);
		 * 
		 * if(evidence.getCreationDate() != null) value =
		 * DateTools.dateToString(evidence.getCreationDate(),
		 * DateTools.Resolution.SECOND); else value = ""; doc.add(new
		 * Field("criacao", value, Field.Store.YES, Field.Index.NOT_ANALYZED));
		 */

		Date date = evidence.getCreationDate();
		if (date != null)
			value = df.format(date);
		else
			value = "";
		doc.add(new StoredField("criacao", value));

		date = evidence.getAccessDate();
		if (date != null)
			value = df.format(date);
		else
			value = "";
		doc.add(new StoredField("acesso", value));

		date = evidence.getModDate();
		if (date != null)
			value = df.format(date);
		else
			value = "";
		doc.add(new StoredField("modificacao", value));

		value = evidence.getPath();
		if (value == null)
			value = "";
		doc.add(new Field("caminho", value, storedTokenizedNoNormsField));
		
		
		doc.add(new Field("parentIds", evidence.getParentIdsString(), storedTokenizedNoNormsField));

		doc.add(new Field("export", evidence.getFileToIndex(), storedTokenizedNoNormsField));

		
		doc.add(new Field("categoria", evidence.getCategories(), storedTokenizedNoNormsField));

		MediaType type = evidence.getMediaType();
		if (type != null)
			value = type.toString();
		else
			value = "";
		doc.add(new Field("content_type", value, storedTokenizedNoNormsField));

		doc.add(new StoredField("timeout", Boolean.toString(evidence.timeOut)));

		value = evidence.getHash();
		if (value != null)
			doc.add(new StringField("hash", value.toLowerCase(), Field.Store.YES));

		value = Boolean.toString(evidence.isPrimaryHash());
		doc.add(new StringField("primary", value, Field.Store.YES));

		value = Boolean.toString(evidence.isDeleted());
		doc.add(new StringField("deletado", value, Field.Store.YES));
		
		value = Boolean.toString(evidence.hasChildren());
		doc.add(new StringField("hasChildren", value, Field.Store.YES));
		
		value = Boolean.toString(evidence.isDir());
		doc.add(new StringField("isDir", value, Field.Store.YES));
		
		if(evidence.isRoot())
			doc.add(new StringField("isRoot", "true", Field.Store.YES));
		
		value = Boolean.toString(evidence.isCarved());
		doc.add(new StringField("carved", value, Field.Store.YES));
		
		long off = evidence.getFileOffset();
		if(off != -1)
			doc.add(new StoredField("offset", Long.toString(off)));

		if (reader != null)
			doc.add(new Field("conteudo", reader, contentField));

		return doc;
	}

}
