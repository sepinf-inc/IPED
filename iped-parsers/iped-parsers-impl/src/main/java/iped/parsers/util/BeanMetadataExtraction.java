package iped.parsers.util;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TimeZone;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.xml.sax.ContentHandler;

import iped.data.ICaseData;
import iped.parsers.standard.StandardParser;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.utils.DateUtil;
import iped.utils.EmptyInputStream;

public class BeanMetadataExtraction {

    private static final Logger logger = LoggerFactory.getLogger(BeanMetadataExtraction.class);

    String prefix;
    String mimeType;
    String nameProperty;
    int expandChildBeansLevel = 0;
    boolean isLocalTime = false;
    TimeZone identifiedTimeZone = null;

    ExpressionParser elparser;

    ArrayList<Class> beanClassesToExtract = new ArrayList<Class>();
    HashMap<Class, List<String>> excludeProperties = new HashMap<Class, List<String>>();
    HashMap<Class, String> nameProperties = new HashMap<Class, String>();
    HashMap<Class, String> referencedQuery = new HashMap<Class, String>();

    HashMap<Class, Set<String>> collectionPropertiesToMergeMap = new HashMap<>();
    HashMap<Class, HashMap<String, String>> propertyNameMapping = new HashMap<>();
    HashMap<Class, ArrayList<String[]>> transformationMapping = new HashMap<>();

    private int level;
    private ParseContext parseContext;

    public BeanMetadataExtraction(String prefix, String mimeType, ParseContext parseContext) {
        this(prefix, mimeType);
        this.parseContext = parseContext;
    }

    public BeanMetadataExtraction(String prefix, String mimeType) {
        this.prefix = prefix;
        this.mimeType = mimeType;
        this.nameProperty = "name";
        elparser = new SpelExpressionParser();
    }

    public void addPropertyExclusion(Class c, String propName) {
        List<String> props = excludeProperties.get(c);
        if (props == null) {
            props = new ArrayList<String>();
            excludeProperties.put(c, props);
        }
        props.add(propName);
    }

    public void registerClassNameProperty(Class c, String propName) {
        nameProperties.put(c, propName);
    }

    public void registerClassNameReferencedQuery(Class c, String propName) {
        referencedQuery.put(c, propName);
    }

    public void extractEmbedded(int seq, ParseContext context, Metadata metadata, ContentHandler handler, Object bean) throws IOException {
        extractEmbedded(seq, context, metadata, null, handler, bean, -1);
    }

    class ChildParams {
        Object value;
        PropertyDescriptor pd;

        public ChildParams(Object value, PropertyDescriptor pd) {
            this.value = value;
            this.pd = pd;
        }
    }

