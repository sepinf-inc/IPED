package dpf.mt.gpinf.registro.keyparsers;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.xml.sax.ContentHandler;

import dpf.mt.gpinf.registro.model.KeyNode;
import dpf.sp.gpinf.indexer.parsers.util.EmbeddedParent;

public interface RegistryKeyParser {
	
	public void parse(KeyNode kn, String title, boolean hasChildren, String keyPath, EmbeddedParent parent, ContentHandler handler, Metadata metadata, ParseContext context) throws TikaException;
	public void addValueDataDecoderFunction(String pattern, String valueName, String function);
	public void addValueNameDecoderFunction(String keyNamePattern, String function);
	public void decodeAllValueDataDecoderFunction(String pattern, String function);	

}
