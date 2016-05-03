package gpinf.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.channels.FileChannel;

/**
 * Reúne métodos auxiliares para manipulação de arquivos.
 *
 * @author Wladimir Leite (GPINF/SP)
 */
public final class FileUtil {

  /**
   * Lê um arquivo texto inteiro e retorna o conteúdo em uma string. As quebras de linha são
   * excluídas. Funciona apenas para arquivos relativamente pequenos, cujo conteúdo possa ser
   * armazenado na memória.
   *
   * @param file arquivo a ser lido
   * @param removeLineBreaks indica se quebra de linhas devem ser removidas
   * @param sizeLimit quantidade máxima de caracteres a serem lidos
   * @return String com o conteúdo de arquivo.
   * @throws IOException Indica erro na leituta do arquivo.
   */
  public static final String read(File file, boolean removeLineBreaks, long sizeLimit) throws IOException {
    if (!file.exists()) {
      throw new FileNotFoundException("Arquivo " + file.getAbsolutePath() + " não encontrado.");
    }
    StringBuilder fileData = new StringBuilder();
    BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "windows-1252"), 64 << 10);
    String line = null;
    while ((line = reader.readLine()) != null) {
      fileData.append(line);
      if (!removeLineBreaks) {
        fileData.append("\n");
      }
      if (fileData.length() >= sizeLimit) {
        break;
      }
    }
    reader.close();
    return fileData.toString();
  }

  /**
   * Lê um arquivo texto inteiro e retorna o conteúdo em uma string. As quebras de linha são
   * excluídas. Funciona apenas para arquivos relativamente pequenos (até 10 Mbytes), cujo conteúdo
   * possa ser armazenado na memória.
   *
   * @param file arquivo a ser lido
   * @return String com o conteúdo de arquivo (sem as quebras de linha).
   * @throws IOException Indica erro na leituta do arquivo.
   */
  public static final String read(File file) throws IOException {
    return read(file, true, 10 << 20);
  }

  /**
   * Lê um arquivo texto inteiro de até 10 Mbytes.
   *
   * @param file arquivo a ser lido
   * @param removeLineBreaks indica se quebra de linhas devem ser removidas
   * @return String com o conteúdo de arquivo (sem as quebras de linha).
   * @throws IOException Indica erro na leituta do arquivo.
   */
  public static final String read(File file, boolean removeLineBreaks) throws IOException {
    return read(file, removeLineBreaks, 10 << 20);
  }

  /**
   * Copia um arquivo.
   *
   * @param src arquivo origem
   * @param dst arquivo destino
   * @return indicador se a cópia foi bem sucedida
   */
  public static final boolean copy(File src, File dst) {
    if (dst.exists()) {
      dst.delete();
    }
    FileChannel srcChannel = null;
    FileChannel dstChannel = null;
    try {
      srcChannel = new FileInputStream(src).getChannel();
      dstChannel = new FileOutputStream(dst).getChannel();

      long size = srcChannel.size();
      long position = 0;
      while (position < size) {
        position += srcChannel.transferTo(position, 32 << 20, dstChannel);
      }

      srcChannel.close();
      dstChannel.close();
      return true;
    } catch (IOException e) {
      e.printStackTrace();
      try {
        srcChannel.close();
        dstChannel.close();
      } catch (Exception ec) {
      }
    }
    return false;
  }
}
