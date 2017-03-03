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
package dpf.sp.gpinf.indexer.desktop;

import dpf.sp.gpinf.indexer.IFileProcessor;
import dpf.sp.gpinf.indexer.parsers.util.OCROutputFolder;
import gpinf.dev.data.EvidenceFile;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntField;
import org.apache.tika.mime.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.search.IPEDSource;
import dpf.sp.gpinf.indexer.util.CancelableWorker;

public class FileProcessor extends CancelableWorker<Void, Void> implements IFileProcessor {
  private static Logger LOGGER = LoggerFactory.getLogger(FileProcessor.class);

  private static int STATUS_LENGTH = 200;
  private volatile static FileProcessor parsingTask;
  private static Object lock = new Object(), lock2 = new Object();
  private Document doc;
  private int docId;
  private boolean listSubItens;
  private static volatile EvidenceFile lastItem;

  public FileProcessor(int docId, boolean listSubItens) {
    this.listSubItens = listSubItens;
    this.docId = docId;

    App.get().getSearchParams().lastSelectedDoc = docId;

    if (parsingTask != null) {
      parsingTask.cancel(true);
    }
    parsingTask = this;

    if (docId >= 0) {
      try {
        doc = App.get().appCase.getSearcher().doc(docId);

        String status = doc.get(IndexItem.PATH);
        if (status.length() > STATUS_LENGTH) {
          status = "..." + status.substring(status.length() - STATUS_LENGTH);
        }
        App.get().status.setText(status);

      } catch (Exception e) {
        e.printStackTrace();
      }
    } else {
      doc = new Document();
      doc.add(new IntField(IndexItem.ID, 0, Field.Store.YES));
      doc.add(new Field(IndexItem.NAME, "Ajuda.htm", Field.Store.YES, Field.Index.NO));
      String moduleDir = App.get().appCase.getAtomicSourceBySourceId(0).getModuleDir().getAbsolutePath();
      doc.add(new Field(IndexItem.EXPORT, moduleDir + "/htm/Ajuda.htm", Field.Store.YES, Field.Index.NO));
      doc.add(new Field(IndexItem.CONTENTTYPE, MediaType.TEXT_HTML.toString(), Field.Store.YES, Field.Index.NO));
      doc.add(new Field(IndexItem.PATH, moduleDir + "/htm/Ajuda.htm", Field.Store.YES, Field.Index.NO));
    }
  }

  @Override
  protected Void doInBackground() {

    synchronized (lock) {

      if (this.isCancelled()) {
        return null;
      }

      try{
    	  process();
    	  
      }catch(Exception e){
    	  e.printStackTrace();
      }

    }
    return null;
  }

  private void process() {

    LOGGER.info(doc.get(IndexItem.PATH));

    if (listSubItens) {
      // listRelatedItens();
      App.get().subItemModel.listSubItens(doc);
      if (Thread.currentThread().isInterrupted()) {
        return;
      }
      App.get().parentItemModel.listParents(doc);
    }

    //TODO usar nova API e contornar exibição da Ajuda
    IPEDSource iCase = App.get().appCase.getAtomicSource(docId);
	EvidenceFile item = IndexItem.getItem(doc, iCase.getModuleDir(), iCase.getSleuthCase(), false);
	
	int textSize = iCase.getTextSize(item.getId());
	item.setExtraAttribute(TextParser.TEXT_SIZE, textSize);
	
	OCROutputFolder ocrOut = new OCROutputFolder(iCase.getModuleDir());
	item.setExtraAttribute(OCROutputFolder.class.getName(), ocrOut);
	EvidenceFile.getAllExtraAttributes().remove(OCROutputFolder.class.getName());

    disposeItem(lastItem);
    lastItem = item;
    String contentType = null;
    if (item.getMediaType() != null) {
      contentType = item.getMediaType().toString();
    }

    EvidenceFile viewItem = item;

    if (item.getViewFile() != null) {
    	viewItem = IndexItem.getItem(doc, iCase.getModuleDir(), iCase.getSleuthCase(), true);
    }
    
    App.get().compositeViewer.loadFile(item, viewItem, contentType, App.get().getParams().highlightTerms);
  }

  private void disposeItem(final EvidenceFile itemToDispose) {
    if (itemToDispose != null) {
      new Thread() {
        public void run() {
          try {
            Thread.sleep(2000);
          } catch (InterruptedException e) {
          }
          itemToDispose.dispose();
        }
      }.start();
    }
  }

  private Thread listTask;

  private void listRelatedItens() {
    if (listTask != null) {
      listTask.interrupt();
    }

    listTask = new Thread(new Runnable() {
      @Override
      public void run() {
        synchronized (lock2) {
          App.get().subItemModel.listSubItens(doc);
          if (Thread.currentThread().isInterrupted()) {
            return;
          }
          App.get().parentItemModel.listParents(doc);
        }

      }
    });
    synchronized (lock2) {
      listTask.start();
    }

  }

}
