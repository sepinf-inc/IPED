package br.gov.pf.labld.graph;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tika.metadata.Message;
import org.apache.tika.metadata.Metadata;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.RelationshipType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberMatch;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.Leniency;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

import br.gov.pf.labld.graph.GraphConfiguration.GraphEntity;
import br.gov.pf.labld.graph.GraphConfiguration.GraphEntityMetadata;
import dpf.ap.gpinf.telegramextractor.TelegramParser;
import dpf.mg.udi.gpinf.vcardparser.VCardParser;
import dpf.mg.udi.gpinf.whatsappextractor.WhatsAppParser;
import dpf.mt.gpinf.skype.parser.SkypeParser;
import dpf.sp.gpinf.indexer.CmdLineArgs;
import dpf.sp.gpinf.indexer.WorkerProvider;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.datasource.IPEDReader;
import dpf.sp.gpinf.indexer.datasource.UfedXmlReader;
import dpf.sp.gpinf.indexer.parsers.OutlookPSTParser;
import dpf.sp.gpinf.indexer.parsers.ufed.UfedMessage;
import dpf.sp.gpinf.indexer.process.task.AbstractTask;
import dpf.sp.gpinf.indexer.process.task.regex.RegexHits;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.IPEDException;
import dpf.sp.gpinf.indexer.util.Util;
import iped3.IItem;
import iped3.util.BasicProps;
import iped3.util.ExtraProperties;
import iped3.util.MediaTypes;
import macee.core.Configurable;

public class GraphTask extends AbstractTask {

    private static final Logger logger = LoggerFactory.getLogger(GraphTask.class);

    public static final String DB_NAME = "graph.db";
    public static final String DB_PATH = "neo4j/databases";
    public static final String GENERATED_PATH = "neo4j/generated";
    public static final String RELATIONSHIP_ID = "relId";
    public static final String RELATIONSHIP_SOURCE = "dataSource";

    private static Pattern ignoreEmailChars = Pattern.compile("[<>'\";()]");

    // TODO externalize to config file
    private static Pattern emailPattern = Pattern
            .compile("[0-9a-zA-Z\\+\\.\\_\\%\\-\\#\\!]{1,64}\\@[0-9a-zA-Z\\-]{2,64}(\\.[0-9a-zA-Z\\-]{2,25}){1,3}");
    private static Pattern whatsappPattern = Pattern.compile("([0-9]{7,20})\\@[sg]\\.whatsapp\\.net");
    private static Pattern oldBRPhonePattern = Pattern.compile("(\\+55 \\d\\d )([7-9]\\d{3}\\-\\d{4})");

    // TODO externalize to config file
    private static String[] contactMimes = { VCardParser.VCARD_MIME.toString(), OutlookPSTParser.OUTLOOK_CONTACT_MIME,
            SkypeParser.CONTACT_MIME_TYPE, WhatsAppParser.WHATSAPP_CONTACT.toString(),
            TelegramParser.TELEGRAM_CONTACT.toString(), "application/windows-adress-book",
            "application/x-ufed-contact" };

    private static final int MAX_PHONE_CACHE_KEY = 50 * 1024;

    private static Map<String, SortedSet<String>> formattedPhonesCache = Collections
            .synchronizedMap(new LinkedHashMap<String, SortedSet<String>>(16, 0.75f, true) {

                private static final long serialVersionUID = 1L;

                @Override
                protected boolean removeEldestEntry(Entry<String, SortedSet<String>> eldest) {
                    return this.size() > 500;
                }
            });

    private GraphConfiguration configuration;

    private boolean enabled = false;

    private static GraphFileWriter graphFileWriter;

    private static Map<String, NodeValues> datasourceOwnerMap = new HashMap<>();

