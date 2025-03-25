package com.lzag.ratelimiter

import io.vertx.circuitbreaker.CircuitBreaker
import io.vertx.circuitbreaker.CircuitBreakerOptions
import io.vertx.circuitbreaker.CircuitBreakerState
import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.coroutines.CoroutineRouterSupport
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.coAwait
import io.vertx.redis.client.Redis
import io.vertx.redis.client.RedisAPI
import kotlinx.coroutines.*

class HttpVerticle : CoroutineVerticle(), CoroutineRouterSupport {
  companion object {
    private val logger = LoggerFactory.getLogger(this::class.java)
  }

  private lateinit var breaker: CircuitBreaker
  private lateinit var rateLimiter: RateLimiterInterface
  private lateinit var concurrentLimiter: ConcurrentRateLimiterInterface

  override suspend fun start() {
    logger.info("Deploying HttpVerticle")
    rateLimiter = RedisRateLimiter.create(config.getJsonObject("rateLimiter"), vertx)
    concurrentLimiter =
      RedisConcurrentRateLimiter(
        config.getJsonObject("rateLimiter").getInteger("maxConcurrentPerEndpoint"),
        RedisAPI.api(Redis.createClient(vertx, "redis://${config.getJsonObject("rateLimiter").getString("redisHost")}:6379")),
      )

    breaker =
      CircuitBreaker.create(
        "redis-breaker",
        vertx,
        CircuitBreakerOptions()
          .setMaxFailures(5)
          .setResetTimeout(5000)
          .setTimeout(Long.MAX_VALUE),
      ).openHandler {
        logger.error("Circuit breaker opened")
      }

    val router = Router.router(vertx)
    router.route().coHandler { ctx ->
      logger.debug("Handler started for ${ctx.request().path()}")
      try {
        logger.debug("Breaker state: ${breaker.state()}")
        if (breaker.state() == CircuitBreakerState.OPEN) {
          logger.error("Circuit breaker is open, rejecting request")
          ctx.response().setStatusCode(503).end("Service Unavailable")
          return@coHandler
        }
        breaker.execute { promise ->
          launch {
            logger.debug("Coroutine launched on ${Thread.currentThread().name}")
            try {
              handleRateLimited(ctx)
              promise.complete("OK")
            } catch (err: Throwable) {
              logger.error("Error in handling request: ${err.message}")
              err.printStackTrace()
              promise.fail(err)
              if (!ctx.response().ended()) {
                ctx.response().setStatusCode(500).end(err.message)
              }
            }
          }.invokeOnCompletion { err ->
            if (err != null && !promise.future().isComplete) {
              promise.fail(err)
            }
          }
        }.coAwait()
      } catch (err: Throwable) {
        logger.error("Breaker error", err)
        if (!ctx.response().ended()) {
          ctx.response().setStatusCode(500).end("Internal Server Error")
        }
      }
    }

    vertx.createHttpServer()
      .requestHandler(router)
      .exceptionHandler {
        logger.error("Exception in server: ${it.message}")
        it.printStackTrace()
      }
      .connectionHandler {
        it.closeHandler {
          logger.debug("Connection closed")
        }
        logger.debug("Connection opened")
      }
      .listen(8888).coAwait()
    logger.debug("HTTP server started on port 8888")
  }

  override suspend fun stop() {
    logger.debug("Stopping HttpVerticle")
  }

  private suspend fun handleRateLimited(ctx: RoutingContext) {
    logger.debug("Common handler executing")
    val key = ctx.request().getHeader("X-User-Id") + ":" + this.deploymentID
    val concurrentKey = ctx.request().path() + ":" + this.deploymentID
    logger.debug("key: ${ctx.request().path()}")
    try {
      concurrentLimiter.executeConcurrent(concurrentKey) {
        val rateLimitCheck = rateLimiter.checkRateLimit(key).coAwait()
        logger.debug("Remaining for user $key: ${rateLimitCheck.remaining}")
        delay(5)
        ctx.response().apply {
          putHeader("content-type", "text/plain")
          putHeader("ratelimit-limit", rateLimitCheck.max.toString())
          putHeader("ratelimit-remaining", rateLimitCheck.remaining.toString())
          if (rateLimitCheck.reset != null) putHeader("ratelimit-reset", rateLimitCheck.reset.toString())
        }

        if (!rateLimitCheck.allowed) {
          ctx.response().setStatusCode(429).end("Too Many Requests")
        } else {
          ctx.response().end("OK")
        }
      }
    } catch (err: ConcurrentExceededException) {
      ctx.response().setStatusCode(420).end("Take a chill pill")
    }
  }
}
