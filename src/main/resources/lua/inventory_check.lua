-- Check inventory availability script
-- KEYS[1]: inventory key (e.g., "inventory:product:123")
-- ARGV[1]: quantity to check

local inventoryKey = KEYS[1]
local quantity = tonumber(ARGV[1])

-- Get current inventory
local currentInventory = tonumber(redis.call('GET', inventoryKey))

-- Check if inventory exists and is sufficient
if currentInventory and currentInventory >= quantity then
    return 1  -- Available
else
    return 0  -- Not available
end
