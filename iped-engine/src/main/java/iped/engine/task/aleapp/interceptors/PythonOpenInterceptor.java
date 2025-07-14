package iped.engine.task.aleapp.interceptors;

import java.io.File;
import java.util.Map;

import iped.data.ICaseData;
import iped.data.IItemReader;
import iped.engine.task.aleapp.AleappUtils;
import iped.engine.task.aleapp.CallInterceptor;
import iped.engine.task.aleapp.FileSeeker;

public class PythonOpenInterceptor extends CallInterceptor {

    public PythonOpenInterceptor(ICaseData caseData) {
        super(caseData, null, "__builtins__['open']");
    }

    @Override
    protected void handleArgs(Object[] args, Map<String, Object> kwargs) throws Exception {

        String filePath = (String) getArgumentValue("file", 0, args, kwargs);

        if (filePath.startsWith(FileSeeker.IPED_PATH_PREFIX)) {
            IItemReader foundItem = AleappUtils.findItemByPath(caseData, filePath);
            if (foundItem != null) {

                File tempFile = foundItem.getTempFile();
                setArgumentValue("file", 0, tempFile.getCanonicalPath(), args, kwargs);

            } else {
                throw new IllegalStateException("Item not found in case: " + filePath);
            }
        }
    }
}