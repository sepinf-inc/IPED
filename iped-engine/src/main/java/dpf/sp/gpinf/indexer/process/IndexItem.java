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
package dpf.sp.gpinf.indexer.process;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoubleDocValuesField;
import org.apache.lucene.document.DoubleField;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.FloatDocValuesField;
import org.apache.lucene.document.FloatField;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.NumericUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.utils.DateUtils;
import org.sleuthkit.datamodel.SleuthkitCase;

import dpf.sp.gpinf.indexer.analysis.FastASCIIFoldingFilter;
import dpf.sp.gpinf.indexer.config.AdvancedIPEDConfig;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.parsers.OCRParser;
import dpf.sp.gpinf.indexer.parsers.util.MetadataUtil;
import dpf.sp.gpinf.indexer.process.task.ImageThumbTask;
import dpf.sp.gpinf.indexer.util.DateUtil;
import dpf.sp.gpinf.indexer.util.SeekableInputStreamFactory;
import dpf.sp.gpinf.indexer.util.SelectImagePathWithDialog;
import dpf.sp.gpinf.indexer.util.UTF8Properties;
import dpf.sp.gpinf.indexer.util.Util;
import gpinf.dev.data.DataSource;
import gpinf.dev.data.Item;
import gpinf.dev.filetypes.GenericFileType;
import iped3.IItem;
import iped3.IEvidenceFileType;
import iped3.datasource.IDataSource;
import iped3.sleuthkit.ISleuthKitItem;
import iped3.util.BasicProps;
import iped3.util.ExtraProperties;

/**
 * Cria um org.apache.lucene.document.Document a partir das propriedades do
 * itens que será adicionado ao índice.
 */
public class IndexItem extends BasicProps {

    public static final String FTKID = "ftkId"; //$NON-NLS-1$
    public static final String SLEUTHID = "sleuthId"; //$NON-NLS-1$

    public static final String ID_IN_SOURCE = "idInDataSource"; //$NON-NLS-1$
    public static final String SOURCE_PATH = "dataSourcePath"; //$NON-NLS-1$
    public static final String SOURCE_DECODER = "dataSourceDecoder"; //$NON-NLS-1$

    public static final String attrTypesFilename = "metadataTypes.txt"; //$NON-NLS-1$

    private static final int MAX_DOCVALUE_SIZE = 4096;

    static HashSet<String> ignoredMetadata = new HashSet<String>();

    private static volatile boolean guessMetaTypes = false;

    private static Map<Path, SeekableInputStreamFactory> inputStreamFactories = new ConcurrentHashMap<>();
    private static Map<File, File> localEvidenceMap = new ConcurrentHashMap<>();

    private static class StringComparator implements Comparator<String> {
        @Override
        public int compare(String o1, String o2) {
            return o1.compareToIgnoreCase(o2);
        }
    }

    private static Map<String, Class> typesMap = Collections
            .synchronizedMap(new TreeMap<String, Class>(new StringComparator()));
    private static Map<String, Class> newtypesMap = new ConcurrentHashMap<String, Class>();

    private static FieldType contentField;
    private static FieldType storedTokenizedNoNormsField = new FieldType();

    static {
        storedTokenizedNoNormsField.setIndexed(true);
        storedTokenizedNoNormsField.setOmitNorms(true);
        storedTokenizedNoNormsField.setStored(true);

        ignoredMetadata.add(Metadata.CONTENT_TYPE);
        ignoredMetadata.add(Metadata.CONTENT_LENGTH);
        ignoredMetadata.add(Metadata.RESOURCE_NAME_KEY);
        ignoredMetadata.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE);
        ignoredMetadata.add(IndexerDefaultParser.INDEXER_TIMEOUT);
        ignoredMetadata.add(TikaCoreProperties.CONTENT_TYPE_HINT.getName());
        ignoredMetadata.add("File Name"); //$NON-NLS-1$
        ignoredMetadata.add("File Size"); //$NON-NLS-1$
        // ocrCharCount is already copied to an extra attribute
        ignoredMetadata.add(OCRParser.OCR_CHAR_COUNT);

