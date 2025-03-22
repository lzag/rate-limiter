package com.lzag.ratelimiter

data class RateLimitCheck(
  val key: String,
  val max: Int,
  val allowed: Boolean,
  val remaining: Int,
  val reset: Long? = null,
)
