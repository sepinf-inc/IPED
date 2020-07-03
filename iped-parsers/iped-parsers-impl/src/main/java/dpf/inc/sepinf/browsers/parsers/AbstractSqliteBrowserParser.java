package dpf.inc.sepinf.browsers.parsers;

import org.apache.tika.config.Field;

import dpf.sp.gpinf.indexer.parsers.jdbc.SQLite3DBParser;

public abstract class AbstractSqliteBrowserParser extends SQLite3DBParser{
    
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private static final String SQLITE_CLASS_NAME = "org.sqlite.JDBC"; //$NON-NLS-1$
    
    protected boolean extractEntries = true;
    
    @Field
    public void setExtractEntries(boolean extractEntries) {
        this.extractEntries = extractEntries;
    }

}
