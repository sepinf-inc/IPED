package org.apache.tika.fork;

import java.io.DataInputStream;
import java.io.DataOutputStream;

import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.html.HtmlMapper;
import org.apache.tika.parser.html.IdentityHtmlMapper;

public class ParseContextProxy extends ParseContext implements ForkProxy{

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    
    private final ParseContext context;
    private transient DataInputStream input;
    private transient DataOutputStream output;
    
    public ParseContextProxy() {
        this.context = new ParseContext();
    }
    
    public ParseContextProxy(ParseContext context) {
        this.context = context;
    }

    @Override
    public void init(DataInputStream input, DataOutputStream output) {
        this.input = input;
        this.output = output;
        
        // Tratamento p/ acentos de subitens de ZIP
        context.set(ArchiveStreamFactory.class,  new ArchiveStreamFactory("Cp850")); //$NON-NLS-1$
        // Indexa conteudo de todos os elementos de HTMLs, como script, etc
        context.set(HtmlMapper.class, IdentityHtmlMapper.INSTANCE);
    }
    
    public <T> void set(Class<T> key, T value) {
        context.set(key, value);
    }

    public <T> T get(Class<T> key) {
        T obj = context.get(key);
        if(obj instanceof ForkProxy) {
            ((ForkProxy)obj).init(input, output);
        }
        return obj;
    }

}
