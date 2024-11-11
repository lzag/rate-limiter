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

  override fun start(startPromise: Promise<Void>) {

    val yamlStore = ConfigStoreOptions()
      .setType("file")
      .setFormat("yaml")
      .setConfig(io.vertx.core.json.JsonObject().put("path", "conf/conf.yaml"))

    val options = ConfigRetrieverOptions().addStore(yamlStore).setScanPeriod(1000)
    val retriever = ConfigRetriever.create(vertx, options)
    var httpVerticleId: String? = null

    retriever.listen { change ->
      println("Processing configuration change")
      if (httpVerticleId != null) {
        logger.info("Undeploying Http Verticle " + httpVerticleId)
        vertx.undeploy(httpVerticleId)
          .compose {
            vertx.deployVerticle(HttpVerticle::class.java, DeploymentOptions().setConfig(change.newConfiguration).setInstances(4))
          }
          .onSuccess {
            httpVerticleId = it
          }
          .onFailure { err ->
            println("Failed to undeploy verticle: ${err.message}")
          }
      }
    }

    retriever.config
      .compose { config ->
        vertx.deployVerticle(HttpVerticle::class.java, DeploymentOptions().setConfig(config).setInstances(4))
          .onSuccess {
            logger.info("Deployed" + it)
            httpVerticleId = it
            println("Http Verticle deployed")
            startPromise.complete()
          }
          .onFailure { err ->
            println("Failed to deploy Http verticle")
            startPromise.fail(err)
          }
      }
  }
}
