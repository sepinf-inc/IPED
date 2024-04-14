package iped.parsers.sqlite;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Properties;
import java.util.Set;

import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.sqlite.SQLiteConfig;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * This is the main class for parsing SQLite3 files. When {@link #parse} is
 * called, this creates a new
 * {@link org.apache.iped.parsers.sqlite.SQLite3DBParser}.
 * <p>
 * Given potential conflicts of native libraries in web servers, users will need
 * to add org.xerial's sqlite-jdbc jar to the class path for this parser to
 * work. For development and testing, this jar is specified in tika-parsers'
 * pom.xml, but it is currently set to "provided."
 * <p>
 * Note that this family of jdbc parsers is designed to treat each CLOB and each
 * BLOB as embedded documents.
 *
 */
public class SQLite3Parser extends AbstractParser {
    /** Serial version UID */
    private static final long serialVersionUID = -752276948656079347L;

    public static final MediaType MEDIA_TYPE = MediaType.application("x-sqlite3"); //$NON-NLS-1$

    private final Set<MediaType> SUPPORTED_TYPES;

	private int tableRowsPerItem;

    /**
     * Checks to see if class is available for org.sqlite.JDBC.
     * <p>
     * If not, this class will return an EMPTY_SET for getSupportedTypes()
     */
    public SQLite3Parser() {
        Set<MediaType> tmp;
        try {
            Class.forName(SQLite3DBParser.SQLITE_CLASS_NAME);
            tmp = Collections.singleton(MEDIA_TYPE);
        } catch (ClassNotFoundException e) {
            tmp = Collections.EMPTY_SET;
        }
        SUPPORTED_TYPES = tmp;
    }
    
    @Field
    public void setTableRowsPerItem(int value) {
    	this.tableRowsPerItem = value;
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        SQLite3DBParser p = new SQLite3DBParser();
        p.setTableRowsPerItem(tableRowsPerItem);
        p.parse(stream, handler, metadata, context);
    }

    public static Properties getConnectionProperties() {
        Properties prop = new Properties();
        prop.setProperty(SQLiteConfig.Pragma.JOURNAL_MODE.pragmaName, SQLiteConfig.JournalMode.DELETE.name());
        // prop.setProperty(SQLiteConfig.Pragma.OPEN_MODE.pragmaName, "1");
        return prop;
    }
}
