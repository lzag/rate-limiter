package com.lzag.ratelimiter

import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.redis.client.Redis
import io.vertx.redis.client.RedisAPI

class RateLimiterVerticle : AbstractVerticle() {

  companion object {
    private val logger = LoggerFactory.getLogger(HttpVerticle::class.java)
  }

  private lateinit var redisClient: Redis
  private lateinit var redis: RedisAPI

  override fun start(startPromise: Promise<Void>) {
    logger.info("Deploying HttpVerticle")

    redisClient = Redis.createClient(vertx, "redis://localhost:6379")
    redis = RedisAPI.api(redisClient)

    val rateLimiterConfig = config().getJsonObject("rate_limiter")
    val requestPerMinute = rateLimiterConfig.getInteger("request_per_minute")
    val concurrentRequests = rateLimiterConfig.getInteger("concurrent_requests")
    val rateLimiterType = RateLimiterType.valueOf(rateLimiterConfig.getString("algo").uppercase())
    startPromise.complete()
  }

  override fun stop(stopPromise: Promise<Void>) {
    println("Stopping RateLimiterVerticle and cleaning up Valkey")
    redis.flushall(emptyList())
      .onSuccess{
        redisClient.close()
        stopPromise.complete()
      }
      .onFailure {
        it.printStackTrace()
        stopPromise.complete()
      }
  }
}
