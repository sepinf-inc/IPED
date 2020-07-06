package dpf.sp.gpinf.indexer.util;

import java.io.IOException;
import java.util.List;

import org.apache.lucene.index.IndexCommit;
import org.apache.lucene.index.IndexDeletionPolicy;

import dpf.sp.gpinf.indexer.CmdLineArgs;

public class CustomIndexDeletionPolicy extends IndexDeletionPolicy {

    private CmdLineArgs cmdArgs;

    public CustomIndexDeletionPolicy(CmdLineArgs args) {
        this.cmdArgs = args;
    }

    @Override
    public void onInit(List<? extends IndexCommit> commits) throws IOException {

        // removes oldest commit
        if (!cmdArgs.isContinue() && !cmdArgs.isRestart() && commits.size() > 1) {
            commits.get(0).delete();
            System.out.println("Deleting oldest commit");
        }

        // removes last commit
        if (cmdArgs.isRestart() && commits.size() > 1) {
            commits.get(commits.size() - 1).delete();
            System.out.println("Deleting last commit");
        }

        // removes intermediary commits
        if (commits.size() > 2) {
            for (int i = 1; i < commits.size() - 1; i++) {
                commits.get(i).delete();
            }
        }
    }

    @Override
    public void onCommit(List<? extends IndexCommit> commits) throws IOException {
        // removes intermediary commits
        if (commits.size() > 2) {
            for (int i = 1; i < commits.size() - 1; i++) {
                commits.get(i).delete();
            }
        }
    }

}
