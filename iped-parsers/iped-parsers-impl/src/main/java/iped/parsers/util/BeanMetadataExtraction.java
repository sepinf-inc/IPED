package iped.parsers.util;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.util.Date;

import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.xml.sax.ContentHandler;

import iped.parsers.standard.StandardParser;
import iped.properties.ExtraProperties;
import iped.utils.DateUtil;
import iped.utils.EmptyInputStream;

public class BeanMetadataExtraction {
	String prefix;
	String mimeType;
	String nameProperty;
	
	public BeanMetadataExtraction(String prefix, String mimeType) {
		this.prefix = prefix;
		this.mimeType = mimeType;
		this.nameProperty="name";
	}

    public void extractEmbedded(int seq, ParseContext context, Metadata metadata, ContentHandler handler, Object bean) throws IOException {
        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                new ParsingEmbeddedDocumentExtractor(context));
        if (extractor.shouldParseEmbedded(metadata)) {
             try {
                 Metadata entryMetadata = new Metadata();
                 entryMetadata.set(StandardParser.INDEXER_CONTENT_TYPE, mimeType);
                 entryMetadata.set(HttpHeaders.CONTENT_TYPE, mimeType);
                 entryMetadata.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());

                 for (PropertyDescriptor pd : Introspector.getBeanInfo(bean.getClass()).getPropertyDescriptors()) {                	 
                  	  if (pd.getReadMethod() != null && !"class".equals(pd.getName())) {
                       	    Object value = pd.getReadMethod().invoke(bean);
                       	    if(pd.getDisplayName().equals(nameProperty)) {
                       	    	entryMetadata.add(TikaCoreProperties.TITLE, value.toString());//adds the name property without prefix
                       	    	entryMetadata.add(TikaCoreProperties.RESOURCE_NAME_KEY, value.toString());
                      		}
                       	    if(value!=null) {
                       	    	String metadataName = pd.getDisplayName();
                       	    	if(prefix!=null && prefix.length()>0) {
                       	    		metadataName = prefix+":"+metadataName;                       	    		                       	 	                       	  		
                       	    	}
    							if(value instanceof Date) {
    								entryMetadata.add(metadataName , DateUtil.dateToString((Date)value));
                       	    	}else {
                       	    		entryMetadata.add(metadataName, value.toString());
                      	    	}
                      	    }
                 	  }
                 }

                 extractor.parseEmbedded(new EmptyInputStream(), handler, entryMetadata, true);
             }catch (Exception e) {
				e.printStackTrace();
             }             
        }
    }

	public String getNameProperty() {
		return nameProperty;
	}

	public void setNameProperty(String nameProperty) {
		this.nameProperty = nameProperty;
	}

}
