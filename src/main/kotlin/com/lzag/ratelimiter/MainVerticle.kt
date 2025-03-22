package com.lzag.ratelimiter

import io.vertx.config.ConfigRetriever
import io.vertx.config.ConfigRetrieverOptions
import io.vertx.config.ConfigStoreOptions
import io.vertx.core.AbstractVerticle
import io.vertx.core.DeploymentOptions
import io.vertx.core.Promise
import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.core.json.JsonObject

object AppConfig {
  val config by lazy { provider.getConfig() }
  var hello: String = "hello1"
  val sth: String
    get() = hello
  private var provider: ConfigProvider = DefConfigProvider()

  fun setConfigProvider(provider: ConfigProvider): AppConfig {
    this.provider = provider
    val configInit = config
    return this
  }
}

class DefConfigProvider : ConfigProvider {
  override fun getConfig(): String {
    return "memory"
  }
}

class RedisConfigProvider : ConfigProvider {
  override fun getConfig(): String {
    return "redis"
  }
}

interface ConfigProvider {
  fun getConfig(): String
}

class MainVerticle : AbstractVerticle() {
  companion object {
    private val logger = LoggerFactory.getLogger(MainVerticle::class.java)
  }

  override fun start(startPromise: Promise<Void>) {
    println(">>>>>>>>>>>>>>>>>" + AppConfig.sth)
    vertx.eventBus().consumer<String>("test") {
      logger.debug("Received message: ${it.body()}")
      AppConfig.hello = "dfsadfasdfasfasfsfsa"
      println(AppConfig.sth)
    }
    val appConfig1 = AppConfig.setConfigProvider(RedisConfigProvider())
    val appConfig = AppConfig.setConfigProvider(DefConfigProvider())
    println(appConfig.config)
    println(appConfig.sth)
    appConfig.hello = "world"
    println(appConfig.sth)

    logger.debug("Starting MainVerticle")
    val yamlStore =
      ConfigStoreOptions()
        .setType("file")
        .setFormat("yaml")
        .setConfig(JsonObject().put("path", "conf/conf.yaml"))

    val options = ConfigRetrieverOptions().addStore(yamlStore).setScanPeriod(10000)
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
