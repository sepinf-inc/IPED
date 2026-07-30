package iped.engine.task.leapp.interceptors;

import java.util.List;
import java.util.Map;

import iped.engine.task.leapp.CallInterceptor;
import iped.engine.task.leapp.LeappContext;
import iped.engine.task.leapp.PluginResultsProcessor;
import jep.PyMethod;

/**
 * Replaces the lava_insert_sqlite_data function. This is the main IPED-LEAPP integration point: instead of letting the
 * plugin results be written to the LAVA sqlite database, they are captured here and handed to
 * {@link PluginResultsProcessor}, which turns each data row into an IPED subitem of the current plugin evidence.
 *
 * This class only captures the call: all handling of the headers and rows lives in {@link PluginResultsProcessor}.
 *
 * lava_insert_sqlite_data is intercepted (rather than tsv/timeline/html) because it is the only output call that
 * receives the RAW data_headers and data_list: headers keep their (name, type) tuples and values keep their original
 * Python types — the other outputs receive stripped headers and stringified values.
 *
 * NOTE: the interception target is the binding INSIDE scripts.ilapfuncs ("from scripts.lavafuncs import
 * lava_insert_sqlite_data" is captured at import time), which is the name artifact_processor actually calls.
 */
public class LavaInsertSqliteDataInterceptor extends CallInterceptor {

    public LavaInsertSqliteDataInterceptor() {
        super("scripts.ilapfuncs", "scripts.ilapfuncs.lava_insert_sqlite_data");
    }

    @SuppressWarnings("unchecked")
    @Override
    @PyMethod(varargs = true, kwargs = true)
    public Object call(Object[] args, Map<String, Object> kwargs) throws Exception {

        // lava_insert_sqlite_data(table_name, data, object_columns, headers, column_map)
        // table_name/object_columns/column_map come from lava_process_artifact, which is
        // disabled and returns None values: only data and headers are used here
        List<List<Object>> dataList = (List<List<Object>>) getArgumentValue("data", 1, args, kwargs);
        List<Object> rawHeaders = (List<Object>) getArgumentValue("headers", 3, args, kwargs);

        if (dataList == null || dataList.isEmpty()) {
            return null;
        }

        // the interceptor is installed globally in the Python interpreter, so the
        // thread-local context tells us which plugin run this call belongs to
        LeappContext context = LeappContext.get();

        // hand the raw headers and data rows to the results processor, 
        // which will turn them into subitems of the current plugin evidence
        new PluginResultsProcessor(context).process(rawHeaders, dataList);

        return null;
    }

}
