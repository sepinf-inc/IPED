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
package dpf.sp.gpinf.indexer.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.swing.SwingUtilities;

import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.IOUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaMetadataKeys;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.ToTextContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.search.App;
import dpf.sp.gpinf.indexer.search.ProgressDialog;

public class LOExtractor extends CancelableWorker implements EmbeddedDocumentExtractor {

	private File output, input;
	private CancelableWorker worker;
	private ProgressDialog progressMonitor;
	private int progress = 0, numSubitens = /* 9165; */6419;// TODO obter número
															// de itens
															// automaticamente
	private volatile boolean completed = false;

	public LOExtractor(String input, String output) {
		this.output = new File(output);
		this.input = new File(input);
		this.worker = this;
	}

	public boolean decompressLO(Parser parser) {

		try {
			if (output.exists()) {
				if (IOUtil.countSubFiles(output) >= numSubitens)
					return true;
				else
					IOUtil.deletarDiretorio(output);
			}

			if (input.exists()) {
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						progressMonitor = new ProgressDialog(App.get(), worker);
						progressMonitor.setMaximum(numSubitens);
						progressMonitor.setNote("Descompactando LibreOffice...");
					}
				});

				ParseContext context = new ParseContext();
				context.set(EmbeddedDocumentExtractor.class, this);
				IndexerContext indexerContext = new IndexerContext(0, null, "");
				context.set(IndexerContext.class, indexerContext);
				parser.parse(new FileInputStream(input), new ToTextContentHandler(), new Metadata(), context);

				if (!progressMonitor.isCanceled())
					progressMonitor.close();

				if (isCompleted())
					return true;
				else
					IOUtil.deletarDiretorio(output);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return false;

	}

	@Override
	public boolean shouldParseEmbedded(Metadata metadata) {
		return !this.isCancelled();
	}

	@Override
	public void parseEmbedded(InputStream inputStream, ContentHandler contentHandler, Metadata metadata, boolean outputHtml) throws SAXException, IOException {

		String name = metadata.get(TikaMetadataKeys.RESOURCE_NAME_KEY);
		File outputFile = new File(output, name);
		File parent = outputFile.getParentFile();

		if (!parent.exists()) {
			if (!parent.mkdirs()) {
				throw new IOException("unable to create directory \"" + parent + "\"");
			}
		}
		// System.out.println("Extracting '"+name+" to " + outputFile);
		FileOutputStream os = new FileOutputStream(outputFile);
		IOUtils.copy(inputStream, os);
		os.close();

		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				progressMonitor.setProgress(++progress);
				if (progress == numSubitens) {
					completed = true;
				}
			}
		});

	}

	@Override
	protected Void doInBackground() throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	public boolean isCompleted() {
		return completed;
	}

}
