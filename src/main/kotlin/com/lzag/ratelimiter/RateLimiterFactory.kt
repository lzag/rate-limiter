package com.lzag.ratelimiter

import com.lzag.ratelimiter.checkers.*
import io.vertx.core.impl.logging.LoggerFactory

class RateLimiterFactory private constructor() {

  companion object {
    private val logger = LoggerFactory.getLogger(RateLimitCheckerFactory::class.java)

    fun create(): RateLimiterInterface {
      logger.info("Creating rate limit checker of type: $algo")

      return when (algo) {
        RateLimiterAlgo.TOKEN_BUCKET -> TokenBucketRateLimitChecker()
        RateLimiterAlgo.TIMESTAMP_BUCKET -> TimestampBucketRateLimitChecker()
        RateLimiterAlgo.LEAKY_BUCKET -> LeakyBucketRateLimitChecker()
        RateLimiterAlgo.SLIDING_WINDOW_COUNTER -> SlidingWindowCounterRateLimitChecker()
        RateLimiterAlgo.FIXED_WINDOW_COUNTER -> FixedWindowCounterRateLimitChecker()
        // TODO RateLimiterType.SLIDING_WINDOW_LOG ->
        else -> throw IllegalArgumentException("Unknown rate limiter type: $algo")
      }
    }
  }
}
