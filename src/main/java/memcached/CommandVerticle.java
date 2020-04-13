package memcached;

import io.netty.buffer.ByteBuf;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetServer;
import memcached.command.Decoder;
import memcached.common.MemcacheMessage;
import memcached.common.MemcacheMessage.CommandType;
import memcached.util.ByteBufHelper;
import memcached.util.Constants;
import java.util.ArrayList;

import static memcached.command.CommandParser.*;
import static memcached.util.Constants.DEFAULT_PORT;

/**
 * CommandVerticle processes the incoming requests (from different clients, such as telnet, etc.)
 * The main job of this verticle is to decode the incoming commands of the protocol:
 * GET or SET for now and post the information via event bus to CacheVerticle.
 * This verticle is responsible for input validation and preparing and posting the response.
 *
 * Cache verticle performs the operation of storing and retrieving entries from the cache
 * instance. One advantage of keeping the two verticles separate is that they can scale
 * independently. If we need more command processing logic, we can have multiple instances
 * of the command verticles which post to the event bus for a single instance of cache verticle
 * to consume.
 */
public class CommandVerticle extends AbstractVerticle {
  private NetServer server;

  @Override
  public void start(Future<Void> startFuture) {
    Decoder decoder = new Decoder();            // Used for decoding input streams into set(s) of command(s).
    ByteBufHelper helper = ByteBufHelper.getInstance(); // Helper method for ByteBuf streams
    final EventBus eventBus = vertx.eventBus(); // Event bus to post/pick messages to/from.

    // Create a TCP server
    server = vertx.createNetServer();
    System.out.println("TC: Started Server!");
    server.connectHandler(netSocket -> {

      // Process each incoming connection and maintain some state per connection
      System.out.println("Incoming connection");
      final boolean[] expectData = {false};         // Flags to indicate if we're expecting data in a subsequent stream
      final MemcacheMessage[] oldCommand = {null};  // Cached old SET message

      // Incoming stream of bytes may contain multiple CRLF-separated buffers.
      // One option is to process the entire stream together, but given that different clients (such as telnet)
      // may split the stream (at CRLF boundaries) and send multiple CRLF-ended streams to netSocket,
      // we have to allow for a single or multiple CRLF-separated buffers in a stream, and also,
      // multiple streams to process a single command (such as SET).
      // For example: we may get a buffer: set abc 0 0 5\r\nhello\r\n as a single request to the socket
      //              or we may get two requests to the socket:
      //              - set abc 0 0 5\r\n
      //              - hello\r\n
      // Our code should be able to handle both.
      netSocket.handler(buffer -> {
        // Extract all the input lines split by CRLF (i.e. '\r\n') separators.
        // Each such line will be processed separately. This is to support different clients, such as telnet
        // which have different ways of sending commands.
        ArrayList<ByteBuf> lines = helper.extractCrlfSplitBufs(buffer.getByteBuf());

        // Asynchronous event bus response handler. This processes responses obtained from
        // the event bus.
        Handler<AsyncResult<Message<Object>>> eventBusResponseHandler = eventBusResponse -> {
          if (eventBusResponse.succeeded()) {
            // Extract the response from event bus and write output to socket
            MemcacheMessage response = Json.decodeValue(eventBusResponse.result().body().toString(), MemcacheMessage.class);
            ByteBuf b = decoder.translate(response);
            netSocket.write(Buffer.buffer(b));
          } else {
            netSocket.write(Buffer.buffer(CR));
          }
        };

        // Process each line
        for (ByteBuf byteBuf : lines) {
          // Do not process a line if it is empty and no input is expected
          if (helper.isCRLFOnly(byteBuf) && !expectData[0]) {
            netSocket.write(Buffer.buffer(CR));
            return;
          }
          // Decode the input buffer and extract a message to process
          MemcacheMessage command = decoder.decode(byteBuf, expectData[0]);
          if (command == null) {
            netSocket.write(Buffer.buffer(CLIENT_ERROR));
            netSocket.write(Buffer.buffer(CRLF));
            return;
          }

          // A SET command expects input meta information (such as keys, length, etc.) in one CRLF ended byteBuf
          // and actual value in the following CRLF ended bytebuf. Hence, maintain state in the verticle
          // which keeps track of expectData, i.e. the state machine expects the next incoming bytes to be
          // the data which corresponds to the previously sent SET command.
          // This is useful when clients such as telnet will send SET command in two separate requests
          // as discussed above
          if (command.getCommandType().equals(CommandType.SET)) {
            // Capture key and length from first SET command issued
            if (!expectData[0]) {
              expectData[0] = true;
              oldCommand[0] = command;
            } else {
              // Capture value associated with the key in the previous SET cmd.
              MemcacheMessage prevCommand = oldCommand[0];
              command.setKey(prevCommand.getKey().clone());
              expectData[0] = false;

              // Compare the length of data of current buffer with previous command's length. They should match
              if (command.getLen() != prevCommand.getLen()) {
                System.out.println("SET: Length of data does not match length in the command: " +
                        "data's length: " + command.getLen() + ", expected length: " + prevCommand.getLen());
                netSocket.write(Buffer.buffer(CLIENT_ERROR));
                netSocket.write(Buffer.buffer(CRLF));
                return;
              }
            }
          }

          // Pass the message to event bus
          if (!expectData[0]) {
            // Note that event bus only accepts JsonObject, so JSON-ify the message
            eventBus.send(Constants.ADDRESS, JsonObject.mapFrom(command), eventBusResponseHandler);
          }
        }
      });
    });
    server.listen(config().getInteger("tcp.port", DEFAULT_PORT), "localhost", tcp -> {
      if (tcp.succeeded()) {
        startFuture.complete();
        System.out.println("Listening on port " + config().getInteger("tcp.port", DEFAULT_PORT));
      } else {
        startFuture.fail(tcp.cause());
      }
    });
  }

  @Override
  public void stop() {
    server.close(res -> {
      if (res.succeeded()) {
        System.out.println("Server is now closed");
      } else {
        System.out.println("close failed");
      }
    });
  }
}
