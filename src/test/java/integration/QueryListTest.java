package integration;

import com.avaje.ebean.Query;
import org.example.domain.Customer;
import org.example.domain.Order;
import org.example.domain.Product;
import org.testng.annotations.Test;

import java.sql.Timestamp;
import java.util.List;

import static org.assertj.core.api.StrictAssertions.assertThat;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class QueryListTest extends BaseTest {

  @Test
  public void findAll() {

    Query<Product> query = server.find(Product.class).setUseDocStore(true);

    List<Product> products = query.findList();

    assertTrue(!products.isEmpty());
    assertEquals(query.getGeneratedSql(), "{\"query\":{\"match_all\":{}}}");
  }

  @Test
  public void firstRowMaxRows_orders() {

    Query<Order> query = server.find(Order.class)
        .setUseDocStore(true)
        .setFirstRow(10)
        .setMaxRows(10);

    query.findList();
    assertEquals(query.getGeneratedSql(), "{\"from\":10,\"size\":10,\"query\":{\"match_all\":{}}}");
  }

  @Test
  public void text() {

    Query<Order> query = server.find(Order.class)
        .text()
          .where().eq("customer.name","Rob")
        .query();

    query.findList();
    assertEquals(query.getGeneratedSql(), "{\"query\":{\"filtered\":{\"filter\":{\"term\":{\"customer.name.raw\":\"Rob\"}}}}}");
  }

  @Test
  public void text_match() {

    Query<Order> query = server.find(Order.class)
        .text()
          .match("customer.name","Rob")
          .query();

    query.findList();
    assertEquals(query.getGeneratedSql(), "{\"query\":{\"match\":{\"customer.name\":\"Rob\"}}}");
  }

  @Test
  public void where_match() {

    Query<Order> query = server.find(Order.class)
        .where()
          .match("customer.name","Rob")
        .query();

    query.findList();
    assertEquals(query.getGeneratedSql(), "{\"query\":{\"filtered\":{\"filter\":{\"match\":{\"customer.name\":\"Rob\"}}}}}");
  }

  @Test
  public void where_match_must() {

    Query<Order> query = server.find(Order.class)
        .where()
          .match("customer.name","Rob")
          .eq("customer.status", Customer.Status.NEW)
          .query();

    query.findList();
    assertEquals(query.getGeneratedSql(), "{\"query\":{\"filtered\":{\"filter\":{\"bool\":{\"must\":[{\"match\":{\"customer.name\":\"Rob\"}},{\"term\":{\"customer.status\":\"NEW\"}}]}}}}}");
  }

  @Test
  public void selectFetch_orders() {

    Query<Order> query = server.find(Order.class)
        .setUseDocStore(true)
        .select("id")
        .fetch("customer", "id,name");

    query.findList();
    assertEquals(query.getGeneratedSql(), "{\"fields\":[\"customer.id\",\"customer.name\",\"id\"],\"query\":{\"match_all\":{}}}");
  }

  @Test
  public void selectFetch_withInclude() {

    Query<Order> query = server.find(Order.class)
        .setUseDocStore(true)
        .select("id")
        .fetch("customer", "id,name")
        .fetch("details");

    query.findList();
    assertEquals(query.getGeneratedSql(), "{\"_source\":{\"include\":[\"details.*\"]},\"fields\":[\"customer.id\",\"customer.name\",\"id\"],\"query\":{\"match_all\":{}}}");
  }

  @Test
  public void orderBy_raw() {

    Query<Product> query = server.find(Product.class)
        .setUseDocStore(true)
        .order().asc("name");

    List<Product> products = query.findList();

    assertEquals(query.getGeneratedSql(), "{\"sort\":[{\"name.raw\":{\"order\":\"asc\"}}],\"query\":{\"match_all\":{}}}");
    assertThat(products.size()).isGreaterThan(4);
  }

  @Test
  public void equals_raw() {

    Query<Product> query = server.find(Product.class)
        .setUseDocStore(true)
        .where().eq("name","Chair")
        .query();

    List<Product> products = query.findList();

    assertEquals(query.getGeneratedSql(), "{\"query\":{\"filtered\":{\"filter\":{\"term\":{\"name.raw\":\"Chair\"}}}}}");
    assertEquals(products.size(), 1);
  }

  @Test
  public void where_startsWith_product() {

    Query<Product> query = server.find(Product.class)
        .setUseDocStore(true)
        .where().startsWith("sku", "C00")
        .query();

    List<Product> products = query.findList();

    assertEquals(products.size(), 3);
    assertEquals(query.getGeneratedSql(), "{\"query\":{\"filtered\":{\"filter\":{\"prefix\":{\"sku\":\"c00\"}}}}}");
  }

  @Test
  public void where_contains_product() {

    Query<Product> query = server.find(Product.class)
        .setUseDocStore(true)
        .where().contains("sku", "C00")
        .query();

    List<Product> products = query.findList();

    assertEquals(products.size(), 3);
    assertEquals(query.getGeneratedSql(), "{\"query\":{\"filtered\":{\"filter\":{\"wildcard\":{\"sku\":\"*c00*\"}}}}}");
  }

  @Test
  public void where_endsWith_product() {

    Query<Product> query = server.find(Product.class)
        .setUseDocStore(true)
        .where().endsWith("sku", "1")
        .query();

    List<Product> products = query.findList();

    assertEquals(query.getGeneratedSql(), "{\"query\":{\"filtered\":{\"filter\":{\"wildcard\":{\"sku\":\"*1\"}}}}}");
    assertEquals(products.size(), 2);
  }

  @Test
  public void where_like_product() {

    Query<Product> query = server.find(Product.class)
        .setUseDocStore(true)
        .where().like("sku", "C_0%")
        .query();

    List<Product> products = query.findList();

    assertEquals(query.getGeneratedSql(), "{\"query\":{\"filtered\":{\"filter\":{\"wildcard\":{\"sku\":\"c?0*\"}}}}}");
    assertEquals(products.size(), 3);
  }

  @Test
  public void where_ieq_product() {

    Query<Product> query = server.find(Product.class)
        .setUseDocStore(true)
        .where().ieq("name", "chair")
        .query();

    List<Product> products = query.findList();

    assertEquals(products.size(), 1);
    assertEquals(query.getGeneratedSql(), "{\"query\":{\"filtered\":{\"filter\":{\"match\":{\"name\":\"chair\"}}}}}");
  }

  @Test
  public void where_ieq_when_hasSpaces() {

    Query<Customer> query = server.find(Customer.class)
        .setUseDocStore(true)
        .where().ieq("name", "cust noaddress")
        .query();

    List<Customer> customers = query.findList();

    assertEquals(customers.size(), 1);
    assertEquals(query.getGeneratedSql(), "{\"query\":{\"filtered\":{\"filter\":{\"bool\":{\"must\":[{\"match\":{\"name\":\"cust\"}},{\"match\":{\"name\":\"noaddress\"}}]}}}}}");
  }

  @Test
  public void where_eq() {

    Query<Customer> query = server.find(Customer.class)
        .setUseDocStore(true)
        .where().eq("name", "Rob")
        .query();

    List<Customer> customers = query.findList();

    assertEquals(customers.size(), 1);
    assertEquals(query.getGeneratedSql(), "{\"query\":{\"filtered\":{\"filter\":{\"term\":{\"name.raw\":\"Rob\"}}}}}");
  }

  @Test
  public void where_in() {

    Query<Customer> query = server.find(Customer.class)
        .setUseDocStore(true)
        .where().in("name", "Rob", "Junk")
        .query();

    List<Customer> customers = query.findList();

    assertEquals(customers.size(), 1);
    assertEquals(query.getGeneratedSql(), "{\"query\":{\"filtered\":{\"filter\":{\"terms\":{\"name.raw\":[\"Rob\",\"Junk\"]}}}}}");
  }

  @Test
  public void where_notIn() {

    Query<Customer> query = server.find(Customer.class)
        .setUseDocStore(true)
        .where().notIn("name", "Rob", "Junk", "Fiona")
        .query();

    List<Customer> customers = query.findList();

    assertEquals(query.getGeneratedSql(), "{\"query\":{\"filtered\":{\"filter\":{\"bool\":{\"must_not\":[{\"terms\":{\"name.raw\":[\"Rob\",\"Junk\",\"Fiona\"]}}]}}}}}");
    assertEquals(customers.size(), 2);
  }

  @Test
  public void where_between_onRaw() {

    Query<Customer> query = server.find(Customer.class)
        .setUseDocStore(true)
        .where().between("name", "R", "S")
        .query();

    List<Customer> customers = query.findList();

    assertEquals(customers.size(), 1);
    assertEquals(query.getGeneratedSql(), "{\"query\":{\"filtered\":{\"filter\":{\"range\":{\"name.raw\":{\"gte\":\"R\",\"lte\":\"S\"}}}}}}");
  }

  @Test
  public void where_between_dateTime() {

    Timestamp before = new Timestamp(System.currentTimeMillis() - 1000000);
    Timestamp now = new Timestamp(System.currentTimeMillis() - 1000000);

    Query<Customer> query = server.find(Customer.class)
        .setUseDocStore(true)
        .where().between("anniversary", before, now)
        .query();

    List<Customer> customers = query.findList();

    assertEquals(customers.size(), 0);
    assertTrue(query.getGeneratedSql().contains("{\"query\":{\"filtered\":{\"filter\":{\"range\":{\"anniversary\":{\"gte\":"));
  }
}
