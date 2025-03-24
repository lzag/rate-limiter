local mock_store = {}

local redis_mock = {
    call = function(command, ...)
        local args = {...}
        if command == 'GET' then
            return mock_store[args[1]]
        elseif command == 'SETEX' then
            if #args ~= 3 then error("ERR wrong number of arguments for 'setex' command") end
            local key, seconds, value = args[1], tonumber(args[2]), args[3]
            if not seconds then error("ERR value is not an integer or out of range") end
            mock_store[key] = tostring(value) -- Store as string, per Redis
            return "OK"
        elseif command == 'HMGET' then
            local key = args[1]
            local result = {}
            for i = 2, #args do
                result[i - 1] = mock_store[key .. ':' .. args[i]] or nil
            end
            return result
        elseif command == 'HSET' then
            if #args < 3 or #args % 2 == 0 then error("ERR wrong number of arguments for 'hset' command") end
            local key = args[1]
            local count = 0
            for i = 2, #args, 2 do
                local field, value = args[i], args[i + 1]
                local full_key = key .. ':' .. field
                if not mock_store[full_key] then count = count + 1 end
                mock_store[full_key] = tostring(value)
            end
            return count
        elseif command == 'PEXPIRE' then
            if #args ~= 2 then error("ERR wrong number of arguments for 'pexpire' command") end
            local key, ms = args[1], tonumber(args[2])
            if not ms then error("ERR value is not an integer or out of range") end
            return mock_store[key] and 1 or 0
        elseif command == 'ZCARD' then
            mock_store[args[1]] = mock_store[args[1]] or {}
            return #mock_store[args[1]]
        elseif command == 'ZADD' then
            if #args ~= 3 then error("ERR wrong number of arguments for 'zadd' command") end
            local key, score, member = args[1], tonumber(args[2]), args[3]
            if not score then error("ERR value is not a valid float") end
            mock_store[key] = mock_store[key] or {}
            local exists = false
            for i, v in ipairs(mock_store[key]) do
                if v.member == member then exists = true; v.score = score; break end
            end
            if not exists then table.insert(mock_store[key], {score = score, member = member}) end
            return exists and 0 or 1
        elseif command == 'ZREMRANGEBYSCORE' then
            if #args ~= 3 then error("ERR wrong number of arguments for 'zremrangebyscore' command") end
            local key, min, max = args[1], tonumber(args[2]), tonumber(args[3])
            if not (min and max) then error("ERR min or max is not a float") end
            mock_store[key] = mock_store[key] or {}
            local removed = 0
            local new_set = {}
            for _, v in ipairs(mock_store[key]) do
                if v.score < min or v.score > max then
                    table.insert(new_set, v)
                else
                    removed = removed + 1
                end
            end
            mock_store[key] = new_set
            return removed
        elseif command == 'HGETALL' then
            local key = args[1]
            local result = {}
            for k, v in pairs(mock_store) do
                if k:match('^' .. key .. ':') then
                    table.insert(result, k:sub(#key + 2))
                    table.insert(result, v)
                end
            end
            return result
        elseif command == 'HDEL' then
            if #args < 2 then error("ERR wrong number of arguments for 'hdel' command") end
            local key = args[1]
            local removed = 0
            for i = 2, #args do
                local full_key = key .. ':' .. args[i]
                if mock_store[full_key] then
                    mock_store[full_key] = nil
                    removed = removed + 1
                end
            end
            return removed
        else
            error("ERR unknown command '" .. command .. "'")
        end
    end,
    pcall = function(command, ...)
        local ok, result = pcall(_G.redis.call, command, ...)
        if ok then return {ok = result} else return {ok = nil, err = result} end
    end,
    store = mock_store
}

redis_mock.error_reply = function(msg) return {err = msg} end
redis_mock.LOG_NOTICE = 1
redis_mock.log = function(_, msg) print("Log: " .. msg) end

return redis_mock
