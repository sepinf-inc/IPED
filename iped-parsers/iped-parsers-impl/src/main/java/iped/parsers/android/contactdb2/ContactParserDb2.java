package iped.parsers.android.contactdb2;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.sql.PreparedStatement;
import java.util.ArrayList;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToXMLContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.parsers.android.AbstractSqliteAndroidParser;
import iped.parsers.android.Contacto;
import iped.parsers.sqlite.SQLite3Parser;
import iped.parsers.standard.StandardParser;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.utils.EmptyInputStream;


/*@ARIEL ABURTO

https://github.com/AburtoArielPM

Parse para extraer contactos de una base de datos android.
*/

public class ContactParserDb2 extends AbstractSqliteAndroidParser {

    private static final long serialVersionUID = 1L;

    public static final MediaType ANDROID_SQLITE2_CONTACTOS = MediaType.application("x-android_sqlite_contactos2"); //$NON-NLS-1$

    public static final MediaType ANDROID_CONTACTOS2 = MediaType.application("x-android_contactos2"); //$NON-NLS-1$

    public static final MediaType ANDROID_CONTACTOS_REGISTRY2 = MediaType.application("x-android_contactos_registry2"); //$NON-NLS-1$

    private static Set<MediaType> SUPPORTED_TYPES = MediaType.set(ANDROID_SQLITE2_CONTACTOS);

    private SQLite3Parser sqliteParser = new SQLite3Parser();

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        TemporaryResources tmp = new TemporaryResources();
        TikaInputStream tis = TikaInputStream.get(stream, tmp);
        File contactosFile = tmp.createTemporaryFile();
        

        try (Connection connection = getConnection(tis, metadata, context)) {

            EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                    new ParsingEmbeddedDocumentExtractor(context));

            List<Contacto> contactos = getContactos(connection, metadata, context);
            

            if (extractor.shouldParseEmbedded(metadata)) {

                try (FileOutputStream tmpContactosFile = new FileOutputStream(contactosFile)) {

                    ToXMLContentHandler contactosHandler = new ToXMLContentHandler(tmpContactosFile, "UTF-8"); //$NON-NLS-1$ //Crea un archivo con las columnas de la base de datos. pero no muestra los datos.
                    Metadata contactosMetadata = new Metadata();
                    contactosMetadata.add(StandardParser.INDEXER_CONTENT_TYPE, ANDROID_CONTACTOS2.toString());
                    contactosMetadata.add(TikaCoreProperties.RESOURCE_NAME_KEY, "Contactos Android"); //$NON-NLS-1$
                    contactosMetadata.add(ExtraProperties.ITEM_VIRTUAL_ID, String.valueOf(0));
                    contactosMetadata.set(BasicProps.HASCHILD, "true"); //$NON-NLS-1$
                    contactosMetadata.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());

                    parseAndroidContactos(contactosHandler, contactosMetadata, context, contactos);

                    try (FileInputStream fis = new FileInputStream(contactosFile)) {
                        extractor.parseEmbedded(fis, handler, contactosMetadata, true);
                    }
                }

                int i = 0;

                for (Contacto d : contactos) {

                    if (!extractEntries)
                        break;

                    i++;
                    Metadata metadataContacto = new Metadata();

                    metadataContacto.add(StandardParser.INDEXER_CONTENT_TYPE, ANDROID_CONTACTOS_REGISTRY2.toString());
                    metadataContacto.add(TikaCoreProperties.RESOURCE_NAME_KEY, "Contact " + d.getDisplayName() +" | Phone:"+ d.getPhoneNumbers() + " || Id: " + i); //$NON-NLS-1$
                    metadataContacto.add(ExtraProperties.CONTACT_DISPLAY_NAME, d.getDisplayName());
                    metadataContacto.add(ExtraProperties.CONTACT_PHONE_NUMBERS, d.getPhoneNumbers());
                    metadataContacto.add(ExtraProperties.CONTACT_ACCOUNTS, d.getAccounts());
                    metadataContacto.add(ExtraProperties.CONTACT_EMAILS, d.getEmails());
                    metadataContacto.add(ExtraProperties.CONTACT_NOTES, d.getNotes());
                    metadataContacto.add(ExtraProperties.CONTACT_DELETED, d.getDeleted());
                    metadataContacto.add(ExtraProperties.PARENT_VIRTUAL_ID, String.valueOf(0));
                    metadataContacto.set(BasicProps.LENGTH, "");
                    metadataContacto.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());

