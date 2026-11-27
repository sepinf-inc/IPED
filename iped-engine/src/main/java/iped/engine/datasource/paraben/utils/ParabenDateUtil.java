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
package iped.engine.datasource.paraben.utils;

import java.util.Date;

public class ParabenDateUtil {

    /**
     * Convierte epoch (string) a Date.
     * Detecta automáticamente si está en segundos o milisegundos.
     */
    public static Date fromEpoch(String epochStr) {
        if (epochStr == null || epochStr.isEmpty())
            return null;

        try {
            long epoch = Long.parseLong(epochStr.trim());

            // detectar segundos vs milisegundos
            if (epoch < 1000000000000L) {
                epoch *= 1000;
            }

            return new Date(epoch);

        } catch (Exception e) {
            return null;
        }
    }
}