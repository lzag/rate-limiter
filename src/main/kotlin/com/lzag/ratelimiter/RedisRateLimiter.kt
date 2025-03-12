package com.lzag.ratelimiter

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.redis.client.RedisAPI

class RedisRateLimiter(
  private val rateLimitScriptSha: String,
  private val algo: String,
  private val maxRequests: Int,
  private val windowSize: Int,
  private val redis: RedisAPI,
) : RateLimiterInterface {
  companion object {
    val logger = LoggerFactory.getLogger(RedisRateLimiter::class.java)
  }
  override fun checkRateLimit(key: String): Future<Int> {
    logger.debug("checking rate limit for key: $key")
    val currentTimestamp = System.currentTimeMillis()
    return redis.evalsha(
      listOf(rateLimitScriptSha, "1", key, algo, maxRequests.toString(), windowSize.toString(), currentTimestamp.toString()),
    )
      .map {
        logger.debug("rate limit check result: $it")
        it.toInteger()
      }
  }

  override fun startConcurrent(key: String): Future<Int> {
    val promise = Promise.promise<Int>()

    redis.incr("$key:concurrent")
      .onSuccess {
        logger.debug("incremented key: $it")
        promise.complete(it.toInteger())
      }
      .onFailure { error ->
        promise.fail(RuntimeException("Failed to increment key: $key", error))
      }
    return promise.future()
  }

  override fun endConcurrent(key: String): Future<Int> {
    val promise = Promise.promise<Int>()
    redis.decr("$key:concurrent")
      .compose {
        redis.expire(listOf("$key:concurrent", "1800")) // Set expiration to 30 minutes (1800 seconds)
      }
      .onSuccess {
        logger.debug("decremented key: $it")
        promise.complete(it.toInteger())
      }
      .onFailure { error ->
        logger.error(error)
        promise.fail(RuntimeException("Failed to decrement key: $key", error))
      }
    return promise.future()
  }
}
