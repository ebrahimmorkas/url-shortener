-- Sliding-window-log rate limiter. Runs atomically inside Redis.
-- KEYS[1]  sorted set holding one member per accepted request, scored by time (ms)
-- ARGV[1]  window length in ms
-- ARGV[2]  max requests per window
-- ARGV[3]  unique member id for this request
-- Returns  {allowed (1/0), remaining, retry_after_ms}

local key = KEYS[1]
local window = tonumber(ARGV[1])
local limit = tonumber(ARGV[2])

-- Use Redis' clock, not the caller's, so app instances with skewed clocks agree
local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)

redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
local count = redis.call('ZCARD', key)

if count < limit then
    redis.call('ZADD', key, now, ARGV[3])
    redis.call('PEXPIRE', key, window)
    return { 1, limit - count - 1, 0 }
end

local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
return { 0, 0, tonumber(oldest[2]) + window - now }
