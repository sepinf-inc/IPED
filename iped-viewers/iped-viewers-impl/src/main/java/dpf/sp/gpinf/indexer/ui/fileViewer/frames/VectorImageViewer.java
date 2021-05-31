package dpf.sp.gpinf.indexer.ui.fileViewer.frames;

public class VectorImageViewer extends ImageViewer {
    protected boolean isVectorViewer() {
        return true;
    }

    @Override
    public boolean isSupportedType(String contentType) {
        return contentType.equals("image/emf") || contentType.equals("image/wmf")
                || contentType.equals("image/svg+xml");
    }
}
