package dpf.sp.gpinf.indexer.search.viewer;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Set;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.ToHTMLContentHandler;

import dpf.sp.gpinf.indexer.search.App;

/*
 * Visualizador para versão Html dos arquivos gerados pelos parsers do Tika.
 * Somente os tipos cujo Html seja satisfatório devem ser configurados.
 */
public class TikaHtmlViewer extends HtmlViewer{

	@Override
	public String getName() {
		return "TikaHtml";
	}
	
	@Override
	public boolean isSupportedType(String contentType) {
		return 	contentType.equals("application/x-msaccess") 
				|| contentType.equals("application/x-sqlite3")
				|| contentType.equals("application/sqlite-skype");  
	}
	

	@Override
	public void loadFile(File file, Set<String> highlightTerms) {
		
		if(file != null)
			file = getHtmlVersion(file);
		
		super.loadFile(file, highlightTerms);
	}

	private File getHtmlVersion(File file) {

		try {
			Metadata metadata = new Metadata();
			TikaInputStream tis = TikaInputStream.get(file);
			ParseContext context = new ParseContext();
			File outFile = File.createTempFile("tmp", ".html");
			outFile.deleteOnExit();
			BufferedOutputStream outStream = new BufferedOutputStream(new FileOutputStream(outFile));
			ToHTMLContentHandler handler = new ToHTMLContentHandler(outStream, "windows-1252");
			((Parser) App.get().autoParser).parse(tis, handler, metadata, context);
			tis.close();
			return outFile;

		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;

	}
}
