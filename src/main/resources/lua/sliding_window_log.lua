-- sliding_window_log.lua
local key = KEYS[1] -- key is userId
local maxRequests = tonumber(ARGV[1]) -- maximum number of requests allowed
local windowSize = tonumber(ARGV[2]) -- window size in seconds
local currentTime = tonumber(ARGV[3]) -- current timestamp

-- Remove timestamps that are outside the current window
redis.call("ZREMRANGEBYSCORE", key, 0, currentTime - windowSize)

-- Get the number of requests in the current window
local requestCount = redis.call("ZCARD", key)

if requestCount < maxRequests then
  -- Add the current request timestamp to the sorted set
  redis.call("ZADD", key, currentTime, currentTime)
  -- Set expiration for the key to ensure old data is cleaned up
  redis.call("EXPIRE", key, windowSize)
  return 1 -- Request allowed
else
  return 0 -- Request denied
end
