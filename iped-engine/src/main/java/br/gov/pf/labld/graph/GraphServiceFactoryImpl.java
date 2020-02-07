package br.gov.pf.labld.graph;

public class GraphServiceFactoryImpl implements GraphServiceFactory {

  private static GraphServiceFactory instance;
  private static GraphService serviceInstance;

  @Override
  public synchronized GraphService getGraphService() {
    if (serviceInstance == null) {
      serviceInstance = new GraphServiceImpl();
    }
    return serviceInstance;
  }

  public synchronized static GraphServiceFactory getInstance() {
    if (instance == null) {
      instance = new GraphServiceFactoryImpl();
    }
    return instance;
  }

}
