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
package iped.engine.task.index;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.lucene.document.BinaryDocValuesField;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoubleDocValuesField;
import org.apache.lucene.document.DoublePoint;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.FloatDocValuesField;
import org.apache.lucene.document.FloatPoint;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.KnnVectorField;
import org.apache.lucene.document.LatLonDocValuesField;
import org.apache.lucene.document.LatLonPoint;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.NumericUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.utils.DateUtils;
import org.sleuthkit.datamodel.SleuthkitCase;

import iped.data.IItem;
import iped.datasource.IDataSource;
import iped.engine.data.DataSource;
import iped.engine.data.IPEDSource;
import iped.engine.data.Item;
import iped.engine.lucene.analysis.FastASCIIFoldingFilter;
import iped.engine.sleuthkit.SleuthkitInputStreamFactory;
import iped.engine.task.ImageThumbTask;
import iped.engine.task.MinIOTask.MinIOInputInputStreamFactory;
import iped.engine.task.similarity.ImageSimilarityTask;
import iped.engine.util.Util;
import iped.parsers.ocr.OCRParser;
import iped.parsers.standard.StandardParser;
import iped.parsers.util.MetadataUtil;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.utils.DateUtil;
import iped.utils.FileInputStreamFactory;
import iped.utils.IOUtil;
import iped.utils.SeekableInputStreamFactory;
import iped.utils.SelectImagePathWithDialog;
import iped.utils.UTF8Properties;
import jep.NDArray;

/**
 * Cria um org.apache.lucene.document.Document a partir das propriedades do
 * itens que será adicionado ao índice.
 */
public class IndexItem extends BasicProps {

    public static final String GEO_SSDV_PREFIX = "geo_ssdv_";

    public static final String TRACK_ID = "trackId"; //$NON-NLS-1$
    public static final String PARENT_TRACK_ID = "parentTrackId"; //$NON-NLS-1$
    public static final String CONTAINER_TRACK_ID = "containerTrackId"; //$NON-NLS-1$

    public static final String IGNORE_CONTENT_REF = "ignoreContentRef"; //$NON-NLS-1$
    public static final String ID_IN_SOURCE = "idInDataSource"; //$NON-NLS-1$
    public static final String SOURCE_PATH = "dataSourcePath"; //$NON-NLS-1$
    public static final String SOURCE_DECODER = "dataSourceDecoder"; //$NON-NLS-1$

    public static final String attrTypesFilename = "metadataTypes.txt"; //$NON-NLS-1$

    private static final String NEW_DATASOURCE_PATH_FILE = "data/newDataSourceLocations.txt";

    private static final int MAX_DOCVALUE_SIZE = 4096;

    public static final char EVENT_IDX_SEPARATOR = ';';
    public static final char EVENT_IDX_SEPARATOR2 = ',';
    public static final String EVENT_SEPARATOR = " | ";

    static HashSet<String> ignoredMetadata = new HashSet<String>();

    private static Map<String, SeekableInputStreamFactory> inputStreamFactories = new HashMap<>();

    private static Map<String, Class<?>> typesMap = MetadataUtil.getMetadataTypes();

    private static FieldType storedTokenizedNoNormsField = new FieldType();
    private static FieldType dateField = new FieldType();

    static {
        storedTokenizedNoNormsField.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
        storedTokenizedNoNormsField.setOmitNorms(true);
        storedTokenizedNoNormsField.setStored(true);
        storedTokenizedNoNormsField.freeze();

        dateField.setIndexOptions(IndexOptions.DOCS);
        dateField.setStored(true);
        dateField.setOmitNorms(true);
        dateField.setTokenized(false);
        dateField.freeze();

        ignoredMetadata.add(Metadata.CONTENT_TYPE);
        ignoredMetadata.add(Metadata.CONTENT_LENGTH);
        ignoredMetadata.add(TikaCoreProperties.RESOURCE_NAME_KEY);
        ignoredMetadata.add(StandardParser.INDEXER_CONTENT_TYPE);
        ignoredMetadata.add(StandardParser.INDEXER_TIMEOUT);
        ignoredMetadata.add(TikaCoreProperties.CONTENT_TYPE_HINT.getName());
        ignoredMetadata.add("File Name"); //$NON-NLS-1$
        ignoredMetadata.add("File Size"); //$NON-NLS-1$
        // ocrCharCount is already copied to an extra attribute
        ignoredMetadata.add(OCRParser.OCR_CHAR_COUNT);

        BasicProps.SET.add(ID_IN_SOURCE);
        BasicProps.SET.add(SOURCE_PATH);
        BasicProps.SET.add(SOURCE_DECODER);
    }