                    extractor.parseEmbedded(new EmptyInputStream(), handler, metadataContacto, true);
                }

            }

        } catch (Exception e) {

            sqliteParser.parse(tis, handler, metadata, context);

            throw new TikaException("SQLite parsing exception", e); //$NON-NLS-1$

        } finally {
            tmp.close();
        }
    }

    private void parseAndroidContactos(ContentHandler handler, Metadata metadata, ParseContext context,
            List<Contacto> contactos) throws IOException, SAXException, TikaException {

        XHTMLContentHandler xHandler = null;

        try {

            xHandler = new XHTMLContentHandler(handler, metadata);
            xHandler.startDocument();

            xHandler.startElement("head"); //$NON-NLS-1$
            xHandler.startElement("style"); //$NON-NLS-1$
            xHandler.characters("table {border-collapse: collapse;} table, td, th {border: 1px solid black;}"); //$NON-NLS-1$
            xHandler.endElement("style"); //$NON-NLS-1$
            xHandler.endElement("head"); //$NON-NLS-1$

            xHandler.startElement("h2 align=center"); //$NON-NLS-1$
            xHandler.characters("IPED Digital Forensic Tool"); //$NON-NLS-1$
            xHandler.endElement("h2"); //$NON-NLS-1$
            xHandler.startElement("h2 align=center"); //$NON-NLS-1$
            xHandler.characters("Lista de Contactos"); //$NON-NLS-1$
            xHandler.endElement("h2"); //$NON-NLS-1$
            xHandler.startElement("br"); //$NON-NLS-1$
            xHandler.startElement("br"); //$NON-NLS-1$

            xHandler.startElement("table"); //$NON-NLS-1$

            xHandler.startElement("tr"); //$NON-NLS-1$

            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters(""); //$NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$

            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters("Display Name"); //$NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$

            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters("Phone Number"); //$NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$

            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters("Account"); //$NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$

            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters("Email"); //$NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$

            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters("Notes"); //$NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$

            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters("Deleted"); //$NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$

            xHandler.endElement("tr"); //$NON-NLS-1$

            int i = 1;

            for (Contacto d : contactos) {
                xHandler.startElement("tr"); //$NON-NLS-1$

                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(Integer.toString(i));
                xHandler.endElement("td"); //$NON-NLS-1$

                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(d.getDisplayName());
                xHandler.endElement("td"); //$NON-NLS-1$

                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(d.getPhoneNumbers());
                xHandler.endElement("td"); //$NON-NLS-1$

                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(d.getAccounts());
                xHandler.endElement("td"); //$NON-NLS-1$

                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(d.getEmails());
                xHandler.endElement("td"); //$NON-NLS-1$

                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(d.getNotes());
                xHandler.endElement("td"); //$NON-NLS-1$

                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(d.getDeleted());
                xHandler.endElement("td"); //$NON-NLS-1$

                xHandler.endElement("tr"); //$NON-NLS-1$

                i++;
            }

            xHandler.endElement("table"); //$NON-NLS-1$

            xHandler.endDocument();

        } finally {
            if (xHandler != null)
                xHandler.endDocument();
        }
    }

        protected List<Contacto> getContactos(Connection connection, Metadata metadata, ParseContext context) throws SQLException {
            List<Contacto> contactos = new ArrayList<>();
            String sql = "SELECT mimetype, data1, name_raw_contact.display_name AS display_name " +
            "FROM raw_contacts JOIN contacts ON (raw_contacts.contact_id=contacts._id) " +
            "JOIN raw_contacts AS name_raw_contact ON(name_raw_contact_id=name_raw_contact._id) " +
            "LEFT OUTER JOIN data ON (data.raw_contact_id=raw_contacts._id) " +
            "LEFT OUTER JOIN mimetypes ON (data.mimetype_id=mimetypes._id) " +
            "WHERE mimetype = 'vnd.android.cursor.item/phone_v2' OR mimetype = 'vnd.android.cursor.item/email_v2' " +
            "ORDER BY name_raw_contact.display_name ASC;";
            try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
            String id = null;
            String displayName = rs.getString("display_name");
            String phoneNumbers = null;
            String accounts = null;
            String emails = null;
            String notes = null;
            String deleted = null;
            String mimetype = rs.getString("mimetype");
            String data1 = rs.getString("data1");
            if ("vnd.android.cursor.item/phone_v2".equals(mimetype)) {
            phoneNumbers = data1;
            } else if ("vnd.android.cursor.item/email_v2".equals(mimetype)) {
            emails = data1;
            }
            contactos.add(new Contacto(id, displayName, phoneNumbers, accounts, emails, notes, deleted));
            }
            } catch (SQLException e) {
            throw e;
        }

        return contactos;
    }

}