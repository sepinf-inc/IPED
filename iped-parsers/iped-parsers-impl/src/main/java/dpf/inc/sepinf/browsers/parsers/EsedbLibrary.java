package dpf.inc.sepinf.browsers.parsers;

import com.sun.jna.*;
import com.sun.jna.ptr.*;

/*
 * Libesedb JNA interface mapping and usage.
 * https://github.com/libyal/libesedb
 */
public interface EsedbLibrary extends Library {

//    NativeLibrary.addSearchPath("esedb", "/usr/local/lib/");
//    EsedbLibrary INSTANCE = (EsedbLibrary)
//            Native.load((Platform.isWindows() ? "esedb" : "esedb"),
//                    EsedbLibrary.class);

    /* Returns the library version
     * const char * libesedb_get_version(void);
     */
    String libesedb_get_version();

    /* Determines if a file contains an ESEDB file signature using a Basic File IO (bfio) handle
     * Returns 1 if true, 0 if not or -1 on error
     * int libesedb_check_file_signature(const char *filename, libesedb_error_t **error);
     */
    int libesedb_check_file_signature(String filename, PointerByReference error);

    /* Creates a file
     * Make sure the value file is referencing, is set to NULL
     * Returns 1 if successful or -1 on error
     * int libesedb_file_initialize(libesedb_file_t **file, libesedb_error_t **error);
     */
    int libesedb_file_initialize(PointerByReference filePointer, PointerByReference error);

    /* Frees a file
     * Returns 1 if successful or -1 on error
     * int libesedb_file_free(libesedb_file_t **file, libesedb_error_t **error );
     */
    int libesedb_file_free(PointerByReference file, PointerByReference error);

    /* Opens a file
     * Returns 1 if successful or -1 on error
     *
     * int libesedb_file_open(libesedb_file_t *file, const char *filename, int access_flags, libcerror_error_t **error )
     * int libesedb_file_open_wide(libesedb_file_t *file, const wchar_t *filename, int access_flags, libesedb_error_t **error);
     *
     * typedef intptr_t libesedb_error_t;
     *
     * #if defined( HAVE_DEBUG_OUTPUT ) && !defined( WINAPI )
     * typedef struct libesedb_file {} libesedb_file_t;
     * #else
     * typedef intptr_t libesedb_file_t;
     *
     * #define LIBESEDB_OPEN_READ				( LIBESEDB_ACCESS_FLAG_READ )
     * LIBESEDB_ACCESS_FLAG_READ			= 0x01
     */
    int libesedb_file_open(Pointer file, String filename, int acessFlags, PointerByReference error);

    /* Retrieves the file type
     * Returns 1 if successful or -1 on error
     * int libesedb_file_get_type(libesedb_file_t *file, uint32_t *type, libesedb_error_t **error);
     */
    int libesedb_file_get_type(Pointer file, LongByReference type, PointerByReference error);

    /* Closes a file
     * Returns 0 if successful or -1 on error
     * int libesedb_file_close(libesedb_file_t *file, libesedb_error_t **error );
     */
    int libesedb_file_close(Pointer file, PointerByReference error);

    /* Retrieves the number of tables
     * Returns 1 if successful or -1 on error
     * int libesedb_file_get_number_of_tables(libesedb_file_t *file, int *number_of_tables, libesedb_error_t **error );
     */
    int libesedb_file_get_number_of_tables(Pointer file, IntByReference numberOfTables, PointerByReference error);

    /* Retrieves the table for the specific entry
     * Returns 1 if successful or -1 on error
     * int libesedb_file_get_table(libesedb_file_t *file, int table_entry, libesedb_table_t **table, libesedb_error_t **error );
     */
    int libesedb_file_get_table(Pointer file, int tableEntry, PointerByReference table, PointerByReference error);

    /* Retrieves the table for the UTF-8 encoded name
     * Returns 1 if successful, 0 if no table could be found or -1 on error
     * int libesedb_file_get_table_by_utf8_name(libesedb_file_t *file, const uint8_t *utf8_string, size_t utf8_string_length, libesedb_table_t **table, libesedb_error_t **error );
     */
    int libesedb_file_get_table_by_utf8_name(Pointer file, String utf8_string, int utf8_string_length, PointerByReference table, PointerByReference error);

