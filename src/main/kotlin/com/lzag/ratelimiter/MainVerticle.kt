package com.lzag.ratelimiter

import io.vertx.config.ConfigRetriever
import io.vertx.config.ConfigRetrieverOptions
import io.vertx.config.ConfigStoreOptions
import io.vertx.core.*
import io.vertx.core.impl.logging.LoggerFactory

class MainVerticle : AbstractVerticle() {
  companion object {
    private val logger = LoggerFactory.getLogger(MainVerticle::class.java)
  }

  private var httpVerticleId: String? = null
  private var rateLimiterVerticleId: String? = null

  override fun start(startPromise: Promise<Void>) {
    val yamlStore = ConfigStoreOptions()
      .setType("file")
      .setFormat("yaml")
      .setConfig(io.vertx.core.json.JsonObject().put("path", "conf/conf.yaml"))

    val options = ConfigRetrieverOptions().addStore(yamlStore).setScanPeriod(1000)
    val retriever = ConfigRetriever.create(vertx, options)

    logger.info("System property: ${System.getProperty("org.vertx.logger-delegate-factory-class-name")}")
    logger.info(logger.javaClass.name)

    retriever.listen { change ->
      println("Processing configuration change")
      if (rateLimiterVerticleId != null) {
        logger.info("Undeploying Rate Limiter Verticle " + rateLimiterVerticleId)
        vertx.undeploy(httpVerticleId)
          .compose {
            vertx.deployVerticle(RateLimiterVerticle::class.java, DeploymentOptions().setConfig(change.newConfiguration))
          }
          .onSuccess {
            rateLimiterVerticleId = it
          }
          .onFailure { err ->
            println("Failed to undeploy verticle: ${err.message}")
          }
      }
    }

    retriever.config
      .compose { config ->
        Future.all(
          listOf(
            vertx.deployVerticle(HttpVerticle::class.java, DeploymentOptions().setConfig(config).setInstances(4)),
            vertx.deployVerticle(RateLimiterVerticle::class.java, DeploymentOptions().setConfig(config)),
          )
        )
      }
      .onSuccess {
        logger.info("Deployed Application")
        httpVerticleId = it.resultAt(0)
        rateLimiterVerticleId = it.resultAt(1)
        startPromise.complete()
      }
      .onFailure { err ->
        println("Failed to deploy Applicattion")
        startPromise.fail(err)
      }
  }

  override fun stop(stopPromise: Promise<Void>) {
    logger.error("Stopping MainVerticle")
    stopPromise.complete()
  }
}
