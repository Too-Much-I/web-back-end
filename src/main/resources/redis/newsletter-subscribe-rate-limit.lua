if #KEYS ~= 2 then
    return redis.error_reply('NEWSLETTER_INVALID_KEY_COUNT')
end

if #ARGV ~= 4 then
    return redis.error_reply('NEWSLETTER_INVALID_ARGUMENT_COUNT')
end

local function positive_integer(value, name)
    local parsed = tonumber(value)
    if not parsed or parsed <= 0 or parsed ~= math.floor(parsed) then
        return nil, name
    end
    return parsed, nil
end

local limits = {}
local windows = {}
for index = 1, 2 do
    local limit, limit_error = positive_integer(ARGV[(index - 1) * 2 + 1], 'LIMIT')
    if limit_error then
        return redis.error_reply('NEWSLETTER_INVALID_' .. limit_error)
    end
    local window, window_error = positive_integer(ARGV[(index - 1) * 2 + 2], 'WINDOW')
    if window_error then
        return redis.error_reply('NEWSLETTER_INVALID_' .. window_error)
    end
    limits[index] = limit
    windows[index] = window
end

local scopes = {'IP_MEDIUM', 'IP_DAILY'}
local blockers = {}

local function redis_type(key)
    local result = redis.call('TYPE', key)
    if type(result) == 'table' then
        return result['ok']
    end
    return result
end

for index = 1, 2 do
    local key_type = redis_type(KEYS[index])
    if key_type ~= 'none' and key_type ~= 'string' then
        return redis.error_reply('NEWSLETTER_COUNTER_WRONGTYPE')
    end
    if key_type == 'string' then
        local raw_count = redis.call('GET', KEYS[index])
        local count = tonumber(raw_count)
        if not count or count < 0 or count ~= math.floor(count) then
            return redis.error_reply('NEWSLETTER_COUNTER_INVALID')
        end
        local ttl = redis.call('TTL', KEYS[index])
        if ttl < 1 then
            return redis.error_reply('NEWSLETTER_COUNTER_TTL_INVALID')
        end
        if count + 1 > limits[index] then
            table.insert(blockers, scopes[index])
            table.insert(blockers, ttl)
        end
    end
end

if #blockers > 0 then
    local response = {'DENIED'}
    for index = 1, #blockers do
        table.insert(response, blockers[index])
    end
    return response
end

for index = 1, 2 do
    local count = redis.call('INCR', KEYS[index])
    if count == 1 then
        redis.call('EXPIRE', KEYS[index], windows[index])
    end
end

return {'ALLOWED'}
