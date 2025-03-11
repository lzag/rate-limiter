package com.lzag.ratelimiter

import io.vertx.core.Future

interface RateLimiterInterface {
  // starts a concurrent request and returns a count of the current concurrent requests
  fun startConcurrent(key: String): Future<Int>

  // ends a concurrent request and returns a count of the current concurrent requests
  fun endConcurrent(key: String): Future<Int>

  // checks the rate limit for a given key
  fun checkRateLimit(key: String): Future<Int>
}
