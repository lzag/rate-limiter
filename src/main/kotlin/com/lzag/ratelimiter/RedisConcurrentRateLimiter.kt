package com.lzag.ratelimiter

import io.vertx.core.Future
import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.kotlin.coroutines.coAwait
import io.vertx.redis.client.RedisAPI

class RedisConcurrentRateLimiter(private val maxConcurrent: Int, private val redis: RedisAPI) : ConcurrentRateLimiterInterface {
  companion object {
    private val logger = LoggerFactory.getLogger(this::class.java)
  }

  var concurrentCount = 0

  private fun startConcurrent(key: String): Future<Int> {
    return redis.incr("$key:concurrent").map {
      logger.debug("incremented key: $it")
      it.toInteger()
    }
  }

  private fun endConcurrent(key: String): Future<Int> {
    return redis.decr("$key:concurrent").map {
      logger.debug("decremented key: $it")
      it.toInteger()
    }
  }

  override suspend fun executeConcurrent(
    key: String,
    block: suspend () -> Unit,
  ) {
    try {
      concurrentCount = startConcurrent(key).coAwait()
      logger.debug("Concurrent count: $concurrentCount")
      if (concurrentCount > maxConcurrent) {
        throw ConcurrentExceededException("Too many concurrent requests")
      }
      block()
    } finally {
      endConcurrent(key)
      logger.debug("Concurrent count: $concurrentCount")
    }
  }
}

class ConcurrentExceededException(message: String) : Exception(message)
