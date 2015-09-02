package dpf.sp.gpinf.indexer.search.viewer;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Set;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;

import dpf.sp.gpinf.indexer.parsers.util.ToXMLContentHandler;
import dpf.sp.gpinf.indexer.util.IOUtil;

/**
 * Visualizador para versão Html dos arquivos gerados pelos parsers do Tika.
 * Somente os tipos cujo Html seja satisfatório devem ser configurados.
 */
public class TikaHtmlViewer extends HtmlViewer{
	
	private Parser parser = new AutoDetectParser();

	@Override
	public String getName() {
		return "TikaHtml";
	}
	
	@Override
	public boolean isSupportedType(String contentType) {
		return 	contentType.equals("application/x-msaccess") 
				|| contentType.equals("application/x-sqlite3")
				|| contentType.equals("application/sqlite-skype")
				|| contentType.equals("application/x-emule")
				|| contentType.equals("application/x-ares-galaxy")
				|| contentType.equals("application/x-shareaza-library-dat");
	}
	
	@Override
	public void loadFile(File file, String contentType, Set<String> highlightTerms) {
		
		if(file != null)
			file = getHtmlVersion(file, contentType);
		
		super.loadFile(file, highlightTerms);
	}
	/*
	 * TODO: Gerar preview html em outra thread, senão pode travar a interface ao trocar de abas no
	 * 		 visualizador caso a geração do preview seja lenta.
	 */
	private File getHtmlVersion(File file, String contentType) {

		File outFile = null;
		TikaInputStream tis = null;
		try {
			tis = TikaInputStream.get(file);
			outFile = File.createTempFile("tmp", ".html");
			outFile.deleteOnExit();
			
			generateHtmlPreview(tis, outFile, contentType);

		} catch (Exception e) {
			e.printStackTrace();
			return null;
			
		}finally{
			IOUtil.closeQuietly(tis);
		}
		return outFile;
	}
	
	public void generateHtmlPreview(TikaInputStream tis, File outFile, String contentType){
		BufferedOutputStream outStream = null;
		try {
			Metadata metadata = new Metadata();
			metadata.set(Metadata.CONTENT_TYPE, contentType);
			ParseContext context = new ParseContext();
			//Habilita parsing de subitens embutidos, o que ficaria ruim no preview de certos arquivos
			//Ex: Como renderizar no preview html um PDF embutido num banco de dados?
			//context.set(Parser.class, parser);
			outStream = new BufferedOutputStream(new FileOutputStream(outFile));
			ToXMLContentHandler handler = new ToXMLContentHandler(outStream, "UTF-8");
			parser.parse(tis, handler, metadata, context);

		} catch (Exception e) {
			e.printStackTrace();
			
		}finally{
			IOUtil.closeQuietly(outStream);
		}
	}
	
}
