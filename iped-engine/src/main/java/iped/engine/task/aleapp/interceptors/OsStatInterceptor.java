package iped.engine.task.aleapp.interceptors;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

import iped.data.ICaseData;
import iped.data.IItemReader;
import iped.engine.task.aleapp.AleappUtils;
import iped.engine.task.aleapp.CallInterceptor;
import iped.engine.task.aleapp.FileSeeker;
import jep.Jep;
import jep.PyMethod;

public class OsStatInterceptor extends CallInterceptor {

    public OsStatInterceptor(ICaseData caseData) {
        super(caseData, "os", "os.stat");
    }
    
    @Override
    public void install(Jep jep) {
        // This maps 'os.stat' to our Java 'interceptor.call'
        super.install(jep);
        
        try {
            // We capture that mapped Java call and wrap it in a pure Python function.
            // This casts the returning Java List back into a native os.stat_result.
            jep.exec("import os");
            jep.exec("_java_os_stat_call = os.stat");
            jep.exec("def os_stat_wrapper(*args, **kwargs):\n" +
                     "    res = _java_os_stat_call(*args, **kwargs)\n" +
                     "    return os.stat_result(tuple(res))");
            jep.exec("os.stat = os_stat_wrapper");
        } catch (Exception e) {
            logger.error("Failed to install Python wrapper for os.stat", e);
        }
    }

    @Override
    @PyMethod(varargs = true, kwargs = true)
    public Object call(Object[] args, Map<String, Object> kwargs) throws Exception {
 
        Object filePath = getArgumentValue("path", 0, args, kwargs);

        if (filePath instanceof String && FileSeeker.isIPEDPath((String) filePath)) {
            
            IItemReader item = AleappUtils.findItemByPath(caseData, (String) filePath);
            
            int mode = item.isDir() ? 16877 : 33188;   // 0o040755 / 0o100644
            long size = item.getLength() != null ? item.getLength() : 0L;

            // 10 values; the python wrapper turns this into a real os.stat_result
            List<Number> statSequence = Arrays.asList(
                mode,
                0,      // st_ino
                0,      // st_dev
                1,      // st_nlink
                0,      // st_uid
                0,      // st_gid
                size,
                dateToUnixTimestamp(item.getAccessDate()),
                dateToUnixTimestamp(item.getModDate()),
                dateToUnixTimestamp(item.getCreationDate())
            );

            // Return the raw Java list. The Python wrapper in install() will handle the conversion.
            return statSequence;
        }

        // Fallback for non-IPED paths
        return originalCall.call(args, kwargs);
    }

    
    private double dateToUnixTimestamp(Date date) {
        if (date == null) {
            return 0.0;
        }
        // Convert milliseconds to seconds as a floating-point number
        return date.getTime() / 1000.0;
    }
}
