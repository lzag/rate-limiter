-- Function to handle sliding window counter
local function sliding_window_counter(key, maxRequests, windowSizeInSeconds)
  local currentTime = tonumber(redis.call("TIME")[1])
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

-- Function to handle token bucket
local function token_bucket(userId, maxTokens, refillRate)
  local currentTime = tonumber(redis.call("TIME")[1])
  local values = redis.call("HMGET", userId, "value", "timestamp")
  local currentValue = values[1]
  local oldTimestamp = values[2]

  if not currentValue then
    redis.call("HSET", userId, "value", maxTokens - 1, "timestamp", currentTime)
    redis.call("PEXPIRE", userId, 1800000)  -- Set expiration to 30 minutes (1800000 milliseconds)
    return maxTokens - 1
  else
    local elapsedTime = currentTime - tonumber(oldTimestamp)
    local tokensToAdd = math.floor(elapsedTime / refillRate)
    local newValue = math.min(tonumber(currentValue) + tokensToAdd, maxTokens) - 1

    if newValue >= 0 then
      redis.call("HSET", userId, "value", newValue, "timestamp", currentTime)
    end
    redis.call("PEXPIRE", userId, 1800000)  -- Set expiration to 30 minutes (1800000 milliseconds)
    return newValue
  end
end

-- Function to handle leaky bucket
local function leaky_bucket(userId, maxTokens, leakRate)
  local currentTime = tonumber(redis.call("TIME")[1])
  local values = redis.call("HMGET", userId, "value", "timestamp")
  local currentValue = values[1]
  local oldTimestamp = values[2]

  if not currentValue then
    redis.call("HSET", userId, "value", maxTokens - 1, "timestamp", currentTime)
    redis.call("PEXPIRE", userId, 1800000)  -- Set expiration to 30 minutes (1800000 milliseconds)
    return maxTokens - 1
  else
    local elapsedTime = currentTime - tonumber(oldTimestamp)
    local tokensToLeak = math.floor(elapsedTime / leakRate)
    local newValue = tonumber(currentValue) - tokensToLeak

    if newValue < 0 then
      newValue = 0
    end

    newValue = newValue - 1

    if newValue >= 0 then
      redis.call("HSET", userId, "value", newValue, "timestamp", currentTime)
    end
    redis.call("PEXPIRE", userId, 1800000)  -- Set expiration to 30 minutes (1800000 milliseconds)
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
    return 1 -- Request allowed
  else
    return 0 -- Request denied
  end
end

-- Function to handle timestamp bucket
local function timestamp_bucket(userId, maxTokens)
  local currentTime = redis.call("TIME")
  local currentTimestamp = tonumber(currentTime[1]) * 1000 + math.floor(tonumber(currentTime[2]) / 1000)

  local values = redis.call("HMGET", userId, "value", "timestamp")
  local currentValue = values[1]
  local oldTimestamp = values[2]

  if not currentValue then
    redis.call("HSET", userId, "value", maxTokens - 1, "timestamp", currentTimestamp)
    redis.call("PEXPIRE", userId, 1800000)  -- Set expiration to 30 minutes (1800000 milliseconds)
    return maxTokens - 1
  else
    local elapsedTime = currentTimestamp - tonumber(oldTimestamp)
    local tokensToAdd = math.floor(elapsedTime / 60000 * maxTokens)  -- Calculate tokens based on minute intervals
    local newValue = math.min(tonumber(currentValue) + tokensToAdd, maxTokens) - 1

    if newValue > 0 then
      redis.call("HSET", userId, "value", newValue, "timestamp", currentTimestamp)
    end
    redis.call("PEXPIRE", userId, 1800000)  -- Set expiration to 30 minutes (1800000 milliseconds)
    return newValue
  end
end

-- Main logic to call the appropriate function based on input
local function main()
  local scriptType = ARGV[1]
  if scriptType == "sliding_window" then
    return sliding_window_counter(KEYS[1], tonumber(ARGV[2]), tonumber(ARGV[3]))
  elseif scriptType == "fixed_window" then
    return fixed_window_counter(KEYS[1], tonumber(ARGV[2]))
  elseif scriptType == "token_bucket" then
    return token_bucket(KEYS[1], tonumber(ARGV[2]), tonumber(ARGV[3]))
  elseif scriptType == "leaky_bucket" then
    return leaky_bucket(KEYS[1], tonumber(ARGV[2]), tonumber(ARGV[3]))
  elseif scriptType == "sliding_window_log" then
    return sliding_window_log(KEYS[1], tonumber(ARGV[2]), tonumber(ARGV[3]), tonumber(ARGV[4]))
  elseif scriptType == "timestamp_bucket" then
    return timestamp_bucket(KEYS[1], tonumber(ARGV[2]))
  else
    return redis.error_reply("Invalid script type")
  end
end

-- Call the main function
return main()
