package macee.annotations.components;

import java.lang.annotation.*;

/**
 * A report is a component that translates analysis results into reports that can be combined into a
 * final report.
 *
 * @author werneck.bwph
 */
@Documented @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
public @interface Reporter {

    static final ComponentType TYPE = ComponentType.REPORTER;

    /**
     * The report type this reporter writes to.
     *
     * @return the report type this reporter writes to.
     */
    ReportType report() default ReportType.MAIN_REPORT;

    String section() default "root";

    static enum ReportType {

        MAIN_REPORT, MAIN_REPORT_ANNEX, SUPPLEMENTARY_REPORT
    }
}
