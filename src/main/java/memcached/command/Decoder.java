package memcached.command;

import io.netty.buffer.ByteBuf;
import memcached.common.MemcacheMessage;
import memcached.util.ByteBufHelper;

import java.util.ArrayList;

/***
 * Decoder is the entry point for the commands issued by clients. Based on the
 * first few bytes, the decoder will either forward the command to appropriate
 * command parser or will return right away, indicating that the command issued
 * by the client is erroneous.
 */
public class Decoder {
  private ArrayList<CommandParser> commands = new ArrayList<>();      // List of command parsers.
  private SetCommandParser setCommandParser = new SetCommandParser(); // Set command parser
  private GetCommandParser getCommandParser = new GetCommandParser(); // Get command parser
  private ByteBufHelper helper = ByteBufHelper.getInstance();

  /***
   * Add new command parsers here. For now, it supports GET and SET parsers.
   */
  public Decoder() {
    commands.add(setCommandParser);
    commands.add(getCommandParser);
  }

  /***
   * Translate the MemcacheMessage into a stream of bytes which can be sent back to
   * the client. Note that this stream of bytes adheres to the response structure of
   * the memcache protocol as outlined here: https://github.com/memcached/memcached/blob/master/doc/protocol.txt
   * @param m is the memcache message received from the event bus
   * @return stream of bytes
   */
  public ByteBuf translate(MemcacheMessage m) {
    switch (m.getCommandType()) {
      case SET: return setCommandParser.translate(m);
      case GET: return getCommandParser.translate(m);
      default: return null;
    }
  }

  /***
   * Decode the input command. If isData is set, the incoming buffer is treated as data for
   * a previously issued SET command.
   * @param in is the input buffer stream for processing
   * @param isData implies if the input buffer is command or if it is data.
   * @return memcache message which can be sent over the event bus.
   */
  public MemcacheMessage decode(ByteBuf in, boolean isData) {
    // Only SET command parser can parse the data stream
    if (isData) {
      return setCommandParser.parse(in, true);
    }

    // Find the first word in the input stream and check which command it is.
    ByteBuf command = helper.tokenize(in, (byte) ' ', 1);
    for (CommandParser c : commands) {
      if (c.getCommandName().equals(command)) {
        return c.parse(in, false);
      }
    }
    // None of the parsers recognize this command
    return null;
  }
}
