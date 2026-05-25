-- release-stock.lua
-- Atomically releases a flash sale reservation (on expiry or cancel).
--
-- KEYS[1] = reservation:{reservationId}   (String: reservationId)
-- KEYS[2] = flashsale:{id}:stock          (String: remaining stock count)
-- KEYS[3] = flashsale:{id}:reserved_users (Set: userIds holding a slot)
--
-- ARGV[1] = quantity
-- ARGV[2] = userId
--
-- Returns:
--   "OK"          reservation existed and was released
--   "NOT_FOUND"   reservation key already gone (expired or double-release)

local existing = redis.call('GET', KEYS[1])
if existing == nil or existing == false then
  return 'NOT_FOUND'
end

redis.call('DEL', KEYS[1])
redis.call('INCRBY', KEYS[2], tonumber(ARGV[1]))
redis.call('SREM', KEYS[3], ARGV[2])

return 'OK'
