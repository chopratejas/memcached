package memcached.cache;

/**
 * Cache interface - exposes the APIs which can be implemented by different
 * caching schemes: LRU, LFU, LFRU, etc.
 * @param <K>
 * @param <V>
 */
public interface MemCache<K, V> {
  V get(K k);
  void set(K k, V v);
  boolean containsKey(K k);
  long size();
}
