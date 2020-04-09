package memcached;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import memcached.common.MemcacheMessage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import java.util.Arrays;
import static memcached.util.Constants.ADDRESS;

/***
 * Tests for cache verticle class.
 */
@ExtendWith(VertxExtension.class)
public class CacheVerticleTest {

  @BeforeAll
  static void deployCacheVerticle(Vertx vertx, VertxTestContext testContext) {
    vertx.deployVerticle(new CacheVerticle(), testContext.completing());
  }

  @AfterAll
  static void tearDownCacheVerticle(Vertx vertx, VertxTestContext testContext) {
    vertx.close(testContext.completing());
  }

  /***
   * Send over the message object on the event bus and validate the response.
   * @param eventBus is the event bus to forward messages on
   * @param command is the message to be sent
   * @param testContext is the vertx test context.
   */
  private void validateMessage(EventBus eventBus, MemcacheMessage command, VertxTestContext testContext) {
    JsonObject message = JsonObject.mapFrom(command);
    eventBus.send(ADDRESS, message, reply -> {
      if (reply.succeeded()) {
        MemcacheMessage response = Json.decodeValue(reply.result().body().toString(), MemcacheMessage.class);
        byte[] outputKey = response.getKey();
        byte[] outputValue = response.getValue();
        byte[] inputKey = command.getKey();
        byte[] inputValue = command.getValue();

        // Input keys & values should match output keys and values.
        if (Arrays.equals(inputKey, outputKey) && Arrays.equals(inputValue, outputValue)) {
          testContext.completeNow();
        } else {
          testContext.failNow(new Throwable("Unexpected response"));
        }
      } else {
        testContext.failNow(reply.cause());
      }
    });
  }

  @Test
  @DisplayName("Handle get command for non-existent key")
  void cacheVerticleGetNotPresent(Vertx vertx, VertxTestContext testContext) {
    EventBus eventBus = vertx.eventBus();
    String key = "abcd";
    MemcacheMessage command = new MemcacheMessage(MemcacheMessage.CommandType.GET, key.getBytes(), null, 0);
    validateMessage(eventBus, command, testContext);
  }

  @Test
  @DisplayName("Handle put command")
  void cacheVerticlePut(Vertx vertx, VertxTestContext testContext) {
    EventBus eventBus = vertx.eventBus();
    String key = "abc";
    String value = "hello";
    MemcacheMessage command = new MemcacheMessage(MemcacheMessage.CommandType.SET, key.getBytes(), value.getBytes(), 0);
    validateMessage(eventBus, command, testContext);
  }

  @Test
  @DisplayName("Handle put followed by get command")
  void cacheVerticlePutAndGet(Vertx vertx, VertxTestContext testContext) {
    EventBus eventBus = vertx.eventBus();
    String key = "abcde";
    String value = "hello";
    MemcacheMessage putCommand = new MemcacheMessage(MemcacheMessage.CommandType.SET, key.getBytes(), value.getBytes(), 0);
    MemcacheMessage getCommand = new MemcacheMessage(MemcacheMessage.CommandType.GET, key.getBytes(), null, 0);
    validateMessage(eventBus, putCommand, testContext);
    validateMessage(eventBus, getCommand, testContext);
  }
}
