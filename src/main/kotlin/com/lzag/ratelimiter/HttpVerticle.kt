package com.lzag.ratelimiter

import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.ext.web.Router

class HttpVerticle : AbstractVerticle() {

  companion object {
    private val logger = LoggerFactory.getLogger(HttpVerticle::class.java)
  }

  override fun start(startPromise: Promise<Void>) {
    logger.info("Deploying HttpVerticle")
    val router = Router.router(vertx)

    // End handler for all routes
    router.route().last().handler { ctx ->
      logger.info("End handler executed")
//              rateLimiter::concurrentEnd(it)
      ctx.response().end("Request processed")
    }

    // Common handler for all routes
    router.route().handler { ctx ->
//            val concurrentCount = rateLimiter.concurrentStart(key)
//            val (remaining, resetTimestamp) = rateLimiter.checkRequest(key, tokensPerMinute)
      logger.info("Rate limiting handler executed")
      val path = ctx.request().path()
      val key = ctx.request().getHeader("X-User-Id")

      val requestPerMinute = 100
      val remaining = 0
      val resetTimestamp = 1000
      val concurrentCount = 0
      val concurrentRequests = 0

//
      if (remaining <= 0 || concurrentCount > concurrentRequests) {
        ctx.response()
          .putHeader("content-type", "text/plain")
          .putHeader("ratelimit-limit", requestPerMinute.toString())
          .putHeader("ratelimit-remaining", remaining.toString())
          .putHeader("ratelimit-reset", resetTimestamp.toString())
        ctx.response().setStatusCode(429).end("Too Many Requests")
      } else {
        ctx.next()
      }
    }

    // Common handler for all routes
    router.route().handler { ctx ->
      logger.info("Circuit breaker handler executed")
      ctx.next()
    }

//    RateLimiterFactory
//      .createRateLimiter(rateLimiterType, redis, requestPerMinute, vertx)
//      .compose { rateLimiter ->
        vertx.createHttpServer()
          .requestHandler(router)
          .exceptionHandler(Throwable::printStackTrace)
          .listen(8888)
//      }
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
}
