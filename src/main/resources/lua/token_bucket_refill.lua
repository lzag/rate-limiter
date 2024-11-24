local cursor = "0"
local initVal = tonumber(ARGV[1]) -- Default value is 10 if not provided

repeat
    local result = redis.call('SCAN', cursor)
    cursor = result[1]
    local keys = result[2]
    if #keys > 0 then
        local msetArgs = {}
        for i, key in ipairs(keys) do
            table.insert(msetArgs, key)
            table.insert(msetArgs, initVal)
        end
        redis.call('MSET', unpack(msetArgs))
    end
until cursor == "0"
return 'OK'
