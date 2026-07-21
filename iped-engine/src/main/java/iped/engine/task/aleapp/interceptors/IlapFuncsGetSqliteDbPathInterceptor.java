package iped.engine.task.aleapp.interceptors;

import java.io.File;
import java.util.Map;

import iped.data.ICaseData;
import iped.data.IItemReader;
import iped.engine.task.aleapp.AleappUtils;
import iped.engine.task.aleapp.CallInterceptor;
import iped.engine.task.aleapp.FileSeeker;
import iped.engine.task.aleapp.LeappContext;

public class IlapFuncsGetSqliteDbPathInterceptor extends CallInterceptor {

    public IlapFuncsGetSqliteDbPathInterceptor(ICaseData caseData) {
        super(caseData, "scripts.ilapfuncs", "scripts.ilapfuncs.get_sqlite_db_path");
    }

    @Override
    protected void handleArgs(Object[] args, Map<String, Object> kwargs) throws Exception {

        String filePath = (String) getArgumentValue("path", 0, args, kwargs);

        if (FileSeeker.isIPEDPath(filePath)) {
            IItemReader foundItem = AleappUtils.findItemByPath(caseData, filePath);
            if (foundItem != null) {

                File tempFile = foundItem.getTempFile();
                setArgumentValue("path", 0, tempFile.getCanonicalPath(), args, kwargs);

                LeappContext.get().getTranslatedPaths().put(tempFile.getCanonicalPath(), foundItem.getPath());

            } else {
                throw new IllegalStateException("Item not found in case: " + filePath);
            }
        }
    }
}