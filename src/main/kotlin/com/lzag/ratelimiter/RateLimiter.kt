package com.lzag.ratelimiter

import io.vertx.core.http.HttpServerRequest

class RateLimiter {
    private var lastRequestTime: Long = 0
    private var requestCount: Int = 0
    private val maxRequestCount: Int = 100
    private val requestInterval: Long = 1000

    // needed configuration
    // maxRequestCount: maximum number of requests allowed in the requestInterval time
    // requestInterval: time interval in milliseconds
    // distributedRateLimiter: distributed rate limiter to handle distributed rate limiting
    // limitBasedOn: limit based on IP or user

    fun allowRequest(request: HttpServerRequest): Boolean {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastRequestTime > requestInterval) {
            lastRequestTime = currentTime
            requestCount = 1
            return true
        }
        return if (requestCount < maxRequestCount) {
            requestCount++
            true
        } else {
            false
        }
    }
}
