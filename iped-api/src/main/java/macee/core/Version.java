package macee.core;

import java.util.Optional;

public interface Version extends Comparable<Version> {

    int getMajor();

    int getMinor();

    int getPatch();

    Optional<String> getPreRelease();

    Optional<String> getBuildMetadata();
}
