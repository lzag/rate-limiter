-- token bucket refill script
local function token_bucket(maxRequests)
  local cursor = "0"
  repeat
      local result = redis.call('SCAN', cursor)
      cursor = result[1]
      local keys = result[2]
      if #keys > 0 then
          local msetArgs = {}
          for i, key in ipairs(keys) do
              table.insert(msetArgs, key)
              table.insert(msetArgs, maxRequests)
          end
          redis.call('MSET', unpack(msetArgs))
      end
  until cursor == "0"
  return 'OK'
end

-- timestamp bucket refill script
local function leaky_bucket()
  local cursor = "0"
  repeat
      local result = redis.call('SCAN', cursor)
      cursor = result[1]
      local keys = result[2]
      if #keys > 0 then
          local msetArgs = {}
          for i, key in ipairs(keys) do
              table.insert(msetArgs, key)
              table.insert(msetArgs, 0)
          end
          redis.call('MSET', unpack(msetArgs))
      end
  until cursor == "0"
  return 'OK'
end

local scriptType = ARGV[1]
local maxRequests = tonumber(ARGV[2])

if scriptType == "tokenBucket" then
  return token_bucket(maxRequests)
elseif scriptType == "leakyBucket" then
  return leaky_bucket()
-- elseif scriptType == "fixedWindowCounter" then
--   return fixed_window_counter(key, maxRequests)
-- elseif scriptType == "slidingWindowLog" then
--   return sliding_window_log(key, maxRequests, windowSize, timestamp)
-- elseif scriptType == "slidingWindowCounter" then
--   return sliding_window_counter(key, maxRequests, windowSize, timestamp)
else
  return redis.error_reply("Invalid script type")
end

