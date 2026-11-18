package iped.viewers.bookmarks;

public interface IBookmarksController {

    static IBookmarksController[] instance = new IBookmarksController[1];

    void setMultiSetting(boolean value);

    boolean isMultiSetting();

    public static void registerBookmarksController(IBookmarksController bc) {
        instance[0] = bc;
    }

    public static IBookmarksController get() {
        return instance[0];
    }

}