    /* Retrieves the size of the UTF-8 encoded string of the table name
     * The returned size includes the end of string character
     * Returns 1 if successful or -1 on error
     * int libesedb_table_get_utf8_name_size(libesedb_table_t *table, size_t *utf8_string_size, libcerror_error_t **error);
     */
    int libesedb_table_get_utf8_name_size(Pointer table, IntByReference utf8_string_size, PointerByReference error);

    /* Retrieves the UTF-8 encoded string of the table name
     * The size should include the end of string character
     * Returns 1 if successful or -1 on error
     * int libesedb_table_get_utf8_name(libesedb_table_t *table, uint8_t *utf8_string, size_t utf8_string_size, libcerror_error_t **error);
     */
//    int libesedb_table_get_utf8_name(Pointer table, IntByReference utf8_string, int utf8_string_size, PointerByReference error);
    int libesedb_table_get_utf8_name(Pointer table, Memory utf8_string, int utf8_string_size, PointerByReference error);

    /* Retrieves the number of columns in the table
     * Use the flag LIBESEDB_GET_COLUMN_FLAG_IGNORE_TEMPLATE_TABLE (0x01) to retrieve the number of columns
     * ignoring the template table
     * Returns 1 if successful or -1 on error
     * int libesedb_table_get_number_of_columns(libesedb_table_t *table, int *number_of_columns, uint8_t flags, libcerror_error_t **error);
     */
    int libesedb_table_get_number_of_columns(Pointer table, IntByReference number_of_columns, int flags, PointerByReference error);

    /* Retrieves a specific column
     * Use the flag LIBESEDB_GET_COLUMN_FLAG_IGNORE_TEMPLATE_TABLE to retrieve the column
     * ignoring the template table
     * Returns 1 if successful or -1 on error
     * int libesedb_table_get_column(libesedb_table_t *table, int column_entry, libesedb_column_t **column, uint8_t flags, libcerror_error_t **error);
     */
    int libesedb_table_get_column(Pointer table, int column_entry, PointerByReference column, int flags, PointerByReference error);

    /* Retrieves the number of records in the table
     * Returns 1 if successful or -1 on error
     * int libesedb_table_get_number_of_records(libesedb_table_t *table, int *number_of_records, libcerror_error_t **error);
     */
    int libesedb_table_get_number_of_records(Pointer table, LongByReference number_of_records, PointerByReference error);

    /* Retrieves a specific record
     * Returns 1 if successful or -1 on error
     * int libesedb_table_get_record(libesedb_table_t *table, int record_entry, libesedb_record_t **record, libcerror_error_t **error);
     */
    int libesedb_table_get_record(Pointer table, int record_entry, PointerByReference record, PointerByReference error);

    /* Frees a table
     * Returns 1 if successful or -1 on error
     * int libesedb_table_free(libesedb_table_t **table, libcerror_error_t **error);
     */
    int libesedb_table_free(PointerByReference table, PointerByReference error);

    /* Retrieves the column type
     * Returns 1 if successful or -1 on error
     * int libesedb_column_get_type(libesedb_column_t *column, uint32_t *type, libcerror_error_t **error);
     */
    int libesedb_column_get_type(Pointer column, IntByReference type, PointerByReference error);

    /* Retrieves the size of the UTF-8 encoded string of the column name
     * The returned size includes the end of string character
     * Returns 1 if successful or -1 on error
     * int libesedb_column_get_utf8_name_size(libesedb_column_t *column, size_t *utf8_string_size, libcerror_error_t **error);
     */
    int libesedb_column_get_utf8_name_size(Pointer column, IntByReference utf8_string_size, PointerByReference error);

