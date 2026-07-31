if #KEYS ~= 1 then
    return redis.error_reply('BLOG_COMMENT_INVALID_RELEASE_KEY_COUNT')
end
if #ARGV ~= 1 or not ARGV[1] or string.len(ARGV[1]) == 0 then
    return redis.error_reply('BLOG_COMMENT_INVALID_RELEASE_OWNER')
end

local current_owner = redis.call('GET', KEYS[1])
if current_owner and current_owner == ARGV[1] then
    return redis.call('DEL', KEYS[1])
end
return 0
