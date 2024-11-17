package com.lzag.ratelimiter

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.json.DecodeException
import io.vertx.redis.client.RedisAPI
import javax.naming.LimitExceededException

class TimestampBucketRateLimiter(
  private val scriptSha: String,
  private val redis: RedisAPI,
  private val maxTokens: Int,
): RateLimiterInterface() {

  override fun allowRequest(request: HttpServerRequest): Future<Boolean> {
    val promise = Promise.promise<Boolean>()
    println("Request received timestamp bucket")
    request.bodyHandler { body ->
      println("Request body: $body")
      try {
        val json = body.toJsonObject()
        val userId = json.getString("userId") ?: throw IllegalArgumentException("User ID is missing in the request")
        val currentTimestamp = System.currentTimeMillis().toString()
        println("Request body: $body")
        // Execute the Lua script to get the counter value
        redis.evalsha(listOf(scriptSha, "1", userId, maxTokens.toString(), currentTimestamp))
          .onSuccess { result ->
            println("Result: $result")
            if (result.toInteger() > 0 ) {
              promise.complete(true)
            } else {
              promise.fail(LimitExceededException("Too Many Requests"))
            }
          }
          .onFailure { error ->
            println("Failed to execute Lua script: $error")
            promise.fail(RuntimeException("Failed to execute Lua script: ${error}"))
          }
      } catch (e: DecodeException) {
        println("Invalid JSON format: $e")
        promise.fail(IllegalArgumentException("Invalid JSON format"))
      } catch (e: Exception) {
        println("Unexpected error: $e")
        promise.fail(e)
      }
    }.exceptionHandler { e ->
      println("Failed to read request body: $e")
      promise.fail(e)
    }
    return promise.future()
  }

}
