package memcached;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.util.Arrays;

import static memcached.command.CommandParser.*;
import static memcached.util.Constants.DEFAULT_PORT;

@ExtendWith(VertxExtension.class)
public class CommandVerticleTest {

  static NetClient client;
  static Integer port = DEFAULT_PORT;
  static String host = "localhost";
  static NetSocket socket;

  /**
   * This method will deploy the command verticle and initialize a client connection
   * to default port of memcached. This connection socket will be used to send
   * requests and read responses from the verticle.
   * @param vertx is the vertx instance
   * @param testContext is the vertx test context
   */
  @BeforeAll
  static void deployCommandVerticle(Vertx vertx, VertxTestContext testContext) {
    vertx.deployVerticle(MainVerticle.class.getName(), handler -> {
      client = vertx.createNetClient();
      client.connect(port, host, result -> {
        if (result.succeeded()) {
          socket = result.result();
          testContext.completeNow();
        } else {
          socket = null;
          testContext.failNow(result.cause());
        }
      });
    });
  }

  @AfterAll
  static void tearDownCommandVerticle(Vertx vertx, VertxTestContext testContext) {
    vertx.close(testContext.completing());
  }

  /***
   * Check if the response buffer contains CLIENT_ERROR semantics of
   * memcached protocol
   * @param buffer is the response buffer
   * @return true if it is equal to the client error string, false, otherwise.
   */
  private boolean isClientError(Buffer buffer) {
    return Arrays.equals(buffer.getBytes(), Buffer.buffer(CLIENT_ERROR).appendBytes(CRLF).getBytes());
  }

  /***
   * Check if the buffer is empty (i.e. in this case, contains \r)
   * @param buffer is input buffer
   * @return true if buffer contains only \r, false otherwise
   */
  private boolean isEmpty(Buffer buffer) {
    return buffer.equals(Buffer.buffer(CR));
  }

  /***
   * Prepare response for SET command based on memcached protocol
   * @return SET response
   */
  private String prepareSetResponse() {
    return Buffer.buffer(STORED)
      .appendString(" ")
      .appendBytes(CRLF).toString();
  }

  /***
   * Prepare response for GET command based on memcached protocol
   * @param key is the input key for GET request
   * @param value is the value obtained
   * @return GET response
   */
  private String prepareGetResponse(Buffer key, Buffer value) {
    return Buffer.buffer()
      .appendBytes(VALUE)
      .appendString(" ")
      .appendBytes(key.getBytes())
      .appendString(" ").appendBytes("0".getBytes()) // NOTE: We set flags to 0
      .appendString(" ")
      .appendInt(value.toString().length())
      .appendBytes(CRLF)
      .appendString(value.toString())
      .appendBytes(CRLF).appendBytes(END).appendBytes(CRLF).toString();
  }

  @Test
  @DisplayName("Handle no command")
  void verticleHandleEmpty(Vertx vertx, VertxTestContext testContext) {
    assert socket != null;
    socket.write("\r\n");
    socket.handler(buffer -> {
      if (isEmpty(buffer)) {
        testContext.completeNow();
      } else {
        testContext.failNow(new Throwable("Unexpected response"));
      }
    });
  }

  @Test
  @DisplayName("Handle incorrect command")
  void verticleHandleIncorrectCmd(Vertx vertx, VertxTestContext testContext) {
    assert socket != null;
    socket.write("setabc\r\n");
    socket.handler(buffer -> {
      if (isClientError(buffer)) {
        testContext.completeNow();
      } else {
        testContext.failNow(new Throwable("Unexpected response"));
      }
    });
  }

  @Test
  @DisplayName("Handle incomplete command")
  void verticleHandleIncompleteCmd(Vertx vertx, VertxTestContext testContext) {
    assert socket != null;
    socket.write("set abc 0\r 0\r\r\n");
    socket.handler(buffer -> {
      if (isClientError(buffer)) {
        testContext.completeNow();
      } else {
        testContext.failNow(new Throwable("Unexpected response"));
      }
    });
  }

