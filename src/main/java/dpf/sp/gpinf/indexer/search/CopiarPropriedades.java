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
package dpf.sp.gpinf.indexer.search;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;

import javax.swing.ProgressMonitor;
import javax.swing.SwingWorker;

import org.apache.lucene.document.Document;

import dpf.sp.gpinf.indexer.analysis.CategoryTokenizer;

public class CopiarPropriedades extends SwingWorker<Boolean, Integer> implements PropertyChangeListener {

	ArrayList<Integer> uniqueIds;
	ProgressMonitor progressMonitor;
	File file;
	int total;

	public CopiarPropriedades(File file, ArrayList<Integer> uniqueIds) {
		this.file = file;
		this.uniqueIds = uniqueIds;
		this.total = uniqueIds.size();

		progressMonitor = new ProgressMonitor(App.get(), "", "", 0, total);
		this.addPropertyChangeListener(this);
	}

	@Override
	protected Boolean doInBackground() throws Exception {

		OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), "windows-1252");

		ArrayList<String> fields = new ArrayList<String>();
		fields.add("id");
		fields.add("marcador");
		for (String field : ((ResultTableModel) App.get().resultsTable.getModel()).fields)
			fields.add(field);
		fields.add("export");
		

		for (int col = 0; col < fields.size(); col++)
			writer.write("\"" + fields.get(col).toUpperCase() + "\"" + ";");
		writer.write("\r\n");

		int progress = 0;
		App app = App.get();
		for (Integer docId : uniqueIds) {
			this.firePropertyChange("progress", progress, ++progress);
			try {
				Document doc = App.get().searcher.doc(docId);
				for (int col = 0; col < fields.size(); col++) {
					String value, field = fields.get(col);
					if(!field.equals("marcador"))
						value = doc.get(fields.get(col));
					else
						value = App.get().marcadores.getLabels(app.ids[docId]);
					if (value == null)
						value = "";
					if (field.equals("categoria"))
						value = value.replace("" + CategoryTokenizer.SEPARATOR, " | ");
					writer.write("\"" + value.replace("\"", "\"\"") + "\";");
				}
				writer.write("\r\n");

			} catch (Exception e1) {
				e1.printStackTrace();
			}

			if (this.isCancelled())
				break;
		}
		writer.close();
		return null;
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {

		if (progressMonitor.isCanceled())
			this.cancel(true);

		else if ("progress" == evt.getPropertyName()) {
			int progress = (Integer) evt.getNewValue();
			progressMonitor.setProgress(progress);
			progressMonitor.setNote("Copiando " + progress + " de " + total);
		}

	}

}
