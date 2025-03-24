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

local scriptType = ARGV[1]
local maxRequests = tonumber(ARGV[2])

if scriptType == "TOKEN_BUCKET" then
  return token_bucket(maxRequests)
else
  return redis.error_reply("Invalid script type")
end

