luaunit = require('luaunit')
local redis_mock = require('redis_mock')

_G.redis = redis_mock
local script = assert(loadfile('../../main/resources/lua/checkers.lua'))

local window_size = 60
local max_requests = 60
local requestTimestamp = 0
local key = 'key1'

TestTokenBucket = {}
function TestTokenBucket:setUp()
  for k in pairs(redis_mock.store) do redis_mock.store[k] = nil end
end
function TestTokenBucket:test_first_request()
  _G.KEYS = {key}
  _G.ARGV = {'token_bucket', max_requests, window_size, requestTimestamp}
  local result = script()
  luaunit.assertEquals(result, {true, max_requests - 1})
  luaunit.assertEquals(redis_mock.store[key], tostring(max_requests - 1))
end
function TestTokenBucket:test_tokens_remaining()
  local remaining = 5
  redis_mock.store[key] = remaining
  _G.KEYS = {key}
  _G.ARGV = {'token_bucket', max_requests, window_size, requestTimestamp}
  local result = script()
  luaunit.assertEquals(result, {true, remaining - 1})
  luaunit.assertEquals(redis_mock.store[key], tostring(remaining - 1))
end
function TestTokenBucket:test_depleted()
  redis_mock.store[key] = '0'
  _G.KEYS = {key}
  _G.ARGV = {'token_bucket', max_requests, window_size, 10000}
  local result = script()
  luaunit.assertEquals(result, {false, -1})
  for k in pairs(redis_mock.store) do redis_mock.store[k] = nil end
end

TestAlgoTokenBucket = {}
function TestAlgoTokenBucket:setUp()
  for k in pairs(redis_mock.store) do redis_mock.store[k] = nil end
end
function TestAlgoTokenBucket:test_first_request()
  _G.KEYS = {key}
  _G.ARGV = {'algo_token_bucket', max_requests, window_size, 1000}
  local result = script()
  luaunit.assertEquals(result, {true, max_requests - 1})
  luaunit.assertEquals(redis.call('HMGET', key, 'value', 'timestamp'), {tostring(max_requests - 1), tostring(1000)})
end
function TestAlgoTokenBucket:test_refilled_after_window()
  redis_mock.call('HSET', key, 'value', 0, 'timestamp', 0)
  _G.KEYS = {key}
  _G.ARGV = {'algo_token_bucket', max_requests, window_size, window_size * 1000}
  local result = script()
  luaunit.assertEquals(result, {true, max_requests - 1})
end
function TestAlgoTokenBucket:test_not_going_over_max()
  redis_mock.call('HSET', key, 'value', 0, 'timestamp', 0)
  _G.KEYS = {key}
  _G.ARGV = {'algo_token_bucket', max_requests, window_size, window_size * 1000 * 3}
  local result = script()
  luaunit.assertEquals(result, {true, max_requests - 1})
end
function TestAlgoTokenBucket:test_not_refilling_before_window_over()
  redis_mock.call('HSET', key, 'value', 30, 'timestamp', 0)
  _G.KEYS = {key}
  _G.ARGV = {'algo_token_bucket', max_requests, window_size, window_size / 2 * 1000}
  local result = script()
  luaunit.assertEquals(result, {true, 29})
end

TestFractionalTokenBucket = {}
function TestFractionalTokenBucket:setUp()
  for k in pairs(redis_mock.store) do redis_mock.store[k] = nil end
end
function TestFractionalTokenBucket:test_first_request()
  _G.KEYS = {key}
  _G.ARGV = {'fractional_token_bucket', max_requests, window_size, 0}
  local result = script()
  luaunit.assertEquals(result, {true, 0})
end
function TestFractionalTokenBucket:test_fractional_refill()
  redis.call("HSET", key, "value", 0, "timestamp", 0)
  _G.KEYS = {key}
  _G.ARGV = {'fractional_token_bucket', max_requests, window_size, 30 * 1000}
  local result = script()
  luaunit.assertEquals(result, {true, 30 - 1})
end
function TestFractionalTokenBucket:test_max()
  redis.call("HSET", key, "value", 0, "timestamp", 0)
  _G.KEYS = {key}
  _G.ARGV = {'fractional_token_bucket', max_requests, window_size, window_size * 10000 * 1000}
  local result = script()
  luaunit.assertEquals(result, {true, max_requests - 1})
end

TestLeakyBucket = {}
function TestLeakyBucket:setUp()
  for k in pairs(redis_mock.store) do redis_mock.store[k] = nil end
end
function TestLeakyBucket:test_first_request()
  _G.KEYS = {key}
  _G.ARGV = {'leaky_bucket', max_requests, window_size, 1000}
  local result = script()
  luaunit.assertEquals(result, {true, max_requests - 1})
  luaunit.assertEquals(redis.call('HMGET', key, 'value', 'timestamp'), {tostring(1), tostring(1000)})
end
function TestLeakyBucket:test_refilled_after_window()
  redis_mock.call('HSET', key, 'value', 60, 'timestamp', 0)
  _G.KEYS = {key}
  _G.ARGV = {'leaky_bucket', max_requests, window_size, window_size * 1000}
  local result = script()
  luaunit.assertEquals(result, {true, max_requests - 1})
