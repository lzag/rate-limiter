package com.lzag.ratelimiter

import io.vertx.core.Future

interface RateLimiterInterface {
  fun checkRateLimit(key: String): Future<RateLimitCheck>
}
