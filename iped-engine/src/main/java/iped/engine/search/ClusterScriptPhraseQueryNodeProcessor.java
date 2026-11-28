package iped.engine.search;

import java.util.List;
import java.util.function.Predicate;

import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.queryparser.flexible.core.nodes.FieldQueryNode;
import org.apache.lucene.queryparser.flexible.core.nodes.QueryNode;
import org.apache.lucene.queryparser.flexible.core.nodes.QuotedFieldQueryNode;
import org.apache.lucene.queryparser.flexible.core.processors.QueryNodeProcessorImpl;
import org.apache.lucene.queryparser.flexible.standard.nodes.TermRangeQueryNode;
import org.apache.lucene.queryparser.flexible.standard.nodes.WildcardQueryNode;

import iped.engine.lucene.analysis.AnalyzerVersion;
import iped.engine.lucene.analysis.UnicodeTokenizer;

/**
 * Texts of scripts written without spaces between words (Chinese, Japanese,
 * Korean, Thai...) are indexed one grapheme cluster per token by the
 * {@link AnalyzerVersion#UNICODE} analysis, so a word typed in the search box
 * becomes several tokens, which the query parser would OR, matching any
 * document with any of the characters. This processor turns such terms into
 * phrase queries, which match the exact sequence of characters.
 * <p>
 * Wildcard terms like {@code *杂志*}, the only way to search these scripts in
 * cases indexed with the legacy analysis, are turned into phrases too.
 * <p>
 * Must run before the AnalyzerQueryNodeProcessor.
 */
public class ClusterScriptPhraseQueryNodeProcessor extends QueryNodeProcessorImpl {

    private final Predicate<CharSequence> fieldHasPositions;

    public ClusterScriptPhraseQueryNodeProcessor() {
        this(field -> true);
    }

    /**
     * @param fieldHasPositions
     *            tells if a field is indexed with positions, phrase queries on
     *            other fields would fail
     */
    public ClusterScriptPhraseQueryNodeProcessor(Predicate<CharSequence> fieldHasPositions) {
        this.fieldHasPositions = fieldHasPositions;
    }

    @Override
    protected QueryNode preProcessNode(QueryNode node) throws QueryNodeException {
        if (node.getParent() instanceof TermRangeQueryNode || !(node instanceof FieldQueryNode)
                || !fieldHasPositions.test(((FieldQueryNode) node).getField())) {
            return node;
        }
        if (node.getClass() == FieldQueryNode.class) {
            FieldQueryNode fieldNode = (FieldQueryNode) node;
            if (UnicodeTokenizer.containsClusterScript(fieldNode.getText())) {
                return new QuotedFieldQueryNode(fieldNode.getField(), fieldNode.getText(), fieldNode.getBegin(),
                        fieldNode.getEnd());
            }
        } else if (node instanceof WildcardQueryNode) {
            WildcardQueryNode wildcardNode = (WildcardQueryNode) node;
            String core = stripLeadingAndTrailingStars(wildcardNode.getTextAsString());
            if (!core.isEmpty() && core.indexOf('*') < 0 && core.indexOf('?') < 0
                    && UnicodeTokenizer.containsClusterScript(core)) {
                return new QuotedFieldQueryNode(wildcardNode.getField(), core, wildcardNode.getBegin(),
                        wildcardNode.getEnd());
            }
        }
        return node;
    }

    private static String stripLeadingAndTrailingStars(String text) {
        int start = 0, end = text.length();
        while (start < end && text.charAt(start) == '*') {
            start++;
        }
        while (end > start && text.charAt(end - 1) == '*') {
            end--;
        }
        return text.substring(start, end);
    }

    @Override
    protected QueryNode postProcessNode(QueryNode node) throws QueryNodeException {
        return node;
    }

    @Override
    protected List<QueryNode> setChildrenOrder(List<QueryNode> children) throws QueryNodeException {
        return children;
    }
}
