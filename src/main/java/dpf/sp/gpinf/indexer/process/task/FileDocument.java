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
