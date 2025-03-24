package com.lzag.ratelimiter

enum class Algo(val value: String, val needsBackfill: Boolean) {
  TOKEN_BUCKET("token_bucket", true),
  ALGO_TOKEN_BUCKET("algo_token_bucket", false),
  FRACTIONAL_TOKEN_BUCKET("fractional_token_bucket", false),
  LEAKY_BUCKET("leaky_bucket", false),
  FIXED_WINDOW_COUNTER("fixed_window_counter", false),
  SLIDING_WINDOW_LOG("sliding_window_log", false),
  SLIDING_WINDOW_COUNTER("sliding_window_counter", false),
}
