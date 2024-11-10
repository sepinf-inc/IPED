package iped.app.home.processmanager;/*
                                     * @created 22/12/2022
                                     * @project IPED
                                     * @author Thiago S. Figueiredo
                                     */

public interface ProcessListener {

    void processStarted();

    void processFinished();

    void caseIsOpening();

    void caseWasClosed();

}
