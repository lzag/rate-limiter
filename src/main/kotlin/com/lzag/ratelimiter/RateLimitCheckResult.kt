package com.lzag.ratelimiter

data class RateLimitCheckResult(
  val remaining: Int,
  val resetTimestamp: Long,
)
