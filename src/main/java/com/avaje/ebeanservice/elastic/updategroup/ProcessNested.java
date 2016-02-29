package com.avaje.ebeanservice.elastic.updategroup;

import com.avaje.ebean.EbeanServer;
import com.avaje.ebean.FetchPath;
import com.avaje.ebean.Query;
import com.avaje.ebean.QueryEachConsumer;
import com.avaje.ebean.plugin.BeanDocType;
import com.avaje.ebean.plugin.BeanType;
import com.avaje.ebean.plugin.Property;
import com.avaje.ebeaninternal.server.query.SplitName;
import com.avaje.ebeanservice.elastic.support.ElasticBatchUpdate;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 */
public class ProcessNested<T> {

  private final EbeanServer server;
  private final BeanType<T> desc;
  private final ElasticBatchUpdate txn;
  private final UpdateNested nested;

  private final Map<Object, String> jsonMap = new HashMap<Object,String>();

  private final String nestedPath;
  private final String nestedIdProperty;
  private final String fullNestedPath;
  private final FetchPath nestedDoc;
  private final BeanType<?> nestedDesc;

  private final Property nestedProperty;
  private final String idPropertyName;
  private final String nestedIdPropertyName;
  private final boolean nestedMany;
  private final BeanDocType<T> beanDocType;
  private FetchPath manyRootDoc;

  public ProcessNested(EbeanServer server, BeanType<T> desc, ElasticBatchUpdate txn, UpdateNested nested) {
    this.server = server;
    this.desc = desc;
    this.txn = txn;
    this.nested = nested;
    this.fullNestedPath = nested.getPath();

    beanDocType = desc.docStore();

    String[] nestedPathSplit = getTopNestedPath(fullNestedPath);
    this.nestedPath = nestedPathSplit[0]; // customer
    this.nestedIdProperty = nestedPathSplit[1]; // billingAddress.id

    this.nestedDoc = beanDocType.getEmbedded(this.nestedPath);
    this.nestedDesc = desc.getBeanTypeAtPath(this.nestedPath);

    this.nestedProperty = desc.getProperty(nestedPath);
    this.nestedMany = nestedProperty.isMany();
    if (nestedMany) {
      manyRootDoc = beanDocType.getEmbeddedManyRoot(nestedPath);
    }

    this.idPropertyName = desc.getIdProperty().getName();
    this.nestedIdPropertyName = nestedDesc.getIdProperty().getName();
  }

  /**
   * Return the 'top' part of the nested path.
   */
  private String[] getTopNestedPath(String fullNestedPath) {
    return SplitName.splitBegin(fullNestedPath);
  }

  public void process() throws IOException {

    List<Object> nestedIds = nested.getIds();

    fetchEmbedded(nestedIds);
    processTop(nestedIds);
  }

  /**
   * Populate a map of all the embedded JSON documents that we then want to send as updates to the parent.
   */
  protected void fetchEmbedded(List<Object> nestedIds) throws IOException {

    if (nestedMany) {
      Query<T> pathQuery = server.createQuery(desc.getBeanType());
      pathQuery.apply(manyRootDoc);
      //pathQuery.select(idPropertyName);
      pathQuery.where().in(fullNestedPath, nestedIds);

      // hit the database and build the embedded JSON documents
      List<T> list = pathQuery.findList();
      for (T bean : list) {

        Object manyList = nestedProperty.getVal(bean);
        String embedJson = server.json().toJson(manyList, nestedDoc);

        //String embedJson = server.json().toJson(bean, nestedDoc);
        //Object beanId = nestedDesc.beanId(bean);
        Object beanId = desc.beanId(bean);
        jsonMap.put(beanId, embedJson);
      }

    } else {

      Query<?> pathQuery = server.createQuery(nestedDesc.getBeanType());
      pathQuery.apply(nestedDoc);
      pathQuery.where().in(nestedIdProperty, nestedIds);

      // hit the database and build the embedded JSON documents
      List<?> list = pathQuery.findList();
      for (Object bean : list) {
        String embedJson = server.json().toJson(bean, nestedDoc);
        Object beanId = nestedDesc.beanId(bean);
        jsonMap.put(beanId, embedJson);
      }
    }
  }

  protected void processTop(List<Object> nestedIds) {

    Query<T> topQuery = server.createQuery(desc.getBeanType());
    topQuery.select(idPropertyName);
    if (!nestedMany) {
      topQuery.fetch(nestedPath, nestedIdPropertyName);
    }
    topQuery.where().in(fullNestedPath, nestedIds);

    topQuery.findEach(new QueryEachConsumer<T>() {
      @Override
      public void accept(T bean)  {
        updateEmbedded(bean);
      }
    });
  }

  private void updateEmbedded(T bean) {
    try {
      Object beanId = desc.getBeanId(bean);
      Object targetId;
      if (nestedMany) {
        targetId = beanId;
      } else {
        Object embBean = nestedProperty.getVal(bean);
        targetId = nestedDesc.beanId(embBean);
      }

      String json = jsonMap.get(targetId);

      beanDocType.updateEmbedded(beanId, nestedPath, json, txn.obtain());

    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }


}
