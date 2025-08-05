/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de Evidências Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package iped.engine.lucene.analysis;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;

import iped.engine.config.ConfigurationManager;
import iped.engine.config.IndexTaskConfig;
import iped.engine.task.HashTask;
import iped.engine.task.PhotoDNATask;
import iped.engine.task.index.IndexItem;
import iped.localization.LocalizedProperties;
import iped.properties.ExtraProperties;

/*
 * Define analizadores, tokenizadores implicitamente, de indexação específicos para cada propriedade, 
 */
public class AppAnalyzer {

    public static Analyzer get() {
        Map<String, Analyzer> analyzerPerField = new HashMap<String, Analyzer>();
        analyzerPerField.put(IndexItem.CATEGORY, new StandardASCIIAnalyzer());
        analyzerPerField.put(LocalizedProperties.getLocalizedField(IndexItem.CATEGORY),
                new StandardASCIIAnalyzer());
        analyzerPerField.put(IndexItem.ID, new KeywordAnalyzer());
        analyzerPerField.put(IndexItem.PARENTID, new KeywordAnalyzer());
        analyzerPerField.put(IndexItem.EVIDENCE_UUID, new KeywordAnalyzer());
        analyzerPerField.put(ExtraProperties.UFED_ID, new KeywordAnalyzer());
        analyzerPerField.put(ExtraProperties.UFED_FILE_ID, new KeywordAnalyzer());
        analyzerPerField.put(ExtraProperties.UFED_JUMP_TARGETS, new KeywordAnalyzer());
        analyzerPerField.put(ExtraProperties.UFED_COORDINATE_ID, new KeywordAnalyzer());

        analyzerPerField.put(IndexItem.CREATED, new KeywordAnalyzer());
        analyzerPerField.put(IndexItem.MODIFIED, new KeywordAnalyzer());
        analyzerPerField.put(IndexItem.ACCESSED, new KeywordAnalyzer());
        analyzerPerField.put(IndexItem.CHANGED, new KeywordAnalyzer());
        analyzerPerField.put(IndexItem.TIMESTAMP, new KeywordAnalyzer());

        IndexTaskConfig indexConfig = ConfigurationManager.get().findObject(IndexTaskConfig.class);
        StandardASCIIAnalyzer hashAnalyzer = new StandardASCIIAnalyzer();
        hashAnalyzer.setMaxTokenLength(Integer.MAX_VALUE);
        hashAnalyzer.setConvertCharsToLower(true);
        analyzerPerField.put(HashTask.HASH.MD5.toString(), hashAnalyzer);
        analyzerPerField.put(HashTask.HASH.EDONKEY.toString(), hashAnalyzer);
        analyzerPerField.put(HashTask.HASH.SHA1.toString(), hashAnalyzer);
        analyzerPerField.put(HashTask.HASH.SHA256.toString(), hashAnalyzer);
        analyzerPerField.put(HashTask.HASH.SHA512.toString(), hashAnalyzer);
        analyzerPerField.put(PhotoDNATask.PHOTO_DNA, hashAnalyzer);

        StandardASCIIAnalyzer defaultAnalyzer = new StandardASCIIAnalyzer();
        defaultAnalyzer.setMaxTokenLength(indexConfig.getMaxTokenLength());
        defaultAnalyzer.setFilterNonLatinChars(indexConfig.isFilterNonLatinChars());
        defaultAnalyzer.setConvertCharsToAscii(indexConfig.isConvertCharsToAscii());
        defaultAnalyzer.setConvertCharsToLower(indexConfig.isConvertCharsToLowerCase());
        defaultAnalyzer.setExtraCharsToIndex(indexConfig.getExtraCharsToIndex());

        return new NonFinalPerFieldAnalyzerWrapper(defaultAnalyzer, analyzerPerField) {
            protected Analyzer getWrappedAnalyzer(String fieldName) {
                if (fieldName != null) {
                    // Use actual (non localized) field names to check if it is a date (See #2175).
                    fieldName = LocalizedProperties.getNonLocalizedField(fieldName);
                    if (Date.class.equals(IndexItem.getMetadataTypes().get(fieldName))) {
                        return new KeywordAnalyzer();
                    }
                }
                return super.getWrappedAnalyzer(fieldName);
            }
        };
    }

}
