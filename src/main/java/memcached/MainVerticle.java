package memcached;

import io.vertx.core.*;
import io.vertx.core.json.JsonObject;

public class MainVerticle extends AbstractVerticle {

  /***
   * Deploy an instance each of CommandVerticle and CacheVerticle
   */
  @Override
  public void start(Promise<Void> promise){

    DeploymentOptions options = new DeploymentOptions()
            .setConfig(new JsonObject().put("tcp.port", 11211));

    CompositeFuture.all(
      deployHelper(CommandVerticle.class.getName(), options),  // Command processor
      deployHelper(CacheVerticle.class.getName(), options))    // Cache processor
      .setHandler(result -> {
        if(result.succeeded()){
          promise.complete();
        } else {
          promise.fail(result.cause());
        }
      });
  }

  private Future<Void> deployHelper(final String name, DeploymentOptions options){
    final Promise<Void> promise = Promise.promise();
    vertx.deployVerticle(name, options, res -> {
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