    public static boolean isByte(String field) {
        return Byte.class.equals(typesMap.get(field));
    }

    public static boolean isShort(String field) {
        return Short.class.equals(typesMap.get(field));
    }

    public static boolean isInteger(String field) {
        return Integer.class.equals(typesMap.get(field));
    }

    public static boolean isLong(String field) {
        return Long.class.equals(typesMap.get(field));
    }

    public static boolean isFloat(String field) {
        return Float.class.equals(typesMap.get(field));
    }

    public static boolean isDouble(String field) {
        return Double.class.equals(typesMap.get(field));
    }

    public static boolean isNumeric(String field) {
        Class<?> type = typesMap.get(field);
        return type != null && Number.class.isAssignableFrom(type);
    }

    public static boolean isIntegerNumber(String field) {
        return isByte(field) || isShort(field) || isInteger(field) || isLong(field);
    }

    public static boolean isRealNumber(String field) {
        return isFloat(field) || isDouble(field);
    }

    public static Map<String, Class> getMetadataTypes() {
        return Collections.unmodifiableMap(typesMap);
    }

    public static void saveMetadataTypes(File confDir) throws IOException {
        File metadataTypesFile = new File(confDir, attrTypesFilename);
        UTF8Properties props = new UTF8Properties();
        for (Object o : typesMap.entrySet().toArray()) {
            Entry<String, Class<?>> e = (Entry<String, Class<?>>) o;
            props.setProperty(e.getKey(), e.getValue().getCanonicalName());
        }
        props.store(metadataTypesFile);
        IOUtils.fsync(metadataTypesFile.toPath(), false);
    }

    public static void loadMetadataTypes(File confDir) throws IOException, ClassNotFoundException {
        File metadataTypesFile = new File(confDir, attrTypesFilename);
        if (metadataTypesFile.exists()) {
            UTF8Properties props = new UTF8Properties();
            props.load(metadataTypesFile);
            for (String key : props.stringPropertyNames()) {
                MetadataUtil.setMetadataType(key, Class.forName(props.getProperty(key)));
            }
        }
    }

    private static final String normalize(String value) {
        return normalize(value, true);
    }

    public static final String normalize(String value, boolean toLowerCase) {
        if (toLowerCase) {
            value = value.toLowerCase();
        }
        char[] input = value.toCharArray();
        char[] output = new char[input.length * 4];
        int len = FastASCIIFoldingFilter.foldToASCII(input, 0, output, 0, input.length);
        return new String(output, 0, len).trim();
    }

