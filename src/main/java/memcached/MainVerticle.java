package memcached;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;

public class MainVerticle extends AbstractVerticle {

  /***
   * Deploy an instance each of CommandVerticle and CacheVerticle
   */
  @Override
  public void start(Promise<Void> promise){
    CompositeFuture.all(
      deployHelper(CommandVerticle.class.getName()),  // Command processor
      deployHelper(CacheVerticle.class.getName()))    // Cache processor
      .setHandler(result -> {
        if(result.succeeded()){
          promise.complete();
        } else {
          promise.fail(result.cause());
        }
      });
  }

  private Future<Void> deployHelper(final String name){
    final Promise<Void> promise = Promise.promise();
    vertx.deployVerticle(name, res -> {
      if (res.failed()){
        System.out.println("Failed to deploy verticle! " + name);
        promise.fail(res.cause());
      } else {
        System.out.println(name + " verticle deployed!");
        promise.complete();
      }
    });
    return promise.future();
  }

}
