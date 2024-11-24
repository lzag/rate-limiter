package com.lzag.ratelimiter

import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.redis.client.Command
import io.vertx.redis.client.Redis
import io.vertx.redis.client.RedisAPI
import io.vertx.redis.client.impl.types.ErrorType

class HttpVerticle : AbstractVerticle() {

  companion object {
    private val logger = LoggerFactory.getLogger(HttpVerticle::class.java)
  }

  override fun start(startPromise: Promise<Void>) {

    logger.info("Deploying HttpVerticle")
    val router = Router.router(vertx)

    val rateLimiter = RedisRateLimiter(
      config().getString("rateLimiterScriptSha"),
      config().getJsonObject("rateLimiter").getInteger("maxRequests"),
      RedisAPI.api(Redis.createClient(vertx, "redis://localhost:6379")),
    )

//     End handler for all routes
    router.route().last().handler { ctx ->
      println("calling end handler")
      endHandleRateLimited(ctx, rateLimiter)
        .onSuccess {
          println("Cleaned up")
//          ctx.response().end()
        }
        .onFailure() {
          println("Failed to clean up")
//          ctx.response().setStatusCode(500).end("Internal Server Error")
        }
    }

    // Common handler for all routes
    router.route().handler { ctx -> handleRateLimited(ctx, rateLimiter) }

    vertx.createHttpServer()
      .requestHandler(router)
      .exceptionHandler(Throwable::printStackTrace)
      .listen(8888)
      .onSuccess {
        println("HTTP server started on port 8888")
        startPromise.complete()
      }
      .onFailure { err ->
        logger.error(err)
        startPromise.fail(err)
      }
  }

  override fun stop(stopPromise: Promise<Void>) {
    println("Stopping HttpVerticle")
    stopPromise.complete()
  }

  private fun handleRateLimited(ctx: RoutingContext ,rateLimiter: RateLimiterInterface) {
    println("Common handler executed")
    rateLimiter
      .startConcurrent(ctx.request().getHeader("X-User-Id"))
      .compose { concurrentCount ->
        println("Concurrent: $concurrentCount")
        rateLimiter.checkRateLimit(ctx.request().getHeader("X-User-Id")).map { result ->
          println("Remaining: ${result.remaining}, Reset: ${result.resetTimestamp}, Concurrent: $concurrentCount")
          Triple(result.remaining, result.resetTimestamp, concurrentCount)
        }
      }
      .onSuccess { (remaining, resetTimestamp, concurrentCount) ->
        println("Remaining: $remaining, Reset: $resetTimestamp, Concurrent: $concurrentCount")
        if (remaining <= 0 || concurrentCount > config().getJsonObject("rateLimiter").getInteger("concurrentRequests")) {
          ctx.response()
            .putHeader("content-type", "text/plain")
            .putHeader("ratelimit-limit", config().getJsonObject("rateLimiter").getString("maxRequests"))
            .putHeader("ratelimit-remaining", remaining.toString())
            .putHeader("ratelimit-reset", vertx.sharedData().getLocalMap<String, Long>("rateLimiterData")["nextExecutionTime"].toString())

          ctx.response().setStatusCode(429).end("Too Many Requests")
          endHandleRateLimited(ctx, rateLimiter)
        } else {
          ctx.next()
        }
      }
      .onFailure() { err ->
        println(err)
        println("Failure!")
        println("Error: ${err.message}")
        if (err is ErrorType && err.message == "NOSCRIPT No matching script.") {
          // TODO if the message is NOSCRIPT No matching script. // need to reload the script
          vertx.eventBus().send("limiter.restart", "")
          println("Error: ${err.message}")
        }
//        endHandleRateLimited(ctx, rateLimiter)
        ctx.response().setStatusCode(500).end("Internal Server Error")
      }

  }

  private fun endHandleRateLimited(ctx: RoutingContext, rateLimiter: RateLimiterInterface): Future<Int> {
    logger.info("End handler executed")
    return rateLimiter.endConcurrent(ctx.request().getHeader("X-User-Id"))
//      .onSuccess() {
//        println("Concurrent request ended")
//      }
//      .onFailure {
//        ctx.response().setStatusCode(500).end("Internal Server Error")
//        println("Failed to end concurrent request")
//      }
  }
}
