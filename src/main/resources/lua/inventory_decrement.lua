-- Atomic inventory decrement script
-- KEYS[1]: inventory key (e.g., "inventory:product:123")
-- ARGV[1]: quantity to decrement
-- ARGV[2]: user ID making the booking

local inventoryKey = KEYS[1]
local quantity = tonumber(ARGV[1])
local userId = ARGV[2]

-- Get current inventory
local currentInventory = tonumber(redis.call('GET', inventoryKey))

-- Check if inventory exists
if not currentInventory then
    return -1  -- Inventory key doesn't exist
end

-- Check if sufficient inventory available
if currentInventory < quantity then
    return 0  -- Insufficient inventory
end

-- Decrement inventory atomically
redis.call('DECRBY', inventoryKey, quantity)

-- Store booking information with expiration (24 hours)
local bookingKey = "booking:" .. inventoryKey .. ":" .. userId
redis.call('SETEX', bookingKey, 86400, quantity)

return 1  -- Success
