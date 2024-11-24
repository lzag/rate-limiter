local userId = KEYS[1]
local initialValue = tonumber(ARGV[1])
local currentValue = redis.call("GET", userId)

if not currentValue then
  redis.call("SET", userId, initialValue)
  return initialValue
else
  local newValue = tonumber(currentValue) - 1
  if newValue <= 0 then
    return newValue
  end
  redis.call("SET", userId, newValue)
  return newValue
end