    @Override
    public List<Configurable<?>> getConfigurables() {
        return Arrays.asList(new GraphTaskConfig());
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {
        GraphTaskConfig config = configurationManager.findObject(GraphTaskConfig.class);
        enabled = config.isEnabled();
        if (enabled) {
            configuration = config.getConfiguration();

            if (graphFileWriter == null) {
                graphFileWriter = new GraphFileWriter(new File(output, GENERATED_PATH),
                        configuration.getDefaultEntity());

                if (configuration.getProcessProximityRelationships() && caseData.isIpedReport()) {
                    logger.warn(
                            "process-proximity-relationships not supported in reports yet, it will be ignored, resulting in different graphs!");
                }
            }

            CmdLineArgs args = (CmdLineArgs) caseData.getCaseObject(CmdLineArgs.class.getName());
            if (args.isAppendIndex() || args.isRestart() || args.isContinue()) {
                File graphDbOutput = new File(output, GraphTask.DB_PATH);
                if (graphDbOutput.exists()) {
                    // TODO test if LOAD CSV Cypher command is faster than rebuilding all database
                    // from scratch with bulk import tool
                    try {
                        IOUtil.deleteDirectory(graphDbOutput, false);
                    } catch (IOException e) {
                        throw new IPEDException("Error appending to graph database, currently it can't be in use!");
                    }
                }
            }
        }
    }

    public static void commit() throws IOException {
        if (graphFileWriter != null) {
            logger.info("Commiting graph CSVs...");
            graphFileWriter.flush();
            logger.info("Commiting graph CSVs finished.");
        }
    }

    private void finishGraphGeneration() throws IOException {
        WorkerProvider.getInstance().firePropertyChange("mensagem", "", "Generating graph database...");
        logger.info("Generating graph database...");
        File graphDbOutput = new File(output, GraphTask.DB_PATH);
        File graphDbGenerated = new File(output, GraphTask.GENERATED_PATH);
        GraphGenerator graphGenerator = new GraphGenerator();
        graphGenerator.generate(graphDbOutput, graphDbGenerated);
        logger.info("Generating graph database finished.");
    }

    @Override
    public void finish() throws Exception {
        if (graphFileWriter != null) {
            WorkerProvider.getInstance().firePropertyChange("mensagem", "", "Finishing graph CSVs...");
            logger.info("Finishing graph CSVs...");
            graphFileWriter.close(caseData.isIpedReport());
            logger.info("Finishing graph CSVs finished.");
            if (caseData.isIpedReport()) {
                File prevCaseModuleDir = (File) caseData.getCaseObject(IPEDReader.ORIG_CASE_MODULE_DIR);
                File prevCSVRoot = new File(prevCaseModuleDir, GraphTask.GENERATED_PATH);
                if (prevCSVRoot.exists() && prevCSVRoot.isDirectory()) {
                    for (File file : prevCSVRoot.listFiles()) {
                        File target = new File(output, GraphTask.GENERATED_PATH + "/" + file.getName());
                        if (file.getName().startsWith(GraphFileWriter.NODE_CSV_PREFIX)
                                || file.getName().startsWith(GraphFileWriter.REPLACE_NAME)) {
                            IOUtil.copiaArquivo(file, target);
                        }
                    }
                }
                graphFileWriter = new GraphFileWriter(new File(output, GENERATED_PATH),
                        configuration.getDefaultEntity());
                graphFileWriter.close();
            }
            finishGraphGeneration();
            WorkerProvider.getInstance().firePropertyChange("mensagem", "", "Compressing graph CSVs...");
            logger.info("Compressing graph CSVs...");
            graphFileWriter.compressGeneratedCSVFiles();
            logger.info("Compressing graph CSVs finished.");
            graphFileWriter = null;
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void process(IItem evidence) throws Exception {

        if (!isEnabled() || !evidence.isToAddToCase()) {
            return;
        }

        processEvidence(evidence);
    }

    private void processEvidence(IItem evidence) throws IOException {

        // old item->node model and gui dependent code was moved to class below
        // ItemNodeGenerator itemNodeGenerator = new ItemNodeGenerator(caseData,
        // configuration, graphFileWriter);
        // itemNodeGenerator.generateNodeForItem(evidence);

        if (includeEvidence(evidence)) {
            processCommunicationMetadata(evidence);
            processContacts(evidence);
            processWifi(evidence);
            processUserAccount(evidence);
            if (configuration.getProcessProximityRelationships() && !caseData.isIpedReport()) {
                processExtraAttributes(evidence);
            }
        }

    }

    private boolean includeEvidence(IItem evidence) {
        boolean include = true;

        Pattern includeCategoriesPattern = configuration.getIncludeCategoriesPattern();
        Pattern excludeCategoriesPattern = configuration.getExcludeCategoriesPattern();

        for (String category : evidence.getCategorySet()) {
            include = include && includeCategoriesPattern.matcher(category).matches();
            include = include && !excludeCategoriesPattern.matcher(category).matches();
        }

        return include;
    }

    // TODO externalize to config file
    private static String getRelationType(String mediaType) {
        if (WhatsAppParser.WHATSAPP_MESSAGE.toString().equals(mediaType)
                || WhatsAppParser.WHATSAPP_ATTACHMENT.toString().equals(mediaType)
                || TelegramParser.TELEGRAM_MESSAGE.toString().equals(mediaType)
                || TelegramParser.TELEGRAM_ATTACHMENT.toString().equals(mediaType)
                || SkypeParser.MESSAGE_MIME_TYPE.toString().equals(mediaType)
                || SkypeParser.ATTACHMENT_MIME_TYPE.toString().equals(mediaType)
                || SkypeParser.FILETRANSFER_MIME_TYPE.toString().equals(mediaType)
                || MediaTypes.UFED_MESSAGE_ATTACH_MIME.toString().equals(mediaType)
                || MediaTypes.UFED_MESSAGE_MIME.equals(mediaType)) {
            return "message";
        }
        if (mediaType.startsWith("message") || mediaType.equals("application/vnd.ms-outlook")) {
            return "email";
        }
        for (String contactMime : contactMimes) {
            if (contactMime.equals(mediaType))
                return "contact";
        }
        if (SkypeParser.ACCOUNT_MIME_TYPE.toString().equals(mediaType)
                || WhatsAppParser.WHATSAPP_ACCOUNT.toString().equals(mediaType)
                || TelegramParser.TELEGRAM_ACCOUNT.toString().equals(mediaType)
                || mediaType.equals("application/x-ufed-user") || mediaType.equals("application/x-ufed-useraccount")) {
            return "useraccount";
        }

        if (WhatsAppParser.WHATSAPP_CALL.toString().equals(mediaType)
                || TelegramParser.TELEGRAM_CALL.toString().equals(mediaType)
                || MediaTypes.UFED_CALL_MIME.equals(mediaType)) {
            return "call";
        }
        int ufedIdx = mediaType.indexOf(UfedXmlReader.UFED_MIME_PREFIX);
        if (ufedIdx > -1) {
            return mediaType.substring(ufedIdx + UfedXmlReader.UFED_MIME_PREFIX.length());
        }
        return "generic";
    }

    private static class NodeValues {
        Label label;
        String propertyName;
        Object propertyValue;
        Map<String, Object> props = new HashMap<>();

        NodeValues(Label label, String propertyName, Object propertyValue) {
            this.label = label;
            this.propertyName = propertyName;
            this.propertyValue = propertyValue;
        }

        void addProp(String key, Object value) {
            if (value != null && (!(value instanceof String)) || !((String) value).isEmpty()) {
                if (value instanceof String[]) {
                    value = new ArrayList<>(Arrays.asList((String[]) value));
                }
                props.put(key, value);
            }
        }
    }

    // PhoneNumberUtil is thread safe???
    private SortedSet<String> getPhones(String value) {
        SortedSet<String> result = formattedPhonesCache.get(value);
        if (result != null) {
            return result;
        }
        result = new TreeSet<>();
        PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
        Set<PhoneNumber> phoneNumbers = new HashSet<>();
        Matcher whatsappMacher = whatsappPattern.matcher(value);
        while (whatsappMacher.find()) {
            String phone = whatsappMacher.group(1);
            value = value.replace(phone, " ");
            phone = "+" + phone;
            try {
                phoneNumbers.add(phoneUtil.parse(phone, null));
            } catch (NumberParseException e) {
                e.printStackTrace();
            }
        }
        for (PhoneNumberMatch m : phoneUtil.findNumbers(value, configuration.getPhoneRegion(), Leniency.POSSIBLE,
                Integer.MAX_VALUE)) {
            phoneNumbers.add(m.number());
        }
        for (PhoneNumber phoneNumber : phoneNumbers) {
            String phone = phoneUtil.format(phoneNumber, PhoneNumberFormat.INTERNATIONAL);
            if (configuration.getPhoneRegion().equals("BR")) {
                Matcher matcher = oldBRPhonePattern.matcher(phone);
                if (matcher.matches()) {
                    phone = matcher.group(1) + "9" + matcher.group(2);
                }
            }
            result.add(phone);
        }
        if (value.length() <= MAX_PHONE_CACHE_KEY) {
            formattedPhonesCache.put(value, result);
        }
        return result;
    }

    private NodeValues getPhoneNodeValues(String value) {
        Set<String> phones = getPhones(value);
        if (!phones.isEmpty()) {
            return new NodeValues(DynLabel.label(GraphConfiguration.PHONE_LABEL), ExtraProperties.USER_PHONE,
                    phones.iterator().next());
        }
        // System.out.println("invalid phone=" + value.trim());
        return null;
    }

    private NodeValues getGenericNodeValues(String value) {
        return new NodeValues(DynLabel.label("GENERIC"), "entity", value.trim().toLowerCase());
    }

    private SortedSet<String> getEmails(String text) {
        return getEmails(text, true);
    }

    private SortedSet<String> getEmails(String text, boolean toLowerCase) {
        SortedSet<String> result = new TreeSet<>();
        Matcher matcher = emailPattern.matcher(text);
        while (matcher.find()) {
            String email = matcher.group();
            if (toLowerCase)
                email = email.toLowerCase();
            result.add(email);
        }
        return result;
    }

    private NodeValues getEmailNodeValues(String value) {
        Set<String> emails = getEmails(value, false);
        if (!emails.isEmpty()) {
            NodeValues nv = new NodeValues(DynLabel.label(GraphConfiguration.EMAIL_LABEL), ExtraProperties.USER_EMAIL,
                    emails.iterator().next().toLowerCase());
            for (String email : emails) {
                value = value.replace(email, "");
            }
            String name = ignoreEmailChars.matcher(value).replaceAll(" ").trim();
            if (!name.isEmpty()) {
                nv.addProp(ExtraProperties.USER_NAME, name);
            }
            return nv;
        }
        return null;
    }

    private NodeValues getAccountNodeValues(String value, Metadata meta) {
        if (meta == null)
            return null;
        String service = meta.get(ExtraProperties.UFED_META_PREFIX + "SourceApplication");
        if (service == null)
            service = meta.get(ExtraProperties.UFED_META_PREFIX + "ServiceType");
        if (service == null)
            service = meta.get(ExtraProperties.UFED_META_PREFIX + "Source");
        if (service == null) {
            service = meta.get(ExtraProperties.USER_ACCOUNT_TYPE);
        }
        if (service == null || WhatsAppParser.WHATSAPP.equalsIgnoreCase(service)) // phone will be decoded from whatsapp
            return null;

        NodeValues nv = null;
        value = value.trim();
        int idx = value.lastIndexOf('(');
        if (idx != -1 && value.endsWith(")")) {
            String account = value.substring(idx + 1, value.length() - 1);
            String name = value.substring(0, idx).trim();
            nv = new NodeValues(DynLabel.label(GraphConfiguration.PERSON_LABEL), ExtraProperties.USER_ACCOUNT,
                    getServiceAccount(account, service));
            if (!name.isEmpty()) {
                nv.addProp(ExtraProperties.USER_NAME, name);
            }
        }
        if (nv == null) {
            nv = new NodeValues(DynLabel.label(GraphConfiguration.PERSON_LABEL), ExtraProperties.USER_ACCOUNT,
                    getServiceAccount(value.trim(), service));
        }
        return nv;
    }

    private NodeValues getNodeValues(String value, Metadata metadata) {
        NodeValues nv1 = null;
        String accountType = metadata.get(ExtraProperties.USER_ACCOUNT_TYPE);
        if (SkypeParser.SKYPE.equals(accountType))
            nv1 = getAccountNodeValues(value, metadata);
        if (nv1 == null)
            nv1 = getEmailNodeValues(value);
        if (nv1 == null)
            nv1 = getAccountNodeValues(value, metadata);
        if (nv1 == null)
            nv1 = getPhoneNodeValues(value);
        if (nv1 == null)
            nv1 = getGenericNodeValues(value);
        return nv1;
    }

    private void processCommunicationMetadata(IItem evidence) throws IOException {
        Metadata metadata = evidence.getMetadata();
        String sender = metadata.get(Message.MESSAGE_FROM);
        if (sender == null || sender.trim().isEmpty()) {
            return;
        }
        if (MediaTypes.isInstanceOf(evidence.getMediaType(), MediaTypes.UFED_MESSAGE_MIME)
                && UfedMessage.SYSTEM_MESSAGE.equals(sender)) {
            return;
        }

        String relationType = getRelationType(evidence.getMediaType().toString());
        NodeValues nv1 = getNodeValues(sender, evidence.getMetadata());

        graphFileWriter.writeNode(nv1.label, nv1.propertyName, nv1.propertyValue, nv1.props);

        RelationshipType relationshipType = DynRelationshipType.withName(relationType);
        Map<String, Object> relProps = new HashMap<>();
        relProps.put(RELATIONSHIP_ID, evidence.getId());
        relProps.put(RELATIONSHIP_SOURCE, evidence.getDataSource().getUUID());

        List<String> recipients = new ArrayList<>();
        recipients.addAll(Arrays.asList(metadata.getValues(Message.MESSAGE_TO)));
        recipients.addAll(Arrays.asList(metadata.getValues(Message.MESSAGE_CC)));
        recipients.addAll(Arrays.asList(metadata.getValues(Message.MESSAGE_BCC)));

        for (String recipient : recipients) {
            NodeValues nv2 = getNodeValues(recipient, evidence.getMetadata());
            graphFileWriter.writeNode(nv2.label, nv2.propertyName, nv2.propertyValue, nv2.props);
            graphFileWriter.writeRelationship(nv1.label, nv1.propertyName, nv1.propertyValue, nv2.label,
                    nv2.propertyName, nv2.propertyValue, relationshipType, relProps);
        }
    }

    private void processUserAccount(IItem item) throws IOException {

        String relationType = getRelationType(item.getMediaType().toString());
        if (!relationType.equals("useraccount")) {
            return;
        }

        SortedSet<String> msisdnPhones = null;
        // often msisdn is not reliable to be used to merge person nodes
        /*
         * List<String> msisdns =
         * (List<String>)caseData.getCaseObject(UfedXmlReader.MSISDN_PROP +
         * item.getDataSource().getUUID()); if(msisdns != null && msisdns.size() == 1) {
         * msisdnPhones = getPhones(msisdns.toString()); }
         */
        writePersonNode(item, msisdnPhones);

    }

    private NodeValues writePersonNode(IItem item, SortedSet<String> msisdnPhones) throws IOException {

        SortedSet<String> possibleEmails = new TreeSet<>();// getEmails(itemText);
        possibleEmails
                .addAll(Arrays.asList(item.getMetadata().getValues(ExtraProperties.UFED_META_PREFIX + "EmailAddress")));
        possibleEmails
                .addAll(Arrays.asList(item.getMetadata().getValues(ExtraProperties.UFED_META_PREFIX + "Username")));
        possibleEmails.addAll(Arrays.asList(item.getMetadata().getValues(ExtraProperties.UFED_META_PREFIX + "UserID")));
        possibleEmails.addAll(Arrays.asList(item.getMetadata().getValues(ExtraProperties.USER_EMAIL)));
        SortedSet<String> emails = getEmails(possibleEmails.toString());

        List<String> possiblePhones = new ArrayList<>();
        possiblePhones
                .addAll(Arrays.asList(item.getMetadata().getValues(ExtraProperties.UFED_META_PREFIX + "PhoneNumber")));
        possiblePhones
                .addAll(Arrays.asList(item.getMetadata().getValues(ExtraProperties.UFED_META_PREFIX + "Username")));
        possiblePhones.addAll(Arrays.asList(item.getMetadata().getValues(ExtraProperties.USER_PHONE)));
        for (String id : item.getMetadata().getValues(ExtraProperties.UFED_META_PREFIX + "UserID")) {
            Matcher matcher = whatsappPattern.matcher(id);
            if (matcher.find())
                possiblePhones.add(id);
        }
        SortedSet<String> formattedPhones = getPhones(possiblePhones.toString());
        if (msisdnPhones == null)
            msisdnPhones = Collections.emptySortedSet();
        formattedPhones.addAll(msisdnPhones);

        String[] name = item.getMetadata().getValues(ExtraProperties.UFED_META_PREFIX + "Name");
        if (name.length == 0)
            name = item.getMetadata().getValues(ExtraProperties.USER_NAME);

        String[] accounts = item.getMetadata().getValues(ExtraProperties.USER_ACCOUNT);
        if (accounts.length == 0)
            accounts = item.getMetadata().getValues(ExtraProperties.UFED_META_PREFIX + "Username");
        if (accounts.length == 0) {
            ArrayList<String> ids = new ArrayList<>();
            for (String id : item.getMetadata().getValues(ExtraProperties.UFED_META_PREFIX + "UserID")) {
                if (!id.contains("whatsapp.net"))
                    ids.add(id);
            }
            accounts = ids.toArray(new String[ids.size()]);
        }

        String[] accountType = item.getMetadata().getValues(ExtraProperties.UFED_META_PREFIX + "ServiceType");
        if (accountType.length == 0)
            accountType = item.getMetadata().getValues(ExtraProperties.UFED_META_PREFIX + "Source");
        if (accountType.length == 0)
            accountType = item.getMetadata().getValues(ExtraProperties.USER_ACCOUNT_TYPE);

        String[] org = item.getMetadata().getValues(ExtraProperties.UFED_META_PREFIX + "Organization");
        if (org.length == 0)
            org = item.getMetadata().getValues(ExtraProperties.USER_ORGANIZATION);

        String[] address = item.getMetadata().getValues(ExtraProperties.USER_ADDRESS);
        if (address.length == 0)
            address = new String[] { getUfedAddress(item.getMetadata()) };

        String[] notes = item.getMetadata().getValues(ExtraProperties.UFED_META_PREFIX + "Notes");
        if (notes.length == 0)
            notes = item.getMetadata().getValues(ExtraProperties.USER_NOTES);

        NodeValues nv1;
        if (!msisdnPhones.isEmpty()) {
            nv1 = new NodeValues(DynLabel.label(GraphConfiguration.PERSON_LABEL), ExtraProperties.USER_PHONE,
                    msisdnPhones.first());
        } else if (!formattedPhones.isEmpty()) {
            nv1 = new NodeValues(DynLabel.label(GraphConfiguration.PERSON_LABEL), ExtraProperties.USER_PHONE,
                    formattedPhones.first());
        } else if (!emails.isEmpty()) {
            nv1 = new NodeValues(DynLabel.label(GraphConfiguration.PERSON_LABEL), ExtraProperties.USER_EMAIL,
                    emails.first());
        } else if (accounts.length != 0 && accountType.length != 0) {
            String account = getNonUUIDAccount(accounts);
            nv1 = new NodeValues(DynLabel.label(GraphConfiguration.PERSON_LABEL), ExtraProperties.USER_ACCOUNT,
                    getServiceAccount(account, accountType[0]));
        } else {
            nv1 = new NodeValues(DynLabel.label("GENERIC"), "entity", item.getName());
        }

        nv1.addProp(ExtraProperties.USER_PHONE, formattedPhones);
        nv1.addProp(ExtraProperties.USER_EMAIL, emails);
        nv1.addProp(ExtraProperties.USER_NAME, name);
        nv1.addProp(ExtraProperties.USER_ORGANIZATION, org);
        nv1.addProp(ExtraProperties.USER_ADDRESS, address);
        nv1.addProp(ExtraProperties.USER_NOTES, notes);
        if (accounts.length != 0 && accountType.length != 0) {
            String serviceAccount = getServiceAccount(getNonUUIDAccount(accounts), accountType[0]);
            nv1.addProp(ExtraProperties.USER_ACCOUNT, serviceAccount);
        }

        String uniqueId = graphFileWriter.writeNode(nv1.label, nv1.propertyName, nv1.propertyValue, nv1.props);

        for (String email : emails) {
            graphFileWriter.writeNodeReplace(DynLabel.label(GraphConfiguration.PERSON_LABEL),
                    ExtraProperties.USER_EMAIL, email, uniqueId);
            graphFileWriter.writeNodeReplace(DynLabel.label(GraphConfiguration.EMAIL_LABEL), ExtraProperties.USER_EMAIL,
                    email, uniqueId);
        }
        for (String phone : formattedPhones) {
            graphFileWriter.writeNodeReplace(DynLabel.label(GraphConfiguration.PERSON_LABEL),
                    ExtraProperties.USER_PHONE, phone, uniqueId);
            graphFileWriter.writeNodeReplace(DynLabel.label(GraphConfiguration.PHONE_LABEL), ExtraProperties.USER_PHONE,
                    phone, uniqueId);
        }
        for (String account : accounts) {
            for (String service : accountType) {
                graphFileWriter.writeNodeReplace(DynLabel.label(GraphConfiguration.PERSON_LABEL),
                        ExtraProperties.USER_ACCOUNT, getServiceAccount(account, service), uniqueId);
            }
        }

        return nv1;
    }

    private String getNonUUIDAccount(String[] accounts) {
        String account = accounts[0];
        for (String a : accounts) {
            try {
                UUID.fromString(a);
            } catch (IllegalArgumentException e) {
                account = a;
                break;
            }
        }
        return account;
    }

    private String getServiceAccount(String account, String serviceType) {
        return account + " (" + serviceType + ")";
    }

    private String getUfedAddress(Metadata a) {
        StringBuilder sb = new StringBuilder();
        String val = a.get(ExtraProperties.UFED_META_PREFIX + "Street1");
        if (val != null)
            sb.append(val).append(" ");
        val = a.get(ExtraProperties.UFED_META_PREFIX + "Street2");
        if (val != null)
            sb.append(val).append(" ");
        val = a.get(ExtraProperties.UFED_META_PREFIX + "City");
        if (val != null)
            sb.append(val).append(" ");
        val = a.get(ExtraProperties.UFED_META_PREFIX + "State");
        if (val != null)
            sb.append(val).append(" ");
        val = a.get(ExtraProperties.UFED_META_PREFIX + "Country");
        if (val != null)
            sb.append(val).append(" ");
        val = a.get(ExtraProperties.UFED_META_PREFIX + "PostalCode");
        if (val != null)
            sb.append(val).append(" ");
        return sb.toString().trim();
    }

    private NodeValues getGenericOwnerNode(IItem item) throws IOException {
        String evidenceUUID = item.getDataSource().getUUID();
        synchronized (this.getClass()) {
            NodeValues nv1 = datasourceOwnerMap.get(evidenceUUID);
            if (nv1 == null) {
                nv1 = new NodeValues(DynLabel.label(GraphConfiguration.DATASOURCE_LABEL), BasicProps.EVIDENCE_UUID,
                        evidenceUUID);
                nv1.addProp(BasicProps.NAME, Util.getRootName(item.getPath()));
                graphFileWriter.writeNode(nv1.label, nv1.propertyName, nv1.propertyValue, nv1.props);
                datasourceOwnerMap.put(evidenceUUID, nv1);

                List<String> msisdns = (List<String>) caseData.getCaseObject(UfedXmlReader.MSISDN_PROP + evidenceUUID);
                if (msisdns != null && !msisdns.isEmpty()) {
                    NodeValues nv2 = this.getPhoneNodeValues(msisdns.get(0));
                    if (nv2 != null) {
                        String id = graphFileWriter.writeNode(nv2.label, nv2.propertyName, nv2.propertyValue);
                        graphFileWriter.writeNodeReplace(nv1.label, nv1.propertyName, nv1.propertyValue, id);
                    }
                }
            }
            return nv1;
        }
    }

    private void processContacts(IItem item) throws IOException {

        String relationType = getRelationType(item.getMediaType().toString());
        if (!relationType.equals("contact")) {
            return;
        }

        NodeValues nv1 = null;
        String contactOfAccount = item.getMetadata().get(ExtraProperties.CONTACT_OF_ACCOUNT);
        if (contactOfAccount != null) {
            String service = item.getMetadata().get(ExtraProperties.USER_ACCOUNT_TYPE);
            nv1 = new NodeValues(DynLabel.label(GraphConfiguration.PERSON_LABEL), ExtraProperties.USER_ACCOUNT,
                    getServiceAccount(contactOfAccount, service));
            graphFileWriter.writeNode(nv1.label, nv1.propertyName, nv1.propertyValue);
        }

        if (nv1 == null)
            nv1 = getGenericOwnerNode(item);

        NodeValues nv2 = writePersonNode(item, null);

        RelationshipType relationshipType = DynRelationshipType.withName(relationType);
        Map<String, Object> relProps = new HashMap<>();
        relProps.put(RELATIONSHIP_ID, item.getId());
        relProps.put(RELATIONSHIP_SOURCE, item.getDataSource().getUUID());

        graphFileWriter.writeRelationship(nv1.label, nv1.propertyName, nv1.propertyValue, nv2.label, nv2.propertyName,
                nv2.propertyValue, relationshipType, relProps);
    }

    private void processWifi(IItem item) throws IOException {

        String relationType = getRelationType(item.getMediaType().toString());
        if (!relationType.equals("wirelessnetwork")) {
            return;
        }

        NodeValues nv1 = getGenericOwnerNode(item);

        NodeValues nv2;
        Map<String, Object> nodeProps = new HashMap<>();

        String bssid = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "BSSId");
        String ssid = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "SSId");

        if (bssid != null) {
            nv2 = new NodeValues(DynLabel.label("WIFI"), "mac_address", bssid.trim());
            if (ssid != null) {
                nodeProps.put("SSID", ssid.trim());
            }
        } else if (ssid != null) {
            nv2 = new NodeValues(DynLabel.label("WIFI"), "SSID", ssid.trim());
        } else {
            return;
        }

        RelationshipType relationshipType = DynRelationshipType.withName(relationType);
        Map<String, Object> relProps = new HashMap<>();
        relProps.put(RELATIONSHIP_ID, item.getId());
        relProps.put(RELATIONSHIP_SOURCE, item.getDataSource().getUUID());

        graphFileWriter.writeNode(nv2.label, nv2.propertyName, nv2.propertyValue, nodeProps);

        graphFileWriter.writeRelationship(nv1.label, nv1.propertyName, nv1.propertyValue, nv2.label, nv2.propertyName,
                nv2.propertyValue, relationshipType, relProps);
    }

    @SuppressWarnings("unchecked")
    private void processExtraAttributes(IItem evidence) throws IOException {
        Map<String, Object> extraAttributeMap = evidence.getExtraAttributeMap();
        if (extraAttributeMap != null) {
            Set<Entry<String, Object>> entries = extraAttributeMap.entrySet();
            Set<String> relationsAdded = new HashSet<>();
            for (Entry<String, Object> entry : entries) {
                String key = entry.getKey();
                GraphEntity entity = configuration.getEntityWithMetadata(key);
                if (entity == null)
                    continue;
                GraphEntityMetadata metadata = entity.getMetadata(key);
                for (Entry<String, Object> entry2 : entries) {
                    String key2 = entry2.getKey();
                    GraphEntity entity2 = configuration.getEntityWithMetadata(key2);
                    if (entity2 == null)
                        continue;
                    GraphEntityMetadata metadata2 = entity2.getMetadata(key2);
                    processMatches(entity, metadata, (Collection<Object>) entry.getValue(), entity2, metadata2,
                            (Collection<Object>) entry2.getValue(), evidence, relationsAdded);
                }
            }
        }
    }

    private void processMatches(GraphEntity entity, GraphEntityMetadata metadata, Collection<Object> matches,
            GraphEntity entity2, GraphEntityMetadata metadata2, Collection<Object> matches2, IItem evidence,
            Set<String> relationsAdded) throws IOException {
        String labelName = entity.getLabel();
        String propertyName = metadata.getProperty();
        Label label = DynLabel.label(labelName);

        String labelName2 = entity2.getLabel();
        String propertyName2 = metadata2.getProperty();
        Label label2 = DynLabel.label(labelName2);

        if (matches != null && matches2 != null) {
            // RelationshipType relationshipType =
            // DynRelationshipType.withName(metadata.getRelationship());
            RelationshipType relationshipType = DynRelationshipType.withName(configuration.getDefaultRelationship());

            Map<String, Object> relProps = new HashMap<>();
            relProps.put(RELATIONSHIP_ID, evidence.getId());
            relProps.put(RELATIONSHIP_SOURCE, evidence.getDataSource().getUUID());

            Set<String> controlSet = new HashSet<>();
            for (Object match : matches) {
                RegexHits hit = (RegexHits) match;

                for (Object match2 : matches2) {

                    String propertyValue = match.toString();
                    String propertyValue2 = match2.toString();

                    String id1 = label.name() + propertyValue;
                    String id2 = label2.name() + propertyValue2;

                    if (id1.equals(id2)) {
                        continue;
                    }

                    RegexHits hit2 = (RegexHits) match2;

                    boolean nearHits = false;
                    long[] offsets1 = hit.getOffsets();
                    long[] offsets2 = hit2.getOffsets();
                    int maxDist = configuration.getMaxProximityDistance();
                    int i = 0, j = 0;
                    while (i < offsets1.length && j < offsets2.length) {
                        if (Math.abs(offsets1[i] - offsets2[j]) <= maxDist) {
                            nearHits = true;
                            break;
                        }
                        if (offsets1[i] < offsets2[j])
                            i++;
                        else
                            j++;
                    }
                    if (!nearHits) {
                        continue;
                    }

                    if (controlSet.add(id1)) {
                        graphFileWriter.writeNode(label, propertyName, propertyValue);
                    }
                    if (controlSet.add(id2)) {
                        graphFileWriter.writeNode(label2, propertyName2, propertyValue2);
                    }

                    String ids = id1.compareTo(id2) <= 0 ? id1 + "-" + id2 : id2 + "-" + id1;
                    if (relationsAdded.add(ids)) {
                        graphFileWriter.writeRelationship(label, propertyName, propertyValue, label2, propertyName2,
                                propertyValue2, relationshipType, relProps);
                    }
                }
            }
        }
    }

}
