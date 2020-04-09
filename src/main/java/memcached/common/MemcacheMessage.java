package memcached.common;

/***
 * MemcacheMessage is the message exchanged between the verticles on the event bus.
 * It is posted by CommandVerticle after decoding the input command, and is consumed
 * by the CacheVerticle to perform get / set operations on memcache.
 */
public class MemcacheMessage {

  // For now, we support GET and SET command types. Extend this to support more in the future
  public enum CommandType {
    GET,
    SET,
  }
  private CommandType commandType;    // Command type issued
  private byte[] key;                 // Key
  private byte[] value;               // Value
  private int len;                    // Length of the value

  /***
   * Default constructor for MemcacheMessage object
   * @param commandType
   * @param key
   * @param value
   * @param len
   */
  public MemcacheMessage(CommandType commandType, byte[] key, byte[] value, int len) {
    this.commandType = commandType;
    this.key = key;
    this.value = value;
    this.len = len;
  }

  public MemcacheMessage() {}

  public CommandType getCommandType() {
    return commandType;
  }

  public void setCommandType(CommandType commandType) {
    this.commandType = commandType;
  }

  public byte[] getKey() {
    return key;
  }

  public void setKey(byte[] key) {
    this.key = key;
  }

  public byte[] getValue() {
    return value;
  }

  public void setValue(byte[] value) {
    this.value = value;
  }

  public int getLen() {
    return len;
  }

  public void setLen(int len) {
    this.len = len;
  }
}
