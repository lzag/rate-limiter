package com.lzag.ratelimiter

data class Config(
  val rateLimiter: RateLimiterConfig,
  val httpVerticleInstances: Int,
)

data class RateLimiterConfig(
  val type: String,
  val algo: String,
  val maxRequests: Int,
  val interval: Long, // in seconds
  val refill: String,
  val maxConcurrentPerUser: Int,
  val maxConcurrentPerEndpoint: Int,
)
