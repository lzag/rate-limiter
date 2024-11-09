local cursor = "0"
repeat
    local result = redis.call('SCAN', cursor)
    cursor = result[1]
    local keys = result[2]
    if #keys > 0 then
        local msetArgs = {}
        for i, key in ipairs(keys) do
            table.insert(msetArgs, key)
            table.insert(msetArgs, 10)
        end
        redis.call('MSET', unpack(msetArgs))
    end
until cursor == "0"
return 'OK'
