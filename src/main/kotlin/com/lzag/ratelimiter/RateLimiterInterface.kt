package com.lzag.ratelimiter

import io.vertx.core.Future
import io.vertx.core.http.HttpServerRequest

abstract class RateLimiterInterface {
  abstract fun allowRequest(request: HttpServerRequest): Future<Boolean>
}