    /* Retrieves the UTF-8 encoded string of the column name
     * The size should include the end of string character
     * Returns 1 if successful or -1 on error
     * int libesedb_column_get_utf8_name(libesedb_column_t *column, uint8_t *utf8_string, size_t *utf8_string_size, libcerror_error_t **error)
     */
    int libesedb_column_get_utf8_name(Pointer column, Memory utf8_string, int utf8_string_size, PointerByReference error);

    /* Frees a column
     * Returns 1 if successful or -1 on error
     * int libesedb_column_free(libesedb_column_t **column, libcerror_error_t **error);
     */
    int libesedb_column_free(PointerByReference column, PointerByReference error);

    /* Retrieves the number of values in the record
     * Returns 1 if successful or -1 on error
     * int libesedb_record_get_number_of_values(libesedb_record_t *record, int *number_of_values, libesedb_error_t **error);
     */
    int libesedb_record_get_number_of_values(Pointer record, IntByReference number_of_values, PointerByReference error);

    /* Retrieves the column identifier of the specific entry
     * Returns 1 if successful or -1 on error
     * int libesedb_record_get_column_identifier(libesedb_record_t *record, int value_entry, uint32_t *column_identifier, libesedb_error_t **error);
     */
    int libesedb_record_get_column_identifier(Pointer record, int value_entry, IntByReference column_identifier, PointerByReference error);

    /* Retrieves the column type of the specific entry
     * Returns 1 if successful or -1 on error
     * int libesedb_record_get_column_type(libesedb_record_t *record, int value_entry, uint32_t *column_type, libesedb_error_t **error);
     */
    int libesedb_record_get_column_type(Pointer record, int value_entry, IntByReference column_type, PointerByReference error);

    /* Retrieves the size of the UTF-8 encoded string of the column name of the specific entry
     * The returned size includes the end of string character
     * Returns 1 if successful or -1 on error
     * int libesedb_record_get_utf8_column_name_size(libesedb_record_t *record, int value_entry, size_t *utf8_string_size, libesedb_error_t **error);
     */
    int libesedb_record_get_utf8_column_name_size(Pointer record, int value_entry, IntByReference utf8_string_size, PointerByReference error);

    /* Retrieves the value data flags of the specific entry
     * Returns 1 if successful or -1 on error
     * int libesedb_record_get_value_data_flags(libesedb_record_t *record, int value_entry, uint8_t *value_data_flags, libcerror_error_t **error);
     */
    int libesedb_record_get_value_data_flags(Pointer record, int value_entry, IntByReference value_data_flags, PointerByReference error);

    /* Retrieves the UTF-8 encoded string of the column name of the specific entry
     * The size should include the end of string character
     * Returns 1 if successful or -1 on error
     * int libesedb_record_get_utf8_column_name(libesedb_record_t *record, int value_entry, uint8_t *utf8_string, size_t utf8_string_size, libesedb_error_t **error);
     */
    int libesedb_record_get_utf8_column_name(Pointer record, int value_entry, Memory utf8_string, int utf8_string_size, PointerByReference error);

    /* Retrieves the value data size of the specific entry
     * Returns 1 if successful or -1 on error
     * int libesedb_record_get_value_data_size(libesedb_record_t *record, int value_entry, size_t *value_data_size, libesedb_error_t **error);
     */
    int libesedb_record_get_value_data_size(Pointer record, int value_entry, IntByReference value_data_size, PointerByReference error);

    /* Retrieves the value data of the specific entry
     * Returns 1 if successful or -1 on error
     * int libesedb_record_get_value_data(libesedb_record_t *record, int value_entry, uint8_t *value_data, size_t value_data_size, libesedb_error_t **error);
     */
    int libesedb_record_get_value_data(Pointer record, int value_entry, LongByReference value_data, int value_data_size, PointerByReference error);

    /* Retrieves the size of an UTF-8 encoded string a specific entry
     * The returned size includes the end of string character
     * Returns 1 if successful, 0 if value is NULL or -1 on error
     * int libesedb_record_get_value_utf8_string_size(libesedb_record_t *record, int value_entry, size_t *utf8_string_size, libesedb_error_t **error);
     */
    int libesedb_record_get_value_utf8_string_size(Pointer record, int value_entry, IntByReference utf8_string_size, PointerByReference error);

