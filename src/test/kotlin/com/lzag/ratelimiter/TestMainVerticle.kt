package com.lzag.ratelimiter

import io.vertx.junit5.VertxExtension
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(VertxExtension::class)
class TestMainVerticle {
  //  @BeforeEach
//  fun deploy_verticle(vertx: Vertx, testContext: VertxTestContext) {
//    vertx.deployVerticle(MainVerticle()).onComplete(testContext.succeeding<String> { _ -> testContext.completeNow() })
//  }
//
//  @Test
//  fun verticle_deployed(vertx: Vertx, testContext: VertxTestContext) {
//    testContext.completeNow()
//  }
}
