package memcached.command;

import io.netty.buffer.ByteBuf;
import memcached.common.MemcacheMessage;

import java.nio.charset.Charset;

/***
 * CommandParser interface is used to parse incoming commands from clients.
 * It is also an interface to translate messages received from event bus into
 * buffers which can be relayed to the client.
 */
public interface CommandParser {

  // A bunch of useful byte arrays to parse data and prepare responses
  byte[] CRLF = new byte[]{'\r', '\n'};
  byte[] CR = new byte[]{'\r'};
  byte[] VALUE = "VALUE".getBytes(Charset.defaultCharset());
  byte[] END = "END".getBytes(Charset.defaultCharset());
  byte[] STORED = "STORED".getBytes(Charset.defaultCharset());
  byte[] CLIENT_ERROR = "CLIENT_ERROR".getBytes(Charset.defaultCharset());
  byte[] NO_REPLY = "noreply".getBytes(Charset.defaultCharset());

  /***
   * Parse an incoming stream of bytes into a Memcache message which can be sent
   * over the event bus to other verticles to start processing. ExpectData implies
   * that the parser expects data in a subsequent stream before it can relay the
   * message to the receiving verticle via event bus. This is used to handle SET
   * commands where clients such as telnet may choose to send over the data for
   * the set command in a subsequent call to the socket.
   * @param in is the input buffer stream
   * @param expectData indicates to the parser to wait for a stream of data bytes
   * @return MemcacheMessage which contains information for the receiving verticle.
   */
  MemcacheMessage parse(ByteBuf in, boolean expectData);

  /***
   * Returns the name of the command parser (GET or SET)
   * @return as above.
   */
  ByteBuf getCommandName();

  /***
   * This method translates the message received from the cache verticle into
   * a response that can be sent back to the client.
   * @param m is the message sent over the event bus in the form of a response.
   * @return stream of bytes that can be sent back to the client
   */
  ByteBuf translate(MemcacheMessage m);
}
