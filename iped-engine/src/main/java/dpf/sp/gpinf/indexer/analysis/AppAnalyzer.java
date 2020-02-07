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
package dpf.sp.gpinf.indexer.analysis;

import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;

import dpf.sp.gpinf.indexer.Versao;
import dpf.sp.gpinf.indexer.config.AdvancedIPEDConfig;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.process.task.HashTask;
import dpf.sp.gpinf.indexer.process.task.PhotoDNATask;

/*
 * Define analizadores, tokenizadores implicitamente, de indexação específicos para cada propriedade, 
 */
public class AppAnalyzer {

    public static Analyzer get() {
        Map<String, Analyzer> analyzerPerField = new HashMap<String, Analyzer>();
        analyzerPerField.put(IndexItem.CATEGORY, new StandardASCIIAnalyzer(Versao.current, true));
        analyzerPerField.put(IndexItem.ID, new KeywordAnalyzer());
        analyzerPerField.put(IndexItem.FTKID, new KeywordAnalyzer());
        analyzerPerField.put(IndexItem.PARENTID, new KeywordAnalyzer());
        analyzerPerField.put(IndexItem.CREATED, new KeywordAnalyzer());
        analyzerPerField.put(IndexItem.MODIFIED, new KeywordAnalyzer());
        analyzerPerField.put(IndexItem.ACCESSED, new KeywordAnalyzer());
        analyzerPerField.put(IndexItem.EVIDENCE_UUID, new KeywordAnalyzer());
        
        StandardASCIIAnalyzer hashAnalyzer = new StandardASCIIAnalyzer(Versao.current, false);
        hashAnalyzer.setMaxTokenLength(Integer.MAX_VALUE);
        analyzerPerField.put(HashTask.HASH.MD5.toString(), hashAnalyzer);
        analyzerPerField.put(HashTask.HASH.EDONKEY.toString(), hashAnalyzer);
        analyzerPerField.put(HashTask.HASH.SHA1.toString(), hashAnalyzer);
        analyzerPerField.put(HashTask.HASH.SHA256.toString(), hashAnalyzer);
        analyzerPerField.put(HashTask.HASH.SHA512.toString(), hashAnalyzer);
        analyzerPerField.put(PhotoDNATask.PHOTO_DNA, hashAnalyzer);

        StandardASCIIAnalyzer defaultAnalyzer = new StandardASCIIAnalyzer(Versao.current, false);
        AdvancedIPEDConfig advancedConfig = (AdvancedIPEDConfig) ConfigurationManager.getInstance()
                .findObjects(AdvancedIPEDConfig.class).iterator().next();
        defaultAnalyzer.setMaxTokenLength(advancedConfig.getMaxTokenLength());
        defaultAnalyzer.setFilterNonLatinChars(advancedConfig.isFilterNonLatinChars());
        defaultAnalyzer.setConvertCharsToAscii(advancedConfig.isConvertCharsToAscii());
        return new PerFieldAnalyzerWrapper(defaultAnalyzer, analyzerPerField);
    }

}