    public static Document Document(IItem evidence, File output) {
        Document doc = new Document();

        doc.add(new IntPoint(ID, evidence.getId()));
        doc.add(new StoredField(ID, evidence.getId()));
        doc.add(new NumericDocValuesField(ID, evidence.getId()));

        doc.add(new StringField(EVIDENCE_UUID, evidence.getDataSource().getUUID(), Field.Store.YES));
        doc.add(new SortedDocValuesField(EVIDENCE_UUID, new BytesRef(evidence.getDataSource().getUUID())));

        if (evidence.getTempAttribute(IGNORE_CONTENT_REF) == null) {
            String value = evidence.getIdInDataSource();
            if (value != null) {
                doc.add(new StringField(ID_IN_SOURCE, value, Field.Store.YES));
                doc.add(new SortedDocValuesField(ID_IN_SOURCE, new BytesRef(value)));
            }
            if (evidence.getInputStreamFactory() != null
                    && evidence.getInputStreamFactory().getDataSourceURI() != null) {
                URI uri = evidence.getInputStreamFactory().getDataSourceURI();
                value = Util.getRelativePath(output, uri).replace('\\', '/');

                doc.add(new StringField(SOURCE_PATH, value, Field.Store.YES));
                doc.add(new SortedDocValuesField(SOURCE_PATH, new BytesRef(value)));

                value = evidence.getInputStreamFactory().getClass().getName();
                doc.add(new StringField(SOURCE_DECODER, value, Field.Store.YES));
                doc.add(new SortedDocValuesField(SOURCE_DECODER, new BytesRef(value)));
            }
        }

        Integer intVal = evidence.getSubitemId();
        if (intVal != null) {
            doc.add(new IntPoint(SUBITEMID, intVal));
            doc.add(new StoredField(SUBITEMID, intVal));
            doc.add(new NumericDocValuesField(SUBITEMID, intVal));
        }

        intVal = evidence.getParentId();
        if (intVal != null) {
            doc.add(new IntPoint(PARENTID, intVal));
            doc.add(new StoredField(PARENTID, intVal));
            doc.add(new NumericDocValuesField(PARENTID, intVal));
        }

        doc.add(new Field(PARENTIDs, evidence.getParentIdsString(), storedTokenizedNoNormsField));
        doc.add(new SortedDocValuesField(PARENTIDs, new BytesRef(evidence.getParentIdsString())));

        String value = evidence.getName();
        if (value == null) {
            value = ""; //$NON-NLS-1$
        }
        Field nameField = new TextField(NAME, value, Field.Store.YES);
        doc.add(nameField);
        doc.add(new SortedDocValuesField(NAME, new BytesRef(normalize(value))));

        value = evidence.getExt();
        if (value == null) {
            value = "";
        }
        doc.add(new Field(EXT, value, storedTokenizedNoNormsField));
        doc.add(new SortedDocValuesField(EXT, new BytesRef(normalize(value))));

        value = evidence.getType();
        if (value == null) {
            value = ""; //$NON-NLS-1$
        }
        doc.add(new Field(TYPE, value, storedTokenizedNoNormsField));
        doc.add(new SortedDocValuesField(TYPE, new BytesRef(normalize(value))));

        Long length = evidence.getLength();
        if (length != null) {
            doc.add(new LongPoint(LENGTH, length));
            doc.add(new StoredField(LENGTH, length));
            doc.add(new NumericDocValuesField(LENGTH, length));
        }

        Set<TimeStampEvent> timeEventSet = new TreeSet<>();

        Date date = evidence.getCreationDate();
        if (date != null) {
            value = DateUtil.dateToString(date);
        } else {
            value = ""; //$NON-NLS-1$
        }
        doc.add(new Field(CREATED, value, dateField));
        doc.add(new SortedDocValuesField(CREATED, new BytesRef(value)));
        timeEventSet.add(new TimeStampEvent(value, CREATED));

        date = evidence.getAccessDate();
        if (date != null) {
            value = DateUtil.dateToString(date);
        } else {
            value = ""; //$NON-NLS-1$
        }
        doc.add(new Field(ACCESSED, value, dateField));
        doc.add(new SortedDocValuesField(ACCESSED, new BytesRef(value)));
        timeEventSet.add(new TimeStampEvent(value, ACCESSED));

        date = evidence.getModDate();
        if (date != null) {
            value = DateUtil.dateToString(date);
        } else {
            value = ""; //$NON-NLS-1$
        }
        doc.add(new Field(MODIFIED, value, dateField));
        doc.add(new SortedDocValuesField(MODIFIED, new BytesRef(value)));
        timeEventSet.add(new TimeStampEvent(value, MODIFIED));

        date = evidence.getChangeDate();
        if (date != null) {
            value = DateUtil.dateToString(date);
        } else {
            value = ""; //$NON-NLS-1$
        }
        doc.add(new Field(CHANGED, value, dateField));
        doc.add(new SortedDocValuesField(CHANGED, new BytesRef(value)));
        timeEventSet.add(new TimeStampEvent(value, CHANGED));

        value = evidence.getPath();
        if (value == null) {
            value = ""; //$NON-NLS-1$
        }
        doc.add(new Field(PATH, value, storedTokenizedNoNormsField));
        if (value.length() > MAX_DOCVALUE_SIZE) {
            value = value.substring(0, MAX_DOCVALUE_SIZE);
        }
        doc.add(new SortedDocValuesField(PATH, new BytesRef(normalize(value))));

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

        byte[] similarityFeatures = (byte[]) evidence.getExtraAttribute(ImageSimilarityTask.IMAGE_FEATURES);
        // clear extra property to don't add it again later when iterating over extra props
        evidence.getExtraAttributeMap().remove(ImageSimilarityTask.IMAGE_FEATURES);
        if (similarityFeatures != null) {
            doc.add(new BinaryDocValuesField(ImageSimilarityTask.IMAGE_FEATURES, new BytesRef(similarityFeatures)));
            doc.add(new StoredField(ImageSimilarityTask.IMAGE_FEATURES, similarityFeatures));
            doc.add(new IntPoint(ImageSimilarityTask.IMAGE_FEATURES, similarityFeatures[0], similarityFeatures[1],
                    similarityFeatures[2], similarityFeatures[3]));
        }

        long off = evidence.getFileOffset();
        if (off != -1) {
            doc.add(new StoredField(OFFSET, Long.toString(off)));
        }

        for (Entry<String, Object> entry : evidence.getExtraAttributeMap().entrySet()) {
            if (entry.getValue() instanceof Collection) {
                for (Object val : (Collection<?>) entry.getValue()) {
                    if (typesMap.get(entry.getKey()) == null) {
                        MetadataUtil.setMetadataType(entry.getKey(), val.getClass());
                    }
                    addExtraAttributeToDoc(doc, entry.getKey(), val, true, timeEventSet);
                }
            } else {
                if (typesMap.get(entry.getKey()) == null) {
                    MetadataUtil.setMetadataType(entry.getKey(), entry.getValue().getClass());
                }
                addExtraAttributeToDoc(doc, entry.getKey(), entry.getValue(), false, timeEventSet);
            }
        }

        Metadata metadata = evidence.getMetadata();
        if (metadata != null) {
            addMetadataToDoc(doc, evidence.getMetadata(), timeEventSet);
        }

        storeTimeStamps(doc, timeEventSet);

        return doc;
    }

