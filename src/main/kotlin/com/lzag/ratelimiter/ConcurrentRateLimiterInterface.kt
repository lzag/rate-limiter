package com.lzag.ratelimiter

import io.vertx.core.Future

interface ConcurrentRateLimiterInterface {
  suspend fun executeConcurrent(
    key: String,
    block: suspend () -> Unit,
  )
}