    /* Retrieves the UTF-8 encoded string of a specific entry
     * The function uses the codepage in the column definition if necessary
     * The size should include the end of string character
     * Returns 1 if successful, 0 if value is NULL or -1 on error
     * int libesedb_record_get_value_utf8_string(libesedb_record_t *record, int value_entry, uint8_t *utf8_string, size_t utf8_string_size, libesedb_error_t **error);

     */
    int libesedb_record_get_value_utf8_string(Pointer record, int value_entry, Memory utf8_string, int utf8_string_size, PointerByReference error);

    /* Retrieves the size of an UTF-16 encoded string a specific entry
     * The returned size includes the end of string character
     * Returns 1 if successful, 0 if value is NULL or -1 on error
     * int libesedb_record_get_value_utf16_string_size(libesedb_record_t *record, int value_entry, size_t *utf16_string_size, libesedb_error_t **error);
     */
    int libesedb_record_get_value_utf16_string_size(Pointer record, int value_entry, IntByReference utf16_string_size, PointerByReference error);

    /* Retrieves the UTF-16 encoded string of a specific entry
     * The function uses the codepage in the column definition if necessary
     * The size should include the end of string character
     * Returns 1 if successful, 0 if value is NULL or -1 on error
     * int libesedb_record_get_value_utf16_string(libesedb_record_t *record, int value_entry, uint16_t *utf16_string, size_t utf16_string_size, libesedb_error_t **error);
     */
    int libesedb_record_get_value_utf16_string(Pointer record, int value_entry, Memory utf16_string, int utf16_string_size, PointerByReference error);

    /* Retrieves the 32-bit value of a specific entry
     * Returns 1 if successful, 0 if value is NULL or -1 on error
     * int libesedb_record_get_value_32bit(libesedb_record_t *record, int value_entry, uint32_t *value_32bit, libesedb_error_t **error);
     */
    int libesedb_record_get_value_32bit(Pointer record, int value_entry, IntByReference value_32bit, PointerByReference error);

    /* Retrieves the 64-bit value of a specific entry
     * Returns 1 if successful, 0 if value is NULL or -1 on error
     * int libesedb_record_get_value_64bit(libesedb_record_t *record, int value_entry, uint64_t *value_64bit, libesedb_error_t **error);
     */
    int libesedb_record_get_value_64bit(Pointer record, int value_entry, LongByReference value_64bit, PointerByReference error);

    /* Determines if a specific entry is a long value
     * Returns 1 if true, 0 if not or -1 on error
     * int libesedb_record_is_long_value(libesedb_record_t *record, int value_entry, libesedb_error_t **error);
     */
    int libesedb_record_is_long_value(Pointer record, int value_entry, PointerByReference error);

    /* Frees a long value
     * Returns 1 if successful or -1 on error
     * int libesedb_long_value_free(libesedb_long_value_t **long_value, libesedb_error_t **error);
     */
    int libesedb_long_value_free(PointerByReference long_value, PointerByReference error);

    /* Determines if a specific entry is a multi value
     * Returns 1 if true, 0 if not or -1 on error
     * int libesedb_record_is_multi_value(libesedb_record_t *record, int value_entry, libesedb_error_t **error);
     */
    int libesedb_record_is_multi_value(Pointer record, int value_entry, PointerByReference error);

    /* Frees a multi value
     * Returns 1 if successful or -1 on error
     * int libesedb_multi_value_free(libesedb_multi_value_t **multi_value, libesedb_error_t **error);
     */
    int libesedb_multi_value_free(PointerByReference multi_value, PointerByReference error);

    /* Frees a record
     * Returns 1 if successful or -1 on error
     * int libesedb_record_free(libesedb_record_t **record, libesedb_error_t **error);
     */
    int libesedb_record_free(PointerByReference record, PointerByReference error);

    /* Free an error and its elements
     * void libesedb_error_free(libesedb_error_t **error);
     */
    void libesedb_error_free(PointerByReference error);
}
