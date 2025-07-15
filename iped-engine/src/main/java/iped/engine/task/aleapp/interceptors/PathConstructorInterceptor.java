package iped.engine.task.aleapp.interceptors;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import iped.data.ICaseData;
import iped.data.IItemReader;
import iped.engine.task.aleapp.AleappTask;
import iped.engine.task.aleapp.AleappUtils;
import iped.engine.task.aleapp.CallInterceptor;
import iped.engine.task.aleapp.FileSeeker;

public class PathConstructorInterceptor extends CallInterceptor {

    public PathConstructorInterceptor(ICaseData caseData) {
        super(caseData, "pathlib", "pathlib.PurePath.__init__", true);
    }

    @Override
    protected void handleArgs(Object[] args, Map<String, Object> kwargs) throws Exception {

        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof String && ((String) args[i]).startsWith(FileSeeker.IPED_PATH_PREFIX)) {
                IItemReader foundItem = AleappUtils.findItemByPath(caseData, (String) args[i]);
                if (foundItem != null) {
                    Path tempDir = Files.createTempDirectory("path_constructor");
                    Path tempFile = tempDir.resolve(foundItem.getName());
                    Files.copy(foundItem.getBufferedInputStream(), tempFile);
                    tempFile.toFile().deleteOnExit();
                    args[i] = tempFile.toString();

                    AleappTask.getTranslatedPaths().put(tempFile.toString(), foundItem.getPath());

                } else {
                    throw new IllegalStateException("Item not found in case: " + args[i]);
                }
            }
        }
    }
}
