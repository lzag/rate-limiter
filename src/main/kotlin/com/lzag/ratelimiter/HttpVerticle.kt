package com.lzag.ratelimiter

import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.coroutines.CoroutineRouterSupport
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.coAwait
import io.vertx.redis.client.Redis
import io.vertx.redis.client.RedisAPI
import io.vertx.redis.client.impl.types.ErrorType
import kotlinx.coroutines.delay

class HttpVerticle : CoroutineVerticle(), CoroutineRouterSupport {
  companion object {
    private val logger = LoggerFactory.getLogger(HttpVerticle::class.java)
  }

  override suspend fun start() {
    logger.info("Deploying HttpVerticle")
    val router = Router.router(vertx)

    val rateLimiter =
      RedisRateLimiter(
        config.getString("rateLimiterScriptSha"),
        config.getJsonObject("rateLimiter").getString("algo"),
        config.getJsonObject("rateLimiter").getInteger("maxRequests"),
        config.getJsonObject("rateLimiter").getInteger("interval"),
        RedisAPI.api(Redis.createClient(vertx, "redis://localhost:6379")),
      )

    router.route().coHandler { ctx -> handleRateLimited(ctx, rateLimiter) }
    router.route().last().coHandler { ctx ->
      try {
        logger.debug("Calling end handler")
        endHandleRateLimited(ctx, rateLimiter)
        logger.debug("Cleaned up concurrent")
        ctx.response().end()
      } catch (err: Throwable) {
        logger.debug("Failed to clean up")
        ctx.response().setStatusCode(500).end("Internal Server Error")
      }
    }

    vertx.createHttpServer()
      .requestHandler(router)
      .exceptionHandler(Throwable::printStackTrace)
      .listen(8888).coAwait()
    logger.debug("HTTP server started on port 8888")
  }

  override suspend fun stop() {
    logger.debug("Stopping HttpVerticle")
  }

  private suspend fun handleRateLimited(
    ctx: RoutingContext,
    rateLimiter: RateLimiterInterface,
  ) {
    try {
      logger.debug("Common handler executing")
      val key = ctx.request().getHeader("X-User-Id")
      val concurrentCount = rateLimiter.startConcurrent(key).coAwait()
      logger.debug("Concurrent: $concurrentCount")
      val remaining = rateLimiter.checkRateLimit(key).coAwait()
      logger.debug("Remaining: $remaining, Concurrent: $concurrentCount")
      delay(3000)
      ctx.response()
        .putHeader("content-type", "text/plain")
        .putHeader("ratelimit-limit", config.getJsonObject("rateLimiter").getString("maxRequests"))
        .putHeader("ratelimit-remaining", remaining.toString())
        .putHeader("ratelimit-reset", vertx.sharedData().getLocalMap<String, Long>("rateLimiterData")["nextExecutionTime"].toString())

      if (remaining <= 0 || concurrentCount > config.getJsonObject("rateLimiter").getInteger("maxConcurrentPerUser") + 10) {
        ctx.response().setStatusCode(429).end("Too Many Requests")
        endHandleRateLimited(ctx, rateLimiter)
      } else {
        ctx.next()
      }
    } catch (err: Throwable) {
      logger.error(err)
      if (err is ErrorType && err.message == "NOSCRIPT No matching script.") {
        // if the message is NOSCRIPT No matching script. // need to reload the script
        vertx.eventBus().send("limiter.restart", "")
      }
      endHandleRateLimited(ctx, rateLimiter)
      ctx.response().setStatusCode(500).end("Internal Server Error")
    }
  }

  private suspend fun endHandleRateLimited(
    ctx: RoutingContext,
    rateLimiter: RateLimiterInterface,
  ): Int {
    logger.info("End handler executed")
    return rateLimiter.endConcurrent(ctx.request().getHeader("X-User-Id")).coAwait()
//      .onSuccess() {
//        println("Concurrent request ended")
//      }
//      .onFailure {
//        ctx.response().setStatusCode(500).end("Internal Server Error")
//        println("Failed to end concurrent request")
//      }
  }
}
