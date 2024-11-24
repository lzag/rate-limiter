local userId = KEYS[1]
local initialValue = tonumber(ARGV[1])
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
