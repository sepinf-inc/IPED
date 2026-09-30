package iped.engine.webapi.json;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * CaseSummaryJSON is the flat (no "data" envelope) root object of
 * GET /case-summary (design 07e): an aggregated portrait of the case in a
 * single cheap request, so an LLM agent can answer "what is in this case?"
 * as its very first call.
 *
 * The body is index metadata only: it makes no promise about extracted text
 * availability (design 07e AC7 / P2-6). It is a coupling-free DTO (no iped.*
 * types), same convention as the other webapi/json DTOs.
 */
@ApiModel(value = "CaseSummary")
@JsonPropertyOrder({ "sources", "totalItems", "top", "categories", "distinctCategories",
        "omittedCategories", "itemsOutsideTop", "period" })
public class CaseSummaryJSON {

    private List<String> sources;
    private long totalItems;
    private int top;
    private List<CategoryCountJSON> categories;
    private int distinctCategories;
    private int omittedCategories;
    private long itemsOutsideTop;
    private PeriodJSON period;

    public CaseSummaryJSON() {
    }

    public CaseSummaryJSON(List<String> sources, long totalItems, int top,
            List<CategoryCountJSON> categories, int distinctCategories, int omittedCategories,
            long itemsOutsideTop, PeriodJSON period) {
        this.sources = sources;
        this.totalItems = totalItems;
        this.top = top;
        this.categories = categories;
        this.distinctCategories = distinctCategories;
        this.omittedCategories = omittedCategories;
        this.itemsOutsideTop = itemsOutsideTop;
        this.period = period;
    }

    @ApiModelProperty(value = "Source (case) ids covered by this summary, lexicographically sorted")
    public List<String> getSources() {
        return sources;
    }

    @ApiModelProperty(value = "Number of documents in the index scope")
    public long getTotalItems() {
        return totalItems;
    }

    @ApiModelProperty(value = "Echo of the requested top-N (explicit default 10)")
    public int getTop() {
        return top;
    }

    @ApiModelProperty(value = "Top-N categories by item count, descending; ties by name ascending")
    public List<CategoryCountJSON> getCategories() {
        return categories;
    }

    @ApiModelProperty(value = "Categories present in the index (count greater than 0), not limited by top")
    public int getDistinctCategories() {
        return distinctCategories;
    }

    @ApiModelProperty(value = "Present categories not shown because of the top-N cut")
    public int getOmittedCategories() {
        return omittedCategories;
    }

    @ApiModelProperty(value = "Sum of the item counts cut by the top-N window (never negative)")
    public long getItemsOutsideTop() {
        return itemsOutsideTop;
    }

    @ApiModelProperty(value = "min/max of the modified field, or null when no cheap boundary exists")
    public PeriodJSON getPeriod() {
        return period;
    }

    /**
     * One aggregated category bucket. residual marks buckets whose label
     * starts with "other" (design 07e D5: it labels the aggregated nature of
     * the bucket, not its size) and the synthetic uncategorized entry (D7).
     */
    @ApiModel(value = "CategoryCount")
    @JsonPropertyOrder({ "category", "items", "residual" })
    public static class CategoryCountJSON {

        private String category;
        private long items;
        private boolean residual;

        public CategoryCountJSON() {
        }

        public CategoryCountJSON(String category, long items, boolean residual) {
            this.category = category;
            this.items = items;
            this.residual = residual;
        }

        @ApiModelProperty
        public String getCategory() {
            return category;
        }

        @ApiModelProperty
        public long getItems() {
            return items;
        }

        @ApiModelProperty
        public boolean isResidual() {
            return residual;
        }
    }

    /**
     * Lexicographic == chronological min/max of the canonical UTC modified
     * values (yyyy-MM-dd'T'HH:mm:ss'Z').
     */
    @ApiModel(value = "Period")
    @JsonPropertyOrder({ "from", "to" })
    public static class PeriodJSON {

        private String from;
        private String to;

        public PeriodJSON() {
        }

        public PeriodJSON(String from, String to) {
            this.from = from;
            this.to = to;
        }

        @ApiModelProperty
        public String getFrom() {
            return from;
        }

        @ApiModelProperty
        public String getTo() {
            return to;
        }
    }
}
