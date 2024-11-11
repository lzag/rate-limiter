package com.lzag.ratelimiter

import io.vertx.config.ConfigRetriever
import io.vertx.config.ConfigRetrieverOptions
import io.vertx.config.ConfigStoreOptions
import io.vertx.core.AbstractVerticle
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.redis.client.Redis
import io.vertx.redis.client.RedisAPI
import java.nio.file.Files
import java.nio.file.Paths

class HttpVerticle : AbstractVerticle() {
  companion object {
    private val logger = LoggerFactory.getLogger(HttpVerticle::class.java)
  }
  override fun start(startPromise: Promise<Void>) {
    logger.info("Deploying HttpVerticle")

    val tokensPerMinute = config().getInteger("tokensPerMinute")
    val rateLimiterType = RateLimiterType.valueOf(config().getString("rateLimiterType").uppercase())
    val redisClient = Redis.createClient(vertx, "redis://localhost:6379")
    val redis = RedisAPI.api(redisClient)

//    RateLimiterFactory
//      .createRateLimiter(rateLimiterType, redis, tokensPerMinute, vertx)
//      .compose { rateLimiter ->
        vertx.createHttpServer()
          .requestHandler { req ->
//            rateLimiter.allowRequest(req).onComplete { ar ->
//              if (ar.succeeded() && ar.result()) {
//                req.response()
//                  .putHeader("content-type", "text/plain")
//                  .end("Request allowed")
//              } else {
                req.response().setStatusCode(429).end("Too Many Requests")
//              }
//            }
          }
          .requestHandler{ req ->
            // clean up the counters
          }
          .exceptionHandler(Throwable::printStackTrace) // clean up the counter
          .listen(8888)
//      }
      .onSuccess {
        startPromise.complete()
        println("HTTP server started on port 8888")
      }
      .onFailure { err ->
        logger.error(err)
        startPromise.fail(err)
      }
  }

  override fun stop() {
    logger.info("Stopping HttpVerticle")
  }
}
