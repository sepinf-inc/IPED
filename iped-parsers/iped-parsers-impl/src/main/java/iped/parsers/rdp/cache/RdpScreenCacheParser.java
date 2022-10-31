package iped.parsers.rdp.cache;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;

import javax.imageio.ImageIO;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.properties.BasicProps;

public class RdpScreenCacheParser extends AbstractParser {
    
    public static final MediaType REGISTRY_MIME = MediaType.application("x-rdp-cachebin");

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(REGISTRY_MIME);
    
    @Override
    public Set<MediaType> getSupportedTypes(ParseContext arg0) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        /* filtra os itens a serem parseados */
        String nome = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY).toUpperCase();
        try {
            File file = TikaInputStream.get(stream).getFile();

            DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(file),64*1024));
            byte[] header = new byte[0x0C];
            dis.read(header);

            EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                    new ParsingEmbeddedDocumentExtractor(context));

            byte[] key = new byte[8];
            int i = dis.read(key);
            int count=0;
            while(i==8) {
                short width, height;
                width = dis.readByte();
                width+=dis.readByte()*256;
                height = dis.readByte();
                height+=dis.readByte()*256;
                byte[] img = new byte[4*width*height];
                dis.read(img);
                BufferedImage bimg = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
                bimg.getWritableTile(0, 0).setDataElements(0, 0, width, height, img);
                File fout = new File("teste"+(count++)+".png");
                //ImageIO.write(bimg, "PNG", fout);

                Metadata imgMetadata = new Metadata();
                imgMetadata.add(TikaCoreProperties.RESOURCE_NAME_KEY, "CachedImage"+count+".png");
                imgMetadata.add(TikaCoreProperties.TITLE, "CachedImage"+count+".png");
                imgMetadata.add(BasicProps.CONTENTTYPE, "image/png");

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(bimg, "png", baos);
                if(extractor.shouldParseEmbedded(imgMetadata)) {
                    extractor.parseEmbedded(TikaInputStream.get(baos.toByteArray(), imgMetadata), handler, imgMetadata, true);
                }

                i = dis.read(key);
            }

        } catch (Exception e) {
            throw new TikaException("Erro ao decodificar arquivo de registro: " + nome, e);
        } finally {
        }
    }

}