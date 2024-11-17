package com.lzag.ratelimiter

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.json.DecodeException
import io.vertx.redis.client.RedisAPI
import javax.naming.LimitExceededException

class FixedWindowCounterRateLimiter(private val scriptSha: String, private val redis: RedisAPI): RateLimiterInterface() {

  override fun allowRequest(request: HttpServerRequest): Future<Boolean> {
    val promise = Promise.promise<Boolean>()
    println("Request received")
    request.bodyHandler { body ->
      try {
        val json = body.toJsonObject()
        val userId = json.getString("userId") ?: throw IllegalArgumentException("User ID is missing in the request")
        // Execute the Lua script to get the counter value
        redis.evalsha(listOf(scriptSha, "1", userId, 3.toString()))

          .onSuccess { result ->
            if (result.toInteger() > 0 ) {
              promise.complete(true)
            } else {
              promise.fail(LimitExceededException("Too Many Requests"))
            }
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
