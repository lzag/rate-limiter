package com.lzag.ratelimiter

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.json.DecodeException
import io.vertx.redis.client.RedisAPI

class TimestampBucketRateLimiter(
  private val scriptSha: String,
  private val redis: RedisAPI,
  private val maxTokens: Int,
): RateLimiterInterface() {
  // needed configuration
  // maxRequestCount: maximum number of requests allowed in the requestInterval time
  // requestInterval: time interval in milliseconds
  // distributedRateLimiter: distributed rate limiter to handle distributed rate limiting
  // limitBasedOn: limit based on IP or user

  override fun allowRequest(request: HttpServerRequest): Future<Boolean> {
    val promise = Promise.promise<Boolean>()
    println("Request received")
    request.bodyHandler { body ->
      try {
        val json = body.toJsonObject()
        val userId = json.getString("userId") ?: throw IllegalArgumentException("User ID is missing in the request")
        val currentTimestamp = System.currentTimeMillis().toString()
        // Execute the Lua script to get the counter value
        redis.evalsha(listOf(scriptSha, "1", userId, maxTokens.toString(), currentTimestamp))
          .onSuccess { result ->
            val isAllowed = result.toInteger() > 0
            promise.complete(isAllowed)
          }
          .onFailure { error ->
            promise.fail(RuntimeException("Failed to execute Lua script: ${error}"))
          }
      } catch (e: DecodeException) {
        promise.fail(IllegalArgumentException("Invalid JSON format"))
      }
    }
    return promise.future()
  }
}
