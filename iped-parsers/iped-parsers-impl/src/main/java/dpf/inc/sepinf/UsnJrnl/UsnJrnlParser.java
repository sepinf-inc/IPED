package dpf.inc.sepinf.UsnJrnl;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import iped3.util.ExtraProperties;

import java.nio.charset.StandardCharsets;

public class UsnJrnlParser extends AbstractParser {
	
	public static final MediaType USNJRNL_$J = MediaType.parse("USNJOURNAL/$J");
	public static final MediaType USNJRNL_REPORT = MediaType.parse("USNJOURNAL/Report");
	
	private static Set<MediaType> SUPPORTED_TYPES = MediaType.set(USNJRNL_$J);
	
	
	@Override
	public Set<MediaType> getSupportedTypes(ParseContext context) {
		return SUPPORTED_TYPES;
	}


   
	
	 
	
	public int readInt16(InputStream in) {
        return readInt16(in, true);
	 }
	
	public int readInt32(InputStream in) {
        return readInt32(in, true);
	 }

    public long readInt64(InputStream in) {
        return readInt64(in, true);
    }
    
    
    public int readInt16(InputStream in, boolean bigEndian) {
        try {
            int b1=in.read(),b2=in.read();
        	if (bigEndian) {
                return b1+ (b2<<8);
            } else {
                return b2+(b1<<8);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return 0;
    }

	    public int readInt32(InputStream in, boolean bigEndian) {
	        try {
	            int i = 0;
	            byte len = 4;
	            for (int j = 0; j < len; j++) {
	                int a = in.read();
	                
	                if (bigEndian) {
	                    i |= (a << (j * 8));
	                } else {
	                    i |= (a << ((len - j - 1) * 8));
	                }
	            }
	            return i;
	        } catch (Exception e) {
	            e.printStackTrace();
	        }

	        return 0;
	    }

	    public long readInt64(InputStream in, boolean bigEndian) {
	        try {
	            long i = 0;
	            byte len = 8;
	            for (int j = 0; j < len; j++) {
	                long a = in.read();
	                if (bigEndian) {
	                    i |= (a << (j * 8L));
	                } else {
	                    i |= (a << ((len - j - 1) * 8L));
	                }
	            }
	            return i;
	        } catch (Exception e) {
	            e.printStackTrace();
	        }

	        return 0;
	    }
	    
	    public String readString(InputStream in,int len) {
	    	try {
	    		byte[] b=new byte[len];
	    		in.read(b,0,len);
	    		return new String(b,StandardCharsets.UTF_16LE);
	    	}catch (Exception e) {
				// TODO: handle exception
			}
	    	return null;
	    }
	    
	    
	    private boolean zero(byte[] b) {
	    	for(int i=0;i<b.length;i++) {
	    		if(b[i]!=0) {
	    			return false;
	    		}
	    	}
	    	return true;
	    }
	
	 
	    public boolean findNextEntry(InputStream in) throws IOException {
		 byte[] b=new byte[8];
		int rb=-1;
		 do{
			 in.mark(8);
			 rb=in.read(b,0,8);
			 
			 if(zero(b)) {
				 continue;
			 }
			 in.reset();
			
			 if(b[4]==2 && b[5]+b[6]+b[7]==0) {
				 return true;
			 }
			 in.read();
			 
		 }while(rb==8);
		 
		 return false;
	 }
	    
	    
    public UsnJrnlEntry readEntry(InputStream in)throws IOException {
		 in.mark(4);
		 int tam=readInt32(in);
		 in.reset();
		 in.mark(tam);
		 in.skip(4);
		 if(tam>0) {
			UsnJrnlEntry u=new UsnJrnlEntry();
			u.setTam(tam);
			u.setMajorVersion(readInt16(in));
			u.setMinorVersion(readInt16(in));
			u.setMftRef(readInt64(in));
			u.setParentMftRef(readInt64(in));
			u.setUSN(readInt64(in));
			long filetime=readInt64(in);
			u.setFileTime(filetime);
			u.setReasonFlag(readInt32(in));
			u.setSourceInformation(readInt32(in));
			u.setSecurityId(readInt32(in));
			u.setFileAttributes(readInt32(in));
			u.setSizeofFileName(readInt16(in));
			u.setOffsetFilename(readInt16(in));
			if(u.getOffsetFilename()!=0x3c) {
				System.out.println("error");
				return null;
			}else {
				u.setFileName(readString(in, u.getSizeofFileName()));
				
			}
			in.reset();
			while(tam>0) {
				tam-=in.skip(tam);
			}
			return u;
		 }
		 
		 return null;
	 }
	 
	 
	 
    private void createReport(ArrayList<UsnJrnlEntry> entries,int n,ParseContext context, ContentHandler handler) throws SAXException, IOException {
    	ReportGenerator rg=new ReportGenerator();
    	EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                new ParsingEmbeddedDocumentExtractor(context));
		 byte[] bytes=rg.createHTMLReport(entries);
		 ByteArrayInputStream html = new ByteArrayInputStream(bytes);
		 
		 Metadata cMetadata = new Metadata();
        cMetadata.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, USNJRNL_REPORT.toString());
        cMetadata.set(TikaCoreProperties.TITLE,"JOURNAL "+n);
		 
        extractor.parseEmbedded(html, handler, cMetadata, false);
    }

	
	@Override
	public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
			throws IOException, SAXException, TikaException {
		
		System.out.println("olaaa");
		
		 
		ArrayList<UsnJrnlEntry> entries=new ArrayList<>();
		int n=1;
		 while(findNextEntry(stream)) {
			 UsnJrnlEntry u=readEntry(stream);
			 
			 if(u==null) {
				 continue;
				 
			 }	
			 if(entries.size()==5000) {
				 createReport(entries,n, context, handler);
				 entries.clear();
				 n++;
			 }
			 entries.add(u);
		 }
		 
		 if(entries.size()>0) {
			 createReport(entries,n, context, handler);
		 }
		 
		
		 
		 
		 
	}
	 
	 

}
