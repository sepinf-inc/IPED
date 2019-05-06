package dpf.mt.gpinf.registro.keyparsers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.mt.gpinf.registro.model.KeyNode;
import dpf.mt.gpinf.registro.model.KeyValue;
import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.parsers.util.EmbeddedParent;
import iped3.util.BasicProps;

public class AresKeyParser extends HtmlKeyParser {
	

	@Override
	public void parse(KeyNode kn, String title, boolean hasChildren, String keyPath, EmbeddedParent parent, ContentHandler handler,
			Metadata metadata, ParseContext context) throws TikaException {
		EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class, new ParsingEmbeddedDocumentExtractor(context));
        if (extractor.shouldParseEmbedded(metadata)) {
            try {
        		ByteArrayOutputStream bout = new ByteArrayOutputStream();
                PrintWriter out = new PrintWriter(new OutputStreamWriter(bout, "UTF-8"));

                startDocument(out);

                
		        out.println("<TABLE>");
		        out.println("<TR>");
		        out.println("<TH>Atributo</TH>");
		        out.println("<TH>Tipo</TH>");
		        out.println("<TH>Valor</TH>");
		        out.println("<TH>Valor Decodificado</TH>");
		        out.println("</TR>");
		        KeyValue[] kvs = kn.getValues();
		        if(kvs!=null) {
		        	for (int j = 0; j < kvs.length; j++) {
		    			KeyValue keyValue = kvs[j];
		    			
		    			if(keyValue.getValueName().equals("General.Language")
		    					|| keyValue.getValueName().equals("PrivateMessage.AwayMessage") 
		    					) {
			    			out.print("<TR>");
			    	        out.print("<TD>"+keyValue.getValueName()+"</TD>");
			    	        out.print("<TD>"+getDatatypeString(keyValue.getValueDatatype())+"</TD>");
			    	        out.print("<TD>"+keyValue.getValueDataAsString().replaceAll("20", "20 ")+"</TD>");
			    	        out.println("<TD>"+decodeAresHexString(keyValue.getValueDataAsString())+"</TD>");
			    			out.print("</TR>");
		    			}else {
		        			out.print("<TR>");
		        	        out.print("<TD>"+keyValue.getValueName()+"</TD>");
		        	        out.print("<TD>"+getDatatypeString(keyValue.getValueDatatype())+"</TD>");
		        	        out.println("<TD>"+keyValue.getValueDataAsString()+"</TD>");
			    	        out.println("<TD>(não se aplica)</TD>");
		        	        out.println("</TR>");
		    			}
		    		}
		        }
		        out.println("</TABLE>");                
                
        		ArrayList<KeyNode> kns = kn.getSubKeys();
        		if(kns!=null) {
        			for (int i = 0; i < kns.size(); i++) {
        				KeyNode knl = kns.get(i);
        				if(knl.getKeyName().contains("Search.History")) {
        					parseSeachHistoryKey(knl, out);
        				}
        			}
        		}

                endDocument(out);
                out.flush();

                ByteArrayInputStream keyStream = new ByteArrayInputStream(bout.toByteArray());
                Metadata kmeta = new Metadata();
                kmeta.set(TikaCoreProperties.MODIFIED, kn.getLastWrittenAsDate());
                kmeta.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, "text/html");
                if(hasChildren){
                	kmeta.set(BasicProps.HASCHILD, "true");
                }
                if(keyPath.equals("ROOT")){
                    kmeta.set(TikaCoreProperties.TITLE, "ROOT");
                }else{
                    kmeta.set(TikaCoreProperties.TITLE, title);
                }

                context.set(EmbeddedParent.class, parent);
                extractor.parseEmbedded(keyStream, handler, kmeta, false);
                
            }catch(SAXException | IOException e) {
            	e.printStackTrace();
            }
        	
        }
	}

	private void parseSeachHistoryKey(KeyNode kn, PrintWriter out) {
		
		ArrayList<KeyNode> kns = kn.getSubKeys();
		if(kns!=null) {
			for (int i = 0; i < kns.size(); i++) {
				KeyNode knl = kns.get(i);
				
		        out.println("<TABLE>");
		        out.println("<TR>");
		        out.println("<TH colspan='2'>Histórico de pesquisas</tH>");
		        out.println("</TR>");
		        out.println("<TR>");
		        out.println("<TD>Chave:</td><td>"+knl.getKeyName()+"</td>");
		        out.println("</TR>");
		        out.println("<TR>");
		        String lastWrittenString = dateFormat.get().format(kn.getLastWrittenAsDate());
		        out.println("<TD>Última modificação:</td><td>"+lastWrittenString+"</td>");
		        out.println("</TR>");
		        out.println("<TABLE>");
		        out.println("<TABLE>");              
		        out.println("<TR>"
		        		+ "<TH>Atributo</TH>"
		        		+ "<TH>Valor Decodificado</TH>"
		        		+ "</TR>");

		        KeyValue[] kvs = knl.getValues();

		        if(kvs!=null) {
		        	for (int j = 0; j < kvs.length; j++) {
		    			KeyValue keyValue = kvs[j];
		    			
		    			out.print("<TR>");
		    	        out.print("<TD>"+keyValue.getValueName()+"</TD>");
		    	        out.println("<TD>"+decodeAresHexString(keyValue.getValueName())+"</TD>");
		    			out.print("</TR>");
		    		}
		        }
		        
		        out.println("</TABLE>");
		        out.println("<br/>");
			}
		}
	}
	
	protected static String decodeAresHexString(String hexString) {
		byte buffer[] = new byte [hexString.length()/2];
		
		for(int i=0; i < (hexString.length()/2); i++) {
			byte b = (byte) (hexString.charAt(i*2)-48);
			if(b>10) b=(byte) (b-7);//from A to F
			
			buffer[i] = (byte) (b << 4);
			
			b = (byte) (hexString.charAt(i*2 + 1)-48);
			if(b>10) b=(byte) (b-7);//from A to F
			buffer[i] += b;
		}
		
		return new String(buffer);		
	}

}
