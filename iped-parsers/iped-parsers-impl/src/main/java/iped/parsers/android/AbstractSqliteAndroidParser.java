package iped.parsers.android;

import org.apache.tika.config.Field;

import iped.parsers.sqlite.SQLite3DBParser;

public abstract class AbstractSqliteAndroidParser extends SQLite3DBParser {

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
