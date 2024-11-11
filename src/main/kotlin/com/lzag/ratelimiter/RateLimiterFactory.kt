package com.lzag.ratelimiter

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.redis.client.RedisAPI
import java.nio.file.Files
import java.nio.file.Paths

class RateLimiterFactory private constructor() {

  companion object {
    fun createRateLimiter(type: RateLimiterType, redis: RedisAPI, tokensPerMinute: Int, vertx: Vertx): Future<RateLimiterInterface> {
      val promise = Promise.promise<RateLimiterInterface>()
      val fileSystem = vertx.fileSystem()

      when (type) {
        RateLimiterType.TOKEN_BUCKET -> {
          fileSystem.readFile("resources/valkey.bucket_refill.lua").compose { bucketRefillScript ->
            fileSystem.readFile("resources/valkey.decrement_counter.lua").compose { decrementScript ->
              val refillSetup = redis.script(listOf("LOAD", bucketRefillScript.toString()))
                .map { bucketRefillScriptSha ->
                  vertx.setPeriodic(6000) {
                    redis.evalsha(listOf(bucketRefillScriptSha.toString(), "0"))
                      .onSuccess { evalRes -> println("Script executed: $evalRes") }
                      .onFailure { evalRes -> println("Failed to execute script: $evalRes") }
                  }
                }

              val rateLimiter = redis.script(listOf("LOAD", decrementScript.toString()))
                .map { decrementScriptSha -> RateLimiter(decrementScriptSha.toString(), redis) }

              Future.all(refillSetup, rateLimiter)
                .map { it.resultAt<RateLimiterInterface>(1) }
                .onComplete(promise)
            }
          }.onFailure { promise.fail(it) }
        }

        RateLimiterType.TIMESTAMP_BUCKET,
        RateLimiterType.SLIDING_WINDOW_LOG,
        RateLimiterType.SLIDING_WINDOW_COUNTER,
        RateLimiterType.FIXED_WINDOW_COUNTER,
        RateLimiterType.FIXED_WINDOW_COUNTER_AND_CONCURRENT -> {
          val decrementScriptPath = Paths.get(
            javaClass.classLoader.getResource("timestamp_bucket.lua")?.toURI() ?: throw Exception("Lua script not found")
          )
          val decrementScript = String(Files.readAllBytes(decrementScriptPath))
          fileSystem.readFile("resources/valkey.bucket_refill.lua")
            .compose{

            }

          redis.script(listOf("LOAD", decrementScript))
            .map { decrementScriptSha ->
              TimestampBucketRateLimiter(decrementScriptSha.toString(), redis, tokensPerMinute)
            }
        }

        else -> throw IllegalArgumentException("Unknown rate limiter type: $type")
      }

      return promise.future()
    }
  }
}
