package dpf.mt.gpinf.registro;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaMetadataKeys;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.mt.gpinf.registro.keyparsers.RegistryKeyParser;
import dpf.mt.gpinf.registro.keyparsers.RegistryKeyParserManager;
import dpf.mt.gpinf.registro.model.KeyNode;
import dpf.mt.gpinf.registro.model.RegistryFile;
import dpf.sp.gpinf.indexer.parsers.util.EmbeddedItem;
import dpf.sp.gpinf.indexer.parsers.util.EmbeddedParent;
import dpf.sp.gpinf.indexer.parsers.util.ItemInfo;

public class RegistroParser extends AbstractParser {

	public static final MediaType REGISTRY_MIME = MediaType.application("x-windows-registry");

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(REGISTRY_MIME);
    
    HashMap<String, EmbeddedParent> parentMap = new HashMap<String, EmbeddedParent>();
    
    RegistryKeyParser defaultRegistryKeyParser = null;
	@Override
	public Set<MediaType> getSupportedTypes(ParseContext arg0) {
		return SUPPORTED_TYPES;
	}

	@Override
	public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
			throws IOException, SAXException, TikaException {
    	/* filtra os itens a serem parseados */
    	String nome = metadata.get(TikaMetadataKeys.RESOURCE_NAME_KEY).toUpperCase();
        try {
        	if(defaultRegistryKeyParser==null){
        		defaultRegistryKeyParser = RegistryKeyParserManager.getRegistryKeyParserManager().getDefaultRegistryKeyParser();
        	}
        	if(!(nome.equals("SYSTEM")||nome.equals("SOFTWARE")||nome.equals("SAM")||nome.equals("SECURITY")||nome.equals("NTUSER.DAT"))) return;
        	ItemInfo itemInfo = context.get(ItemInfo.class);
        	String caminho = itemInfo.getPath().toLowerCase().replace("\\", "/");
        	if(!(caminho.contains("system32/config")||caminho.contains("users")||caminho.contains("settings"))) return;
        	
        	File dbFile = TikaInputStream.get(stream).getFile();

        	RegistryFile rf = new RegistryFile(dbFile);

    		rf.load();

    		KeyNode kf = rf.findKeyNode("/");
    		
    		recursiveKeyParser(kf, "ROOT", "", handler, metadata, context);
        } catch(Exception e){
        	throw new TikaException("Erro ao decodificar arquivo de registro: "+nome, e);        	
        }finally{
        }
	}

	private void keyParser(KeyNode kn, boolean hasChildren, String keyPath, String parentPath, ContentHandler handler, Metadata metadata, ParseContext context) throws TikaException {
        RegistryKeyParser parser = RegistryKeyParserManager.getRegistryKeyParserManager().getRegistryKeyParser(keyPath);
        if(parser!=null){
            String title = keyPath.substring(parentPath.length()+1);
            EmbeddedParent parent = parentMap.get(parentPath);        
            parser.parse(kn, title, hasChildren, keyPath, parent, handler, metadata, context);
            parentMap.put(keyPath, context.get(EmbeddedItem.class));
        }else{
    		if(RegistryKeyParserManager.getRegistryKeyParserManager().hasChildRegistered(keyPath)){
                String title = keyPath.substring(parentPath.length()+1);
                EmbeddedParent parent = parentMap.get(parentPath);        
    			defaultRegistryKeyParser.parse(kn, title, hasChildren, keyPath, parent, handler, metadata, context);
                parentMap.put(keyPath, context.get(EmbeddedItem.class));
    		}
        }
	}

	private void recursiveKeyParser(KeyNode kn, String keyPath, String parentPath, ContentHandler handler, Metadata metadata, ParseContext context) throws TikaException {
		ArrayList<KeyNode> kns = kn.getSubKeys();

		keyParser(kn, ((kns!=null)&&(kns.size()>0)), keyPath, parentPath, handler, metadata, context);

		EmbeddedItem item = context.get(EmbeddedItem.class);

		if(RegistryKeyParserManager.getRegistryKeyParserManager().hasChildRegistered(keyPath)){
			if(kns!=null) {
				for (int i = 0; i < kns.size(); i++) {
					recursiveKeyParser(kns.get(i), keyPath+"/"+kns.get(i).getKeyName(), keyPath, handler, metadata, context);
				}		
			}
		}
	}

	
}