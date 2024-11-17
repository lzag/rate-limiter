package com.lzag.ratelimiter

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServerRequest
import io.vertx.redis.client.RedisAPI
import java.time.Instant
import javax.naming.LimitExceededException

class SlidingWindowRateLimiter(
    private val redis: RedisAPI,
    private val maxRequests: Int,
    private val windowSizeInSeconds: Long
) : RateLimiterInterface() {

    override fun allowRequest(request: HttpServerRequest): Future<Boolean> {
      val promise = Promise.promise<Boolean>()
//      request.bodyHandler { body ->
//        try {
//          val json = body.toJsonObject()
//          val userId = json.getString("userId") ?: throw IllegalArgumentException("User ID is missing in the request")
//
//          val currentTime = Instant.now().epochSecond
//          val windowStart = currentTime - windowSizeInSeconds
//
//          // Execute the Lua script to get the counter value
//          redis.evalsha(listOf(scriptSha, "1", userId, 3.toString()))
//
//            .onSuccess { result ->
//              if (result.toInteger() > 0 ) {
//                promise.complete(true)
//              } else {
//                promise.fail(LimitExceededException("Too Many Requests"))
//              }
//            }
//            .onFailure { error ->
//              promise.fail(RuntimeException("Failed to execute Lua script: ${error}"))
//            }
//        } catch (e: DecodeException) {
//          promise.fail(IllegalArgumentException("Invalid JSON format"))
//        }
//      }
      return promise.future()
    }
}
