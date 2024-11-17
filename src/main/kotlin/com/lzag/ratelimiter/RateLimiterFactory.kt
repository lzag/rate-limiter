package com.lzag.ratelimiter

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.redis.client.RedisAPI

class RateLimiterFactory private constructor() {

  companion object {
    private val logger = LoggerFactory.getLogger(RateLimiterFactory::class.java)

    fun createRateLimiter(type: RateLimiterType, redis: RedisAPI, tokensPerMinute: Int, vertx: Vertx): Future<RateLimiterInterface> {
      logger.info("Creating rate limiter of type: $type")
      val promise = Promise.promise<RateLimiterInterface>()
      val fileSystem = vertx.fileSystem()

      when (type) {
        RateLimiterType.TOKEN_BUCKET -> {
          val rateLimiter = fileSystem.readFile("src/main/resources/valkey.decrement_counter.lua")
            .compose { counterScript ->
              redis.script(listOf("LOAD", counterScript.toString()))
            }
            .map { counterScriptSha -> RateLimiter(counterScriptSha.toString(), redis) }

          val bucketRefill = fileSystem
            .readFile("src/main/resources/valkey.bucket_refill.lua")
            .compose { bucketRefillScript ->
              redis.script(listOf("LOAD", bucketRefillScript.toString()))
                .map { bucketRefillScriptSha ->
                  vertx.setPeriodic(6000) {
                    redis.evalsha(listOf(bucketRefillScriptSha.toString(), "0"))
                      .onSuccess { evalRes -> println("Script executed: $evalRes") }
                      .onFailure { evalRes -> println("Failed to execute script: $evalRes") }
                  }
                }
            }

          Future
            .all(rateLimiter, bucketRefill)
            .map { it.resultAt<RateLimiterInterface>(0) }
            .onSuccess(promise::complete)
            .onFailure(promise::fail)
        }

        RateLimiterType.TIMESTAMP_BUCKET -> {
          fileSystem.readFile("src/main/resources/timestamp_bucket.lua")
            .compose { counterScript ->
              redis.script(listOf("LOAD", counterScript.toString()))
            }
            .map { counterScriptSha -> TimestampBucketRateLimiter(counterScriptSha.toString(), redis, 6) }
            .onSuccess(promise::complete)
            .onFailure(promise::fail)
        }
//        RateLimiterType.SLIDING_WINDOW_LOG,
//        RateLimiterType.SLIDING_WINDOW_COUNTER,
//        RateLimiterType.FIXED_WINDOW_COUNTER,
//        RateLimiterType.FIXED_WINDOW_COUNTER_AND_CONCURRENT -> {
//          val decrementScriptPath = Paths.get(
//            javaClass.classLoader.getResource("timestamp_bucket.lua")?.toURI() ?: throw Exception("Lua script not found")
//          )
//          val decrementScript = String(Files.readAllBytes(decrementScriptPath))
//          fileSystem.readFile("resources/valkey.bucket_refill.lua")
//            .compose{
//
//            }
//
//          redis.script(listOf("LOAD", decrementScript))
//            .map { decrementScriptSha ->
//              TimestampBucketRateLimiter(decrementScriptSha.toString(), redis, tokensPerMinute)
//            }
//        }

        else -> throw IllegalArgumentException("Unknown rate limiter type: $type")
      }

      return promise.future()
    }
  }
}