end
function TestLeakyBucket:test_not_going_over_max()
  redis_mock.call('HSET', key, 'value', 60, 'timestamp', 0)
  _G.KEYS = {key}
  _G.ARGV = {'leaky_bucket', max_requests, window_size, window_size * 1000 * 3}
  local result = script()
  luaunit.assertEquals(result, {true, max_requests - 1})
end
function TestLeakyBucket:test_not_refilling_before_window_over()
  redis_mock.call('HSET', key, 'value', 30, 'timestamp', 0)
  _G.KEYS = {key}
  _G.ARGV = {'leaky_bucket', max_requests, window_size, window_size / 2 * 1000}
  local result = script()
  luaunit.assertEquals(result, {true, 29})
end

TestFixedWindowCounter = {}
function TestFixedWindowCounter:setUp()
  for k in pairs(redis_mock.store) do redis_mock.store[k] = nil end
end
function TestFixedWindowCounter:test_new_window()
  _G.KEYS = {key}
  _G.ARGV = {'fixed_window_counter', max_requests, window_size, 0}
  local result = script()
  luaunit.assertEquals(result, {true, max_requests - 1})
  luaunit.assertEquals(redis_mock.store[key .. ':' .. tostring(0)], tostring(max_requests - 1))
end
function TestFixedWindowCounter:test_depleted()
  redis_mock.call('SETEX', key .. ':' .. tostring(0), window_size, 0)
  _G.KEYS = {key}
  _G.ARGV = {'fixed_window_counter', max_requests, window_size, 0}
  local result = script()
  luaunit.assertEquals(result, {false, -1})
  luaunit.assertEquals(redis_mock.store[key .. ':' .. tostring(0)], '0')
end
function TestFixedWindowCounter:test_not_going_over_max()
  _G.KEYS = {key}
  _G.ARGV = {'fixed_window_counter', max_requests, window_size, 0}
  local result = script()
  luaunit.assertEquals(result, {true, max_requests - 1})
  luaunit.assertEquals(redis_mock.store['key1:' .. tostring(0)], tostring(max_requests - 1))
  _G.ARGV = {'fixed_window_counter', max_requests, window_size, window_size * 10 * 1000}
  luaunit.assertEquals(result, {true, max_requests - 1})
end

TestSlidingWindowLog = {}
function TestSlidingWindowLog:setUp()
  for k in pairs(redis_mock.store) do redis_mock.store[k] = nil end
end
function TestSlidingWindowLog:test_new_window()
  _G.KEYS = {key}
  _G.ARGV = {'sliding_window_log', max_requests, window_size, 0}
  local result = script()
  luaunit.assertEquals(result, {true, max_requests - 1})
end
function TestSlidingWindowLog:test_depleted()
    for i = 0, max_requests - 1 do
        redis.call("ZADD", key, i * 10, i * 10)
    end
    _G.KEYS = {key}
    _G.ARGV = {'sliding_window_log', max_requests, window_size, 0}
    local result = script()
    luaunit.assertEquals(result, {false, -1})
end
function TestSlidingWindowLog:test_not_going_over_max()
    for i = 0, max_requests - 1 do
        redis.call("ZADD", key, i * 10, i * 10)
    end
    _G.KEYS = {key}
    _G.ARGV = {'sliding_window_log', max_requests, window_size, 10000000}
    local result = script()
    luaunit.assertEquals(result, {true, max_requests - 1})
end

TestSlidingWindowCounter = {}
function TestSlidingWindowCounter:setUp()
  for k in pairs(redis_mock.store) do redis_mock.store[k] = nil end
end
function TestSlidingWindowCounter:test_new_window()
  _G.KEYS = {key}
  _G.ARGV = {'sliding_window_counter', max_requests, window_size, 0}
  local result = script()
  luaunit.assertEquals(result, {true, max_requests - 1})
end
function TestSlidingWindowCounter:test_depleted()
    local lastTimestamp = 0
    local lastWindowIndex = 0
    local lastCount = 0
    for i = 0, max_requests - 1 do
        lastTimestamp = i * 1000
        windowIndex = math.floor(lastTimestamp / window_size / 60)
        if windowIndex == lastWindowIndex then
            lastCount = lastCount + 1
        else
            lastWindowIndex = windowIndex
            lastCount = 1
        end
        redis.call("HSET", key, windowIndex, lastCount)
    end
    _G.KEYS = {key}
    _G.ARGV = {'sliding_window_counter', max_requests, window_size, lastTimestamp}
    local result = script()
    luaunit.assertEquals(result, {false, -1})
end
function TestSlidingWindowCounter:test_not_going_over_max()
    for i = 0, max_requests - 1 do
        windowIndex = math.floor(i * 10 / 60)
        redis.call("HSET", key, windowIndex, max_requests)
    end
    _G.KEYS = {key}
    _G.ARGV = {'sliding_window_counter', max_requests, window_size, 10000000}
    local result = script()
    luaunit.assertEquals(result, {true, max_requests - 1})
end

os.exit( luaunit.LuaUnit.run() )
