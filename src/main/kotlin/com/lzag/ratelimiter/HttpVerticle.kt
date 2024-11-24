package com.lzag.ratelimiter

import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
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
    val rateLimiterConfig = config().getJsonObject("rate_limiter")

    val rateLimiter = RateLimiter(
      rateLimiterConfig.getInteger("max_requests"),
      rateLimiterConfig.getInteger("concurrent_requests"),
      RateLimiterAlgo.valueOf(rateLimiterConfig.getString("algo").uppercase()),
      RedisAPI.api(Redis.createClient(vertx, "redis://localhost:6379")),
      vertx
    )

    // End handler for all routes
    router.route().last().handler { ctx -> endHandleRateLimited(ctx, rateLimiter) }

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

  private fun handleRateLimited(ctx: RoutingContext ,rateLimiter: RateLimiter) {
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
        if (remaining <= 0 || concurrentCount > rateLimiterConfig.getInteger("concurrent_requests")) {
          ctx.response()
            .putHeader("content-type", "text/plain")
            .putHeader("ratelimit-limit", "60")
            .putHeader("ratelimit-remaining", remaining.toString())
            .putHeader("ratelimit-reset", resetTimestamp.toString())
          ctx.response().setStatusCode(429).end("Too Many Requests")
        } else {
          ctx.next()
        }
      }
      .onFailure() { err ->
        println("Error: ${err.message}")
        if (err is ErrorType && err.message == "NOSCRIPT No matching script.") {
          // TODO if the message is NOSCRIPT No matching script. // need to reload the script
          println("Error: ${err.message} hheheh")
        }
        ctx.response().setStatusCode(500).end("Internal Server Error")
      }

  }

  private fun endHandleRateLimited(ctx: RoutingContext, rateLimiter: RateLimiter) {
    logger.info("End handler executed")
    rateLimiter
      .endConcurrent(ctx.request().getHeader("X-User-Id"))
      .onSuccess() {
        ctx.response().end()
        println("Concurrent request ended")
      }
      .onFailure {
        ctx.response().setStatusCode(500).end("Internal Server Error")
        println("Failed to end concurrent request")
      }
  }
}
