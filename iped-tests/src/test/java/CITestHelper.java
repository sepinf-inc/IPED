/**
 * Helper class para detectar ambiente CI e ajustar testes adequadamente
 */
public class CITestHelper {

    /**
     * Verifica se está rodando em ambiente CI (GitHub Actions, Jenkins, etc.)
     */
    public static boolean isCIEnvironment() {
        String ci = System.getenv("CI");
        String githubActions = System.getenv("GITHUB_ACTIONS");
        String jenkins = System.getenv("JENKINS_URL");
        String travis = System.getenv("TRAVIS");
        String circle = System.getenv("CIRCLECI");

        return "true".equalsIgnoreCase(ci) ||
               "true".equalsIgnoreCase(githubActions) ||
               jenkins != null ||
               "true".equalsIgnoreCase(travis) ||
               "true".equalsIgnoreCase(circle);
    }

    /**
     * Verifica se deve pular testes que dependem de dados do IPED
     */
    public static boolean shouldSkipIPEDDependentTests() {
        return isCIEnvironment();
    }

    /**
     * Retorna mensagem apropriada para ambiente CI
     */
    public static String getCISkipMessage() {
        if (isCIEnvironment()) {
            return "Skipping test in CI environment - IPED may fail with dummy test image";
        }
        return "Test skipped";
    }

    /**
     * Loga informações sobre o ambiente de execução
     */
    public static void logEnvironmentInfo() {
        System.out.println("=== ENVIRONMENT INFO ===");
        System.out.println("  CI Environment: " + isCIEnvironment());
        System.out.println("  GitHub Actions: " + System.getenv("GITHUB_ACTIONS"));
        System.out.println("  Jenkins: " + System.getenv("JENKINS_URL"));
        System.out.println("  Travis: " + System.getenv("TRAVIS"));
        System.out.println("  CircleCI: " + System.getenv("CIRCLECI"));
        System.out.println("  Java Version: " + System.getProperty("java.version"));
        System.out.println("  OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
        System.out.println("=========================");
    }
}
