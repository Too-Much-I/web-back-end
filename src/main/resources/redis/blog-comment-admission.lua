if #KEYS ~= 6 then
    return redis.error_reply('BLOG_COMMENT_INVALID_KEY_COUNT')
end

if #ARGV ~= 12 then
    return redis.error_reply('BLOG_COMMENT_INVALID_ARGUMENT_COUNT')
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
for index = 1, 5 do
    local limit, limit_error = positive_integer(ARGV[(index - 1) * 2 + 1], 'LIMIT')
    if limit_error then
        return redis.error_reply('BLOG_COMMENT_INVALID_' .. limit_error)
    end
    local window, window_error = positive_integer(ARGV[(index - 1) * 2 + 2], 'WINDOW')
    if window_error then
        return redis.error_reply('BLOG_COMMENT_INVALID_' .. window_error)
    end
    limits[index] = limit
    windows[index] = window
end

local duplicate_ttl, duplicate_ttl_error = positive_integer(ARGV[11], 'DUPLICATE_TTL')
if duplicate_ttl_error then
    return redis.error_reply('BLOG_COMMENT_INVALID_' .. duplicate_ttl_error)
end
if not ARGV[12] or string.len(ARGV[12]) == 0 then
    return redis.error_reply('BLOG_COMMENT_INVALID_RESERVATION_OWNER')
end

local scopes = {
    'VISITOR_SHORT',
    'VISITOR_MEDIUM',
    'VISITOR_DAILY',
    'IP_MEDIUM',
    'IP_DAILY'
}

local blockers = {}
local function add_blocker(scope, ttl)
    table.insert(blockers, scope)
    table.insert(blockers, ttl)
end

local function redis_type(key)
    local result = redis.call('TYPE', key)
    if type(result) == 'table' then
        return result['ok']
    end
    return result
end

for index = 1, 5 do
    local key_type = redis_type(KEYS[index])
    if key_type ~= 'none' and key_type ~= 'string' then
        return redis.error_reply('BLOG_COMMENT_COUNTER_WRONGTYPE')
    end
    if key_type == 'string' then
        local raw_count = redis.call('GET', KEYS[index])
        local count = tonumber(raw_count)
        if not count or count < 0 or count ~= math.floor(count) then
            return redis.error_reply('BLOG_COMMENT_COUNTER_INVALID')
        end
        local ttl = redis.call('TTL', KEYS[index])
        if ttl < 1 then
            return redis.error_reply('BLOG_COMMENT_COUNTER_TTL_INVALID')
        end
        if count + 1 > limits[index] then
            add_blocker(scopes[index], ttl)
        end
    end
end

local duplicate_type = redis_type(KEYS[6])
if duplicate_type ~= 'none' and duplicate_type ~= 'string' then
    return redis.error_reply('BLOG_COMMENT_DUPLICATE_WRONGTYPE')
end
if duplicate_type == 'string' then
    local duplicate_remaining = redis.call('TTL', KEYS[6])
    if duplicate_remaining < 1 then
        return redis.error_reply('BLOG_COMMENT_DUPLICATE_TTL_INVALID')
    end
    add_blocker('DUPLICATE', duplicate_remaining)
end

if #blockers > 0 then
    local response = {'DENIED'}
    for index = 1, #blockers do
        table.insert(response, blockers[index])
    end
    return response
end

for index = 1, 5 do
    local count = redis.call('INCR', KEYS[index])
    if count == 1 then
        redis.call('EXPIRE', KEYS[index], windows[index])
    end
end

local reserved = redis.call('SET', KEYS[6], ARGV[12], 'EX', duplicate_ttl, 'NX')
if not reserved then
    return redis.error_reply('BLOG_COMMENT_DUPLICATE_RESERVATION_FAILED')
end

return {'ALLOWED'}
