package dpf.sp.gpinf.indexer.util;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;

import javax.imageio.ImageIO;

import com.drew.imaging.jpeg.JpegMetadataReader;
import com.drew.imaging.jpeg.JpegSegmentMetadataReader;
import com.drew.imaging.jpeg.JpegSegmentType;
import com.drew.lang.annotations.NotNull;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifReader;
import com.drew.metadata.exif.ExifThumbnailDirectory;
import com.drew.metadata.jpeg.JpegDirectory;

public class ImageMetadataUtil {
    private static int TAG_THUMBNAIL_DATA = 0x10000;
    private static boolean exifReaderUpdated = false;

    static {
        updateExifReaderToLoadThumbData();
    }

    public static final synchronized void updateExifReaderToLoadThumbData() {
        if (exifReaderUpdated) {
            return;
        }
        exifReaderUpdated = true;
        List<JpegSegmentMetadataReader> allReaders = (List<JpegSegmentMetadataReader>) JpegMetadataReader.ALL_READERS;
        for (int n = 0, cnt = allReaders.size(); n < cnt; n++) {
            if (allReaders.get(n).getClass() != ExifReader.class) {
                continue;
            }

            allReaders.set(n, new ExifReader() {
                @Override
                public void readJpegSegments(@NotNull final Iterable<byte[]> segments, @NotNull final Metadata metadata,
                        @NotNull final JpegSegmentType segmentType) {
                    super.readJpegSegments(segments, metadata, segmentType);

                    for (byte[] segmentBytes : segments) {
                        // Filter any segments containing unexpected preambles
                        if (!startsWithJpegExifPreamble(segmentBytes)) {
                            continue;
                        }

                        // Extract the thumbnail
                        try {
                            ExifThumbnailDirectory tnDirectory = metadata
                                    .getFirstDirectoryOfType(ExifThumbnailDirectory.class);
                            if (tnDirectory != null
                                    && tnDirectory.containsTag(ExifThumbnailDirectory.TAG_THUMBNAIL_OFFSET)) {
                                int offset = tnDirectory.getInt(ExifThumbnailDirectory.TAG_THUMBNAIL_OFFSET);
                                int length = tnDirectory.getInt(ExifThumbnailDirectory.TAG_THUMBNAIL_LENGTH);

                                if (JPEG_SEGMENT_PREAMBLE.length() + offset + length > segmentBytes.length) {
                                    length = segmentBytes.length - (JPEG_SEGMENT_PREAMBLE.length() + offset);
                                }
                                if (length > 0 && length < 1 << 24) {
                                    byte[] tnData = new byte[length];
                                    System.arraycopy(segmentBytes, JPEG_SEGMENT_PREAMBLE.length() + offset, tnData, 0,
                                            length);
                                    tnDirectory.setObject(TAG_THUMBNAIL_DATA, tnData);
                                }
                            }
                        } catch (MetadataException e) {
                            // ignore
                        }
                    }
                }
            });
            break;
        }
    }

    public static BufferedImage getThumb(InputStream stream) {
        try {
            Metadata metadata = JpegMetadataReader.readMetadata(stream);
            if (metadata != null) {
                ExifThumbnailDirectory dir = metadata.getFirstDirectoryOfType(ExifThumbnailDirectory.class);
                if (dir != null) {
                    byte[] imgBytes = (byte[]) dir.getObject(TAG_THUMBNAIL_DATA);
                    if (imgBytes == null)
                        return null;
                    BufferedImage img = ImageIO.read(new ByteArrayInputStream(imgBytes));
                    try {
                        JpegDirectory dj = metadata.getFirstDirectoryOfType(JpegDirectory.class);
                        if (dj != null) {
                            int iw = dj.getImageWidth();
                            int ih = dj.getImageHeight();
                            int tw = img.getWidth();
                            int th = img.getHeight();
                            if ((tw > th && iw < ih) || (tw < th && iw > ih)) {
                                // Orientacao (retrato/paisagem) da miniatura EXIF inconsistente com a imagem.
                                // Melhor retornar e utilizar a geracao "normal", a partir da imagem.
                                return null;
                            }
                            int x = 0;
                            int y = 0;
                            while (iw * th > ih * tw && th > 20) {
                                y++;
                                th -= 2;
                            }
                            while (iw * th < ih * tw && tw > 20) {
                                x++;
                                tw -= 2;
                            }
                            if (x > 0 || y > 0) {
                                img = img.getSubimage(x, y, tw, th);
                            }
                        }

                    } catch (Exception e) {
                    }

                    return img;
                }
            }
        } catch (Exception e) {
            // e.printStackTrace();
        }
        return null;
    }

    public static int getOrientation(InputStream imageFile) {
        try {
            Metadata metadata = JpegMetadataReader.readMetadata(imageFile);
            if (metadata != null) {
                Collection<ExifIFD0Directory> dirs = metadata.getDirectoriesOfType(ExifIFD0Directory.class);
                if (dirs != null) {
                    for (ExifIFD0Directory dir : dirs) {
                        Integer tagOrientation = dir.getInteger(ExifIFD0Directory.TAG_ORIENTATION);
                        if (tagOrientation != null)
                            return tagOrientation;
                    }
                }
            }
        } catch (Exception e) {
        }
        return -1;
    }
}