  /***
   * Handle get request of missing data
   * @param vertx
   * @param testContext
   */
  @Test
  @DisplayName("Handle get not present")
  void verticleHandleGetNotPresent(Vertx vertx, VertxTestContext testContext) {
    socket.write("get def\r\n");
    socket.handler(buffer -> {
      if (isEmpty(buffer)) {
        testContext.completeNow();
      } else {
        testContext.failNow(new Throwable("Unexpected response"));
      }
    });
  }

  /***
   * Handle set request with correct params and data
   * @param vertx
   * @param testContext
   */
  @Test
  @DisplayName("Handle set")
  void verticleHandleSet(Vertx vertx, VertxTestContext testContext) {
    socket.write("set abc 0 0 5\r\nhello\r\n");
    socket.handler(buffer -> {
      if (buffer.toString().equals(prepareSetResponse())) {
        testContext.completeNow();
      } else {
        testContext.failNow(new Throwable("Unexpected response"));
      }
    });
  }

  /***
   * Handle set request with incorrect len field
   * @param vertx
   * @param testContext
   */
  @Test
  @DisplayName("Handle set with incorrect len")
  void verticleHandleSetIncorrect(Vertx vertx, VertxTestContext testContext) {
    assert socket != null;
    socket.write("set abc 0 0 6\r\nhello\r\n");
    socket.handler(buffer -> {
      if (isClientError(buffer)) {
        testContext.completeNow();
      } else {
        testContext.failNow(new Throwable("Unexpected response"));
      }
    });
  }

  /***
   * Handle set request followed by a get request and validate the basic case
   * @param vertx
   * @param testContext
   */
  @Test
  @DisplayName("Handle set and get")
  void verticleHandleSetAndGet(Vertx vertx, VertxTestContext testContext) {
    assert socket != null;
    socket.write("set abc 0 0 1\r\n\r\r\nget abc\r\n");
    socket.handler(buffer -> {
      String response = buffer.toString();

      if (response.equals(prepareGetResponse(Buffer.buffer("abc"), Buffer.buffer("hello"))) ||
        response.equals(prepareSetResponse())) {
        testContext.completeNow();
      } else {
        testContext.failNow(new Throwable("Unexpected response"));
      }
    });
  }

  /***
   * Handle set request with zero len data
   * @param vertx
   * @param testContext
   */
  @Test
  @DisplayName("Handle set with zero len")
  void verticleHandleSetZeroLen(Vertx vertx, VertxTestContext testContext) {
    assert socket != null;
    socket.write("set abc 0 0 0\r\n\r\n");
    socket.handler(buffer -> {
      if (buffer.toString().equals(prepareSetResponse())) {
        testContext.completeNow();
      } else {
        testContext.failNow(new Throwable("Unexpected response"));
      }
    });
  }

  /***
   * Handle set request with negative len specified
   * @param vertx
   * @param testContext
   */
  @Test
  @DisplayName("Handle set with negative len")
  void verticleHandleSetNegativeLen(Vertx vertx, VertxTestContext testContext) {
    socket.write("set abc 0 0\r -1\r\n\r\n");
    socket.handler(buffer -> {
      if (isClientError(buffer)) {
        testContext.completeNow();
      } else {
        testContext.failNow(new Throwable("Unexpected response"));
      }
    });
  }

  /***
   * Handle set request with 'noreply' added to the command
   * @param vertx
   * @param testContext
   */
  @Test
  @DisplayName("Handle set with 'noreply'")
  void verticleHandleSetNoreply(Vertx vertx, VertxTestContext testContext) {
    assert socket != null;
    socket.write("set abc 0 0 0 noreply\r\n\r\n");
    socket.handler(buffer -> {
      if (buffer.toString().equals(prepareSetResponse())) {
        testContext.completeNow();
      } else {
        testContext.failNow(new Throwable("Unexpected response"));
      }
    });
  }

  /***
   * Handle set request with \r interspersed in data.
   * @param vertx
   * @param testContext
   */
  @Test
  @DisplayName("Handle set with \\r in payload")
  void verticleHandleSetPayloadWithEsc(Vertx vertx, VertxTestContext testContext) {
    assert socket != null;
    socket.write("set abc 0 0 6\r\nhell\ro\r\n");
    socket.handler(buffer -> {
      if (buffer.toString().equals(prepareSetResponse())) {
        testContext.completeNow();
      } else {
        testContext.failNow(new Throwable("Unexpected response"));
      }
    });
  }
}
