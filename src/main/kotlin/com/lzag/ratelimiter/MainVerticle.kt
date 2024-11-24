package com.lzag.ratelimiter

import io.vertx.config.ConfigRetriever
import io.vertx.config.ConfigRetrieverOptions
import io.vertx.config.ConfigStoreOptions
import io.vertx.core.*
import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.core.json.JsonObject

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
        logger.info("Undeploying Rate Limiter Verticle $rateLimiterVerticleId")
        vertx.undeploy(rateLimiterVerticleId)
          .compose {
            vertx.deployVerticle(HttpVerticle::class.java, DeploymentOptions().setConfig(change.newConfiguration).setInstances(4))
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
      .compose(::initRateLimiter)
      .compose { rateLimitConfig ->
            vertx.deployVerticle(HttpVerticle::class.java, DeploymentOptions().setConfig(rateLimitConfig).setInstances(4))
      }
      .onSuccess {
        logger.info("Deployed Application")
        httpVerticleId = it.resultAt(0)
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

  private fun initRateLimiter(config: JsonObject): Future<JsonObject> {
    val fileSystem = vertx.fileSystem()
    val rateLimitSetup = fileSystem.readFile(rateLimitChecker.checkerScriptPath)
      .compose { redis.script(listOf("LOAD", it.toString())) }
      .map{ rateLimitScriptSha = it.toString() }
      .map{ println(rateLimitScriptSha) }

    val periodicScriptSetup = if (rateLimitChecker.refillScriptPath != null) {
      fileSystem
        .readFile(rateLimitChecker.refillScriptPath)
        .compose { redis.script(listOf("LOAD", it.toString())) }
        .onSuccess { bucketRefillScriptSha ->
          if (runPeriodicOnRedis) {
            setupRateLimitCronJob(bucketRefillScriptSha.toString(), redis)
          } else {
            // this is not strictly necessary
            val now = Instant.now()
            val nextFullMinute = now.plus(Duration.ofMinutes(1)).truncatedTo(java.time.temporal.ChronoUnit.MINUTES)
            val initialDelay = Duration.between(now, nextFullMinute).toMillis()
            nextExecutionTime = nextFullMinute.toEpochMilli()

            vertx.setTimer(initialDelay) {
              periodicTimer = vertx.setPeriodic(60000) {
                nextExecutionTime = Instant.now().plusMillis(60000).toEpochMilli()
                redis.evalsha(listOf(bucketRefillScriptSha.toString(), "0"))
                  .onSuccess { println("Bucket refill completed") }
                  .onFailure { error -> println("Failed to refill bucket: $error") }
              }
            }
          }
        }
    } else null

    Future.all(listOfNotNull(rateLimitSetup, periodicScriptSetup))
      .onSuccess {
        println("Rate limiter setup completed")
      }
      .onFailure {
        throw RuntimeException("Failed to setup rate limiter", it)
      }
  }
}
