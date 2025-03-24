package com.lzag.ratelimiter

import io.vertx.core.DeploymentOptions
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.WebClient
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.random.Random

@ExtendWith(VertxExtension::class)
class TestHttpVerticle {

  private lateinit var client: WebClient

  @BeforeEach
  fun setup(vertx: Vertx) {
    client = WebClient.create(vertx)
  }

  @Timeout(value = 5)
  @DisplayName("Test rate limiter")
  @ParameterizedTest
  @EnumSource(Algo::class)
  fun testRateLimiter(algo: Algo, vertx: Vertx, testContext: VertxTestContext) {
    val (max, interval, reps) = when (algo) {
      Algo.FRACTIONAL_TOKEN_BUCKET -> Triple(60, 60, 2)
      else -> Triple(3, 60, 4)
    }
    val config = JsonObject()
      .put(
        "rateLimiter", JsonObject()
          .put("type", "redis")
          .put("algo", algo.name)
          .put("maxRequests", max)
          .put("interval", interval)
          .put("maxConcurrentPerUser", 100)
          .put("maxConcurrentPerEndpoint", 100)
          .put("httpVerticleInstances", 1)
      );
    vertx.deployVerticle(DeployerVerticle(), DeploymentOptions().setConfig(config))
      .onFailure { testContext.failNow(it) }
      .onSuccess {
        val requestAllowed = testContext.checkpoint(reps - 1)
        val requestDenied = testContext.checkpoint(1)

        val userId = Random.nextInt(1, 1_000_001)
        repeat(reps) {
          client.get(8888, "localhost", "/")
            .putHeader("X-User-Id", userId.toString())
            .send()
            .onSuccess { response ->
              testContext.verify {
                if(response.statusCode() == 200) {
                  requestAllowed.flag()
                }
                if(response.statusCode() == 429) {
                  requestDenied.flag()
                }
              }
            }
            .onFailure { testContext.failNow(it) }
        }
      }
  }
}
