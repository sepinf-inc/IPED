package iped3.util;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;

import iped3.io.IItemBase;

public class MediaTypes {

    public static final MediaType METADATA_ENTRY = MediaType.application("x-metadata-entry"); //$NON-NLS-1$
    public static final MediaType UFED_EMAIL_MIME = MediaType.parse("message/x-ufed-email"); //$NON-NLS-1$
    public static final MediaType UFED_MESSAGE_ATTACH_MIME = MediaType.parse("message/x-ufed-attachment"); //$NON-NLS-1$
    public static final MediaType CHAT_MESSAGE_MIME = MediaType.parse("message/x-chat-message"); //$NON-NLS-1$
    public static final MediaType UFED_MESSAGE_MIME = MediaType.application("x-ufed-instantmessage"); //$NON-NLS-1$
    public static final MediaType UFED_CALL_MIME = MediaType.application("x-ufed-call"); //$NON-NLS-1$
    public static final MediaType UFED_SMS_MIME = MediaType.application("x-ufed-sms"); //$NON-NLS-1$
    public static final MediaType UFED_MMS_MIME = MediaType.application("x-ufed-mms"); //$NON-NLS-1$
    public static final MediaType UFED_DEVICE_INFO = MediaType.application("x-ufed-deviceinfo"); //$NON-NLS-1$
    public static final MediaType UNALLOCATED = MediaType.application("x-unallocated"); //$NON-NLS-1$
    public static final MediaType JBIG2 = MediaType.image("x-jbig2");

    public static final String UFED_MIME_PREFIX = "x-ufed-"; //$NON-NLS-1$

    private static MediaTypeRegistry mediaTypeRegistry;

    public static MediaTypeRegistry getMediaTypeRegistry() {
        if (mediaTypeRegistry == null) {
            synchronized (MediaTypes.class) {
                if (mediaTypeRegistry == null) {
                    mediaTypeRegistry = TikaConfig.getDefaultConfig().getMediaTypeRegistry();
                }
            }
        }
        return mediaTypeRegistry;
    }

    public static MediaType normalize(MediaType type) {
        return getMediaTypeRegistry().normalize(type);
    }

    public static MediaType getParentType(MediaType type) {
        MediaType parent = getMediaTypeRegistry().getSupertype(type);
        if (type != null && type.toString().contains(UFED_MIME_PREFIX) && MediaType.OCTET_STREAM.equals(parent)) {
            parent = MediaTypes.METADATA_ENTRY;
            getMediaTypeRegistry().addSuperType(type, parent);
        }
        return parent;
    }

    public static boolean isInstanceOf(MediaType instance, MediaType parent) {
        return instance != null && (instance.equals(parent) || isInstanceOf(getParentType(instance), parent));
    }

    public static boolean isMetadataEntryType(MediaType type) {
        while (type != null && !type.equals(MediaType.OCTET_STREAM)) {
            if (MediaTypes.METADATA_ENTRY.equals(type)) {
                return true;
            }
            type = getParentType(type);
        }
        return false;
    }

    public static String getMimeTypeIfJBIG2(IItemBase item) {
        if (item.getMediaType() != null && item.getMediaType().equals(JBIG2)) {
            return JBIG2.toString();
        }
        return null;
    }

}
