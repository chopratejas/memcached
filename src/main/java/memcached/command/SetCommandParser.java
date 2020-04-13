package memcached.command;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.vertx.core.buffer.Buffer;
import memcached.common.MemcacheMessage;
import memcached.util.ByteBufHelper;

import java.nio.charset.Charset;

import static memcached.util.Constants.MAX_KEY_SIZE_IN_BYTES;
import static memcached.util.Constants.MAX_VALUE_SIZE_IN_BYTES;

/***
 * SET command parser is responsible for parsing the set commands in accordance with
 * memcached protocol listed: https://github.com/memcached/memcached/blob/master/doc/protocol.txt
 *
 * SET command format
 * ------------------
 * set <key> <flags> <exptime> <bytes> [noreply]\r\n
 * <data block>\r\n
 *
 * - <key> is the key under which the client asks to store the data
 * - <flags> is an arbitrary 16-bit unsigned integer (written out in
 *   decimal) that the server stores along with the data and sends back
 *   when the item is retrieved. NOTE: We're disregarding this field.
 * - <exptime> is expiration time. If it's 0, the item never expires
 *   (although it may be deleted from the cache to make place for other
 *   items). If it's non-zero (either Unix time or offset in seconds from
 *   current time), it is guaranteed that clients will not be able to
 *   retrieve this item after the expiration time arrives (measured by
 *   server time). If a negative value is given the item is immediately
 *   expired. NOTE: We're disregarding this field.
 * - <bytes> is the number of bytes in the data block to follow, *not*
 *   including the delimiting \r\n. <bytes> may be zero (in which case
 *   it's followed by an empty data block).
 * - <data block> is a chunk of arbitrary 8-bit data of length <bytes>
 *   from the previous line.
 *
 * SET response format
 * -------------------
 * STORED\r\n to indicate success.
 *
 */
public class SetCommandParser implements CommandParser {
  private static final ByteBuf SET = Unpooled.copiedBuffer("set", Charset.defaultCharset());
  private ByteBufHelper helper = ByteBufHelper.getInstance();

  /***
   * Given a SET command, this method is used to extract the size from the input
   * stream of bytes.
   * @param in is the input stream of bytes
   * @return size field of the SET command statement.
   */
  private Integer getSize(ByteBuf in) {
    ByteBuf sizeInByteBuf;
    ByteBuf noReply;

    // We have processed all the bytes in the command upto the size field.
    // Per the protocol, we may have an optional 'noreply' field, which our
    // size parsing code should take care of.
    // In the current implementation, we do not process the noreply field, i.e.
    // we will respond with the STORED response even if 'noreply' is set.
    int bytesBeforeSpace = in.bytesBefore((byte) ' ');

    // If there is a space detected, then there is some data *after* the size field.
    // Confirm that this data is infact 'noreply'. If it is something else, return error
    if (bytesBeforeSpace >= 0) {
      sizeInByteBuf = helper.tokenize(in, (byte)' ', 1);
      noReply = in.readSlice(in.readableBytes());

      // Check that the slice is 'noreply'
      if (noReply != null &&
          !noReply.toString(Charset.defaultCharset()).equals(Buffer.buffer(NO_REPLY).toString())) {
        return -1;
      }
    } else {
      // No other field but size is present. Read the size value
      sizeInByteBuf = in.readBytes(in.readableBytes());
    }
    return Integer.valueOf(sizeInByteBuf.toString(Charset.defaultCharset()));
  }

  /***
   * This method is used to parse the SET command not including the data blob.
   * It extracts fields such as key and len, and will later use them to validate
   * the data blob sent by the client in the subsequent request.
   * @param in is the input command stream sent by the client
   * @return mem cache message which will later (once we receive the data blob) be
   *         posted to event bus for cache verticle to process it.
   */
  private MemcacheMessage parseSetCommand(ByteBuf in) {
    try {
      // Extract key, flags and expiration time. We ignore flags and expiration time
      // in our current implementation.
      ByteBuf payloadMeta = in.readSlice(in.bytesBefore((byte) '\r'));
      ByteBuf key = helper.tokenize(payloadMeta, (byte) ' ', 1);
      ByteBuf flags = helper.tokenize(payloadMeta, (byte) ' ', 1);
      ByteBuf expTime = helper.tokenize(payloadMeta, (byte) ' ', 1);
      if (expTime == null) {
        payloadMeta.skipBytes(1);
      }
      // Extract the len of the data which will follow this command
      Integer size = getSize(payloadMeta);

      if (size == null || size < 0 || size >= MAX_VALUE_SIZE_IN_BYTES ||
              Buffer.buffer(key).length() > MAX_KEY_SIZE_IN_BYTES) {
        return null;
      }
      return new MemcacheMessage(MemcacheMessage.CommandType.SET, key.array().clone(), null, size);
    } catch (Exception e) {
      return null;
    }
  }

  /***
   * This method is used to parse the data blob received by the parser. Validation
   * of this blob is done on the side of the caller.
   * @param in is the input stream of bytes
   * @return a mem cache message which will be pushed to the event bus
   */
  private MemcacheMessage parseSetData(ByteBuf in) {
    int bytesBeforeCRLF = helper.bytesBeforeCRLF(in.copy());
    ByteBuf payload = in.readBytes(bytesBeforeCRLF);
    return new MemcacheMessage(MemcacheMessage.CommandType.SET, null, payload.array().clone(), bytesBeforeCRLF);
  }

  /***
   * This is used to parse the input stream of bytes as either a SET command
   * or SET data.
   * @param in is the input buffer stream
   * @param parseIncomingStreamAsData indicates if the incoming stream should be
   *                                  treated as data or cmd.
   * @return memcache message to push to event bus
   */
  @Override
  public MemcacheMessage parse(ByteBuf in, boolean parseIncomingStreamAsData) {
    return parseIncomingStreamAsData ? parseSetData(in) : parseSetCommand(in);
  }

  /***
   * This method returns the command parser's name
   * @return SET command parser's name.
   */
  @Override
  public ByteBuf getCommandName() {
    return SET;
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
    if (valueBytes != null) {
      response.writeBytes(STORED)
        .writeByte(' ')
        .writeBytes(CRLF);
    }
    return response;
  }
}