    private static void storeTimeStamps(Document doc, Set<TimeStampEvent> timeEventSet) {

        String prevTimeStamp = null;
        Set<String> eventsSet = new TreeSet<>();
        List<String> eventsList = new ArrayList<>();
        int i = 0;
        for (TimeStampEvent tse : timeEventSet) {
            i++;
            if (tse.timeStamp == null || tse.timeStamp.isEmpty()) {
                continue;
            }
            tse.timeEvent = tse.timeEvent.toLowerCase();

            doc.add(new Field(TIMESTAMP, tse.timeStamp, dateField));
            doc.add(new SortedSetDocValuesField(TIMESTAMP, new BytesRef(tse.timeStamp)));
            doc.add(new Field(TIME_EVENT, tse.timeEvent, storedTokenizedNoNormsField));
            doc.add(new SortedSetDocValuesField(TIME_EVENT, new BytesRef(tse.timeEvent)));

            if (prevTimeStamp != null && !tse.timeStamp.equals(prevTimeStamp)) {
                addTimeStampEventGroup(doc, eventsSet, eventsList);
            }
            eventsSet.add(tse.timeEvent);
            if (i == timeEventSet.size()) {
                addTimeStampEventGroup(doc, eventsSet, eventsList);
            }
            prevTimeStamp = tse.timeStamp;
        }
        // some date metadata could have multiple timestamps
        List<String> sortedList = new ArrayList<>(eventsList);
        Collections.sort(sortedList);
        StringBuilder indexes = new StringBuilder();
        String prevEvent = null;
        for (String event : sortedList) {
            if (indexes.length() > 0) {
                if (event.equals(prevEvent)) {
                    indexes.append(EVENT_IDX_SEPARATOR2);
                } else {
                    indexes.append(EVENT_IDX_SEPARATOR);
                }
            }
            indexes.append(indexOfObject(eventsList, event));
            prevEvent = event;
        }
        doc.add(new BinaryDocValuesField(ExtraProperties.TIME_EVENT_ORDS, new BytesRef(indexes.toString())));
    }

