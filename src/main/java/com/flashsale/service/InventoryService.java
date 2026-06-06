package com.flashsale.service;

import com.flashsale.dto.BookingEvent;
import com.flashsale.dto.InventoryInit;
import com.flashsale.entity.Order;
import com.flashsale.entity.Product;
import com.flashsale.repository.OrderRepository;
import com.flashsale.repository.ProductRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisScript<String> inventoryDecrementScript;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, BookingEvent> kafkaTemplate;

    @Value("${flashsale.redis.inventory-prefix}")
    private String inventoryPrefix;

    @Value("${flashsale.kafka.topic.booking}")
    private String bookingTopic;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public void initializeInventory(Long productId) {
        Optional<Product> productOpt = productRepository.findById(productId);

        if (productOpt.isEmpty()) {
            log.error("Product not found: {}", productId);
            return;
        }

        Product product = productOpt.get();
        String inventoryKey = inventoryPrefix + productId;

        InventoryInit inventoryInit = new InventoryInit(product.getAvailableStock(), product.getPrice().doubleValue());

        redisTemplate.opsForValue().set(inventoryKey, inventoryInit);

        log.info("Initialized inventory for product {}: {} units", productId, product.getAvailableStock());
    }

    public BookingResult bookInventory(String userId, Long productId, Integer quantity) {
        // Validate inputs
        if (userId == null || productId == null || quantity == null || quantity <= 0) {
            return BookingResult.failed("Invalid booking request");
        }

        String inventoryKey = inventoryPrefix + productId;
        String scriptResult = redisTemplate.execute(
            inventoryDecrementScript,
            Collections.singletonList(inventoryKey),
            quantity,
            userId
        );

        if (scriptResult == null) {
            return BookingResult.failed("Product not available for sale");
        } else if ("0".equals(scriptResult)) {
            log.info("Insufficient inventory for product {} - User: {}", productId, userId);
            return BookingResult.insufficientStock();
        }

        InventoryInit updatedInventory;
        try {
            updatedInventory = OBJECT_MAPPER.readValue(scriptResult, InventoryInit.class);
        } catch (JsonProcessingException ex) {
            log.error("Failed to parse inventory result for product {}: {}", productId, ex.getMessage(), ex);
            return BookingResult.failed("Failed to parse inventory result");
        }

        if (updatedInventory == null) {
            return BookingResult.failed("Product not available for sale");
        }

        // Booking successful in Redis - send event to Kafka
        String orderNumber = generateOrderNumber();
        BigDecimal totalPrice = BigDecimal.valueOf(updatedInventory.getPrice()).multiply(BigDecimal.valueOf(quantity));

        BookingEvent event = BookingEvent.builder()
            .orderNumber(orderNumber)
            .userId(userId)
            .productId(productId)
            .quantity(quantity)
            .totalPrice(totalPrice)
            .bookingTime(LocalDateTime.now())
            .build();

        // Send to Kafka asynchronously
        kafkaTemplate.send(bookingTopic, orderNumber, event)
            .whenComplete((result1, ex) -> {
                if (ex != null) {
                    log.error("Failed to send booking event to Kafka: {}", orderNumber, ex);
                } else {
                    log.info("Booking event sent to Kafka: {}", orderNumber);
                }
            });

        log.info("Booking successful - Order: {}, User: {}, Product: {}, Quantity: {}",
            orderNumber, userId, productId, quantity);

        return BookingResult.success(orderNumber);
    }

    public int getAvailableInventory(Long productId) {
        String inventoryKey = inventoryPrefix + productId;
        Object inventory = redisTemplate.opsForValue().get(inventoryKey);

        if (inventory instanceof InventoryInit) {
            return ((InventoryInit) inventory).getAvailableStock();
        }

        return 0;
    }

    public Optional<Order> getOrderStatus(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber);
    }

    private String generateOrderNumber() {
        return "FS-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    public static class BookingResult {
        private final boolean success;
        private final String message;
        private final String orderNumber;
        private final ResultType type;

        public enum ResultType {
            SUCCESS, INSUFFICIENT_STOCK, FAILED
        }

        private BookingResult(boolean success, String message, String orderNumber, ResultType type) {
            this.success = success;
            this.message = message;
            this.orderNumber = orderNumber;
            this.type = type;
        }

        public static BookingResult success(String orderNumber) {
            return new BookingResult(true, "Booking successful", orderNumber, ResultType.SUCCESS);
        }

        public static BookingResult insufficientStock() {
            return new BookingResult(false, "Insufficient stock", null, ResultType.INSUFFICIENT_STOCK);
        }

        public static BookingResult failed(String message) {
            return new BookingResult(false, message, null, ResultType.FAILED);
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public String getOrderNumber() { return orderNumber; }
        public ResultType getType() { return type; }
    }
}
