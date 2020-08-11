/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iped3.process;

import java.io.File;
import java.util.Properties;

import iped3.IItem;

/**
 *
 * @author WERNECK
 */
public interface ITask {
    /**
     * Método de inicialização da tarefa. Chamado em cada instância da tarefa pelo
     * Worker no qual ela está instalada.
     *
     * @param confParams
     *            Parâmetros obtidos do arquivo de configuração principal
     * @param confDir
     *            Diretório que pode conter um arquivo avançado de configuração da
     *            tarefa
     * @throws Exception
     *             Se ocorreu erro durante a inicialização
     */
    void init(final Properties confParams, File confDir) throws Exception;

    /**
     * Realiza o processamento do item na tarefa e o envia para a próxima tarefa.
     *
     * @param evidence
     *            Item a ser processado.
     * @throws Exception
     *             Caso ocorra erro inesperado.
     */
    void process(IItem evidence) throws Exception;

    /**
     * Método chamado ao final do processamento em cada tarefa instanciada. Pode
     * conter código de finalização da tarefa e liberação de recursos.
     *
     * @throws Exception
     *             Caso ocorra erro inesperado.
     */
    void finish() throws Exception;

    /**
     * Retorna se a tarefa está habilitada. Padrão é sim, mas pode ser sobrescrita
     * se a tarefa possuir esse controle.
     */
    boolean isEnabled();

    void addSubitemProcessingTime(long time);

    long getTaskTime();

}
