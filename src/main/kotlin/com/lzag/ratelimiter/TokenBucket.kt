package com.lzag.ratelimiter

class TokenBucket {
  val capacity: Int = 100
  val tokensCount: Int = 0
  val tokensPerSecond: Int = 10
}
