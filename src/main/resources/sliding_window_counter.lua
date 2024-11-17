local key = KEYS[1] -- key is userId
local maxRequests = tonumber(ARGV[1])
local windowSizeInSeconds = tonumber(ARGV[2])
local currentTime = tonumber(redis.call("TIME")[1])
local currentTimeInMs = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
local windowStart = currentTime - windowSizeInSeconds

-- Add the current timestamp to the sorted set
redis.call("ZADD", key, currentTime, currentTime)

-- Remove entries older than the window
redis.call("ZREMRANGEBYSCORE", key, 0, windowStart)

-- Get the number of requests in the current window
local requestCount = redis.call("ZCARD", key)

-- Set the expiration for the key to the window size
redis.call("EXPIRE", key, windowSizeInSeconds)

if requestCount < maxRequests then
  return true -- Request allowed
else
  return false -- Rate limit exceeded
en
