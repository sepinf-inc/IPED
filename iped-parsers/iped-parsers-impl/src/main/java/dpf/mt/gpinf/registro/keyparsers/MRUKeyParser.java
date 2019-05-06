package dpf.mt.gpinf.registro.keyparsers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;

import org.apache.commons.lang.ArrayUtils;
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
import dpf.sp.gpinf.indexer.parsers.util.EmbeddedItem;
import dpf.sp.gpinf.indexer.parsers.util.EmbeddedParent;

public class MRUKeyParser extends HtmlKeyParser {
	@Override
	public void parse(KeyNode kn, String title, boolean hasChildren, String keyPath, EmbeddedParent parent, ContentHandler handler,
			Metadata metadata, ParseContext context) throws TikaException {
		EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class, new ParsingEmbeddedDocumentExtractor(context));
        if (extractor.shouldParseEmbedded(metadata)) {
            try {            	
            	super.parse(kn, title, hasChildren, keyPath, parent, handler, metadata, context);
            	EmbeddedItem item = context.get(EmbeddedItem.class);

		        KeyValue[] kvs = kn.getValues();
		        if(kvs!=null) {
		        	for (int j = 0; j < kvs.length; j++) {
		    			KeyValue keyValue = kvs[j];

		    			if(keyValue.getValueName().equals("MRUListEx")) {

		    			} else {
		    	            Metadata kmeta = new Metadata();

		    	            ByteArrayInputStream keyValueStream = new ByteArrayInputStream(generateMRUHtml(keyValue, 0, kmeta));

		    				context.set(EmbeddedParent.class, item);
		                    extractor.parseEmbedded(keyValueStream, handler, kmeta, false);
		    			}
		        	}
		        }                
            }catch(IOException | SAXException e) {
            	e.printStackTrace();
            }
        	
        }
        
	}

	private byte[] generateMRUHtml(KeyValue keyValue, int startOffset, Metadata kmeta) throws UnsupportedEncodingException {
		try {
	        ByteArrayOutputStream bout = new ByteArrayOutputStream();
	        PrintWriter out = new PrintWriter(new OutputStreamWriter(bout, "UTF-8"));

	        startDocument(out);
	        
			out.print("<table>");

	        byte[] data = keyValue.getValueData();
	        ArrayList<Integer> shellBagIndexes = new ArrayList<Integer>();

	        StringBuffer fullPath = new StringBuffer("");

	    	shellBagIndexes.add(0);
	        for (int i = 0; i < data.length;) {
	        	int length=(data[i]& 0xFF) + (data[i+1]& 0xFF)*256;
	        	if(length==0) break;
	        	i+=length;
	        	shellBagIndexes.add(i);
			}
	        shellBagIndexes.remove(shellBagIndexes.size()-1);

			long filesize = 0;
			Date dataUltimaModificacao = null;

	        for (Iterator iterator = shellBagIndexes.iterator(); iterator.hasNext();) {
				Integer i = (Integer) iterator.next();
				int lenSB = (data[i]& 0xFF) + (data[i+1]& 0xFF)*256;
				int endNamePos = i+lenSB-6;
				i+=2;
				if((data[i]==0x2F)) {
					fullPath.append(new String(ArrayUtils.subarray(data, i+1, i+3)) + "/");
				}
				if((data[i]==0x31)||(data[i]==0x35)||(data[i]==0x32)||(data[i]==0x36)||(data[i]==0xb1)) {
					int len=0;
					for (int j = endNamePos; data[j]!=0; j-=2) {
						len+=2;
					}
					fullPath.append(new String(ArrayUtils.subarray(data, endNamePos+2-len, endNamePos+4),"UTF-16LE"));
					if(data[i]!=0x32) {
						fullPath.append("/");	
					}
					if(data[i]!=0x32) {
						int j=i+2;
						filesize = (data[j]& 0xFF) + (data[j+1]& 0xFF)*256 + (data[j+2]& 0xFF)*256*256 + (data[j+3]& 0xFF)*256*256*256 ;
						j+=4;
						try {
							dataUltimaModificacao = dosDate2Date(data, j);	
						}catch(Exception e) {
							dataUltimaModificacao = null;
						}
						
					}
				}
			}

			kmeta.add(TikaCoreProperties.TITLE, keyValue.getValueName()+"--"+fullPath.toString());
			kmeta.set(TikaCoreProperties.MODIFIED, dataUltimaModificacao);
					
			out.print("<tr>");
			out.print("<td>Caminho do arquivo:</td>");
			out.print("<td>");
			out.print(fullPath.toString());
			out.print("</td>");
			out.print("</tr>");

			out.print("<tr>");
			out.print("<td>Tamanho:</td>");
			out.print("<td>");
			out.print(filesize);
			out.print("</td>");
			out.print("</tr>");

			if(dataUltimaModificacao!=null) {
				out.print("<tr>");
				out.print("<td>Data:</td>");
				out.print("<td>");
				DateFormat df = DateFormat.getDateTimeInstance();
				out.print(df.format(dataUltimaModificacao));
				out.print("</td>");
				out.print("</tr>");
			}

	        endDocument(out);
	        out.flush();

			return bout.toByteArray();
		}catch(Exception e) {
			e.printStackTrace();
			throw e;
		}
	}

	static Date dosDate2Date(byte[] data, int index) {
		int year = (data[index+1]>>1) + 1980;  // 7 bits
		int month = (data[index+1]&0x01)*8;
		month += (data[index]>>5 & 0x07);  // 4 bits
		int day = (data[index]&0x1F); // 5 bits
		int hour = (data[index+3]>>3)&0x1F; // 5 bits
		int minute = (data[index+3]&0x1F);
		minute+=(data[index+2]>>5 & 0x07);	// 6 bits	
		int second = (data[index+2]&0x1F) * 2;  // 5 bits
		
		return java.util.Date.from(LocalDateTime.of(year, month, day, hour, minute, second).atZone(ZoneId.of("UTC")).toInstant());
	}
}
