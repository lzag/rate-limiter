package com.lzag.ratelimiter

import io.vertx.config.ConfigRetriever
import io.vertx.config.ConfigRetrieverOptions
import io.vertx.config.ConfigStoreOptions
import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
import io.vertx.redis.client.Redis
import io.vertx.redis.client.RedisAPI
import java.nio.file.Files
import java.nio.file.Paths

class MainVerticle : AbstractVerticle() {

  override fun start(startPromise: Promise<Void>) {
    val yamlStore = ConfigStoreOptions()
      .setType("file")
      .setFormat("yaml")
      .setConfig(io.vertx.core.json.JsonObject().put("path", "conf/conf.yaml"))

    // Create the ConfigRetriever with the YAML store
    val options = ConfigRetrieverOptions().addStore(yamlStore)
    val retriever = ConfigRetriever.create(vertx, options)

    retriever.listen { change ->
      vertx.eventBus().publish("config.change", change.newConfiguration)
    }

    retriever.config
      .onSuccess { config ->
        val tokensPerMinute = config.getInteger("tokensPerMinute")
        println("tokensPerMinute: $tokensPerMinute")
        val rateLimiter = RateLimiter()

        val redis = Redis.createClient(vertx, "redis://localhost:6379")
        val redisAPI = RedisAPI.api(redis)

        // Load the Lua script from the resources directory
        val luaScriptPath = Paths.get(javaClass.classLoader.getResource("valkey.bucket_refill.lua").toURI())
        val luaScript = String(Files.readAllBytes(luaScriptPath))

        // Load the script into Redis
        redisAPI.script(listOf("LOAD", luaScript)) { loadRes ->
          if (loadRes.succeeded()) {
            val scriptSha = loadRes.result().toString()
            println("Script SHA1: $scriptSha")

            // Schedule the script to run every minute
            vertx.setPeriodic(6000) {
              redisAPI.evalsha(listOf(scriptSha, "0")) { evalRes ->
                if (evalRes.succeeded()) {
                  println("Script executed: ${evalRes.result()}")
                } else {
                  println("Failed to execute script: ${evalRes.cause()}")
                }
              }
            }

            vertx
              .createHttpServer()
              .requestHandler { req ->
                if (!rateLimiter.allowRequest(req)) {
                  // return not allowed
                  req.response().setStatusCode(429).end("Too Many Requests")
                } else {
                  // make the actual API call
                  req.response()
                    .putHeader("content-type", "text/plain")
                    .end("Request allowed")
                }
              }
              .listen(8888).onComplete { http ->
                if (http.succeeded()) {
                  startPromise.complete()
                  println("HTTP server started on port 8888")
                } else {
                  startPromise.fail(http.cause())
                }
              }
          } else {
            startPromise.fail(loadRes.cause())
          }
        }
      }
  }
}
