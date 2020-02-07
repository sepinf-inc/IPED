/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iped3;

import java.io.File;
import java.io.Serializable;
import java.util.List;

/**
 *
 * @author WERNECK
 */
public interface IEvidenceFileType extends Serializable {

    /**
     * Retorna a descrição do tipo de arquivo de evidência. Por exemplo: "Documento
     * do MS Word 2000"
     *
     * @return String com a descrição na forma longa do tipo de arquivo.
     */
    String getLongDescr();

    /**
     * Retorna a descrição do tipo de arquivo de evidência. Por exemplo: "Documento
     * do Word".
     *
     * @return String com a descrição na forma curta do tipo de arquivo.
     */
    String getShortDescr();

    /**
     * Processa lista de arquivos de evidência. Este método é responsável por
     * "processar" todos arquivos de evidência de um determinado tipo e deve ser
     * sobrescrito pela implementações de classes de tipo de arquivo. "Processar"
     * significa extrair informações adicionais e converter para um formato tratado
     * pelo visualizador de arquivo. Por questões de performance este método recebe
     * uma lista de arquivos e não atua sobre um arquivo e deveria ser declarado
     * como "static". Porém a linguagem Java não permite que métodos estáticos sejam
     * sobrescritos, por isso é declarado como um método comum, ou seja, é feito um
     * sacrifício nas boas práticas de utilização da linguagem em benefício da
     * performance.
     *
     * @param baseDir
     *            diretório base onde arquivo de evidência exportados estão
     *            armazenados
     * @param items
     *            lista de arquivos a ser processada
     */
    void processFiles(File baseDir, List<IItem> items);

    @Override
    String toString();

}
