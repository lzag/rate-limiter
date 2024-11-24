package com.lzag.ratelimiter

import io.vertx.core.Future
import io.vertx.core.http.HttpServerRequest

abstract class RateLimitChecker {
  abstract val checkerScriptPath: String
  abstract val refillScriptPath: String?
}
