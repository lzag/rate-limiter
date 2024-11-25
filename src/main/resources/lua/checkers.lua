-- Function to handle token bucket
local function token_bucket(userId, maxTokens)
  local initialValue = maxTokens
  local currentValue = redis.call("GET", userId)

  if not currentValue then
    redis.call("SET", userId, initialValue)
    redis.call("PEXPIRE", userId, 1800000)  -- Set expiration to 30 minutes (1800000 milliseconds)
    return initialValue
  else
    local newValue = tonumber(currentValue) - 1
    -- if we don't want negative values
    if newValue < 0 then
      newValue = 0
    end
    -- update the value
    redis.call("SET", userId, newValue)
    redis.call("PEXPIRE", userId, 1800000)  -- Set expiration to 30 minutes (1800000 milliseconds)
    return newValue
  end
end

-- Function to handle timestamp bucket
local function timestamp_bucket(userId, maxTokens, windowSize, timestamp)
--   local currentTime = redis.call("TIME")
--   local currentTimestamp = tonumber(currentTime[1]) * 1000 + math.floor(tonumber(currentTime[2]) / 1000)
  local currentTimestamp = timestamp

  local values = redis.call("HMGET", userId, "value", "timestamp")
  local currentValue = values[1]
  local oldTimestamp = values[2]

  if not currentValue then
    redis.call("HSET", userId, "value", maxTokens - 1, "timestamp", currentTimestamp)
    redis.call("PEXPIRE", userId, 1800000)  -- Set expiration to 30 minutes (1800000 milliseconds)
    return maxTokens - 1
  else
    local elapsedTime = currentTimestamp - tonumber(oldTimestamp)
    local tokensToAdd = math.floor(elapsedTime / windowSize * maxTokens)  -- Calculate tokens based on minute intervals
    local newValue = math.min(tonumber(currentValue) + tokensToAdd, maxTokens) - 1

    if newValue > 0 then
      redis.call("HSET", userId, "value", newValue, "timestamp", currentTimestamp)
    end
    redis.call("PEXPIRE", userId, 1800000)  -- Set expiration to 30 minutes (1800000 milliseconds)
    return newValue
  end
end

-- Function to handle leaky bucket as a meter
-- https://en.wikipedia.org/wiki/Leaky_bucket
local function leaky_bucket(userId, maxTokens, windowSize, timestamp)
  local userId = userId
  local maxTokens = maxTokens
  local initialValue = 0 -- we drain it to 0
  local currentTimestamp = timestamp

  local values = redis.call("HMGET", userId, "value", "timestamp")
  local currentValue = values[1]
  local oldTimestamp = values[2]

  if not currentValue then
    redis.call("HSET", userId, "value", initialValue, "timestamp", currentTimestamp)
    redis.call("PEXPIRE", userId, 1800000)  -- Set expiration to 30 minutes (1800000 milliseconds)
    return initialValue
  else
    local elapsedTime = currentTimestamp - tonumber(oldTimestamp)
    local tokensToRemove = math.floor(elapsedTime / windowSize * maxTokens)  -- Calculate tokens based on minute intervals
    local newValue = math.min(tonumber(currentValue) - tokensToAdd, maxTokens) + 1

    if newValue > maxTokens then
      redis.call("HSET", userId, "value", newValue, "timestamp", currentTimestamp)
    end
    redis.call("PEXPIRE", userId, 1800000)  -- Set expiration to 30 minutes (1800000 milliseconds)
    return newValue
  end
end

-- Function to handle fixed window counter
local function fixed_window_counter(key, maxToken)
  local currentValue = redis.call("GET", key)

  if not currentValue then
    redis.call("SETEX", key, 1800, maxToken - 1)  -- Set expiration to 30 minutes (1800 seconds)
    return maxToken - 1
  else
    local newValue = tonumber(currentValue) - 1
    if newValue < 0 then
      return newValue
    end
    redis.call("SET", key, newValue)
    return newValue
  end
end

-- Function to handle sliding window log
local function sliding_window_log(key, maxRequests, windowSize, currentTime)
  -- Remove timestamps that are outside the current window
  redis.call("ZREMRANGEBYSCORE", key, 0, currentTime - windowSize)

  -- Get the number of requests in the current window
  local requestCount = redis.call("ZCARD", key)

  if requestCount < maxRequests then
    -- Add the current request timestamp to the sorted set
    redis.call("ZADD", key, currentTime, currentTime)
    -- Set expiration for the key to ensure old data is cleaned up
    redis.call("EXPIRE", key, windowSize)
  end
  return requestCount
end

-- Function to handle sliding window counter
local function sliding_window_counter(key, maxRequests, windowSize, timestamp)
  local currentTime = timestamp / 1000
  local windowStart = currentTime - windowSizeInSeconds

  -- Add the current timestamp to the sorted set
  redis.call("ZADD", key, currentTime, currentTime)

  -- Remove entries older than the window
  redis.call("ZREMRANGEBYSCORE", key, 0, windowStart)

  -- Get the number of requests in the current window
  local requestCount = redis.call("ZCARD", key)

  -- Set the expiration for the key to the window size
  redis.call("EXPIRE", key, windowSizeInSeconds)
  return requestCount
end

-- Main logic to call the appropriate function based on input
local key = KEYS[1]
local scriptType = ARGV[1]
local maxRequests = tonumber(ARGV[2])
local windowSize = tonumber(ARGV[3]) * 1000
local timestamp = tonumber(ARGV[4])

if scriptType == "tokenBucket" then
  return token_bucket(key, maxRequests)
elseif scriptType == "timestampBucket" then
  return timestamp_bucket(key, maxRequests, windowSize, timestamp)
elseif scriptType == "leakyBucket" then
  return leaky_bucket(key, maxRequests, windowSize, timestamp)
elseif scriptType == "fixedWindowCounter" then
  return fixed_window_counter(key, maxRequests)
elseif scriptType == "slidingWindowLog" then
  return sliding_window_log(key, maxRequests, windowSize, timestamp)
elseif scriptType == "slidingWindowCounter" then
  return sliding_window_counter(key, maxRequests, windowSize, timestamp)
else
  return redis.error_reply("Invalid script type")
end