    protected boolean extractEmbedded(int seq, ParseContext context, Metadata metadata, PropertyDescriptor parentPd, ContentHandler handler, Object bean, int parentSeq) throws IOException {
        if (parseContext != context) {
            parseContext = context;
            if (this.isLocalTime && this.parseContext != null) {
                configureIdentifiedTimezone(this.parseContext);
            }
        }

        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class, new ParsingEmbeddedDocumentExtractor(context));
        if (extractor.shouldParseEmbedded(metadata)) {
            try {
                Metadata entryMetadata = new Metadata();
                entryMetadata.set(StandardParser.INDEXER_CONTENT_TYPE, mimeType);
                entryMetadata.set(HttpHeaders.CONTENT_TYPE, mimeType);
                entryMetadata.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());
                entryMetadata.set(BasicProps.LENGTH, "");
                entryMetadata.set("bean:className", bean.getClass().getCanonicalName());

                ArrayList<ChildParams> children = new ArrayList<ChildParams>();

                Object[] colObj = null;
                if (bean instanceof Collection) {
                    colObj = ((Collection) bean).toArray();
                }
                if (bean.getClass().isArray()) {
                    colObj = (Object[]) bean;
                }
                if (bean instanceof Map) {
                    colObj = ((Map) bean).entrySet().toArray();
                }


                if (colObj == null) {
                    String resolvedNameProp = null;
                    if (bean instanceof Entry) {
                        resolvedNameProp = "key";
                    } else {
                        resolvedNameProp = nameProperties.get(bean.getClass());
                        if (resolvedNameProp == null) {
                            resolvedNameProp = nameProperty;
                        }
                    }
                    String resolvedReferencedQuery = referencedQuery.get(bean.getClass());
                    if (resolvedReferencedQuery != null) {
                        entryMetadata.add(ExtraProperties.LINKED_ITEMS, parseQuery(resolvedReferencedQuery, bean));
                    }

                    List<String[]> transformations = getTransformationMapping(bean.getClass());
                    if (transformations != null) {
                        for (Iterator iterator = transformations.iterator(); iterator.hasNext();) {
                            String[] strings = (String[]) iterator.next();
                            if (!strings[0].equals(resolvedNameProp)) {
                                entryMetadata.add(strings[0], parseQuery(strings[1], bean));
                            }
                        }
                    }

                    for (PropertyDescriptor pd : Introspector.getBeanInfo(bean.getClass()).getPropertyDescriptors()) {
                        List<String> exclProps = excludeProperties.get(bean.getClass());
                        if (exclProps != null && exclProps.contains(pd.getName())) {
                            continue;
                        }
                        if (pd.getReadMethod() != null && !"class".equals(pd.getName())) {
                            Object value = null;
                            try {
                                value = pd.getReadMethod().invoke(bean);
                            } catch (Exception e) {
                                e.printStackTrace();
                                continue;
                            }

                            if (pd.getDisplayName().equals(resolvedNameProp)) {
                                String name = value.toString();
                                if (transformations != null) {
                                    for (Iterator<String[]> it = transformations.iterator(); it.hasNext();) {
                                        String[] strs = it.next();
                                        if (strs[0].equals(resolvedNameProp)) {
                                            name = parseQuery(strs[1], bean);
                                            break;
                                        }
                                    }
                                }
                                entryMetadata.add(TikaCoreProperties.TITLE, name);// adds the name property without prefix
                                entryMetadata.add(TikaCoreProperties.RESOURCE_NAME_KEY, name);
                            }

                            if (value != null) {
                                String metadataName = getPropertyNameMapping(bean.getClass(), pd.getDisplayName());
                                if (metadataName == null) {
                                    metadataName = pd.getDisplayName();
                                }
                                if (prefix != null && prefix.length() > 0) {
                                    metadataName = prefix + metadataName;
                                }
                                if (isBean(value)) {
                                    if (shouldMergeCollectionProperty(bean, pd) && isCollectionNotEmpty(value)) {
                                        for (Iterator<?> iterator = IteratorUtils.getIterator(value); iterator.hasNext();) {
                                            Object subValue =  iterator.next();
                                            if (isBean(subValue)) {
                                                children.add(new ChildParams(subValue, pd));
                                            } else {
                                                fillMetadataForNonBeanValue(entryMetadata, metadataName, subValue);
                                            }
                                        }
                                    } else {
                                        children.add(new ChildParams(value, pd));
                                    }
                                } else {
                                    fillMetadataForNonBeanValue(entryMetadata, metadataName, value);
                                }
                            }
                        }
                    }
                } else {
                    if (colObj.length <= 0) {
                        return false;
                    }

                    String metadataName = getPropertyNameMapping(bean.getClass(), parentPd.getDisplayName());
                    if (metadataName == null) {
                        metadataName = parentPd.getDisplayName();
                    }

                    entryMetadata.add(TikaCoreProperties.TITLE, metadataName);// adds the name property without prefix
                    entryMetadata.add(TikaCoreProperties.RESOURCE_NAME_KEY, metadataName);

                    if (prefix != null && prefix.length() > 0) {
                        metadataName = prefix + metadataName;
                    }

                    for (int i = 0; i < colObj.length; i++) {
                        Object value = colObj[i];
                        if (isBean(value)) {
                            children.add(new ChildParams(value, parentPd));
                        } else {
                            fillMetadataForNonBeanValue(entryMetadata, metadataName, value);
                        }
                    }
                }

                if (children.size() > 0) {
                    // entryMetadata.set(BasicProps.HASCHILD, "true");
                    entryMetadata.set(ExtraProperties.EMBEDDED_FOLDER, "true");
                }

                entryMetadata.set(ExtraProperties.PARENT_VIRTUAL_ID, Integer.toString(parentSeq));
                entryMetadata.set(ExtraProperties.ITEM_VIRTUAL_ID, Integer.toString(seq));
                extractor.parseEmbedded(new EmptyInputStream(), handler, entryMetadata, true);

                int childSeq = seq;
                int count = 0;
                if (children.size() > 0) {
                    for (Iterator<ChildParams> iterator = children.iterator(); iterator.hasNext();) {
                        ChildParams cp = iterator.next();
                        childSeq++;
                        if (this.extractEmbedded(childSeq, context, entryMetadata, cp.pd, handler, cp.value, seq)) {
                            count++;
                        }
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
            return true;
        } else {
            return false;
        }
    }

    private void fillMetadataForNonBeanValue(Metadata entryMetadata, String metadataName, Object value) {
        if (value instanceof Date) {
            if (isLocalTime) {
                TimeZone tz = identifiedTimeZone != null ? identifiedTimeZone : TimeZone.getDefault();
                // this can still be wrong by 1h when daylight saving ends
                // there is no way to know if clock was already turned back by 1h or not
                int offset = tz.getOffset(((Date) value).getTime() - tz.getRawOffset());
                value = new Date(((Date) value).getTime() - offset);
            }
            entryMetadata.add(metadataName, DateUtil.dateToString((Date) value));
        } else if (!isCollectionEmpty(value)) {
            entryMetadata.add(metadataName, value.toString());
        }
    }

    private String parseQuery(String resolvedReferencedQuery, Object value) {
        ArrayList<String> variables = new ArrayList<>();
        EvaluationContext context = new StandardEvaluationContext(value);

        String result = resolvedReferencedQuery;
        int i = result.indexOf("${");
        while (i >= 0) {
            int end = result.indexOf("}", i + 2);
            String varString = result.substring(i + 2, end);
            variables.add(varString);
            i = result.indexOf("${", i + 2);
        }

        for (String var : variables) {
            try {
                String parsedVar = elparser.parseExpression(var).getValue(context, String.class);
                result = result.replace("${" + var + "}", StringUtils.defaultString(parsedVar));
            } catch (Exception e) {
                logger.error("Error parsing expression: " + var, e);
            }
        }

        return result;
    }

    public boolean isCollectionEmpty(Object value) {
        try {
            return CollectionUtils.sizeIsEmpty(value);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isCollectionNotEmpty(Object value) {
        try {
            return CollectionUtils.size(value) > 0;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isBean(Object value) {
        BeanInfo beanInfo;
        try {
            if (value instanceof Collection) {
                if (((Collection) value).size() <= 0) {
                    return false;// considers empty collections as non bean objects
                }
            }
            if (value.getClass().isArray()) {
                if (((Object[]) value).length <= 0) {
                    return false;// considers empty arrays as non bean objects
                }
            }
            if (value instanceof Map) {
                if (((Map) value).size() <= 0) {
                    return false;// considers empty maps as non bean object
                }
            }
            beanInfo = Introspector.getBeanInfo(value.getClass());
            if (beanInfo.getPropertyDescriptors().length > 1 && !(value instanceof String) && !(value instanceof Date)) {
                return true;
            }

        } catch (IntrospectionException e) {
            return false;
        }
        return false;
    }

    public String getNameProperty() {
        return nameProperty;
    }

    public void setNameProperty(String nameProperty) {
        this.nameProperty = nameProperty;
    }

    public void registerCollectionPropertyToMerge(Class<?> beanClass, String propertyName) {
        Set<String> set = collectionPropertiesToMergeMap.get(beanClass);
        if (set == null) {
            collectionPropertiesToMergeMap.put(beanClass, set = new HashSet<String>());
        }
        set.add(propertyName);
    }

    public void registerPropertyNameMapping(Class beanClass, String propertyName, String metadataPropName) {
        HashMap<String, String> map = propertyNameMapping.get(beanClass);
        if (map == null) {
            propertyNameMapping.put(beanClass, map = new HashMap<>());
        }
        map.put(propertyName, metadataPropName);
    }

    public String getPropertyNameMapping(Class beanClass, String propertyName) {
        HashMap<String, String> map = propertyNameMapping.get(beanClass);
        if (map == null) {
            return null;
        }
        return map.get(propertyName);
    }

    public void registerTransformationMapping(Class beanClass, String metadataPropName, String transformationExpression) {
        String[] value = new String[2];
        value[0] = metadataPropName;
        value[1] = transformationExpression;

        ArrayList<String[]> l = transformationMapping.get(beanClass);
        if (l == null) {
            l = new ArrayList<String[]>();
            transformationMapping.put(beanClass, l);
        }
        l.add(value);
    }

    public List<String[]> getTransformationMapping(Class beanClass) {
        return transformationMapping.get(beanClass);
    }

    private boolean shouldMergeCollectionProperty(Object bean, PropertyDescriptor pd) {
        Set<String> propertiesToMerge = collectionPropertiesToMergeMap.get(bean.getClass());
        return propertiesToMerge != null && propertiesToMerge.contains(pd.getDisplayName());
    }

    public boolean isLocalTime() {
        return isLocalTime;
    }

    public void setLocalTime(boolean isLocalTime) {
        this.isLocalTime = isLocalTime;
        if (this.isLocalTime && parseContext != null) {
            configureIdentifiedTimezone(parseContext);
        }
    }

    private void configureIdentifiedTimezone(ParseContext context) {
        ItemInfo itemInfo = context.get(ItemInfo.class);
        String caminho = itemInfo.getPath().toLowerCase().replace("\\", "/");
        ICaseData caseData = context.get(ICaseData.class);
        if (caseData != null) {
            HashMap<String, TimeZone> tzs = (HashMap<String, TimeZone>) caseData.getCaseObject(ICaseData.TIMEZONE_INFO_KEY);
            if (tzs != null) {
                if (tzs.size() == -1) {
                    identifiedTimeZone = tzs.values().iterator().next();
                } else {
                    int i = caminho.lastIndexOf("/");
                    boolean found = false;
                    while (!found && i > 0) {
                        caminho = caminho.substring(0, i);
                        for (Entry<String, TimeZone> entry : tzs.entrySet()) {
                            if (entry.getKey().startsWith(caminho)) {
                                found = true;
                                identifiedTimeZone = entry.getValue();
                                break;
                            }
                        }
                        i = caminho.lastIndexOf("/");
                    }

                }
            }
        }
    }
}
