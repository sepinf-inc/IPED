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

import java.io.File;
import java.io.IOException;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.tika.mime.MediaType;

import dpf.sp.gpinf.indexer.util.CancelableWorker;
import dpf.sp.gpinf.indexer.util.IOUtil;

public class FileProcessor extends CancelableWorker<Void, Void> {

	private static int STATUS_LENGTH = 200;
	private volatile static FileProcessor parsingTask;
	private static Object lock = new Object(), lock2 = new Object();
	private Document doc;
	private boolean listSubItens;
	private static volatile File lastFile, dirFile;

	public FileProcessor(int docId, boolean listSubItens) {
		this.listSubItens = listSubItens;

		App.get().lastSelectedDoc = docId;

		if (parsingTask != null) {
			parsingTask.cancel(true);
		}
		parsingTask = this;

		if (docId >= 0) {
			try {
				doc = App.get().searcher.doc(docId);

				String status = doc.get("caminho");
				if (status.length() > STATUS_LENGTH)
					status = "..." + status.substring(status.length() - STATUS_LENGTH);
				App.get().status.setText(status);

			} catch (Exception e) {
				e.printStackTrace();
			}
		} else {
			doc = new Document();
			doc.add(new Field("export", App.get().codePath + "/../htm/Ajuda.htm", Field.Store.YES, Field.Index.NO));
			doc.add(new Field("content_type", MediaType.TEXT_HTML.toString(), Field.Store.YES, Field.Index.NO));
			doc.add(new Field("caminho", App.get().codePath + "/../htm/Ajuda.htm", Field.Store.YES, Field.Index.NO));
		}
	}

	@Override
	protected Void doInBackground() {
		
		synchronized (lock) {

			if (this.isCancelled())
				return null;

			process();

		}
		return null;
	}

	private void process() {

		System.out.println(doc.get("caminho"));

		RowComparator.dispose();

		File file = null;
		String export = doc.get("export");
		if (export != null && !export.isEmpty()){
			file = IOUtil.getRelativeFile(App.get().codePath + "/../..", export);
			file = IOUtil.getFile(file, doc);
			if(file.isDirectory()){
				if(dirFile == null)
					try {
						dirFile = File.createTempFile("indexador", ".tmp");
						dirFile.deleteOnExit();
					} catch (IOException e) {}
				file = dirFile;
			}
			
		}else {
			if (doc.get("tamanho").isEmpty() || Long.valueOf(doc.get("tamanho")) > 10000000)
				App.get().compositeViewer.clear();

			file = IOUtil.extractSleuthFile(App.get().sleuthCase, doc);

			final File fileToDelete = lastFile;
			if (fileToDelete != null)
				new Thread() {
					public void run() {
						try {
							Thread.sleep(2000);
						} catch (InterruptedException e) {
						}
						fileToDelete.delete();
					}
				}.start();
				
			lastFile = file;

			if (file == null)
				return;

		}

		String contentType = doc.get("content_type");

		App.get().compositeViewer.loadFile(doc, file, contentType, App.get().highlightTerms);

		if (listSubItens) {
			// listRelatedItens();
			App.get().subItemModel.listSubItens(doc);
			if (Thread.currentThread().isInterrupted())
				return;
			App.get().parentItemModel.listParents(doc);
		}
	}

	private Thread listTask;

	private void listRelatedItens() {
		if (listTask != null)
			listTask.interrupt();

		listTask = new Thread(new Runnable() {
			@Override
			public void run() {
				synchronized (lock2) {
					App.get().subItemModel.listSubItens(doc);
					if (Thread.currentThread().isInterrupted())
						return;
					App.get().parentItemModel.listParents(doc);
				}

			}
		});
		synchronized (lock2) {
			listTask.start();
		}

	}

}