        BasicProps.SET.add(FTKID);
        BasicProps.SET.add(SLEUTHID);
    }

    private static final FieldType getContentField() {
        if (contentField == null) {
            FieldType field = new FieldType();
            field.setIndexed(true);
            field.setOmitNorms(true);
            AdvancedIPEDConfig advancedConfig = (AdvancedIPEDConfig) ConfigurationManager.getInstance()
                    .findObjects(AdvancedIPEDConfig.class).iterator().next();
            field.setStoreTermVectors(advancedConfig.isStoreTermVectors());
            contentField = field;
        }
        return contentField;
    }

    public static Map<String, Class> getMetadataTypes() {
        return Collections.unmodifiableMap(typesMap);
    }

    public static void saveMetadataTypes(File confDir) throws IOException {
        File metadataTypesFile = new File(confDir, attrTypesFilename);
        UTF8Properties props = new UTF8Properties();
        for (Entry<String, Class> e : typesMap.entrySet()) {
            props.setProperty(e.getKey(), e.getValue().getCanonicalName());
        }
        props.store(metadataTypesFile);
    }

    public static void loadMetadataTypes(File confDir) throws IOException, ClassNotFoundException {
        if(typesMap.size() > 0)
            return;
        File metadataTypesFile = new File(confDir, attrTypesFilename);
        if (metadataTypesFile.exists()) {
            UTF8Properties props = new UTF8Properties();
            props.load(metadataTypesFile);
            for (String key : props.stringPropertyNames()) {
                typesMap.put(key, Class.forName(props.getProperty(key)));
            }
        }
    }
    
    private static final String normalize(String value) {
        return normalize(value, true);
    }

    private static final String normalize(String value, boolean toLowerCase) {
        if (toLowerCase)
            value = value.toLowerCase();
        char[] input = value.toCharArray();
        char[] output = new char[input.length];
        FastASCIIFoldingFilter.foldToASCII(input, 0, output, 0, input.length);
        return new String(output).trim();
    }

    public static Document Document(IItem evidence, Reader reader, File output) {
        Document doc = new Document();

        doc.add(new IntField(ID, evidence.getId(), Field.Store.YES));
        doc.add(new NumericDocValuesField(ID, evidence.getId()));

        doc.add(new StringField(EVIDENCE_UUID, evidence.getDataSource().getUUID(), Field.Store.YES));
        doc.add(new SortedDocValuesField(EVIDENCE_UUID, new BytesRef(evidence.getDataSource().getUUID())));

        Integer intVal = evidence.getFtkID();
        if (intVal != null) {
            doc.add(new IntField(FTKID, intVal, Field.Store.YES));
            doc.add(new NumericDocValuesField(FTKID, intVal));
        }

        if (evidence instanceof ISleuthKitItem) {
            ISleuthKitItem sevidence = (ISleuthKitItem) evidence;
            intVal = sevidence.getSleuthId();
            if (intVal != null) {
                doc.add(new IntField(SLEUTHID, intVal, Field.Store.YES));
                doc.add(new NumericDocValuesField(SLEUTHID, intVal));
            }
        }

        String value = evidence.getIdInDataSource();
        if (value != null) {
            doc.add(new StringField(ID_IN_SOURCE, value, Field.Store.YES));
            doc.add(new SortedDocValuesField(ID_IN_SOURCE, new BytesRef(value)));

            Path srcPath = evidence.getInputStreamFactory().getDataSourcePath();
            value = Util.getRelativePath(output, srcPath.toFile());
            doc.add(new StringField(SOURCE_PATH, value, Field.Store.YES));
            doc.add(new SortedDocValuesField(SOURCE_PATH, new BytesRef(value)));

            value = evidence.getInputStreamFactory().getClass().getName();
            doc.add(new StringField(SOURCE_DECODER, value, Field.Store.YES));
            doc.add(new SortedDocValuesField(SOURCE_DECODER, new BytesRef(value)));
        }

        intVal = evidence.getParentId();
        if (intVal != null) {
            doc.add(new IntField(PARENTID, intVal, Field.Store.YES));
            doc.add(new NumericDocValuesField(PARENTID, intVal));
        }

        doc.add(new Field(PARENTIDs, evidence.getParentIdsString(), storedTokenizedNoNormsField));
        doc.add(new SortedDocValuesField(PARENTIDs, new BytesRef(evidence.getParentIdsString())));

        value = evidence.getName();
        if (value == null) {
            value = ""; //$NON-NLS-1$
        }
        Field nameField = new TextField(NAME, value, Field.Store.YES);
        nameField.setBoost(1000.0f);
        doc.add(nameField);
        doc.add(new SortedDocValuesField(NAME, new BytesRef(normalize(value))));

        IEvidenceFileType fileType = evidence.getType();
        if (fileType != null) {
            value = fileType.getLongDescr();
        } else {
            value = ""; //$NON-NLS-1$
        }
        doc.add(new Field(TYPE, value, storedTokenizedNoNormsField));
        doc.add(new SortedDocValuesField(TYPE, new BytesRef(normalize(value))));

        Long length = evidence.getLength();
        if (length != null) {
            doc.add(new LongField(LENGTH, length, Field.Store.YES));
            doc.add(new NumericDocValuesField(LENGTH, length));
        }

        Date date = evidence.getCreationDate();
        if (date != null) {
            value = DateUtil.dateToString(date);
        } else {
            value = ""; //$NON-NLS-1$
        }
        doc.add(new StringField(CREATED, value, Field.Store.YES));
        doc.add(new SortedDocValuesField(CREATED, new BytesRef(value)));

        date = evidence.getAccessDate();
        if (date != null) {
            value = DateUtil.dateToString(date);
        } else {
            value = ""; //$NON-NLS-1$
        }
        doc.add(new StringField(ACCESSED, value, Field.Store.YES));
        doc.add(new SortedDocValuesField(ACCESSED, new BytesRef(value)));

        date = evidence.getModDate();
        if (date != null) {
            value = DateUtil.dateToString(date);
        } else {
            value = ""; //$NON-NLS-1$
        }
        doc.add(new StringField(MODIFIED, value, Field.Store.YES));
        doc.add(new SortedDocValuesField(MODIFIED, new BytesRef(value)));

        date = evidence.getRecordDate();
        if (date != null) {
            value = DateUtil.dateToString(date);
        } else {
            value = ""; //$NON-NLS-1$
        }
        doc.add(new StringField(RECORDDATE, value, Field.Store.YES));
        doc.add(new SortedDocValuesField(RECORDDATE, new BytesRef(value)));

        value = evidence.getPath();
        if (value == null) {
            value = ""; //$NON-NLS-1$
        }
        doc.add(new Field(PATH, value, storedTokenizedNoNormsField));
        if (value.length() > MAX_DOCVALUE_SIZE) {
            value = value.substring(0, MAX_DOCVALUE_SIZE);
        }
        doc.add(new SortedDocValuesField(PATH, new BytesRef(normalize(value))));

        doc.add(new Field(EXPORT, evidence.getFileToIndex(), storedTokenizedNoNormsField));
        doc.add(new SortedDocValuesField(EXPORT, new BytesRef(evidence.getFileToIndex())));

        for (String val : evidence.getCategorySet()) {
            doc.add(new Field(CATEGORY, val, storedTokenizedNoNormsField));
            doc.add(new SortedSetDocValuesField(CATEGORY, new BytesRef(normalize(val, false))));
        }

        MediaType type = evidence.getMediaType();
        if (type != null) {
            value = type.toString();
        } else {
            value = ""; //$NON-NLS-1$
        }
        doc.add(new Field(CONTENTTYPE, value, storedTokenizedNoNormsField));
        doc.add(new SortedDocValuesField(CONTENTTYPE, new BytesRef(value)));

        if (evidence.isTimedOut()) {
            doc.add(new StringField(TIMEOUT, Boolean.TRUE.toString(), Field.Store.YES));
            doc.add(new SortedDocValuesField(TIMEOUT, new BytesRef(Boolean.TRUE.toString())));
        }

        value = evidence.getHash();
        if (value != null) {
            doc.add(new Field(HASH, value, storedTokenizedNoNormsField));
            doc.add(new SortedDocValuesField(HASH, new BytesRef(value)));
        }

        value = Boolean.toString(evidence.isDuplicate());
        doc.add(new StringField(DUPLICATE, value, Field.Store.YES));
        doc.add(new SortedDocValuesField(DUPLICATE, new BytesRef(value)));

        value = Boolean.toString(evidence.isDeleted());
        doc.add(new StringField(DELETED, value, Field.Store.YES));
        doc.add(new SortedDocValuesField(DELETED, new BytesRef(value)));

        value = Boolean.toString(evidence.hasChildren());
        doc.add(new StringField(HASCHILD, value, Field.Store.YES));
        doc.add(new SortedDocValuesField(HASCHILD, new BytesRef(value)));

        value = Boolean.toString(evidence.isDir());
        doc.add(new StringField(ISDIR, value, Field.Store.YES));
        doc.add(new SortedDocValuesField(ISDIR, new BytesRef(value)));

        if (evidence.isRoot()) {
            doc.add(new StringField(ISROOT, Boolean.TRUE.toString(), Field.Store.YES));
            doc.add(new SortedDocValuesField(ISROOT, new BytesRef(Boolean.TRUE.toString())));
        }

        value = Boolean.toString(evidence.isCarved());
        doc.add(new StringField(CARVED, value, Field.Store.YES));
        doc.add(new SortedDocValuesField(CARVED, new BytesRef(value)));

        value = Boolean.toString(evidence.isSubItem());
        doc.add(new StringField(SUBITEM, value, Field.Store.YES));
        doc.add(new SortedDocValuesField(SUBITEM, new BytesRef(value)));

        if (evidence.getThumb() != null)
            doc.add(new StoredField(THUMB, evidence.getThumb()));

        long off = evidence.getFileOffset();
        if (off != -1) {
            doc.add(new StoredField(OFFSET, Long.toString(off)));
        }

        if (reader != null) {
            doc.add(new Field(CONTENT, reader, getContentField()));
        }

        if (typesMap.size() == 0) {
            guessMetaTypes = true;
        }

        for (Entry<String, Object> entry : evidence.getExtraAttributeMap().entrySet()) {
            if (entry.getValue() instanceof List) {
                for (Object val : (List) entry.getValue()) {
                    if (!typesMap.containsKey(entry.getKey()))
                        typesMap.put(entry.getKey(), val.getClass());
                    addExtraAttributeToDoc(doc, entry.getKey(), val, false, true);
                }
            } else {
                if (!typesMap.containsKey(entry.getKey()))
                    typesMap.put(entry.getKey(), entry.getValue().getClass());
                addExtraAttributeToDoc(doc, entry.getKey(), entry.getValue(), false, false);
            }
        }

        // TRIAGE comentar
        Metadata metadata = evidence.getMetadata();
        if (metadata != null) {
            if (guessMetaTypes) {
                guessMetadataTypes(evidence.getMetadata());
            } else {
                addMetadataToDoc(doc, evidence.getMetadata());
            }
        }

        return doc;
    }

    private static void addExtraAttributeToDoc(Document doc, String key, Object oValue, boolean isMetadataKey,
            boolean isMultiValued) {
        boolean isString = false;

        /*
         * utilizar docvalue de outro tipo com mesmo nome provoca erro, entao usamos um
         * prefixo no nome para diferenciar
         */
        String keyPrefix = ""; //$NON-NLS-1$
        if (isMetadataKey) {
            keyPrefix = "_num_"; //$NON-NLS-1$
        }
        if (oValue instanceof Date) {
            String value = DateUtils.formatDate((Date)oValue);
            //query parser converts range queries to lowercase
            doc.add(new StringField(key, value.toLowerCase(), Field.Store.YES));
            if (!isMultiValued)
                doc.add(new SortedDocValuesField(key, new BytesRef(value)));
            else
                doc.add(new SortedSetDocValuesField(key, new BytesRef(value)));

        } else if (oValue instanceof Byte) {
            doc.add(new IntField(key, (Byte) oValue, Field.Store.YES));
            if (!isMultiValued)
                doc.add(new NumericDocValuesField(key, (Byte) oValue));
            else
                doc.add(new SortedNumericDocValuesField(key, (Byte) oValue));

        } else if (oValue instanceof Integer) {
            doc.add(new IntField(key, (Integer) oValue, Field.Store.YES));
            if (!isMultiValued)
                doc.add(new NumericDocValuesField(key, (Integer) oValue));
            else
                doc.add(new SortedNumericDocValuesField(key, (Integer) oValue));

        } else if (oValue instanceof Long) {
            doc.add(new LongField(key, (Long) oValue, Field.Store.YES));
            if (!isMultiValued)
                doc.add(new NumericDocValuesField(key, (Long) oValue));
            else
                doc.add(new SortedNumericDocValuesField(key, (Long) oValue));

        } else if (oValue instanceof Float) {
            doc.add(new FloatField(key, (Float) oValue, Field.Store.YES));
            if (!isMultiValued)
                doc.add(new FloatDocValuesField(key, (Float) oValue));
            else
                doc.add(new SortedNumericDocValuesField(key, NumericUtils.floatToSortableInt((Float) oValue)));

        } else if (oValue instanceof Double) {
            doc.add(new DoubleField(key, (Double) oValue, Field.Store.YES));
            if (!isMultiValued)
                doc.add(new DoubleDocValuesField(keyPrefix + key, (Double) oValue));
            else
                doc.add(new SortedNumericDocValuesField(keyPrefix + key,
                        NumericUtils.doubleToSortableLong((Double) oValue)));
        } else {
            isString = true;
        }

        if (isString) {
            doc.add(new Field(key, oValue.toString(), storedTokenizedNoNormsField));
        }

        if (isMetadataKey || isString) {
            String value = oValue.toString();
            if (value.length() > MAX_DOCVALUE_SIZE) {
                value = value.substring(0, MAX_DOCVALUE_SIZE);
            }
            if (isMetadataKey) {
                keyPrefix = "_"; //$NON-NLS-1$
            }
            if (!isMultiValued)
                doc.add(new SortedDocValuesField(keyPrefix + key, new BytesRef(normalize(value))));
            else
                doc.add(new SortedSetDocValuesField(keyPrefix + key, new BytesRef(normalize(value))));
        }

    }

    private static void addMetadataToDoc(Document doc, Metadata metadata) {
        MediaType mimetype = MediaType.parse(metadata.get(Metadata.CONTENT_TYPE));
        if (mimetype != null)
            mimetype = mimetype.getBaseType();

        // previne mto raro ConcurrentModificationException no caso de
        // thread desconectada por timeout que altere os metadados
        String[] names = null;
        while (names == null) {
            try {
                names = metadata.names();
            } catch (ConcurrentModificationException e) {
            }
        }

        for (String key : names) {
            if (key == null || key.contains("Unknown tag") || ignoredMetadata.contains(key)) { //$NON-NLS-1$
                continue;
            }
            boolean isMultiValued = true;// metadata.getValues(key).length > 1;
            for (String val : metadata.getValues(key)) {
                if (val != null && !(val = val.trim()).isEmpty())
                    addMetadataKeyToDoc(doc, key, val, isMultiValued, mimetype);
            }

        }
    }

    private static void addMetadataKeyToDoc(Document doc, String key, String value, boolean isMultiValued,
            MediaType mimetype) {
        Object oValue = value;
        Class type = typesMap.get(key);

        if (type == null && MetadataUtil.isHtmlMediaType(mimetype) && !key.startsWith(ExtraProperties.UFED_META_PREFIX))
            return;

        if (type == null || !type.equals(String.class)) {

            try {
                if (type == null || type.equals(Double.class)) {
                    oValue = Double.valueOf(value);
                    if (type == null) {
                        newtypesMap.put(key, Double.class);
                        typesMap.put(key, Double.class);
                    }
                } else if (type.equals(Integer.class)) {
                    oValue = Integer.valueOf(value);
                } else if (type.equals(Float.class)) {
                    oValue = Float.valueOf(value);
                } else if (type.equals(Long.class)) {
                    oValue = Long.valueOf(value);
                }

            } catch (NumberFormatException e) {
                if (newtypesMap.containsKey(key)) {
                    typesMap.put(key, String.class);
                }
            }
        }

        Date date = DateUtil.tryToParseDate(value);
        if(date != null) {
            oValue = date;
            typesMap.put(key, Date.class);
        }

        addExtraAttributeToDoc(doc, key, oValue, true, isMultiValued);
    }

    private static void guessMetadataTypes(Metadata metadata) {

        for (String key : metadata.names()) {
            if (key.contains("Unknown tag") || ignoredMetadata.contains(key)) { //$NON-NLS-1$
                continue;
            }
            if (metadata.getValues(key).length > 1) {
                typesMap.put(key, String.class);

                continue;
            }
            String val = metadata.get(key);

            if (typesMap.get(key) == null || !typesMap.get(key).equals(String.class)) {
                int type = 0;
                while (type <= 4) {
                    try {
                        switch (type) {
                            case 0:
                                if (typesMap.get(key) == null || typesMap.get(key).equals(Integer.class)) {
                                    Integer.parseInt(val);
                                    typesMap.put(key, Integer.class);
                                    break;
                                }
                            case 1:
                                if (typesMap.get(key) == null || typesMap.get(key).equals(Integer.class)
                                        || typesMap.get(key).equals(Long.class)) {
                                    Long.parseLong(val);
                                    typesMap.put(key, Long.class);
                                    break;
                                }
                            case 2:
                                if (typesMap.get(key) == null || typesMap.get(key).equals(Float.class)) {
                                    Float.parseFloat(val);
                                    typesMap.put(key, Float.class);
                                    break;
                                }
                            case 3:
                                if (typesMap.get(key) == null || typesMap.get(key).equals(Float.class)
                                        || typesMap.get(key).equals(Double.class)) {
                                    Double.parseDouble(val);
                                    typesMap.put(key, Double.class);
                                    break;
                                }
                            case 4:
                                typesMap.put(key, String.class);
                        }
                        type = 100;

                    } catch (NumberFormatException e) {
                        type++;
                    }
                }
            }

        }

    }

    public static IItem getItem(Document doc, File outputBase, SleuthkitCase sleuthCase, boolean viewItem) {

        try {
            Item evidence = new Item() {
                public File getFile() {
                    try {
                        return getTempFile();
                    } catch (IOException e) {
                        e.printStackTrace();
                        return null;
                    }
                }
            };

            evidence.setName(doc.get(IndexItem.NAME));

            String value = doc.get(IndexItem.LENGTH);
            Long len = null;
            if (value != null && !value.isEmpty()) {
                len = Long.valueOf(value);
            }
            evidence.setLength(len);

            value = doc.get(IndexItem.ID);
            if (value != null) {
                evidence.setId(Integer.valueOf(value));
            }

            // evidence.setLabels(state.getLabels(id));
            value = doc.get(IndexItem.PARENTID);
            if (value != null) {
                evidence.setParentId(Integer.valueOf(value));
            }

            value = doc.get(IndexItem.EVIDENCE_UUID);
            if (value != null) {
                // TODO obter source corretamente
                IDataSource dataSource = new DataSource();
                dataSource.setUUID(value);
                evidence.setDataSource(dataSource);
            }

            value = doc.get(IndexItem.TYPE);
            if (value != null) {
                evidence.setType(new GenericFileType(value));
            }

            for (String category : doc.getValues(IndexItem.CATEGORY)) {
                evidence.addCategory(category);
            }

            value = doc.get(IndexItem.ACCESSED);
            if (value != null && !value.isEmpty()) {
                evidence.setAccessDate(DateUtil.stringToDate(value));
            }

            value = doc.get(IndexItem.CREATED);
            if (value != null && !value.isEmpty()) {
                evidence.setCreationDate(DateUtil.stringToDate(value));
            }

            value = doc.get(IndexItem.MODIFIED);
            if (value != null && !value.isEmpty()) {
                evidence.setModificationDate(DateUtil.stringToDate(value));
            }

            value = doc.get(IndexItem.RECORDDATE);
            if (value != null && !value.isEmpty()) {
                evidence.setRecordDate(DateUtil.stringToDate(value));
            }

            evidence.setPath(doc.get(IndexItem.PATH));

            value = doc.get(IndexItem.CONTENTTYPE);
            if (value != null) {
                evidence.setMediaType(MediaType.parse(value));
            }

            boolean hasFile = false;
            value = doc.get(IndexItem.EXPORT);
            if (value != null && !value.isEmpty()) {
                File localFile = Util.getResolvedFile(outputBase.getParent(), value);
                localFile = checkIfEvidenceFolderExists(evidence, localFile);
                evidence.setFile(localFile);
                hasFile = true;

            } else {
                value = doc.get(IndexItem.SLEUTHID);
                if (value != null && !value.isEmpty()) {
                    evidence.setSleuthId(Integer.valueOf(value));
                    evidence.setSleuthFile(sleuthCase.getContentById(Long.valueOf(value)));
                }

                value = doc.get(IndexItem.ID_IN_SOURCE);
                if (value != null && !value.isEmpty()) {
                    evidence.setIdInDataSource(value.trim());
                    String relPath = doc.get(IndexItem.SOURCE_PATH);
                    Path absPath = Util.getResolvedFile(outputBase.getParent(), relPath).toPath();
                    SeekableInputStreamFactory sisf = inputStreamFactories.get(absPath);
                    if (sisf == null) {
                        String className = doc.get(IndexItem.SOURCE_DECODER);
                        Class<?> clazz = Class.forName(className);
                        Constructor<SeekableInputStreamFactory> c = (Constructor) clazz.getConstructor(Path.class);
                        sisf = c.newInstance(absPath);
                        inputStreamFactories.put(absPath, sisf);
                    }
                    evidence.setInputStreamFactory(sisf);
                }
            }

            value = doc.get(IndexItem.TIMEOUT);
            if (value != null) {
                evidence.setTimeOut(Boolean.parseBoolean(value));
            }

            value = doc.get(IndexItem.HASH);
            if (value != null) {
                value = value.toUpperCase();
                evidence.setHash(value);
            }

            if (evidence.getHash() != null && !evidence.getHash().isEmpty()) {

                if (Boolean.valueOf(doc.get(ImageThumbTask.HAS_THUMB))) {
                    String mimePrefix = evidence.getMediaType().getType();
                    if (doc.getBinaryValue(THUMB) != null) {
                        evidence.setThumb(doc.getBinaryValue(THUMB).bytes);

                    } else if (mimePrefix.equals("image") || mimePrefix.equals("video")) { //$NON-NLS-1$ //$NON-NLS-2$
                        String thumbFolder = mimePrefix.equals("image") ? ImageThumbTask.thumbsFolder : "view"; //$NON-NLS-1$ //$NON-NLS-2$
                        File thumbFile = Util.getFileFromHash(new File(outputBase, thumbFolder), evidence.getHash(),
                                "jpg"); //$NON-NLS-1$
                        try {
                            evidence.setThumb(Files.readAllBytes(thumbFile.toPath()));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }

                File viewFile = Util.findFileFromHash(new File(outputBase, "view"), evidence.getHash()); //$NON-NLS-1$
                /*
                 * if (viewFile == null && !hasFile && evidence.getSleuthId() == null) {
                 * viewFile = Util.findFileFromHash(new File(outputBase,
                 * ImageThumbTask.thumbsFolder), value); }
                 */
                if (viewFile != null) {
                    evidence.setViewFile(viewFile);

                    if (viewItem
                            || (!hasFile && evidence.getSleuthId() == null && evidence.getIdInDataSource() == null)) {
                        evidence.setFile(viewFile);
                        evidence.setTempFile(viewFile);
                        evidence.setMediaType(null);
                    }
                }
            }

            value = doc.get(IndexItem.DELETED);
            if (value != null) {
                evidence.setDeleted(Boolean.parseBoolean(value));
            }

            value = doc.get(IndexItem.ISDIR);
            if (value != null) {
                evidence.setIsDir(Boolean.parseBoolean(value));
            }

            value = doc.get(IndexItem.CARVED);
            if (value != null) {
                evidence.setCarved(Boolean.parseBoolean(value));
            }

            value = doc.get(IndexItem.SUBITEM);
            if (value != null) {
                evidence.setSubItem(Boolean.parseBoolean(value));
            }

            value = doc.get(IndexItem.HASCHILD);
            if (value != null) {
                evidence.setHasChildren(Boolean.parseBoolean(value));
            }

            value = doc.get(IndexItem.OFFSET);
            if (value != null) {
                evidence.setFileOffset(Long.parseLong(value));
            }

            Set<String> multiValuedFields = new HashSet<>();
            for (IndexableField f : doc.getFields()) {
                if (BasicProps.SET.contains(f.name()))
                    continue;
                Class<?> c = typesMap.get(f.name());
                if (Item.getAllExtraAttributes().contains(f.name())) {
                    if (multiValuedFields.contains(f.name()))
                        continue;
                    IndexableField[] fields = doc.getFields(f.name());
                    if (fields.length > 1) {
                        multiValuedFields.add(f.name());
                        List<Object> fieldList = new ArrayList<>();
                        for (IndexableField field : fields)
                            fieldList.add(getCastedValue(c, field));
                        evidence.setExtraAttribute(f.name(), fieldList);
                    } else
                        evidence.setExtraAttribute(f.name(), getCastedValue(c, f));
                } else {
                    String val = f.stringValue();
                    if(Date.class.equals(c)) {
                        //it was stored lowercase because query parser converts range queries to lowercase
                        val = val.toUpperCase();
                    }
                    evidence.getMetadata().add(f.name(), val);
                }
                    
            }

            return evidence;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;

    }
    
    private static File checkIfEvidenceFolderExists(Item evidence, File localFile) {
        if(evidence.getPath().contains(">>"))
            return localFile;
        Path path;
        try {
            path = Paths.get(evidence.getPath());
        }catch(InvalidPathException e) {
            return localFile;
        }
        String pathSuffix = "";
        if(path.getNameCount() > 1)
            pathSuffix = path.subpath(1, path.getNameCount()).toString();
        if(localFile.toPath().endsWith(pathSuffix)) {
            String evidenceFolderStr = localFile.getAbsolutePath().substring(0, localFile.getAbsolutePath().lastIndexOf(pathSuffix));
            File evidenceFolder = new File(evidenceFolderStr);
            File mappedFolder = localEvidenceMap.get(evidenceFolder);
            if(mappedFolder == null) {
                if(evidenceFolder.exists()) {
                    mappedFolder = evidenceFolder;
                }else {
                    SelectImagePathWithDialog siwd = new SelectImagePathWithDialog(evidenceFolder, true);
                    mappedFolder = siwd.askImagePathInGUI();
                }
                localEvidenceMap.put(evidenceFolder, mappedFolder);
            }
            localFile = new File(mappedFolder, pathSuffix);
        }
        return localFile;
    }

    public static Object getCastedValue(Class<?> c, IndexableField f) throws ParseException {
        if (Date.class.equals(c)) {
            //it was stored lowercase because query parser converts range queries to lowercase
            String value = f.stringValue().toUpperCase();
            try {
                return DateUtil.stringToDate(value);
            }catch(ParseException e) {
                return DateUtil.tryToParseDate(value);
            }   
        }else if (Number.class.isAssignableFrom(c))
            return f.numericValue();
        else
            return f.stringValue();
    }

}
