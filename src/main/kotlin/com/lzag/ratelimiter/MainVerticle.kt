package com.lzag.ratelimiter

import io.vertx.config.ConfigRetriever
import io.vertx.config.ConfigRetrieverOptions
import io.vertx.config.ConfigStoreOptions
import io.vertx.core.AbstractVerticle
import io.vertx.core.DeploymentOptions
import io.vertx.core.Promise
import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.core.json.JsonObject

class MainVerticle : AbstractVerticle() {
  companion object {
    private val logger = LoggerFactory.getLogger(MainVerticle::class.java)
  }

  override fun start(startPromise: Promise<Void>) {
    logger.debug("Starting MainVerticle")
    val mainConf =
      ConfigStoreOptions()
        .setType("file")
        .setFormat("yaml")
        .setConfig(JsonObject().put("path", "conf/conf.yaml"))

    val options = ConfigRetrieverOptions().addStore(mainConf).setScanPeriod(10000)
    val retriever = ConfigRetriever.create(vertx, options)
    retriever.listen { change ->
      vertx.eventBus().send("redeploy", change.newConfiguration)
    }
    val config =
      retriever.config
        .compose {
          vertx.deployVerticle(
            DeployerVerticle::class.java,
            DeploymentOptions().setConfig(it),
          )
        }
        .onSuccess {
          startPromise.complete()
        }
        .onFailure(startPromise::fail)
    logger.debug("Configuration loaded: $config")
  }
}
