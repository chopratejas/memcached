package memcached;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import memcached.cache.LRUCache;
import memcached.common.MemcacheMessage;
import memcached.util.Constants;

import java.nio.charset.Charset;

import static memcached.util.Constants.NUM_CACHE_ENTRIES;

/***
 * Cache verticle picks up messages from the event bus and processes them.
 * The message is a MemcacheMessage which contains the following:
 * - CommandType: Indicating if it is a GET or SET
 * - Key: Key for the cache
 * - Value: Value to be associated with the key.
 *
 * The cache verticle interacts with the cache to store/retrieve data and
 * passes the response back in the form of another MemcacheMessage.
 *
 * Note that the cache verticle contains an instance of the LRU cache which
 * is sized by the number of entries. This can be extended in the future
 * to be sized by the total memory size.
 *
 * Keeping a cache verticle separated from the command verticle simplifies
 * the coding logic. All the command processing tasks and the response
 * formatting tasks which are protocol specific are handled by the Command
 * verticle, whereas all the basic protocol agnostic set/get operations are
 * performed by this verticle.
 */
public class CacheVerticle extends AbstractVerticle {

  @Override
  public void start() {
    final EventBus eventBus = vertx.eventBus();
    LRUCache<ByteBuf, ByteBuf> cache = new LRUCache<>(NUM_CACHE_ENTRIES); // allow 10k entries.

    // For each received message, extract the memcachemessage object and process it
    eventBus.consumer(Constants.ADDRESS, receivedMessage -> {
      MemcacheMessage memcacheMessage = Json.decodeValue(receivedMessage.body().toString(), MemcacheMessage.class);
      receivedMessage.reply(JsonObject.mapFrom(process(memcacheMessage, cache)));
    });
  }

  /***
   * Depending on the memcache message object, this method will either store content
   * in the LRU cache or will retrieve content from the cache.
   * @param input is the incoming memcache message
   * @param cache is the cache instance
   * @return response memcache object which contains the kv pair
   */
  private MemcacheMessage process(MemcacheMessage input, LRUCache<ByteBuf, ByteBuf> cache) {
    MemcacheMessage output = new MemcacheMessage();
    ByteBuf key = Unpooled.copiedBuffer(input.getKey());

    output.setCommandType(input.getCommandType());
    output.setKey(input.getKey().clone());

    // For a set command, perform a cache put. This will internally evict entries from the cache
    // if size is exceeded.
    if (input.getCommandType().equals(MemcacheMessage.CommandType.SET)) {
      ByteBuf value = Unpooled.copiedBuffer(input.getValue());
      cache.set(key, value);
      output.setValue(input.getValue().clone());
      System.out.println("SET: Key: " + key.toString(Charset.defaultCharset()) + ", Value: " + value.toString(Charset.defaultCharset()));
    } else {
      // Obtain the value corresponding to the key if it is available
      System.out.println("GET: Key: " + key.toString(Charset.defaultCharset()));
      if (cache.containsKey(key)) {
        ByteBuf value = cache.get(key).copy();
        output.setValue(value.array().clone());
      }
    }
    return output;
  }

}
