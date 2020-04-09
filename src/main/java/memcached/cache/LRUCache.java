package memcached.cache;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;

import java.util.concurrent.ConcurrentMap;

/***
 * LRU cache or Least Recently Used cache is an in-memory cache initialized with a size.
 * This size corresponds to the number of entries in the cache. Each entry is a key-value
 * pair where both keys and values can be of arbitrary sizes.
 * The goal of this implementation is to have O(1) insertion and lookup time. Since cache
 * size is limited by memory, per LRU principle, this cache will evict its least
 * recently used entry to make way for an incoming new entry.
 * At any point in time, the maximum entries available in the cache are 'size'.
 * We leverage the ConcurrentLinkedHashMap for implementing this LRU cache.
 *
 * Properties of ConcurrentLinkedHashMap:
 * - Implements a linked hash map, which allows for O(1) inserts and lookups, amortized
 * - Each access to an entry updates the recency of the entry, and in the traditional
 *   LinkedHashMap implementation, it would mean locking the list. ConcurrentLinkedHashMap
 *   overcomes this by separating the synchronous update of the hashmap, from the
 *   asynchronous update for the linked list.
 *
 * Details of the implementation and design choices:
 * https://github.com/ben-manes/concurrentlinkedhashmap/wiki/Design
 *
 * @param <K> is the key type: Can be ByteBuf, String, etc. CRLF ended.
 * @param <V> is the value type: Can be ByteBuf, String, etc. CRLF ended.
 */
public class LRUCache<K, V> implements MemCache<K, V> {

  private ConcurrentMap<K, V> cache;

  public LRUCache(int size) {
    cache = new ConcurrentLinkedHashMap.Builder<K, V>()
      .maximumWeightedCapacity(size)
      .build();
  }

  @Override
  public V get(K k) {
    return cache.get(k);
  }

  @Override
  public void set(K k, V v) {
    cache.put(k, v);
  }

  @Override
  public boolean containsKey(K k) {
    return cache.containsKey(k);
  }

  @Override
  public long size() {
    return cache.size();
  }
}
