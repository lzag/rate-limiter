local key = KEYS[1]
local time = redis.call("TIME")
local currentTimestamp = tonumber(time[1])
local roundedTimestamp = math.floor(currentTimestamp / 60) * 60
return roundedTimestamp
redisKey = userId .. ":" .. roundedTimestamp
currentValue = redis.call("GET", redisKey)

if not currentValue then
  redis.call("SET", redisKey, 1)
  redis.call("PEXPIRE", redisKey, 60000)  -- Set expiration to one minute (60000 milliseconds)
  return initialValue
else
  local newValue = tonumber(currentValue) + 1
  redis.call("SET", redisKey, newValue) -- no need to call expiration, because it's set to interval of one minute
  return newValue
end
