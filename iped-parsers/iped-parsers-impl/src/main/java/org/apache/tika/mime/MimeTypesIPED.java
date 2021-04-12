package org.apache.tika.mime;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.metadata.Metadata;

/**
 * Custom MimeTypes
 */
public class MimeTypesIPED implements Detector {
    private static final long serialVersionUID = 1L;

    private static MimeTypes mimeTypes;

    static {
    	TikaConfig tikaConfig = TikaConfig.getDefaultConfig();
    	mimeTypes = tikaConfig.getMimeRepository();
    }
    
    /**
     * Use the MimeType hint to try to clarify or specialise the current
     *  possible types list.
     * If the hint is a specialised form, use that instead
     * If there are multiple possible types, use the hint to select one
     */
    private List<MimeType> applyHint(List<MimeType> possibleTypes, MimeType hint) {
        if (possibleTypes == null || possibleTypes.isEmpty()) {
            return Collections.singletonList(hint);
        } else {
        	MediaTypeRegistry registry = mimeTypes.getMediaTypeRegistry();
            for (int i=0; i<possibleTypes.size(); i++) {
                final MimeType type = possibleTypes.get(i);
                if (hint.equals(type) ||
                    registry.isSpecializationOf(hint.getType(), type.getType())) {
                    // Use just this type
                    return Collections.singletonList(hint);
                }
            }
        }
        
        // Hint didn't help, sorry
        return possibleTypes;
    }
  
  
    @Override
    public MediaType detect(InputStream input, Metadata metadata)
            throws IOException {
    	
    	List<MimeType> possibleTypes = null;

        // Get type based on magic prefix
        if (input != null) {
            input.mark(mimeTypes.getMinLength());
            try {
                byte[] prefix = mimeTypes.readMagicHeader(input);
                possibleTypes = mimeTypes.getMimeType(prefix);
            } finally {
                input.reset();
            }
        }
        
        
        // Get type based on resourceName hint (if available)
        MimeType extensionHint = null;
        String resourceName = metadata.get(Metadata.RESOURCE_NAME_KEY);
        if (resourceName != null) {
            String name = null;
            boolean isHttp = false;

            // Deal with a URI or a path name in as the resource  name
            try {
                URI uri = new URI(resourceName);
                String scheme = uri.getScheme();
                isHttp = scheme != null && scheme.startsWith("http"); // http or https
                String path = uri.getPath();
                if (path != null) {
                    int slash = path.lastIndexOf('/');
                    if (slash + 1 < path.length()) {
                        name = path.substring(slash + 1);
                    }
                }
            } catch (URISyntaxException e) {
                name = resourceName;
            }

            if (name != null) {
                extensionHint = mimeTypes.getMimeType(name);

                // For server-side scripting languages, we cannot rely on the filename to detect the mime type
                if (!(isHttp && extensionHint.isInterpreted())) {
                    
              	
                	boolean signatureDetected = (possibleTypes != null) && !possibleTypes.isEmpty() 
                    		&& (possibleTypes.get(0).getType() != MediaType.OCTET_STREAM);
                	
                	List<Magic> magics = extensionHint.getMagics();
                	boolean noMagicDefinedForHint = (magics == null) || magics.isEmpty();
                	  	
                	//Name based detection is used in three situations:
                	//1 - If the mime hint has no magic associated with it. 
                	//2 - If there was a signature match(try to specialize).
                	//3 - If the mime hint is defined to always run name detection(TO DO).
                	if (noMagicDefinedForHint || signatureDetected) {
                		// If we have some types based on mime magic, try to specialise
                        //  and/or select the type based on that
                        // Otherwise, use the type identified from the name
                		possibleTypes = applyHint(possibleTypes, extensionHint);
                	}
                }
            }
        }

        // Get type based on metadata hint (if available)
        MimeType metadataHint = null;
        String typeName = metadata.get(Metadata.CONTENT_TYPE);
        if (typeName != null) {
            try {
            	metadataHint = mimeTypes.forName(typeName);
                possibleTypes = applyHint(possibleTypes, metadataHint);
            } catch (MimeTypeException e) {
                // Malformed type name, ignore
            }
        }

        if (possibleTypes == null || possibleTypes.isEmpty()) {
            // Report that we don't know what it is
            return MediaType.OCTET_STREAM;
        } else {
            return possibleTypes.get(0).getType();
        }
    	
    }


}
