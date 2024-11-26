-- Function to handle token bucket
local function token_bucket(key, maxRequests)
  local initialValue = maxRequests
  local currentValue = redis.call("GET", key)

  if not currentValue then
    redis.call("SET", key, initialValue)
    redis.call("PEXPIRE", key, 1800000)  -- Set expiration to 30 minutes (1800000 milliseconds)
    return initialValue
  else
    local newValue = tonumber(currentValue) - 1
    -- if we don't want negative values
    if newValue < 0 then
      newValue = 0
    end
    -- update the value
    redis.call("SET", key, newValue)
    redis.call("PEXPIRE", key, 1800000)  -- Set expiration to 30 minutes (1800000 milliseconds)
    return newValue
  end
end

-- Function to handle timestamp bucket
local function timestamp_bucket(key, maxRequests, windowSize, requestTimestamp)
  local values = redis.call("HMGET", key, "value", "timestamp")
  local currentValue = values[1]
  local oldTimestamp = values[2]

  if not currentValue then
    redis.call("HSET", key, "value", maxTokens - 1, "timestamp", requestTimestamp)
    redis.call("PEXPIRE", key, 1800000)  -- Set expiration to 30 minutes (1800000 milliseconds)
    return maxRequests - 1
  else
    local elapsedTime = requestTimestamp - tonumber(oldTimestamp)
    local tokensToAdd = math.floor(elapsedTime / windowSize * maxRequests)  -- Calculate tokens based on minute intervals
    local newValue = math.min(tonumber(currentValue) + tokensToAdd, maxTokens) - 1

    if newValue > 0 then
      redis.call("HSET", key, "value", newValue, "timestamp", currentTimestamp)
    end

    redis.call("PEXPIRE", key, 1800000)  -- Set expiration to 30 minutes (1800000 milliseconds)
    return newValue
  end
end

-- Function to handle leaky bucket as a meter
-- https://en.wikipedia.org/wiki/Leaky_bucket
local function leaky_bucket(key, maxRequests, windowSize, requestTimestamp)
  local values = redis.call("HMGET", key, "value", "timestamp")
  local currentValue = values[1]
  local oldTimestamp = values[2]

  if not currentValue then
    redis.call("HSET", key, "value", 0, "timestamp", requestTimestamp)
    redis.call("PEXPIRE", key, 1800000)  -- Set expiration to 30 minutes (1800000 milliseconds)
    return initialValue
  else
    local elapsedTime = requestTimestamp - tonumber(oldTimestamp)
    local tokensToRemove = math.floor(elapsedTime / windowSize * maxRequests)  -- Calculate tokens based on minute intervals
    local newValue = math.min(tonumber(currentValue) - tokensToAdd, maxRequests) + 1

    if newValue > maxRequests then
      redis.call("HSET", key, "value", newValue, "timestamp", requestTimestamp)
    end
    redis.call("PEXPIRE", key, 1800000)  -- Set expiration to 30 minutes (1800000 milliseconds)
    return newValue
  end
end

-- Function to handle fixed window counter
local function fixed_window_counter(key, maxRequests, windowSize, requestTimestamp)
  local currentValue = redis.call("GET", key)
  -- Divide the timestamp by the window size and get the full integer value
  local windowIndex = math.floor(requestTimestamp / windowSize)
  -- Concatenate the key with the window index
  local concatenatedKey = key .. ":" .. windowIndex
  local currentValue = redis.call("GET", concatenatedKey)

  if not currentValue then
    redis.call("SETEX", concatenatedKey, windowSize, maxRequests - 1)  -- Set expiration to window size
    return maxRequests - 1
  else
    local newValue = tonumber(currentValue) - 1
    if newValue < 0 then
      return 0
    end
    redis.call("SETEX", concatenatedKey, windowSize, newValue)
    return newValue
  end
end

-- Function to handle sliding window log
local function sliding_window_log(key, maxRequests, windowSize, requestTimestamp)
  -- Remove timestamps that are outside the current window
  redis.call("ZREMRANGEBYSCORE", key, 0, requestTimestamp - windowSize)
  -- Get the number of requests in the current window
  local requestCount = redis.call("ZCARD", key)
  if requestCount < maxRequests then
    -- Add the current request timestamp to the sorted set
    redis.call("ZADD", key, requestTimestamp, requestTimestamp)
    -- Set expiration for the key to ensure old data is cleaned up
    redis.call("EXPIRE", key, windowSize)
  end
  return requestCount
end

-- Function to handle sliding window counter
local function sliding_window_counter(key, maxRequests, windowSize, requestTimestamp)
  local timestampIndex = math.floor(requestTimestamp / (windowSize / 60))
  redis.log(redis.LOG_WARNING, timestampIndex)
  local countStartIndex = math.floor((requestTimestamp - windowSize) / (windowSize / 60))
  redis.log(redis.LOG_WARNING, countStartIndex)
  local currentValue = redis.call("HGET", key, timestampIndex)

  if not currentValue then
    redis.call("HSET", key, timestampIndex, 1)
  else
    redis.call("HSET", key, timestampIndex, 1 + currentValue)
  end

  -- calculate the window start
  local startTimestamp = requestTimestamp - windowSize
  local sum = 0
  local allFields = redis.call("HGETALL", key)

  for i = 1, #allFields, 2 do
    local timestamp = tonumber(allFields[i])
    local counter = tonumber(allFields[i + 1])

    if timestamp >= countStartIndex and timestamp <= timestampIndex then
      sum = sum + counter
    end
  end
  return maxRequests - sum
end

-- Main logic to call the appropriate function based on input
local key = KEYS[1]
local scriptType = ARGV[1]
local maxRequests = tonumber(ARGV[2])
-- window size is passed in secs vs millis
local windowSize = tonumber(ARGV[3]) * 1000
local requestTimestamp = tonumber(ARGV[4])

if scriptType == "tokenBucket" then
  return token_bucket(key, maxRequests)
elseif scriptType == "timestampBucket" then
  return timestamp_bucket(key, maxRequests, windowSize, requestTimestamp)
elseif scriptType == "leakyBucket" then
  return leaky_bucket(key, maxRequests, windowSize, requestTimestamp)
elseif scriptType == "fixedWindowCounter" then
  return fixed_window_counter(key, maxRequests, windowSize, requestTimestamp)
elseif scriptType == "slidingWindowLog" then
  return sliding_window_log(key, maxRequests, windowSize, requestTimestamp)
elseif scriptType == "slidingWindowCounter" then
  return sliding_window_counter(key, maxRequests, windowSize, requestTimestamp)
else
  return redis.error_reply("Invalid script type")
end

