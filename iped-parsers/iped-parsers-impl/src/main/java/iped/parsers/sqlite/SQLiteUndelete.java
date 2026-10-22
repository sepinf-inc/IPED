package iped.parsers.sqlite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import fqlite.base.Base;
import fqlite.base.Global;
import fqlite.base.Job;
import fqlite.base.SqliteRow;
import fqlite.descriptor.TableDescriptor;

public class SQLiteUndelete {
    private Path sqliteFile = null;
    private Path walFile = null;
    private Path rollbackJournalFile = null;
    private Set<String> tablesToRecover = new HashSet<>();
    private Map<String, SQLiteRecordValidator> recordValidators = new HashMap<>();
    private boolean recoverOnlyDeletedRecords = true;
    private Set<String> tablesToRecoverOnlyDeleted = new HashSet<>();
    private boolean fixCarvingPatterns = false;

    public SQLiteUndelete(Path sqliteFile) {
        this.sqliteFile = sqliteFile;
        Path walFile = sqliteFile.resolveSibling(sqliteFile.getFileName().toString() + "-wal");
        Path rollbackJournalFile = sqliteFile.resolveSibling(sqliteFile.getFileName().toString() + "-journal");

        if (Files.exists(walFile)) {
            this.walFile = walFile;
        }

        if (Files.exists(rollbackJournalFile)) {
            this.rollbackJournalFile = rollbackJournalFile;
        }
    }

    public void addRecordValidator(String tableName, SQLiteRecordValidator validator) {
        recordValidators.put(tableName, validator);
    }

    public void addTableToRecover(String tableName) {
        tablesToRecover.add(tableName);
    }

    public void setRecoverOnlyDeletedRecords(boolean recoverOnlyDeletedRecords) {
        this.recoverOnlyDeletedRecords = recoverOnlyDeletedRecords;
    }

    public boolean getRecoverOnlyDeletedRecords() {
        return recoverOnlyDeletedRecords;
    }

    /**
     * If true, the record header patterns used by fqlite to carve deleted records
     * of the tables to recover are replaced by the ones built by
     * {@link SQLiteHeaderPatternFixer}. Without it, records deleted into
     * freeblocks of tables with integer columns holding 0 or 1, or with NULL
     * text columns (most real databases), are not carved. As more (possibly
     * partially overwritten) records are carved, callers should use strict
     * record validators.
     */
    public void setFixCarvingPatterns(boolean fixCarvingPatterns) {
        this.fixCarvingPatterns = fixCarvingPatterns;
    }

    public void addTableToRecoverOnlyDeleted(String tableName) {
        tablesToRecoverOnlyDeleted.add(tableName);
    }

    public Map<String, SQLiteUndeleteTable> undeleteData() throws InterruptedException, ExecutionException, IOException {
        Map<String, SQLiteUndeleteTable> result = null;

        Job job = new Job() {
            @Override
            protected void tableDescriptorReady(TableDescriptor td) {
                // libfqlite 1.57.05 builds record header patterns that reject valid
                // records (e.g. integer columns holding 0 or 1, NULL text columns), so
                // records deleted into freeblocks would never be carved. Only the tables
                // that will be returned are changed, to avoid carving more garbage into
                // tables nobody reads (e.g. from index pages, tried with all patterns).
                if (fixCarvingPatterns
                        && (tablesToRecover.isEmpty() || tablesToRecover.contains(td.getName()))) {
                    SQLiteHeaderPatternFixer.fix(td);
                }
            }
        };
        job.setPath(sqliteFile.toString());
        if (null != walFile) {
            job.setWALPath(walFile.toString());
            job.readWAL = true;
        }
        if (null != rollbackJournalFile) {
            job.setRollbackJournalPath(rollbackJournalFile.toString());
            job.readRollbackJournal = true;
        }

        Global.LOGLEVEL = Base.NONE;
        Job.LOGLEVEL = Base.NONE;
        Global.CONVERT_DATETIME = false;
        Global.numberofThreads = 0;

        // recover only deleted records
        job.recoverOnlyDeletedRecords = recoverOnlyDeletedRecords;
        job.collectInternalRows = false;
        result = new HashMap<>();

        
        if (job.processDB() == 0) {
            for (TableDescriptor td : job.headers.values()) {
                if (td.columnnames != null) {
                    if (tablesToRecover.isEmpty() || tablesToRecover.contains(td.getName())) {
                        SQLiteUndeleteTable table = new SQLiteUndeleteTable(td.columnnames);
                        table.setTableName(td.getName());
                        SQLiteRecordValidator validator = recordValidators.get(td.getName());
                        for (SqliteRow row : job.getRowsForTable(td.getName())) {
                            if (shouldRecoverRow(row, td.getName())) {
                                if (validator == null || validator.validateRecord(row)) {
                                    table.getTableRows().add(row);
                                }
                            }
                        }
                        result.put(td.getName(), table);
                    }
                }
            }
        }
        return result;
    }

    private boolean shouldRecoverRow(SqliteRow row, String tableName) {
        if (row.isDeletedRow()) {
            return true;
        }
        
        if (!recoverOnlyDeletedRecords) {
            if (!tablesToRecoverOnlyDeleted.contains(tableName)) {
                return true;
            }
        }

        return false;
    }

}
