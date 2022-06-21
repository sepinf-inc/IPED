package iped.search;

public class SearchResult {

    int[] ids;
    float[] scores;

    private SearchResult() {
    }

    public SearchResult(int[] ids, float[] scores) {
        this.ids = ids;
        this.scores = scores;
    }

    public int getId(int i) {
        return ids[i];
    }

    public float getScore(int i) {
        return scores[i];
    }

    public int getLength() {
        return ids.length;
    }

    public int[] getIds() {
        return this.ids;
    }

    public void compactResults() {
        int blanks = 0;
        for (int i = 0; i < ids.length; i++)
            if (ids[i] != -1) {
                ids[i - blanks] = ids[i];
                scores[i - blanks] = scores[i];
            } else
                blanks++;

        int[] _ids = new int[ids.length - blanks];
        float[] _scores = new float[scores.length - blanks];

        System.arraycopy(ids, 0, _ids, 0, _ids.length);
        System.arraycopy(scores, 0, _scores, 0, _scores.length);

        ids = _ids;
        scores = _scores;
    }

    @Override
    public SearchResult clone() {
        SearchResult result = new SearchResult();
        result.ids = this.ids.clone();
        result.scores = this.scores.clone();
        return result;
    }
}
