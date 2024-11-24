local userKey = KEYS[1]
local maxTokens = tonumber(ARGV[1])  -- Maximum tokens allowed

-- Increment the counter
local currentValue = redis.call("INCR", userKey)

if currentValue <= maxTokens then
  return true
else
  -- Decrement the counter if it exceeds the maxTokens
  redis.call("DECR", userKey)
  return false
end