    private static int indexOfObject(List<String> list, String o) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == o) {
                return i;
            }
        }
        return -1;
    }

    private static void addTimeStampEventGroup(Document doc, Set<String> eventsSet, List<String> eventsList) {
        String events = eventsSet.stream().collect(Collectors.joining(EVENT_SEPARATOR));
        doc.add(new SortedSetDocValuesField(ExtraProperties.TIME_EVENT_GROUPS, new BytesRef(events)));
        eventsList.add(events);
        eventsSet.clear();
    }

    private static class TimeStampEvent implements Comparable<TimeStampEvent> {

        private String timeStamp, timeEvent;

        private TimeStampEvent(String timestamp, String timeEvent) {
            this.timeStamp = timestamp;
            this.timeEvent = timeEvent;
        }

        @Override
        public int compareTo(TimeStampEvent o) {
            boolean t1Empty = timeStamp == null || timeStamp.isEmpty();
            boolean t2Empty = o.timeStamp == null || o.timeStamp.isEmpty();
            if (t1Empty) {
                if (t2Empty) {
                    return 0;
                } else {
                    return -1;
                }
            } else {
                if (t2Empty) {
                    return 1;
                } else {
                    int ret = timeStamp.compareTo(o.timeStamp);
                    if (ret == 0) {
                        ret = timeEvent.compareTo(o.timeEvent);
                    }
                    return ret;
                }
            }
        }

    }

    private static void addExtraAttributeToDoc(Document doc, String key, Object oValue, boolean isMultiValued,
            Set<TimeStampEvent> timeEventSet) {

        if (key.equals(ExtraProperties.LOCATIONS)) {
            String[] coords = oValue.toString().split(";");
            double lat = Double.valueOf(coords[0].trim());
            double lon = Double.valueOf(coords[1].trim());
            if (lat >= -90.0 && lat <= 90.0 && lon >= -180.0 && lon <= 180.0) {
                doc.add(new LatLonPoint(key, lat, lon));
                doc.add(new LatLonDocValuesField(key, lat, lon));
                doc.add(new StringField(key, oValue.toString(), Field.Store.YES));
                // used to group values in metadata filter panel, sorting doesn't make sense
                doc.add(new SortedSetDocValuesField(GEO_SSDV_PREFIX + key, new BytesRef(oValue.toString())));
            }
        } else if (oValue instanceof Date) {
            String value = DateUtils.formatDate((Date) oValue);
            doc.add(new Field(key, value, dateField));
            if (!isMultiValued)
                doc.add(new SortedDocValuesField(key, new BytesRef(value)));
            else
                doc.add(new SortedSetDocValuesField(key, new BytesRef(value)));

            timeEventSet.add(new TimeStampEvent(value, key));

        } else if (oValue instanceof Byte || oValue instanceof Short || oValue instanceof Integer) {
            int intVal = ((Number) oValue).intValue();
            doc.add(new IntPoint(key, intVal));
            doc.add(new StoredField(key, intVal));
            if (!isMultiValued)
                doc.add(new NumericDocValuesField(key, intVal));
            else
                doc.add(new SortedNumericDocValuesField(key, intVal));

        } else if (oValue instanceof Long) {
            doc.add(new LongPoint(key, (Long) oValue));
            doc.add(new StoredField(key, (Long) oValue));
            if (!isMultiValued)
                doc.add(new NumericDocValuesField(key, (Long) oValue));
            else
                doc.add(new SortedNumericDocValuesField(key, (Long) oValue));

        } else if (oValue instanceof Float) {
            doc.add(new FloatPoint(key, (Float) oValue));
            doc.add(new StoredField(key, (Float) oValue));
            if (!isMultiValued)
                doc.add(new FloatDocValuesField(key, (Float) oValue));
            else
                doc.add(new SortedNumericDocValuesField(key, NumericUtils.floatToSortableInt((Float) oValue)));

        } else if (oValue instanceof Double) {
            doc.add(new DoublePoint(key, (Double) oValue));
            doc.add(new StoredField(key, (Double) oValue));
            if (!isMultiValued)
                doc.add(new DoubleDocValuesField(key, (Double) oValue));
            else
                doc.add(new SortedNumericDocValuesField(key, NumericUtils.doubleToSortableLong((Double) oValue)));

        } else if (oValue instanceof NDArray) {
            float[] floatArray = convNDArrayToFloatArray((NDArray) oValue);
            byte[] byteArray = convFloatArrayToByteArray(floatArray);
            int suffix = 0;
            // KnnVectorField is not multivalued, must use other key if it exists
            String knnKey = key;
            while (doc.getField(knnKey) != null) {
                knnKey = key + (++suffix);
            }
            doc.add(new SortedSetDocValuesField(key, new BytesRef(byteArray)));
            doc.add(new StoredField(key, byteArray));
            doc.add(new KnnVectorField(knnKey, floatArray));

        } else {
            // value is typed as string
            String value = oValue.toString();
            doc.add(new Field(key, value, storedTokenizedNoNormsField));
            if (value.length() > MAX_DOCVALUE_SIZE) {
                value = value.substring(0, MAX_DOCVALUE_SIZE);
            }
            if (!isMultiValued)
                doc.add(new SortedDocValuesField(key, new BytesRef(normalize(value))));
            else
                doc.add(new SortedSetDocValuesField(key, new BytesRef(normalize(value))));
        }

    }

    public static final byte[] convFloatArrayToByteArray(float[] array) {
        ByteBuffer buffer = ByteBuffer.allocate(4 * array.length);
        for (float value : array) {
            buffer.putFloat(value);
        }
        return buffer.array();
    }

    public static final float[] convNDArrayToFloatArray(NDArray nd) {
        return convDoubleToFloatArray((double[]) nd.getData());
    }

    public static final float[] convDoubleToFloatArray(double[] array) {
        float[] result = new float[array.length];
        for (int i = 0; i < array.length; i++) {
            result[i] = (float) array[i];
        }
        return result;
    }

    private static void addMetadataToDoc(Document doc, Metadata metadata, Set<TimeStampEvent> timeEventSet) {
        MediaType mimetype = MediaType.parse(metadata.get(Metadata.CONTENT_TYPE));
        if (mimetype != null)
            mimetype = mimetype.getBaseType();

        String[] names = metadata.names();

        for (String key : names) {
            if (key == null || key.contains("Unknown tag") || ignoredMetadata.contains(key)) { //$NON-NLS-1$
                continue;
            }
            boolean isMultiValued = true;// metadata.getValues(key).length > 1;
            for (String val : metadata.getValues(key)) {
                if (val != null && !(val = val.trim()).isEmpty())
                    addMetadataKeyToDoc(doc, key, val, isMultiValued, mimetype, timeEventSet);
            }

        }
    }

    private static void addMetadataKeyToDoc(Document doc, String key, String value, boolean isMultiValued,
            MediaType mimetype, Set<TimeStampEvent> timeEventSet) {
        Object oValue = value;
        Class<?> type = typesMap.get(key);

        if (type == null && MetadataUtil.isHtmlMediaType(mimetype) && !key.startsWith(ExtraProperties.UFED_META_PREFIX))
            return;

        if (type == null) {
            try {
                Double doubleVal = Double.valueOf(value);
                String newKey = key + ":number";
                MetadataUtil.setMetadataType(newKey, Double.class);
                addExtraAttributeToDoc(doc, newKey, doubleVal, isMultiValued, timeEventSet);

            } catch (NumberFormatException e) {
                Date date = DateUtil.tryToParseDate(value);
                if (date != null) {
                    String newKey = key + ":date";
                    MetadataUtil.setMetadataType(newKey, Date.class);
                    addExtraAttributeToDoc(doc, newKey, date, isMultiValued, timeEventSet);
                }
            }
        } else {
            try {
                if (type.equals(Double.class)) {
                    oValue = Double.valueOf(value);
                } else if (type.equals(Integer.class)) {
                    oValue = Integer.valueOf(value);
                } else if (type.equals(Float.class)) {
                    oValue = Float.valueOf(value);
                } else if (type.equals(Long.class)) {
                    oValue = Long.valueOf(value);
                } else if (type.equals(Date.class)) {
                    Date date = DateUtil.tryToParseDate(value);
                    if (date != null)
                        oValue = date;
                    else
                        throw new ParseException("Not a date", 0);
                }
            } catch (NumberFormatException | ParseException e) {
                // value doesn't match built-in type, store value in other field as string
                key += ":string";
                MetadataUtil.setMetadataType(key, String.class);
            }
        }

        addExtraAttributeToDoc(doc, key, oValue, isMultiValued, timeEventSet);
    }

    public static IItem getItem(Document doc, IPEDSource iCase, boolean viewItem) {

        try {
            Item evidence = new Item();

            evidence.setName(doc.get(IndexItem.NAME));

            // if evidence was stored with EXT, replace the generated in setName()
            String ext = doc.get(IndexItem.EXT);
            if (ext != null) {
                evidence.setExtension(ext);
            }

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

            value = doc.get(IndexItem.SUBITEMID);
            if (value != null) {
                evidence.setSubitemId(Integer.valueOf(value));
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
                evidence.setType(value);
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

            value = doc.get(IndexItem.CHANGED);
            if (value != null && !value.isEmpty()) {
                evidence.setChangeDate(DateUtil.stringToDate(value));
            }

            evidence.setPath(doc.get(IndexItem.PATH));

            value = doc.get(IndexItem.CONTENTTYPE);
            if (value != null) {
                evidence.setMediaType(MediaType.parse(value));
            }

            File outputBase = iCase.getModuleDir();

            value = doc.get(IndexItem.ID_IN_SOURCE);
            if (value != null) {
                evidence.setIdInDataSource(value);
            }
            if (doc.get(IndexItem.SOURCE_PATH) != null && doc.get(IndexItem.SOURCE_DECODER) != null) {
                String sourcePath = doc.get(IndexItem.SOURCE_PATH);
                String className = doc.get(IndexItem.SOURCE_DECODER);
                if (SleuthkitInputStreamFactory.class.getName().equals(className)) {
                    // Use the correct TSK database (sleuth.db location and name may change in some
                    // situations), to avoid issue #1782.
                    SleuthkitCase sleuthCase = iCase.getSleuthCase();
                    if (sleuthCase != null) {
                        sourcePath = sleuthCase.getDbDirPath() + File.separatorChar + sleuthCase.getDatabaseName();
                    }
                } else if (!MinIOInputInputStreamFactory.class.getName().equals(className)) {
                    sourcePath = Util.getResolvedFile(outputBase.getParent(), sourcePath).toString();
                }
                synchronized (inputStreamFactories) {
                    SeekableInputStreamFactory sisf = inputStreamFactories.get(sourcePath);
                    if (sisf == null) {
                        Class<?> clazz = Class.forName(className);
                        try {
                            Constructor<SeekableInputStreamFactory> c = (Constructor) clazz.getConstructor(Path.class);
                            sisf = c.newInstance(Path.of(sourcePath));

                        } catch (NoSuchMethodException e) {
                            Constructor<SeekableInputStreamFactory> c = (Constructor) clazz.getConstructor(URI.class);
                            sisf = c.newInstance(URI.create(sourcePath));
                        }
                        if (!iCase.isReport() && sisf.checkIfDataSourceExists()) {
                            checkIfExistsAndAsk(sisf, iCase.getModuleDir());
                        }
                        inputStreamFactories.put(sourcePath, sisf);
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
                    if (doc.getBinaryValue(THUMB) != null) {
                        evidence.setThumb(doc.getBinaryValue(THUMB).bytes);

                    } else if (MetadataUtil.isImageType(evidence.getMediaType())
                            || MetadataUtil.isVideoType(evidence.getMediaType())) {
                        String thumbFolder = MetadataUtil.isImageType(evidence.getMediaType())
                                ? ImageThumbTask.thumbsFolder
                                : "view";
                        File thumbFile = Util.getFileFromHash(new File(outputBase, thumbFolder), evidence.getHash(),
                                "jpg"); //$NON-NLS-1$
                        try {
                            if (thumbFile.exists())
                                evidence.setThumb(Files.readAllBytes(thumbFile.toPath()));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }

                BytesRef bytesRef = doc.getBinaryValue(ImageSimilarityTask.IMAGE_FEATURES);
                if (bytesRef != null) {
                    evidence.setExtraAttribute(ImageSimilarityTask.IMAGE_FEATURES, bytesRef.bytes);
                }

                File viewFile = Util.findFileFromHash(new File(outputBase, "view"), evidence.getHash()); //$NON-NLS-1$
                /*
                 * if (viewFile == null && !hasFile && evidence.getSleuthId() == null) {
                 * viewFile = Util.findFileFromHash(new File(outputBase,
                 * ImageThumbTask.thumbsFolder), value); }
                 */
                if (viewFile != null) {
                    evidence.setViewFile(viewFile);

                    if (viewItem || (!IOUtil.hasFile(evidence) && evidence.getIdInDataSource() == null)) {
                        evidence.setIdInDataSource("");
                        evidence.setInputStreamFactory(new FileInputStreamFactory(viewFile.toPath()));
                        evidence.setTempFile(viewFile);
                        // Do not reset media type (see issue #1409)
                        // evidence.setMediaType(null);
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

            value = doc.get(IndexItem.ISROOT);
            if (value != null) {
                evidence.setRoot(Boolean.parseBoolean(value));
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
                    if (Date.class.equals(c) && f.stringValue() != null) {
                        String val = f.stringValue();
                        evidence.getMetadata().add(f.name(), val);
                    } else {
                        Object casted = getCastedValue(c, f);
                        if (casted != null) {
                            evidence.getMetadata().add(f.name(), casted.toString());
                        }
                    }
                }
            }

            return evidence;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;

    }

    public static synchronized void checkIfExistsAndAsk(SeekableInputStreamFactory sisf, File caseModuleDir)
            throws IOException {
        Path path = Paths.get(sisf.getDataSourceURI());
        if (path != null && !Files.exists(path)) {
            Path newPath = loadDataSourcePath(caseModuleDir, path);
            if (newPath != null && Files.exists(newPath)) {
                sisf.setDataSourceURI(newPath.toUri());
                return;
            }
            SelectImagePathWithDialog siwd = new SelectImagePathWithDialog(path.toFile(), true);
            File newDataSource = siwd.askImagePathInGUI();
            if (newDataSource != null) {
                sisf.setDataSourceURI(newDataSource.toPath().toUri());
                saveDataSourcePath(caseModuleDir, path, newDataSource.toPath());
            }
        }
    }

    private static void saveDataSourcePath(File caseModuleDir, Path oldPath, Path newPath) throws IOException {
        File file = new File(caseModuleDir, NEW_DATASOURCE_PATH_FILE);
        UTF8Properties props = new UTF8Properties();
        if (file.exists())
            props.load(file);
        String newPathStr = Util.getRelativePath(caseModuleDir, newPath.toFile());
        props.setProperty(oldPath.toString(), newPathStr);
        try {
            props.store(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Path loadDataSourcePath(File caseModuleDir, Path oldPath) throws IOException {
        File file = new File(caseModuleDir, NEW_DATASOURCE_PATH_FILE);
        UTF8Properties props = new UTF8Properties();
        if (file.exists())
            props.load(file);
        String path = props.getProperty(oldPath.toString());
        if (path == null)
            return null;
        return Util.getResolvedFile(caseModuleDir.getParentFile().toPath().toString(), path);
    }

    public static Object getCastedValue(Class<?> c, IndexableField f) throws ParseException {
        if (Date.class.equals(c)) {
            String value = f.stringValue();
            try {
                return DateUtil.stringToDate(value);
            } catch (ParseException e) {
                return DateUtil.tryToParseDate(value);
            }
        } else if (f.numericValue() != null) {
            Number num = f.numericValue();
            if (Byte.class.equals(c)) {
                return num.byteValue();
            } else if (Short.class.equals(c)) {
                return num.shortValue();
            } else if (Integer.class.equals(c)) {
                return num.intValue();
            } else if (Long.class.equals(c)) {
                return num.longValue();
            } else if (Float.class.equals(c)) {
                return num.floatValue();
            } else if (Double.class.equals(c)) {
                return num.doubleValue();
            } else {
                return num;
            }
        } else if (f.binaryValue() != null) {
            return f.binaryValue().bytes;
        } else {
            return f.stringValue();
        }
    }

}
