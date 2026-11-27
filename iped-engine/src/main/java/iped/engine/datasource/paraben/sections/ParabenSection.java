/**
 * Paraben Data Parser Integration for IPED
 *
 * Developed by: Ariel E Aburo
 * Contact: infmpfch@gmail.com
 *
 * Description:
 * Implements parsing of data extracted via Paraben tools,
 * converting XML and associated database content into
 * IPED-compatible structures.
 *
 * This module supports multiple artifacts such as:
 * - Conversations
 * - Messages
 * - Attachments
 * - Device and user data and more.
 *
 * Notes:
 * - Uses XML as primary source
 * - Designed to be extensible for additional Paraben artifacts
 */
package iped.engine.datasource.paraben.sections;

import java.io.File;
import java.util.List;

import java.util.Map;
import iped.data.ICaseData;
import iped.engine.data.Item;

public interface ParabenSection {
    void process(File root, Item rootItem, List<File> xmlChain, ICaseData caseData,
            Map<String, List<org.w3c.dom.Element>> index) throws Exception;
}