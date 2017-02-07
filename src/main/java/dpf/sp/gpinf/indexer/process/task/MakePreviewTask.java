package dpf.sp.gpinf.indexer.process.task;

import gpinf.dev.data.EvidenceFile;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.ContentHandlerDecorator;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.io.TimeoutException;
import dpf.sp.gpinf.indexer.parsers.util.ToCSVContentHandler;
import dpf.sp.gpinf.indexer.parsers.util.ToXMLContentHandler;
import dpf.sp.gpinf.indexer.process.Worker;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.Log;
import dpf.sp.gpinf.indexer.util.Util;

public class MakePreviewTask extends AbstractTask {

  public static String viewFolder = "view";

  private Parser parser = new AutoDetectParser();

  private static boolean enableFileParsing = true;
  
  private volatile Throwable exception;

  public MakePreviewTask(Worker worker) {
    super(worker);
  }

  @Override
  public void init(Properties confParams, File confDir) throws Exception {
    String value = confParams.getProperty(ParsingTask.ENABLE_PARSING);
    if (value != null & !value.trim().isEmpty()) {
      enableFileParsing = Boolean.valueOf(value.trim());
    }
  }

  @Override
  public void finish() throws Exception {
  }

  public boolean isSupportedType(String contentType) {
    return contentType.equals("application/x-msaccess")
        || contentType.equals("application/x-sqlite3")
        || contentType.equals("application/sqlite-skype")
        || contentType.equals("application/x-emule")
        || contentType.equals("application/x-ares-galaxy")
        || contentType.equals("application/x-lnk")
        || contentType.equals("application/x-whatsapp-db")
        || contentType.equals("application/x-shareaza-searches-dat")
        || contentType.equals("application/x-shareaza-library-dat")
        || contentType.equals("application/x-msie-cache")
        || isSupportedTypeCSV(contentType);
  }

  private boolean isSupportedTypeCSV(String contentType) {
    return false;//contentType.equals("application/x-shareaza-library-dat");
  }

  @Override
  public boolean isEnabled() {
    return enableFileParsing;
  }
  
  @Override
  protected void process(EvidenceFile evidence) throws Exception {

    if (!enableFileParsing) {
      return;
    }

    String mediaType = evidence.getMediaType().toString();
    if(evidence.getLength() == Long.valueOf(0) || evidence.getHash() == null || evidence.getHash().isEmpty() || !isSupportedType(mediaType) || !evidence.isToAddToCase()) {
      return;
    }

    String ext = "html";
    if (isSupportedTypeCSV(mediaType)) {
      ext = "csv";
    }

    File viewFile = Util.getFileFromHash(new File(output, viewFolder), evidence.getHash(), ext);
    if (viewFile.exists()) {
      return;
    }

    if (!viewFile.getParentFile().exists()) {
      viewFile.getParentFile().mkdirs();
    }

    try {
      makeHtmlPreview(evidence, viewFile);

    } catch (Throwable e) {
      Log.warning(this.getClass().getSimpleName(), "Erro ao processar " + evidence.getPath() + " " + e.toString());
    }

  }

  private void makeHtmlPreview(EvidenceFile evidence, File outFile) throws Throwable {
    BufferedOutputStream outStream = null;
    try {
      final Metadata metadata = evidence.getMetadata();
      //Não é necessário fechar tis pois será fechado em evidence.dispose()
      final TikaInputStream tis = evidence.getTikaStream();
      
      //Habilita parsing de subitens embutidos, o que ficaria ruim no preview de certos arquivos
      //Ex: Como renderizar no preview html um PDF embutido num banco de dados?
      //context.set(Parser.class, parser);
      
      outStream = new BufferedOutputStream(new FileOutputStream(outFile));
      ContentHandler handler;
      if (!isSupportedTypeCSV(evidence.getMediaType().toString())) {
        handler = new ToXMLContentHandler(outStream, "UTF-8");
      } else {
        handler = new ToCSVContentHandler(outStream, "UTF-8");
      }
      final ProgressContentHandler pch = new ProgressContentHandler(handler);
      
      exception = null;
	  Thread t = new Thread(){
		  @Override
		  public void run(){
			  try {
				parser.parse(tis, pch, metadata, new ParseContext());
				
			} catch (IOException | SAXException | TikaException | OutOfMemoryError e) {
				exception = e;
			}
		  }
	  };
	  t.start();
	  
	  long start = System.currentTimeMillis();
	  while(t.isAlive()){
		  if(pch.getProgress())
			  start = System.currentTimeMillis();
		  
		  if((System.currentTimeMillis() - start)/1000 >= Configuration.timeOut){
			  t.interrupt();
			  stats.incTimeouts();
			  throw new TimeoutException();
		  }
		  t.join(1000);
		  if(exception != null)
			  throw exception;
	  }

    } finally {
      IOUtil.closeQuietly(outStream);
    }
  }
  
  public class ProgressContentHandler extends ContentHandlerDecorator {

		private volatile boolean progress = false;

		public ProgressContentHandler(ContentHandler handler) {
			super(handler);
		}

		@Override
		public void characters(char[] ch, int start, int length) throws SAXException {
			progress = true;
			super.characters(ch, start, length);
		}

		public boolean getProgress() {
			if(progress){
				progress = false;
				return true;
			}
			return false;
		}

	}

}
