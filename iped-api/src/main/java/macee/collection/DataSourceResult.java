package macee.collection;

import java.io.Serializable;

/**
 * Um resultado de busca transferido via HTTP no formato CSV.
 * 
 * COMENTÁRIO (Werneck): pode ser descartado.
 * 
 * @author WERNECK
 */
public interface DataSourceResult extends Serializable {

  String getDataSource();

  String getItems();

  int getLimit();

  int getOffset();

  void setDataSource(String dataSource);

  void setItems(String items);

  void setLimit(int limit);

  void setOffset(int offset);
  
}
