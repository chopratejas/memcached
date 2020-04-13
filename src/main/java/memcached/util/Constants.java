package memcached.util;

/**
 * Utils
 */
public class Constants {
  public static final int DEFAULT_PORT = 11211;     // Default port for memcached
  public static final String ADDRESS = "memcache";  // Event bus address to which verticles subscribe/publish
  public static final int NUM_CACHE_ENTRIES = 100000;
  public static final int MAX_KEY_SIZE_IN_BYTES = 256;
  public static final int MAX_VALUE_SIZE_IN_BYTES = 1024;
  private Constants() {}
}
