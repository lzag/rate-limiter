package com.lzag.ratelimiter

import io.vertx.core.DeploymentOptions
import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.CoroutineEventBusSupport
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.coAwait

class DeployerVerticle : CoroutineVerticle(), CoroutineEventBusSupport {
  companion object {
    private val logger = LoggerFactory.getLogger(this::class.java)
  }

  private var httpVerticleId: String? = null

  override suspend fun start() {
    logger.debug("Starting Deployer")
    httpVerticleId = deployHttp(config).coAwait()
    vertx.eventBus().coConsumer<JsonObject>("redeploy") { msg ->
      logger.debug("Processing configuration change")
      try {
        if (httpVerticleId != null) {
          logger.debug("Undeploying Rate Limiter Verticle $httpVerticleId")
          vertx.undeploy(httpVerticleId).coAwait()
          httpVerticleId = deployHttp(msg.body()).coAwait()
        }
      } catch (e: Exception) {
        logger.error("Failed to process configuration change", e)
      }
    }
  }

  private fun deployHttp(config: JsonObject) =
    vertx.deployVerticle(
      HttpVerticle::class.java,
      DeploymentOptions().setConfig(config).setInstances(config.getInteger("httpVerticleInstances", 1)),
    )

  override suspend fun stop() {
    logger.info("Stopping DeployerVerticle")
  }
}
