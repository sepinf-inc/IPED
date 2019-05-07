/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package macee.descriptor;

import java.time.LocalDate;

/**
 *
 * @author WERNECK
 */
public interface CaseDescriptor extends Descriptor {

    int getCaseId();

    LocalDate getDataCriacao();

    String[] getDocumentos();

    String getOperacao();

    String getPerfil();

    String getUnidade();

    void setDocumentos(String[] documentos);

    void setOperacao(String operacao);

    void setPerfil(String perfil);

    void setUnidade(String unidade);

}
