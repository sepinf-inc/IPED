package iped.utils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.DateFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * A presentation-only timezone preference. Never changes the JVM default zone,
 * stored evidence, indexed values, or parser timestamp interpretation.
 */
public final class DisplayTimeZone {
    private static final Path PREFERENCE = Paths.get(System.getProperty("user.home"), ".iped", "display-timezone.txt");
    private static final CopyOnWriteArrayList<Consumer<ZoneId>> LISTENERS = new CopyOnWriteArrayList<>();
    private static final String AUTOMATIC = "SYSTEM";
    private static volatile Choice choice = load();

    private static final class Choice {
        final ZoneId zone;
        final boolean system;

        Choice(ZoneId zone, boolean system) {
            this.zone = zone;
            this.system = system;
        }
    }

    private DisplayTimeZone() {
    }

    private static Choice load() {
        if (Files.isRegularFile(PREFERENCE)) {
            try {
                String saved = new String(Files.readAllBytes(PREFERENCE), StandardCharsets.UTF_8).trim();
                if (AUTOMATIC.equals(saved)) {
                    return new Choice(null, true);
                }
                return new Choice(ZoneId.of(saved), false);
            } catch (IOException | RuntimeException ex) {
                System.err.println("Invalid saved IPED display timezone; using system timezone: " + ex.getMessage());
            }
        }
        // On the first launch (or for invalid preference), follow the host computer.
        return new Choice(null, true);
    }

    public static ZoneId getZoneId() {
        Choice selected = choice;
        return selected.system ? ZoneId.systemDefault() : selected.zone;
    }

    public static boolean isSystemDefault() {
        return choice.system;
    }

    public static TimeZone getTimeZone() {
        return TimeZone.getTimeZone(getZoneId());
    }

    /** Use the computer's current time zone, including on the next startup. */
    public static void useSystemDefault() {
        saveChoice(new Choice(null, true));
    }

    /** Save an explicit time zone. It overrides the computer's time zone. */
    public static void setZoneId(String id) {
        saveChoice(new Choice(ZoneId.of(id), false));
    }

    private static synchronized void saveChoice(Choice desired) {
        Choice previous = choice;
        if (previous.system == desired.system &&
                (desired.system || desired.zone.equals(previous.zone))) {
            return;
        }
        try {
            Files.createDirectories(PREFERENCE.getParent());
            String value = desired.system ? AUTOMATIC : desired.zone.getId();
            Files.write(PREFERENCE, value.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not save IPED display timezone", ex);
        }
        choice = desired;
        ZoneId activeZone = getZoneId();
        for (Consumer<ZoneId> listener : LISTENERS) {
            listener.accept(activeZone);
        }
    }

    public static void addListener(Consumer<ZoneId> listener) {
        LISTENERS.addIfAbsent(listener);
    }

    public static void removeListener(Consumer<ZoneId> listener) {
        LISTENERS.remove(listener);
    }

    /** Includes event-specific UTC offset as well as the configured IANA zone. */
    public static String format(Date instant, DateFormat pattern) {
        if (instant == null) {
            return null;
        }
        ZoneId zone = getZoneId();
        DateFormat safeCopy = (DateFormat) pattern.clone();
        safeCopy.setTimeZone(TimeZone.getTimeZone(zone));
        ZonedDateTime zoned = instant.toInstant().atZone(zone);
        return safeCopy.format(instant) + " " + zoned.getOffset().getId() + " [" + zone.getId() + "]";
    }
}
