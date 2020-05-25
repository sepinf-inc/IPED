package iped3.util;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;

public class MediaTypes {
    
    public static final MediaType METADATA_ENTRY = MediaType.application("x-metadata-entry"); //$NON-NLS-1$
    public static final MediaType UFED_EMAIL_MIME = MediaType.parse("message/x-ufed-email"); //$NON-NLS-1$
    public static final MediaType UFED_MESSAGE_ATTACH_MIME = MediaType.parse("message/x-ufed-attachment"); //$NON-NLS-1$
    
    public static final String UFED_MIME_PREFIX = "x-ufed-"; //$NON-NLS-1$
    
    private static MediaTypeRegistry mediaTypeRegistry;
    
    public static MediaTypeRegistry getMediaTypeRegistry() {
        if(mediaTypeRegistry == null) {
            synchronized(MediaTypes.class) {
                if(mediaTypeRegistry == null) {
                    mediaTypeRegistry = TikaConfig.getDefaultConfig().getMediaTypeRegistry();
                }
            }
        }
        return mediaTypeRegistry;
    }
    
    public static MediaType getParentType(MediaType type) {
        MediaType parent = getMediaTypeRegistry().getSupertype(type);
        if(type != null && type.toString().contains(UFED_MIME_PREFIX) &&
                MediaType.OCTET_STREAM.equals(parent)) {
            parent = MediaTypes.METADATA_ENTRY;
            getMediaTypeRegistry().addSuperType(type, parent);
        }
        return parent;
    }

}
