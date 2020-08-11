/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iped3.process;

/**
 *
 * @author WERNECK
 */
public interface IStatistics {

    void addVolume(long volume);

    int getActiveProcessed();

    int getCorruptCarveIgnored();

    int getIgnored();

    int getIoErrors();

    int getLastId();

    int getProcessed();

    int getSplits();

    int getTimeouts();

    long getVolume();

    void incActiveProcessed();

    void incCorruptCarveIgnored();

    void incIgnored();

    void incIoErrors();

    void incProcessed();

    void incSplits();

    void incTimeouts();

    void logStatistics(IManager manager) throws Exception;

    void printSystemInfo() throws Exception;

    void setLastId(int id);

    void updateLastId(int id);

}
