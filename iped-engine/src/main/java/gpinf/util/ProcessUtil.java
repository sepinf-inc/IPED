package gpinf.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

/**
 * Reúne métodos auxiliares para execução de processos externo.
 *
 * @author Wladimir Leite (GPINF/SP)
 */
public class ProcessUtil {

    /**
     * Executa um processo externo.
     *
     * @param program
     *            Nome (e caminho completo) do programa a ser executado.
     * @param input
     *            Entrada, que é direcionada para stdin (entrada padrão) do
     *            processo.
     * @return String com a saída padrão (stdout) gerada pelo processo em caso de
     *         sucesso. No caso de alguma exceção se disparada é retornado um valor
     *         <code>null</code>.
     */
    public static final String run(String program, String input) {
        try {
            Process process = Runtime.getRuntime().exec(program);
            OutputStream stdin = process.getOutputStream();
            InputStream stdout = process.getInputStream();

            stdin.write(input.getBytes());
            stdin.flush();
            stdin.close();

            String line = null;
            StringBuilder ret = new StringBuilder();
            BufferedReader out = new BufferedReader(new InputStreamReader(stdout));
            while ((line = out.readLine()) != null) {
                ret.append(line).append('\n');
            }
            out.close();
            return ret.toString();
        } catch (IOException e) {
            System.err.println("Error running program '" + program + "':"); //$NON-NLS-1$ //$NON-NLS-2$
            e.printStackTrace();
        }
        return null;
    }
}
