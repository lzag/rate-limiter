package com.lzag.ratelimiter

import io.vertx.config.ConfigRetriever
import io.vertx.config.ConfigRetrieverOptions
import io.vertx.config.ConfigStoreOptions
import io.vertx.core.*
import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.core.json.JsonObject
import io.vertx.redis.client.Command
import io.vertx.redis.client.Redis
import io.vertx.redis.client.RedisAPI
import io.vertx.redis.client.Response
import java.time.Duration
import java.time.Instant

class MainVerticle : AbstractVerticle() {
  companion object {
    private val logger = LoggerFactory.getLogger(MainVerticle::class.java)
  }

  private var httpVerticleId: String? = null
  private var nextExecutionTime: Long = 0

  override fun start(startPromise: Promise<Void>) {
    val yamlStore = ConfigStoreOptions()
      .setType("file")
      .setFormat("yaml")
      .setConfig(JsonObject().put("path", "conf/conf.yaml"))

    val options = ConfigRetrieverOptions().addStore(yamlStore).setScanPeriod(1000)
    val retriever = ConfigRetriever.create(vertx, options)

    retriever.listen { change ->
      println("Processing configuration change")
      if (httpVerticleId != null) {
        logger.info("Undeploying Rate Limiter Verticle $httpVerticleId")
        vertx.undeploy(httpVerticleId)
          .compose{ initRateLimiter(change.newConfiguration) }
          .compose { config ->
            vertx.deployVerticle(HttpVerticle::class.java, DeploymentOptions().setConfig(config).setInstances(config.getInteger("httpVerticleInstances", 4)))
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
      .compose(::initRateLimiter)
      .compose { config ->
        vertx.deployVerticle(HttpVerticle::class.java, DeploymentOptions().setConfig(config).setInstances(config.getInteger("instances", 4)))
      }
      .onSuccess {
        logger.info("Deployed Application")
        httpVerticleId = it
        startPromise.complete()
      }
      .onFailure { err ->
        println("Failed to deploy Applicattion")
        startPromise.fail(err)
      }
    // Listen for the limiter.restart event
    vertx.eventBus().consumer<String>("limiter.restart") {
      retriever.config
        .compose(::initRateLimiter)
        .onSuccess {
          println("Rate limiter re-initialized")
        }
        .onFailure { err ->
          println("Failed to re-initialize rate limiter: ${err.message}")
        }
    }
  }

  override fun stop(stopPromise: Promise<Void>) {
    logger.error("Stopping MainVerticle")
    stopPromise.complete()
  }

  private fun initRateLimiter(config: JsonObject): Future<JsonObject> {
    val rateLimiterData = vertx.sharedData().getLocalMap<String, Long>("rateLimiterData")
    val runPeriodicOnRedis = config.getBoolean("runPeriodicOnRedis", false)
    val redis = RedisAPI.api(Redis.createClient(vertx, "redis://localhost:6379"))
    val promise = Promise.promise<JsonObject>()
    val fileSystem = vertx.fileSystem()
    val rateLimiterConfig = config.getJsonObject("rateLimiter")
    val rateLimitAlgo = rateLimiterConfig.getString("algo")
    val refillEnabled = rateLimiterConfig.getBoolean("refill", false)
    val newConfig = config.copy()
    val interval = rateLimiterConfig.getLong("interval", 60) * 1000

    val rateLimitSetup = fileSystem.readFile("src/main/resources/lua/checkers.lua")
      .compose { redis.script(listOf("LOAD", it.toString())) }
      .map {
        newConfig.put("rateLimiterScriptSha", it.toString())
      }
      .map{ println(newConfig) }

    val periodicScriptSetup = if (refillEnabled == true) {
      fileSystem
        .readFile("src/main/resources/lua/refill.lua")
        .compose { redis.script(listOf("LOAD", it.toString())) }
        .onSuccess { bucketRefillScriptSha ->
          if (runPeriodicOnRedis) {
            setupRateLimitCronJob(bucketRefillScriptSha.toString(), redis)
          } else {
            // this is not strictly necessary
//            val now = Instant.now()
//            val nextFullMinute = now.plus(Duration.ofMinutes(1)).truncatedTo(java.time.temporal.ChronoUnit.MINUTES)
//            val initialDelay = Duration.between(now, nextFullMinute).toMillis()
//            val nextExecutionTime = nextFullMinute.toEpochMilli()

//            vertx.setTimer(initialDelay) {
              vertx.setPeriodic(interval) {
               rateLimiterData["nextExecutionTime"] = System.currentTimeMillis() + interval
                redis.evalsha(listOf(bucketRefillScriptSha.toString(), "0", rateLimitAlgo, rateLimiterConfig.getString("maxRequests")))
                  .onSuccess { println("Bucket refill completed") }
                  .onFailure { error -> println("Failed to refill bucket: $error") }
              }
//            }
          }
        }
    } else {
      nextExecutionTime = interval
      null
    }

    Future.all(listOfNotNull(rateLimitSetup, periodicScriptSetup))
      .onSuccess {
        println("Rate limiter setup completed")
        promise.complete(newConfig)
      }
      .onFailure {
        throw RuntimeException("Failed to setup rate limiter", it)
      }
    return promise.future()
  }

  // maybe will user in the future
  private fun setupRateLimitCronJob(bucketRefillScriptSha: String, redis: RedisAPI): Future<Response> {
    val cronExpression = "* * * * *"  // Adjust the cron expression as needed
    val cronCommand = "EVALSHA $bucketRefillScriptSha 0"
    val configSetCommand = "CONFIG SET keydb.cron \"$cronExpression $cronCommand\""

    return redis.send(Command.CONFIG, "SET", "keydb.cron", configSetCommand)
      .onSuccess { println("Cron job scheduled successfully") }
      .onFailure { error -> println("Failed to schedule cron job: $error") }
  }
}
