package com.lzag.ratelimiter

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.json.DecodeException
import io.vertx.redis.client.RedisAPI

class ConcurrentRequestLimiter(private val redis: RedisAPI, private val scriptSha: String) {

  fun processStart(req: HttpServerRequest): Future<Boolean> {
    val promise = Promise.promise<Boolean>()
    println("Request received to concurrent request limiter")
    req.bodyHandler { body ->
      try {
        val json = body.toJsonObject()
        val userId = json.getString("userId") ?: throw IllegalArgumentException("User ID is missing in the request")
        val userKey = "user:${userId}"
        // Execute the Lua script to get the counter value
        redis.evalsha(listOf(scriptSha, "2", userKey, 3.toString()))
          .onSuccess { result ->
            val isAllowed = result.toBoolean()
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

  fun processEnd(req: HttpServerRequest): Future<Unit> {
    val promise = Promise.promise<Unit>()
    req.bodyHandler { body ->
      try {
        val json = body.toJsonObject()
        val userId = json.getString("userId") ?: throw IllegalArgumentException("User ID is missing in the request")
        val userKey = "user:${userId}"
        // Execute the Lua script to get the counter value
        redis.decr(userKey)
          .onSuccess { promise.complete() }
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
