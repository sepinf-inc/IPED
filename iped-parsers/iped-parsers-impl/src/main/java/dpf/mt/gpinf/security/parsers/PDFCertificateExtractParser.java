package dpf.mt.gpinf.security.parsers;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import com.itextpdf.text.pdf.AcroFields;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.security.PdfPKCS7;

import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;

public class PDFCertificateExtractParser extends AbstractParser {
	public static final MediaType PDF_MIME = MediaType.application("pdf");
    private static Set<MediaType> SUPPORTED_TYPES = null;

    
	@Override
	public Set<MediaType> getSupportedTypes(ParseContext context) {
		if(SUPPORTED_TYPES == null){
			SUPPORTED_TYPES = new HashSet<MediaType>();
			SUPPORTED_TYPES.add(PDF_MIME);
		}

		return SUPPORTED_TYPES;
	}

	@Override
	public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
			throws IOException, SAXException, TikaException {
		TemporaryResources tmp = new TemporaryResources();
		TikaInputStream tis = TikaInputStream.get(stream, tmp);
		File file = tis.getFile();
		
		java.security.Security.addProvider(new BouncyCastleProvider());

		EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class, new ParsingEmbeddedDocumentExtractor(context));
        if (extractor.shouldParseEmbedded(metadata)) {

    		try{
    			PdfReader reader = new PdfReader(new FileInputStream(file));
    			
    			 AcroFields af = reader.getAcroFields();
    			 ArrayList<String> names = af.getSignatureNames();
    			 for (int k = 0; k < names.size(); ++k) {
    			    String name = (String)names.get(k);
    			    System.out.println("Signature name: " + name);
    			    System.out.println("Signature covers whole document: " + af.signatureCoversWholeDocument(name));

    			    PdfPKCS7 pk = af.verifySignature(name);
    			    Calendar cal = pk.getSignDate();
    			    Certificate pkc[] = pk.getCertificates();

    			    X509Certificate cert = pk.getSigningCertificate();

                    Metadata kmeta = new Metadata();
                    kmeta.set(TikaCoreProperties.MODIFIED, pk.getSignDate());
	                kmeta.set(HttpHeaders.CONTENT_TYPE, MediaType.application("pkix-cert").toString());
	                kmeta.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, MediaType.application("pkix-cert").toString());
	                
	                kmeta.add(TikaCoreProperties.TITLE, cert.getSubjectX500Principal().getName());

                    extractor.parseEmbedded(new ByteArrayInputStream(cert.getEncoded()), handler, kmeta, false);
    			 }
    		}catch(Exception e){
    			throw new TikaException("Invalid or unkown certificate format.", e);
    		}finally {
    			tis.close();
    		}
        }
	}
}
