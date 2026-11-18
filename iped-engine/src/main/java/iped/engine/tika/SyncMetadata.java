package iped.engine.tika;

import java.util.Calendar;
import java.util.Date;
import java.util.Properties;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;

/**
 * Synchronized wrapper around Tika's Metadata class to avoid concurrent access
 * issues.
 * 
 * @author Nassif
 *
 */
public class SyncMetadata extends Metadata {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private boolean readOnly = false;

    /**
     * Constructs a new, empty metadata.
     */
    public SyncMetadata() {
        super();
    }

    /**
     * Constructs a new synchronized metadata from another metadata.
     * 
     * @param metadata
     */
    public SyncMetadata(Metadata metadata) {
        super();
        String[] keys = metadata.names();
        for (String key : keys) {
            String[] values = metadata.getValues(key);
            for (String val : values) {
                this.add(key, val);
            }
        }
    }

    public synchronized void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    private synchronized void checkReadOnly() {
        if (readOnly) {
            try {
                throw new UnsupportedOperationException("Metadata can't be changed after set readonly!");
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
        }
    }

    @Override
    public synchronized boolean isMultiValued(final Property property) {
        return super.isMultiValued(property);
    }

    @Override
    public synchronized boolean isMultiValued(final String name) {
        return super.isMultiValued(name);
    }

    @Override
    public synchronized String[] names() {
        return super.names();
    }

    @Override
    public synchronized String get(final String name) {
        return super.get(name);
    }

    @Override
    public synchronized String get(Property property) {
        return super.get(property);
    }

    @Override
    public synchronized Integer getInt(Property property) {
        return super.getInt(property);
    }

    @Override
    public synchronized Date getDate(Property property) {
        return super.getDate(property);
    }

    @Override
    public synchronized String[] getValues(final Property property) {
        return super.getValues(property);
    }

    @Override
    public synchronized String[] getValues(final String name) {
        return super.getValues(name);
    }

    @Override
    public synchronized void add(final String name, final String value) {
        checkReadOnly();
        super.add(name, value);
    }

    @Override
    public synchronized void add(final Property property, final String value) {
        checkReadOnly();
        super.add(property, value);
    }

    @Override
    public synchronized void setAll(Properties properties) {
        checkReadOnly();
        super.setAll(properties);
    }

    @Override
    public synchronized void set(String name, String value) {
        checkReadOnly();
        super.set(name, value);
    }

    @Override
    public synchronized void set(Property property, String value) {
        checkReadOnly();
        super.set(property, value);
    }

    @Override
    public synchronized void set(Property property, String[] values) {
        checkReadOnly();
        super.set(property, values);
    }

    @Override
    public synchronized void set(Property property, int value) {
        checkReadOnly();
        super.set(property, value);
    }

    @Override
    public synchronized void add(Property property, int value) {
        checkReadOnly();
        super.add(property, value);
    }

    @Override
    public synchronized int[] getIntValues(Property property) {
        return super.getIntValues(property);
    }

    @Override
    public synchronized void set(Property property, double value) {
        checkReadOnly();
        super.set(property, value);
    }

    @Override
    public synchronized void set(Property property, Date date) {
        checkReadOnly();
        super.set(property, date);
    }

    @Override
    public synchronized void set(Property property, Calendar date) {
        checkReadOnly();
        date.get(Calendar.HOUR_OF_DAY);
        super.set(property, date);
    }

    @Override
    public synchronized void remove(String name) {
        checkReadOnly();
        super.remove(name);
    }

    @Override
    public synchronized int size() {
        return super.size();
    }

    @Override
    public synchronized int hashCode() {
        return super.hashCode();
    }

    @Override
    public synchronized boolean equals(Object o) {
        return super.equals(o);
    }

    @Override
    public synchronized String toString() {
        return super.toString();
    }

}
