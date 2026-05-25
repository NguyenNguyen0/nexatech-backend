-- reserve-stock.lua
-- Atomically reserves a flash sale slot for a user.
--
-- KEYS[1] = flashsale:{id}:meta          (Hash: status, start_time, end_time)
-- KEYS[2] = flashsale:{id}:stock         (String: remaining stock count)
-- KEYS[3] = flashsale:{id}:reserved_users (Set: userIds holding a slot)
-- KEYS[4] = flashsale:{id}:buyers        (Set: userIds who already paid)
-- KEYS[5] = reservation:{reservationId}  (String: reservationId, EX ttl)
--
-- ARGV[1] = userId
-- ARGV[2] = reservationId
-- ARGV[3] = ttlSeconds
-- ARGV[4] = currentTimestamp (Unix seconds)
-- ARGV[5] = quantity
-- ARGV[6] = flashSaleId
--
-- Returns:
--   "OK"           on success
--   "NOT_FOUND"    flash sale not loaded in space
--   "NOT_STARTED"  flash sale has not started yet
--   "ENDED"        flash sale has ended or sold out
--   "ALREADY_RESERVED" user already holds a slot
--   "ALREADY_BOUGHT"   user already confirmed purchase
--   "OUT_OF_STOCK"     not enough stock

local meta        = redis.call('HMGET', KEYS[1], 'status', 'start_time', 'end_time')
local status      = meta[1]
local startTime   = tonumber(meta[2])
local endTime     = tonumber(meta[3])
local userId      = ARGV[1]
local reservationId = ARGV[2]
local ttl         = tonumber(ARGV[3])
local now         = tonumber(ARGV[4])
local quantity    = tonumber(ARGV[5])

if status == nil or status == false then
  return 'NOT_FOUND'
end

if now < startTime then
  return 'NOT_STARTED'
end

if now >= endTime or status == 'ENDED' then
  return 'ENDED'
end

if redis.call('SISMEMBER', KEYS[3], userId) == 1 then
  return 'ALREADY_RESERVED'
end

if redis.call('SISMEMBER', KEYS[4], userId) == 1 then
  return 'ALREADY_BOUGHT'
end

local stock = tonumber(redis.call('GET', KEYS[2]))
if stock == nil or stock < quantity then
  return 'OUT_OF_STOCK'
end

redis.call('DECRBY', KEYS[2], quantity)
redis.call('SADD', KEYS[3], userId)
redis.call('SET', KEYS[5], reservationId, 'EX', ttl)

return 'OK'
