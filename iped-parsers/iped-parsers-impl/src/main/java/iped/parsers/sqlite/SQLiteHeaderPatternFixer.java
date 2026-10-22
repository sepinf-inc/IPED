package iped.parsers.sqlite;

import java.util.List;
import java.util.Locale;

import fqlite.descriptor.TableDescriptor;
import fqlite.pattern.Constraint;
import fqlite.pattern.HeaderPattern;

/**
 * Rebuilds the record header pattern that libfqlite uses to carve deleted
 * records from freeblocks and unallocated areas of table pages.
 * <p>
 * The pattern built by libfqlite 1.57.05 rejects valid record headers, so
 * deleted records of most real databases (e.g. WhatsApp msgstore.db) are never
 * carved from freeblocks:
 * <ul>
 * <li>INTEGER columns only accept serial types 0..6, but SQLite (schema format
 * 4, the default) stores the values 0 and 1 as serial types 8 and 9;</li>
 * <li>TEXT columns do not accept NULL (serial type 0) nor the empty string
 * (serial type 13), BLOB columns do not accept NULL nor the empty blob (12) and
 * REAL columns do not accept NULL nor integral values, which SQLite stores as
 * integers;</li>
 * <li>the INTEGER PRIMARY KEY (rowid alias) column, always stored as NULL, is
 * not recognized when the column constraints end up concatenated to the
 * declared type by the schema parser (e.g. "INTEGERPRIMARYKEYAUTOINCREMENT").</li>
 * </ul>
 * This class can be removed when libfqlite fixes these issues.
 */
public class SQLiteHeaderPatternFixer {

    private enum Kind {
        ROWID, INTEGER, REAL, TEXT, BLOB, ANY
    }

    private static final String[] COLUMN_CONSTRAINT_KEYWORDS = { "CONSTRAINT", "PRIMARYKEY", "NOTNULL", "NULL",
            "UNIQUE", "CHECK", "DEFAULT", "COLLATE", "REFERENCES", "GENERATED", "AS(" };

    /**
     * Replaces the header pattern of the table with a corrected one. If anything
     * goes wrong, the original pattern is kept.
     */
    public static void fix(TableDescriptor td) {
        try {
            HeaderPattern pattern = build(td);
            if (pattern != null) {
                td.setHpattern(pattern);
            }
        } catch (RuntimeException e) {
            // keep the original libfqlite pattern
        }
    }

    static HeaderPattern build(TableDescriptor td) {
        if (td == null || td.isVirtual() || td.getHpattern() == null) {
            return null;
        }
        List<String> types = td.getColumntypes();
        if (types == null || types.isEmpty()) {
            return null;
        }
        int numCols = types.size();
        int rowIdAlias = td.ROWID ? findRowIdAlias(td) : -1;

        HeaderPattern pattern = new HeaderPattern();
        // header length varint (1 byte) + one serial type varint per column; a serial
        // type takes 1 byte, or 2 bytes for TEXT/BLOB longer than 57 bytes, or 3
        // bytes for values longer than 8185 bytes
        pattern.addHeaderConstraint(numCols + 1, numCols * 3 + 1);

        for (int i = 0; i < numCols; i++) {
            if (i == rowIdAlias) {
                pattern.add(new SerialTypeConstraint(Kind.ROWID, false));
                continue;
            }
            String decl = normalize(get(td.sqltypes, i)) + normalize(get(td.constraints, i));
            boolean nullable = !decl.contains("NOTNULL");
            pattern.add(new SerialTypeConstraint(toKind(types.get(i)), nullable));
        }
        return pattern;
    }

    private static Kind toKind(String fqliteType) {
        if (fqliteType == null) {
            return Kind.ANY;
        }
        switch (fqliteType) {
            case "INT":
                return Kind.INTEGER;
            case "REAL":
                return Kind.REAL;
            case "TEXT":
                return Kind.TEXT;
            case "BLOB":
                return Kind.BLOB;
            default:
                // NUMERIC affinity or no declared type (BLOB affinity): any storage class
                return Kind.ANY;
        }
    }

    /**
     * Returns the index of the column that is an alias of the rowid ("INTEGER
     * PRIMARY KEY", not DESC), or -1.
     */
    private static int findRowIdAlias(TableDescriptor td) {
        List<String> names = td.columnnames;
        if (names == null) {
            return -1;
        }
        if (td.rowidcolumn != null) {
            return names.indexOf(td.rowidcolumn);
        }
        int found = -1;
        for (int i = 0; i < names.size(); i++) {
            String decl = normalize(get(td.sqltypes, i)) + normalize(get(td.constraints, i));
            if (decl.contains("PRIMARYKEY")) {
                if (found != -1) {
                    return -1; // more than one primary key column
                }
                found = i;
                if (!"INTEGER".equals(declaredType(decl)) || decl.contains("PRIMARYKEYDESC")) {
                    return -1;
                }
            }
        }
        if (found == -1 && td.primarykeycolumns != null && td.primarykeycolumns.size() == 1) {
            // primary key declared as a table constraint: PRIMARY KEY(col)
            int i = names.indexOf(td.primarykeycolumns.get(0));
            if (i >= 0 && "INTEGER".equals(declaredType(normalize(get(td.sqltypes, i))))) {
                found = i;
            }
        }
        return found;
    }

    /**
     * Declared type without the column constraints concatenated by the parser.
     */
    private static String declaredType(String decl) {
        int end = decl.length();
        for (String keyword : COLUMN_CONSTRAINT_KEYWORDS) {
            int idx = decl.indexOf(keyword);
            if (idx >= 0 && idx < end) {
                end = idx;
            }
        }
        return decl.substring(0, end);
    }

    private static String get(List<String> list, int i) {
        return list != null && i < list.size() ? list.get(i) : null;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    private static class SerialTypeConstraint implements Constraint {

        private final Kind kind;
        private final boolean nullable;

        SerialTypeConstraint(Kind kind, boolean nullable) {
            this.kind = kind;
            this.nullable = nullable;
        }

        @Override
        public boolean match(int serialType) {
            if (serialType < 0) {
                return false;
            }
            if (serialType == 0) {
                // the rowid alias is always stored as NULL
                return nullable || kind == Kind.ROWID;
            }
            switch (kind) {
                case ROWID:
                    return false;
                case INTEGER:
                    // 1..6: integers of 1, 2, 3, 4, 6 and 8 bytes; 8 and 9: constants 0 and 1
                    return serialType <= 6 || serialType == 8 || serialType == 9;
                case REAL:
                    // 7: float; integral values are stored as integers
                    return serialType <= 9;
                case TEXT:
                    // odd values >= 13 (13 is the empty string)
                    return serialType >= 13 && (serialType & 1) == 1;
                case BLOB:
                    // even values >= 12 (12 is the empty blob)
                    return serialType >= 12 && (serialType & 1) == 0;
                default:
                    // 10 and 11 are reserved
                    return serialType != 10 && serialType != 11;
            }
        }

        @Override
        public String toString() {
            return kind + (nullable ? "?" : "");
        }
    }
}
