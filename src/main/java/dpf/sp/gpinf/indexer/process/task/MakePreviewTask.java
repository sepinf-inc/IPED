package dpf.sp.gpinf.indexer.process.task;

import gpinf.dev.data.EvidenceFile;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Properties;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.xml.sax.ContentHandler;

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
      //Não é necessário fechar tis pois será fechado em evidence.dispose()
      TikaInputStream tis = evidence.getTikaStream();
      makeHtmlPreview(tis, viewFile, mediaType);

    } catch (Exception e) {
      Log.warning(this.getClass().getSimpleName(), "Erro ao processar " + evidence.getPath() + " " + e.toString());
    }

  }

  private void makeHtmlPreview(TikaInputStream tis, File outFile, String mediaType) throws Exception {
    BufferedOutputStream outStream = null;
    try {
      Metadata metadata = new Metadata();
      metadata.set(Metadata.CONTENT_TYPE, mediaType);
      ParseContext context = new ParseContext();
      //Habilita parsing de subitens embutidos, o que ficaria ruim no preview de certos arquivos
      //Ex: Como renderizar no preview html um PDF embutido num banco de dados?
      //context.set(Parser.class, parser);
      outStream = new BufferedOutputStream(new FileOutputStream(outFile));
      ContentHandler handler;
      if (!isSupportedTypeCSV(mediaType)) {
        handler = new ToXMLContentHandler(outStream, "UTF-8");
      } else {
        handler = new ToCSVContentHandler(outStream, "UTF-8");
      }

      parser.parse(tis, handler, metadata, context);

    } finally {
      IOUtil.closeQuietly(outStream);
    }
  }

}
