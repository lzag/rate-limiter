package com.lzag.ratelimiter

interface ConcurrentRateLimiterInterface {
  suspend fun executeConcurrent(
    key: String,
    block: suspend () -> Unit,
  )
}
