package com.lzag.ratelimiter

import io.vertx.config.ConfigRetriever
import io.vertx.config.ConfigRetrieverOptions
import io.vertx.config.ConfigStoreOptions
import io.vertx.core.DeploymentOptions
import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.CoroutineEventBusSupport
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.coAwait
import io.vertx.redis.client.Redis
import io.vertx.redis.client.RedisAPI
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

class MainVerticle : CoroutineVerticle(), CoroutineEventBusSupport {
  companion object {
    private val logger = LoggerFactory.getLogger(MainVerticle::class.java)
  }

  private var httpVerticleId: String? = null

  override suspend fun start() {
    logger.debug("Starting MainVerticle")
    val retriever = initConfigRetriever()
    val config = retriever.config.coAwait()
    logger.debug("Configuration loaded: $config")
    val rateLimiterSha = initRateLimiter(config)
    httpVerticleId =
      vertx.deployVerticle(
        HttpVerticle::class.java,
        DeploymentOptions().setConfig(
          config.put("rateLimiterScriptSha", rateLimiterSha),
        ).setInstances(config.getInteger("httpVerticleInstances", 1)),
      ).coAwait()
    vertx.eventBus().coConsumer<String>("limiter.restart") {
      try {
        initRateLimiter(retriever.config.coAwait())
        logger.debug("Rate limiter re-initialized")
      } catch (err: Exception) {
        logger.error("Failed to re-initialize rate limiter: ${err.message}")
      }
    }
  }

  override suspend fun stop() {
    logger.info("Stopping MainVerticle")
  }

  private fun initConfigRetriever(): ConfigRetriever {
    val yamlStore =
      ConfigStoreOptions()
        .setType("file")
        .setFormat("yaml")
        .setConfig(JsonObject().put("path", "conf/conf.yaml"))

    val options = ConfigRetrieverOptions().addStore(yamlStore).setScanPeriod(60000)
    val retriever = ConfigRetriever.create(vertx, options)

    retriever.listen { change ->
      launch {
        logger.debug("Processing configuration change")
        try {
          if (httpVerticleId != null) {
            logger.info("Undeploying Rate Limiter Verticle $httpVerticleId")
            vertx.undeploy(httpVerticleId).coAwait()
            val rateLimiterSha = initRateLimiter(change.newConfiguration)
            change.newConfiguration.put("rateLimiterScriptSha", rateLimiterSha)
            httpVerticleId =
              vertx.deployVerticle(
                HttpVerticle::class.java,
                DeploymentOptions().setConfig(
                  change.newConfiguration,
                ).setInstances(change.newConfiguration.getInteger("httpVerticleInstances", 1)),
              ).coAwait()
          }
        } catch (e: Exception) {
          logger.error("Failed to process configuration change", e)
        }
      }
    }
    return retriever
  }

  /**
   * Initialize the rate limiter and return the rate limiting script sha
   */
  private suspend fun initRateLimiter(config: JsonObject): String {
    val rlConfig = config.getJsonObject("rateLimiter")
    val rateLimiterData = vertx.sharedData().getLocalMap<String, Long>("rateLimiterData")
    val redis = RedisAPI.api(Redis.createClient(vertx, "redis://localhost:6379"))
    val fileSystem = vertx.fileSystem()

    val rateLimiterSha =
      fileSystem.readFile("src/main/resources/lua/checkers.lua")
        .compose { redis.script(listOf("LOAD", it.toString())) }
        .map {
          logger.debug("Rate limit script loaded: $it")
          it.toString()
        }.coAwait()

    if (rlConfig.getBoolean("refill") == false) {
      rateLimiterData["nextExecutionTime"] = rlConfig.getLong("interval")
    } else {
      val backFillSha =
        fileSystem
          .readFile("src/main/resources/lua/refill.lua")
          .compose { redis.script(listOf("LOAD", it.toString())) }
          .coAwait()
      vertx.setPeriodic(rlConfig.getLong("interval").seconds.inWholeMilliseconds) {
        rateLimiterData["nextExecutionTime"] = System.currentTimeMillis() + rlConfig.getLong("interval")
        redis.evalsha(
          listOf(
            backFillSha.toString(),
            "0",
            rlConfig.getString("algo"),
            rlConfig.getString("maxRequests"),
          ),
        )
        .onSuccess { logger.debug("Bucket refill completed") }
        .onFailure(logger::error)
      }
    }
    return rateLimiterSha
  }
}
