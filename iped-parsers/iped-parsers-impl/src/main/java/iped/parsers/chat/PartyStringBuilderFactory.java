package iped.parsers.chat;

import org.apache.commons.lang3.StringUtils;

/**
 * A factory class to create {@link PartyStringBuilder} instances for various chat applications.
 * This class provides a static method to get the appropriate builder for a given chat source.
 */
public class PartyStringBuilderFactory {

    public static final String SOURCE_WHATSAPP = "WhatsApp";
    public static final String SOURCE_TELEGRAM = "Telegram";
    public static final String SOURCE_INSTAGRAM = "Instagram";
    public static final String SOURCE_EMAIL = "Email";

    /**
     * Creates a specific {@link PartyStringBuilder} based on the chat application source.
     *
     * @param source      The name of the chat application (e.g., "WhatsApp", "Telegram").
     * @return A concrete {@link PartyStringBuilder} instance. Returns a {@link GenericPartyStringBuilder}
     *         if the source is null, blank, or unsupported.
     */
    public static PartyStringBuilder getBuilder(String source) {
        if (StringUtils.isBlank(source)) {
            return new GenericPartyStringBuilder();
        }

        if (StringUtils.containsIgnoreCase(source, SOURCE_WHATSAPP)) {
            return new WhatsAppPartyStringBuilder();
        }

        switch (source) {
            case SOURCE_TELEGRAM:
                return new TelegramPartyStringBuilder();
            case SOURCE_INSTAGRAM:
                return new InstagramPartyStringBuilder();
            case SOURCE_EMAIL:
                return new EmailPartyStringBuilder();
            default:
                return new GenericPartyStringBuilder();
        }
    }
}
