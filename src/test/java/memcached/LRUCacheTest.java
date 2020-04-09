package memcached;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import memcached.cache.LRUCache;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
public class LRUCacheTest {
  static LRUCache<String, String> cache;
  static int size;

  @BeforeAll
  public static void setup(Vertx vertx, VertxTestContext testContext) {
    size = 10;
    cache = new LRUCache<>(size);
    testContext.completeNow();
  }

  @Test
  void handleSetAndGet(Vertx vertx, VertxTestContext testContext) {
    String key = "abc";
    String value = "hello";

    cache.set(key, value);
    assert cache.get(key).equals(value);
    testContext.completeNow();
  }

  @Test
  void handleSetMultipleAndGet(Vertx vertx, VertxTestContext testContext) {
    String key = "abcd";
    String value = "hello";
    String value2 = "hello2";

    cache.set(key, value);
    cache.set(key, value2);
    assert cache.get(key).equals(value2);
    testContext.completeNow();
  }

  @Test
  void checkEvictionPolicy(Vertx vertx, VertxTestContext testContext) {
    String key = "abcde";
    String value = "hello";

    cache.set(key, value);
    // Set 1 more entry than the size
    for (Integer i = 0; i < (size - 1) + 1; i++) {
      cache.set(key + "-" + i, value + "-" + i);
    }

    assert cache.get(key) == null;
    testContext.completeNow();
  }

}
