package com.lzag.ratelimiter

import io.vertx.core.Future
import io.vertx.core.http.HttpServerRequest

abstract class RateLimiterInterface {
  abstract fun allowRequest(request: HttpServerRequest): Future<Boolean>

//  abstract fun concurrentStart(key: String): Future<Void>
//
//  // returns the number of requests available and the time until the next request is available
//  abstract fun validateRequest(key: String): Future<Pair<Int, Long>>
//
//  abstract fun concurrentEnd(key: String): Future<Void>
}
