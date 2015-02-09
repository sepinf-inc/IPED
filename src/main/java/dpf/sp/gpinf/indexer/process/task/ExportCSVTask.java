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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Properties;

import dpf.sp.gpinf.indexer.analysis.CategoryTokenizer;
import dpf.sp.gpinf.indexer.process.Worker;

/**
 * Responsável por gerar arquivo CSV com as propriedades dos itens processados.
 */
public class ExportCSVTask extends AbstractTask{

	private static int MAX_MEM_SIZE = 1000000;
	private static String CSV_NAME = "FileListing.csv";
	
	public static boolean exportFileProps = false;
	public static volatile boolean headerWritten = false;
	
	private File output;
	private StringBuilder list = new StringBuilder();

	public ExportCSVTask(Worker worker) throws NoSuchAlgorithmException, IOException {
		super(worker);
		this.output = new File(worker.output, CSV_NAME);
	}

	public void process(EvidenceFile evidence) {
		
		if (!exportFileProps)
			return;

		String value = evidence.getName();
		if (value == null)
			value = "";
		list.append("\"" + value.replace("\"", "\"\"") + "\";");
		
		value = evidence.getFileToIndex();
		if (!value.isEmpty() && evidence.getFileOffset() == -1)
			value = "=HIPERLINK(\"\"" + value + "\"\";\"\"Abrir\"\")";
		list.append("\"" + value + "\";");
		
		Long length = evidence.getLength();
		if (length == null)
			value = "";
		else
			value = length.toString();
		list.append("\"" + value.replace("\"", "\"\"") + "\";");
		
		value = evidence.getExt();
		if(value == null)
			value = "";
		list.append("\"" + value.replace("\"", "\"\"") + "\";");
		
		value = evidence.getLabels();
		if (value == null)
			value = "";
		list.append("\"" + value.replace("\"", "\"\"") + "\";");
		
		value = evidence.getCategories().replace("" + CategoryTokenizer.SEPARATOR, " | ");
		if (value == null)
			value = "";
		list.append("\"" + value.replace("\"", "\"\"") + "\";");
		
		value = evidence.getHash();
		if (value == null)
			value = "";
		list.append("\"" + value.replace("\"", "\"\"") + "\";");
		
		value = Boolean.toString(evidence.isDeleted());
		list.append("\"" + value.replace("\"", "\"\"") + "\";");
		
		Date date = evidence.getAccessDate();
		if (date == null)
			value = "";
		else
			value = date.toString();
		list.append("\"" + value.replace("\"", "\"\"") + "\";");
		
		date = evidence.getModDate();
		if (date == null)
			value = "";
		else
			value = date.toString();
		list.append("\"" + value.replace("\"", "\"\"") + "\";");
		
		date = evidence.getCreationDate();
		if (date == null)
			value = "";
		else
			value = date.toString();
		list.append("\"" + value.replace("\"", "\"\"") + "\";");
		
		value = evidence.getPath();
		if (value == null)
			value = "";
		list.append("\"" + value.replace("\"", "\"\"") + "\";");
		
		list.append("\r\n");

		if (list.length() > MAX_MEM_SIZE)
			flush();

	}

	public void flush() {
		try {
			flush(list, output);
		} catch (IOException e) {
			e.printStackTrace();
		}
		list = new StringBuilder();
	}

	private static synchronized void flush(StringBuilder list, File output) throws IOException {
		OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(output, true), "UTF-8");
		if (!headerWritten) {
			writer.write("\"Nome\";\"Tamanho\";\"Ext\";\"Categoria\";\"Hash\";\"Deletado\";\"Acesso\";\"Modificação\";\"Criação\";\"Caminho\";\"Export\"\r\n");
			headerWritten = true;
		}
		writer.write(list.toString());
		writer.close();
	}
	
	public void finish(){
		if (exportFileProps)
			flush();
	}

	@Override
	public void init(Properties confProps, File confDir) throws Exception {
		
		String value = confProps.getProperty("exportFileProps");
		if (value != null)
			value = value.trim();
		if (value != null && !value.isEmpty())
			exportFileProps = Boolean.valueOf(value);
		
		headerWritten = false;
		
	}

}
