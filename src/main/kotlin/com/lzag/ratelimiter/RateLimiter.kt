package com.lzag.ratelimiter

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.redis.client.Command
import io.vertx.redis.client.RedisAPI
import io.vertx.redis.client.Request
import io.vertx.redis.client.Response
import java.time.Duration
import java.time.Instant

class RedisRateLimiter (
  private val rateLimitScriptSha: String,
  private val periodicScriptSha: String
  private val maxRequests: Int,
  private val redis: RedisAPI,
): RateLimiterInterface {

  private var nextExecutionTime: Long? = null

  override fun checkRateLimit(key: String): Future<RateLimitCheckResult> {
    println("checking rate limit for key: $key")
    return redis.evalsha(listOf(rateLimitScriptSha, "1", key, maxRequests.toString()))
      .map { result ->
        RateLimitCheckResult(
          result.toInteger(),
          nextExecutionTime!!
        )
      }
  }

  override fun startConcurrent(key: String): Future<Int> {
    val promise = Promise.promise<Int>()

    redis.incr("$key:concurrent")
      .onSuccess { promise.complete(it.toInteger()) }
      .onFailure { error ->
        promise.fail(RuntimeException("Failed to increment key: $key", error))
      }
    return promise.future()
  }

  override fun endConcurrent(key: String): Future<Int> {
    val promise = Promise.promise<Int>()
    redis.decr("$key:concurrent")
      .compose {
        redis.expire(listOf("$key:concurrent", "1800"))  // Set expiration to 30 minutes (1800 seconds)
      }
      .onSuccess { promise.complete(it.toInteger()) }
      .onFailure { error ->
        promise.fail(RuntimeException("Failed to decrement key: $key", error))
      }
    return promise.future()
  }

  private fun setupRateLimitCronJob(bucketRefillScriptSha: String, redis: RedisAPI): Future<Response> {
    val cronExpression = "* * * * *"  // Adjust the cron expression as needed
    val cronCommand = "EVALSHA $bucketRefillScriptSha 0"
    val configSetCommand = "CONFIG SET keydb.cron \"$cronExpression $cronCommand\""

    return redis.send(Command.CONFIG, "SET", "keydb.cron", configSetCommand)
      .onSuccess { println("Cron job scheduled successfully") }
      .onFailure { error -> println("Failed to schedule cron job: $error") }
  }
}
