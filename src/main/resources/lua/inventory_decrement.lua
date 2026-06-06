-- Atomic inventory decrement script for InventoryInit JSON objects
-- KEYS[1]: inventory key (e.g., "inventory:product:123")
-- ARGV[1]: quantity to decrement
-- ARGV[2]: user ID making the booking

local inventoryKey = KEYS[1]
local quantity = tonumber(ARGV[1])
local userId = ARGV[2]

-- Get current inventory JSON
local inventoryJson = redis.call('GET', inventoryKey)
if not inventoryJson then
    return nil  -- Inventory key doesn't exist
end

local inventory = cjson.decode(inventoryJson)
if not inventory or inventory.availableStock == nil then
    return nil  -- Invalid inventory object
end

local availableStock = tonumber(inventory.availableStock)
if not availableStock then
    return nil
end

-- Check if sufficient inventory available
if availableStock < quantity then
    return "0"  -- Insufficient inventory
end

-- Decrement availableStock and persist JSON back to Redis
inventory.availableStock = availableStock - quantity
local updatedInventoryJson = cjson.encode(inventory)
redis.call('SET', inventoryKey, updatedInventoryJson)

-- Store booking information with expiration (24 hours)
local bookingKey = "booking:" .. inventoryKey .. ":" .. userId
redis.call('SETEX', bookingKey, 86400, quantity)

return updatedInventoryJson  -- Return updated InventoryInit object
