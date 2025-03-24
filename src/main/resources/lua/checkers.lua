local rate_limiters = {
  token_bucket = function(key, maxRequests, windowSize)
    local expiration = math.floor(windowSize / 1000)
    local currentRemaining = tonumber(redis.call("GET", key))
    local remaining = 0

    if not currentRemaining then
      remaining = maxRequests - 1
    else
      remaining = currentRemaining - 1
    end
    if remaining >= 0 then
      redis.call("SETEX", key, expiration, remaining)
    end
    return { remaining >= 0, remaining }
  end,
  algo_token_bucket = function(key, maxRequests, windowSize, requestTimestamp)
    local values = redis.call("HMGET", key, "value", "timestamp")
    local currentValue = tonumber(values[1])
    local oldTimestamp = tonumber(values[2])
    local remaining = 0

    if not currentValue then
      remaining = maxRequests - 1
    else
      local elapsedTime = math.max(requestTimestamp - oldTimestamp, 0)
      local tokensToAdd = math.floor(elapsedTime / windowSize) * maxRequests
      remaining = math.min(currentValue + tokensToAdd, maxRequests) - 1
    end
    if remaining >= 0 then
      redis.call("HSET", key, "value", remaining, "timestamp", requestTimestamp)
      redis.call("PEXPIRE", key, windowSize)
    end
    return { remaining >= 0, remaining }
  end,
  fractional_token_bucket = function(key, maxRequests, windowSize, requestTimestamp)
    local values = redis.call("HMGET", key, "value", "timestamp")
    local currentValue = tonumber(values[1])
    local oldTimestamp = tonumber(values[2])
    local remaining = 0
    local fraction = 60
    local tokenFraction = math.floor(maxRequests / fraction)

    if not currentValue then
      remaining = tokenFraction - 1
    else
      local elapsedTime = requestTimestamp - oldTimestamp
      local tokensToAdd = math.floor(elapsedTime / (windowSize / fraction)) * tokenFraction
      remaining = math.min(currentValue + tokensToAdd, maxRequests) - 1
    end
    if remaining >= 0 then
      redis.call("HSET", key, "value", remaining, "timestamp", requestTimestamp)
      redis.call("PEXPIRE", key, windowSize * 10)
    end
    return { remaining >= 0, remaining }
  end,
  -- https://en.wikipedia.org/wiki/Leaky_bucket -- leaky bucket as a meter
  leaky_bucket = function(key, maxRequests, windowSize, requestTimestamp)
    local values = redis.call("HMGET", key, "value", "timestamp")
    local currentCount = tonumber(values[1])
    local oldTimestamp = tonumber(values[2])
    local count = 0

    if not currentCount then
      count = 1
    else
      local elapsedTime = math.max(requestTimestamp - oldTimestamp, 0)
      local tokensToRemove = math.floor(elapsedTime / windowSize) * maxRequests
      count = math.max(currentCount - tokensToRemove, 0) + 1
    end
    if count <= maxRequests then
      redis.call("HSET", key, "value", count, "timestamp", requestTimestamp)
      redis.call("PEXPIRE", key, windowSize)
    end
    return { count <= maxRequests, maxRequests - count }
  end,
  -- Function to handle fixed window counter
  fixed_window_counter = function(key, maxRequests, windowSize, requestTimestamp)
    -- Divide the timestamp by the window size and get the full integer value
    local windowIndex = math.floor(requestTimestamp / windowSize)
    -- Concatenate the key with the window index
    local concatenatedKey = key .. ":" .. windowIndex
    local currentValue = tonumber(redis.call("GET", concatenatedKey))
    local expiration = windowSize / 1000
    local remaining = 0

    if not currentValue then
      remaining = maxRequests - 1
    else
      remaining = currentValue - 1
    end
    if remaining >= 0 then
      redis.call("SETEX", concatenatedKey, expiration, remaining)
    end
    return { remaining >= 0, remaining }
  end,
  sliding_window_log = function(key, maxRequests, windowSize, requestTimestamp)
    -- Remove timestamps that are outside the current window
    local removed = redis.call("ZREMRANGEBYSCORE", key, 0, requestTimestamp - windowSize)
    -- Get the number of requests in the current window before adding the new one
    local requestCount = redis.call("ZCARD", key)
    -- Calculate remaining requests before processing
    local remaining = maxRequests - requestCount - 1
    -- Only add the request if there’s capacity
    if remaining >= 0 then
      -- Add the current request timestamp to the sorted set
      redis.call("ZADD", key, requestTimestamp, requestTimestamp .. math.random(1, 1000))
      -- Set expiration in milliseconds
      redis.call("PEXPIRE", key, windowSize * 10)
      -- Return remaining requests after adding (one less than before)
    end
    return { remaining >= 0, remaining }
  end,
  sliding_window_counter = function(key, maxRequests, windowSize, requestTimestamp)
    local subWindowSize = windowSize / 60
    local timestampIndex = math.floor(requestTimestamp / subWindowSize)
    local countStartIndex = math.floor((requestTimestamp - windowSize) / subWindowSize)

    local remaining = maxRequests
    local fieldsToDelete = {}
    local allFields = redis.call("HGETALL", key)
    local currentIndexCount = 0
    for i = 1, #allFields, 2 do
      local timestamp = tonumber(allFields[i])
      local counter = tonumber(allFields[i + 1])
      if timestamp >= countStartIndex and timestamp <= timestampIndex then
        if timestamp == timestampIndex then
          currentIndexCount = counter
        end
        remaining = remaining - counter
      elseif timestamp < countStartIndex then
        table.insert(fieldsToDelete, timestamp)
      end
    end
    -- Clean up old fields in one call
    if #fieldsToDelete > 0 then
      for _, field in ipairs(fieldsToDelete) do
          redis.call("HDEL", key, field)
      end
    end

    if remaining >= 0 then
       redis.call("HSET", key, timestampIndex, currentIndexCount + 1)
       remaining = remaining - 1
    end
    return { remaining >= 0, remaining}
  end
}

-- Main logic to call the appropriate function based on input
local key = KEYS[1]
local scriptType = ARGV[1]
local maxRequests = tonumber(ARGV[2])
-- window size is passed in secs vs millis
local windowSize = tonumber(ARGV[3]) * 1000
-- timestampInMs
local requestTimestamp = tonumber(ARGV[4])

local limiter = rate_limiters[scriptType]
if limiter then
  return limiter(key, maxRequests, windowSize, requestTimestamp)
else
  return redis.error_reply("Invalid script type")
end
