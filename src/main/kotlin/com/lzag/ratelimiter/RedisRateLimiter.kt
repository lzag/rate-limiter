package com.lzag.ratelimiter

import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.coAwait
import io.vertx.redis.client.Redis
import io.vertx.redis.client.RedisAPI
import io.vertx.redis.client.ResponseType
import kotlin.time.Duration.Companion.seconds

class RedisRateLimiter private constructor(
  config: JsonObject,
  private val vertx: Vertx,
) : RateLimiterInterface {
  private lateinit var rateLimitScriptSha: String
  private var backFillSha: String? = null
  private var nextExcecutionTime: Long? = null
  private val algo: Algo = Algo.valueOf(config.getString("algo"))
  private val maxRequests: Int = config.getInteger("maxRequests")
  private val windowSize: Int = config.getInteger("interval")
  private val redis: RedisAPI = RedisAPI.api(Redis.createClient(vertx, "redis://${config.getString("redisHost")}:6379"))

  companion object {
    val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun create(
      config: JsonObject,
      vertx: Vertx,
    ): RedisRateLimiter {
      println(config)
      val instance = RedisRateLimiter(config, vertx)
      instance.rateLimitScriptSha = instance.loadScript()
      instance.backFillSha = instance.loadBackfill()
      return instance
    }
  }

  override fun checkRateLimit(key: String): Future<RateLimitCheck> {
    logger.debug("checking rate limit for key: $key")
    return redis.evalsha(
      listOf(rateLimitScriptSha, "1", key, algo.value, maxRequests.toString(), windowSize.toString(), System.currentTimeMillis().toString()),
    )
      .map { response ->
        logger.debug("Rate limit check response: $response")
        val allowed = response[0]?.toBoolean() ?: false
        RateLimitCheck(key, maxRequests, allowed, response[1].toInteger(), nextExcecutionTime)
      }
  }

  private suspend fun loadScript(): String {
    val fileSystem = vertx.fileSystem()
    return fileSystem.readFile("lua/checkers.lua")
      .compose { redis.script(listOf("LOAD", it.toString())) }
      .map {
        logger.debug("Rate limit script loaded: $it")
        it.toString()
      }.coAwait()
  }

  private suspend fun loadBackfill(): String? {
    val fileSystem = vertx.fileSystem()
    return if (algo.needsBackfill) {
      logger.debug("Setting up rate limiter refill")
      backFillSha =
        fileSystem
          .readFile("lua/refill.lua")
          .compose { redis.script(listOf("LOAD", it.toString())) }
          .coAwait().toString()
      runBackfill()
        .onSuccess { response ->
          if (response.type() == ResponseType.ERROR) {
            logger.error("Error running backfill: $response")
          } else {
            logger.debug("Bucket refill completed")
            nextExcecutionTime = System.currentTimeMillis() + windowSize.seconds.inWholeMilliseconds
            vertx.setTimer(windowSize.seconds.inWholeMilliseconds) {
              runBackfill()
            }
          }
        }
        .onFailure(logger::error)
      redis.set(listOf("nextExecutionTime", nextExcecutionTime.toString())).coAwait()
      backFillSha
    } else {
      null
    }
  }

  private fun runBackfill() = redis.evalsha(listOf(backFillSha, "0", algo.value, maxRequests.toString()))
}
