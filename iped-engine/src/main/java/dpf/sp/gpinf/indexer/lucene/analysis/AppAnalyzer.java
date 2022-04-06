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
package dpf.sp.gpinf.indexer.lucene.analysis;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;

import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.config.IndexTaskConfig;
import dpf.sp.gpinf.indexer.datasource.UfedXmlReader;
import dpf.sp.gpinf.indexer.localization.LocalizedProperties;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.process.task.HashTask;
import dpf.sp.gpinf.indexer.process.task.PhotoDNATask;

/*
 * Define analizadores, tokenizadores implicitamente, de indexação específicos para cada propriedade, 
 */
public class AppAnalyzer {

    public static Analyzer get() {
        Map<String, Analyzer> analyzerPerField = new HashMap<String, Analyzer>();
        analyzerPerField.put(IndexItem.CATEGORY, new StandardASCIIAnalyzer(true));
        analyzerPerField.put(LocalizedProperties.getLocalizedField(IndexItem.CATEGORY),
                new StandardASCIIAnalyzer(true));
        analyzerPerField.put(IndexItem.ID, new KeywordAnalyzer());
        analyzerPerField.put(IndexItem.PARENTID, new KeywordAnalyzer());
        analyzerPerField.put(IndexItem.EVIDENCE_UUID, new KeywordAnalyzer());
        analyzerPerField.put(UfedXmlReader.UFED_ID, new KeywordAnalyzer());

        analyzerPerField.put(IndexItem.CREATED, new KeywordLowerCaseAnalyzer());
        analyzerPerField.put(IndexItem.MODIFIED, new KeywordLowerCaseAnalyzer());
        analyzerPerField.put(IndexItem.ACCESSED, new KeywordLowerCaseAnalyzer());
        analyzerPerField.put(IndexItem.CHANGED, new KeywordLowerCaseAnalyzer());
        analyzerPerField.put(IndexItem.TIMESTAMP, new KeywordLowerCaseAnalyzer());

        IndexTaskConfig indexConfig = ConfigurationManager.get().findObject(IndexTaskConfig.class);
        StandardASCIIAnalyzer hashAnalyzer = new StandardASCIIAnalyzer(false);
        hashAnalyzer.setMaxTokenLength(Integer.MAX_VALUE);
        hashAnalyzer.setConvertCharsToLower(true);
        analyzerPerField.put(HashTask.HASH.MD5.toString(), hashAnalyzer);
        analyzerPerField.put(HashTask.HASH.EDONKEY.toString(), hashAnalyzer);
        analyzerPerField.put(HashTask.HASH.SHA1.toString(), hashAnalyzer);
        analyzerPerField.put(HashTask.HASH.SHA256.toString(), hashAnalyzer);
        analyzerPerField.put(HashTask.HASH.SHA512.toString(), hashAnalyzer);
        analyzerPerField.put(PhotoDNATask.PHOTO_DNA, hashAnalyzer);

        StandardASCIIAnalyzer defaultAnalyzer = new StandardASCIIAnalyzer(false);
        defaultAnalyzer.setMaxTokenLength(indexConfig.getMaxTokenLength());
        defaultAnalyzer.setFilterNonLatinChars(indexConfig.isFilterNonLatinChars());
        defaultAnalyzer.setConvertCharsToAscii(indexConfig.isConvertCharsToAscii());
        defaultAnalyzer.setConvertCharsToLower(indexConfig.isConvertCharsToLowerCase());
        defaultAnalyzer.setExtraCharsToIndex(indexConfig.getExtraCharsToIndex());

        return new NonFinalPerFieldAnalyzerWrapper(defaultAnalyzer, analyzerPerField) {
            protected Analyzer getWrappedAnalyzer(String fieldName) {
                if (Date.class.equals(IndexItem.getMetadataTypes().get(fieldName))) {
                    return new KeywordLowerCaseAnalyzer();
                }
                return super.getWrappedAnalyzer(fieldName);
            }
        };
    }

}
