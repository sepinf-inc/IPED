package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.nio.file.DirectoryStream.Filter;

import dpf.sp.gpinf.indexer.util.UTF8Properties;
import gpinf.dev.data.ReportInfo;

import java.nio.file.Path;

public class HtmlReportTaskConfig extends AbstractPropertiesConfigurable {

    private static final String CONFIG_FILE = "HTMLReportConfig.txt"; //$NON-NLS-1$

    /**
     * Indica se a tarefa está habilitada ou não.
     */
    private boolean taskEnabled = false;

    /**
     * Flag de controle se a geração de miniaturas de imagem está habilitada.
     */
    private boolean imageThumbsEnabled = false;

    /**
     * Flag de controle se a inclusão de miniaturas de cenas de vídeos (que devem
     * estar disponíveis) está habilitada.
     */
    private boolean videoThumbsEnabled = false;

    /**
     * Flag de controle que indica que uma lista de categorias deve se mostrada na
     * página de conteúdo, além da lista de marcadores, que é incluída como padrão.
     */
    private boolean categoriesListEnabled = false;

    /**
     * Tamanho da miniatura (utilizado no HTML).
     */
    private int thumbSize = 112;

    /**
     * Quantidade de frames utilizado na "faixa" de cenas de vídeo.
     */
    private int framesPerStripe = 8;

    /**
     * Largura (em pixels) da "faixa" de cenas de vídeo.
     */
    private int videoStripeWidth = 800;

    /**
     * Itens por página HTML.
     */
    private int itemsPerPage = 100;

    /**
     * Flag de controle se página com galeria de miniaturas de imagem é criada.
     */
    private boolean thumbsPageEnabled = false;

    /**
     * Miniaturas de imagem na página de galeria.
     */
    private static int thumbsPerPage = 500;

    /**
     * Objeto com informações que serão incluídas no relatório.
     */
    private ReportInfo info;

    public ReportInfo getReportInfo() {
        return this.info;
    }

    public boolean isEnabled() {
        return taskEnabled;
    }

    public boolean isImageThumbsEnabled() {
        return imageThumbsEnabled;
    }

    public boolean isVideoThumbsEnabled() {
        return videoThumbsEnabled;
    }

    public boolean isCategoriesListEnabled() {
        return categoriesListEnabled;
    }

    public boolean isThumbsPageEnabled() {
        return thumbsPageEnabled;
    }

    public int getItemsPerPage() {
        return itemsPerPage;
    }

    public int getThumbsPerPage() {
        return thumbsPerPage;
    }

    public int getThumbSize() {
        return thumbSize;
    }

    public int getFramesPerStripe() {
        return framesPerStripe;
    }

    public int getVideoStripeWidth() {
        return videoStripeWidth;
    }

    @Override
    public Filter<Path> getResourceLookupFilter() {
        return new Filter<Path>() {
            @Override
            public boolean accept(Path entry) throws IOException {
                return entry.endsWith(CONFIG_FILE) || entry.endsWith(IPEDConfig.CONFIG_FILE);
            }
        };
    }

    @Override
    public void processConfig(Path resource) throws IOException {
        
        UTF8Properties properties = new UTF8Properties();
        properties.load(resource.toFile());
        
        if(resource.endsWith(IPEDConfig.CONFIG_FILE)) {
            String value = properties.getProperty("enableHTMLReport"); // $NON-NLS-1$
            if (value != null) {
                taskEnabled = Boolean.valueOf(value.trim());
            }
        } else {
            String value = properties.getProperty("ItemsPerPage"); //$NON-NLS-1$
            if (value != null) {
                itemsPerPage = Integer.parseInt(value.trim());
            }

            value = properties.getProperty("ThumbsPerPage"); //$NON-NLS-1$
            if (value != null) {
                thumbsPerPage = Integer.parseInt(value.trim());
            }

            value = properties.getProperty("ThumbSize"); //$NON-NLS-1$
            if (value != null) {
                thumbSize = Integer.parseInt(value.trim());
            }

            value = properties.getProperty("EnableImageThumbs"); //$NON-NLS-1$
            if (value != null) {
                imageThumbsEnabled = Boolean.valueOf(value.trim());
            }

            value = properties.getProperty("EnableVideoThumbs"); //$NON-NLS-1$
            if (value != null) {
                videoThumbsEnabled = Boolean.valueOf(value.trim());
            }

            value = properties.getProperty("FramesPerStripe"); //$NON-NLS-1$
            if (value != null) {
                framesPerStripe = Integer.parseInt(value.trim());
            }

            value = properties.getProperty("VideoStripeWidth"); //$NON-NLS-1$
            if (value != null) {
                videoStripeWidth = Integer.parseInt(value.trim());
            }

            value = properties.getProperty("EnableCategoriesList"); //$NON-NLS-1$
            if (value != null) {
                categoriesListEnabled = Boolean.valueOf(value.trim());
            }

            value = properties.getProperty("EnableThumbsGallery"); //$NON-NLS-1$
            if (value != null) {
                thumbsPageEnabled = Boolean.valueOf(value.trim());
            }

            info = new ReportInfo();
            info.reportHeader = properties.getProperty("Header"); //$NON-NLS-1$
            info.reportDate = properties.getProperty("ReportDate"); //$NON-NLS-1$
            info.requestDate = properties.getProperty("RequestDate"); //$NON-NLS-1$
            info.labCaseDate = properties.getProperty("RecordDate"); //$NON-NLS-1$
            info.requestForm = properties.getProperty("RequestDoc"); //$NON-NLS-1$
            info.caseNumber = properties.getProperty("Investigation"); //$NON-NLS-1$
            info.reportNumber = properties.getProperty("Report"); //$NON-NLS-1$
            info.fillEvidenceFromText(properties.getProperty("Material")); //$NON-NLS-1$
            info.examinersID.add(properties.getProperty("ExaminerID")); //$NON-NLS-1$
            info.examiners.add(properties.getProperty("Examiner")); //$NON-NLS-1$
            info.labCaseNumber = properties.getProperty("Record"); //$NON-NLS-1$
            info.requester = properties.getProperty("Requester"); //$NON-NLS-1$
            info.reportTitle = properties.getProperty("Title"); //$NON-NLS-1$
        }

    }

}
