local userId = KEYS[1]
local maxTokens = tonumber(ARGV[1])  -- Maximum tokens allowed
local initialValue = maxTokens - 1
local currentTimestamp = tonumber(ARGV[2])

local values = redis.call("HMGET", userId, "value", "timestamp")
local currentValue = values[1]
local oldTimestamp = values[2]

if not currentValue then
  redis.call("HSET", userId, "value", initialValue, "timestamp", currentTimestamp)
  redis.call("PEXPIRE", userId, 1800000)  -- Set expiration to 30 minutes (1800000 milliseconds)
  return initialValue
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
