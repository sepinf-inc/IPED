package iped.engine.task.aleapp.interceptors;

import java.util.Map;

import org.apache.commons.io.IOUtils;

import iped.data.ICaseData;
import iped.data.IItemReader;
import iped.engine.task.aleapp.AleappUtils;
import iped.engine.task.aleapp.CallInterceptor;
import iped.engine.task.aleapp.FileSeeker;
import jep.PyMethod;

/**
 * Replace the ilapfuncs.get_binary_file_content function to return the file content directly in Java
 */
public class IlapfuncsGetBinaryFileContentInterceptor extends CallInterceptor {

    public IlapfuncsGetBinaryFileContentInterceptor(ICaseData caseData) {
        super(caseData, "scripts.ilapfuncs", "scripts.ilapfuncs.get_binary_file_content");
    }

    @Override
    @PyMethod(varargs = true, kwargs = true)
    public Object call(Object[] args, Map<String, Object> kwargs) throws Exception {

        String filePath = (String) getArgumentValue("file_path", 0, args, kwargs);

        if (filePath.startsWith(FileSeeker.IPED_PATH_PREFIX)) {
            IItemReader foundItem = AleappUtils.findItemByPath(caseData, filePath);
            if (foundItem != null) {
                return IOUtils.toByteArray(foundItem.getBufferedInputStream());
            } else {
                throw new IllegalStateException("Item not found in case: " + filePath);
            }
        }

        return super.call(args, kwargs);
    }
}