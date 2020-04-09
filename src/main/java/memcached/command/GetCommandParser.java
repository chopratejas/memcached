package memcached.command;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import memcached.common.MemcacheMessage;
import memcached.util.ByteBufHelper;

import java.nio.charset.Charset;

/***
 * GET command parser is responsible for parsing the get commands in accordance with
 * memcached protocol listed: https://github.com/memcached/memcached/blob/master/doc/protocol.txt
 *
 * GET command format:
 * -------------------
 * get <key>*\r\n
 * - <key>* means one or more key strings separated by whitespace.
 *   NOTE: We do not support multiple keys in our current implementation.
 *
 * GET response format:
 * --------------------
 * VALUE <key> <flags> <bytes> [<cas unique>]\r\n
 * <data block>\r\n
 * END\r\n
 *
 * - <key> is the key for the item being sent
 * - <flags> is the flags value set by the storage command.
 *   NOTE: We return 0 for now, we do not support storing or retrieving flags.
 * - <bytes> is the length of the data block to follow, *not* including
 *   its delimiting \r\n
 * - <cas unique> is a unique 64-bit integer that uniquely identifies
 *   this specific item. NOTE: We do not send back this field.
 * - <data block> is the data for this item.
 *
 */
public class GetCommandParser implements CommandParser {
  private static final ByteBuf GET = Unpooled.copiedBuffer("get", Charset.defaultCharset());
  private ByteBufHelper helper = ByteBufHelper.getInstance();

  /***
   * This method parses the incoming GET command. Note that for now, we support getting a
   * single key. If the client issues multiple keys separated by space, they will be treated
   * as a single key: for example: for command such as, `get abc hello`, the single key would be
   * `abc hello`. In the future, we would like to extend this command to take in multiple keys.
   * @param payload is the input stream of bytes
   * @param unused - not currently used
   * @return the memcache message which encapsulates the command to send over to the cache verticle
   */
  @Override
  public MemcacheMessage parse(ByteBuf payload, boolean unused) {
    ByteBuf key = helper.tokenize(payload, (byte) '\r', 0);
    return new MemcacheMessage(MemcacheMessage.CommandType.GET, key.array().clone(), null, 0);
  }

  /***
   * This method returns the command name for GET command parser.
   * @return command name
   */
  @Override
  public ByteBuf getCommandName() {
    return GET;
  }

  /***
   * This method processes the input memcache message and translates it into a response
   * which can be forwarded to the client. This response is in line with what the protocol
   * expects
   * @param input is the input mem cache message
   * @return stream of bytes which are sent back to the client.
   */
  @Override
  public ByteBuf translate(MemcacheMessage input) {
    ByteBuf response = Unpooled.buffer();
    byte[] valueBytes = input.getValue();
    // If the value is missing, our implementation returns nothing.
    if (valueBytes != null) {
      ByteBuf value = Unpooled.copiedBuffer(input.getValue());
      response.writeBytes(VALUE)
        .writeByte(' ')
        .writeBytes(Unpooled.copiedBuffer(input.getKey()))  // key
        .writeByte(' ')
        .writeBytes("0".getBytes(Charset.defaultCharset())) // flags
        .writeByte(' ')
        .writeBytes(String.valueOf(value.readableBytes())   // num bytes
          .getBytes(Charset.defaultCharset()))
        .writeBytes(CRLF)
        .writeBytes(value.slice().readerIndex(0))           // value
        .writeBytes(CRLF)
        .writeBytes(END)
        .writeBytes(CRLF);
    } else {
      response.writeBytes(CR);
    }
    return response;
  }
}
