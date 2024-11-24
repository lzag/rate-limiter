local key = KEYS[1] -- key is userId.timestamp
local maxToken = tonumber(ARGV[1])
local currentValue = redis.call("GET", key)

if not currentValue then
  redis.call("SETEX", userId, 1800, 1)  -- Set expiration to 30 minutes (1800 seconds)
  return initialValue
else
  local newValue = tonumber(currentValue) - 1
  if newValue <= 0 then
    return newValue
  end
  redis.call("SET", userId, newValue)
  return newValue
end
