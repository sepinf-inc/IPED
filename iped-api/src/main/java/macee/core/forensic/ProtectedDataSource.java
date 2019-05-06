package macee.core.forensic;

public interface ProtectedDataSource extends DataSource {

    boolean unlock(String accessKey);

    void lock();

    @Override default boolean isProtected() {
        return true;
    }
}
