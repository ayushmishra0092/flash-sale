package com.flashsale.controller;

import com.flashsale.dto.BookingRequest;
import com.flashsale.dto.BookingResponse;
import com.flashsale.dto.InitInventoryRequest;
import com.flashsale.entity.Order;
import com.flashsale.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/flash-sale")
@RequiredArgsConstructor
public class FlashSaleController {

    private final InventoryService inventoryService;
    @PostMapping("/inventory/init")
    public ResponseEntity<String> initInventory(@RequestBody InitInventoryRequest request) {
        log.info("Initializing inventory for product: {}", request.getProductId());
        inventoryService.initializeInventory(request.getProductId());
        return ResponseEntity.ok("Inventory initialized successfully");
    }

    @PostMapping("/book")
    public ResponseEntity<BookingResponse> bookInventory(@RequestBody BookingRequest request) {
        log.info("Booking request - User: {}, Product: {}, Quantity: {}",
            request.getUserId(), request.getProductId(), request.getQuantity());

        InventoryService.BookingResult result = inventoryService.bookInventory(
            request.getUserId(),
            request.getProductId(),
            request.getQuantity()
        );

        BookingResponse response = BookingResponse.builder()
            .success(result.isSuccess())
            .message(result.getMessage())
            .orderNumber(result.getOrderNumber())
            .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/inventory/{productId}")
    public ResponseEntity<InventoryResponse> checkInventory(@PathVariable Long productId) {
        int availableStock = inventoryService.getAvailableInventory(productId);

        InventoryResponse response = InventoryResponse.builder()
            .productId(productId)
            .availableStock(availableStock)
            .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/order/{orderNumber}")
    public ResponseEntity<OrderStatusResponse> getOrderStatus(@PathVariable String orderNumber) {
        Optional<Order> orderOpt = inventoryService.getOrderStatus(orderNumber);

        if (orderOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Order order = orderOpt.get();
        OrderStatusResponse response = OrderStatusResponse.builder()
            .orderNumber(order.getOrderNumber())
            .userId(order.getUserId())
            .productId(order.getProductId())
            .quantity(order.getQuantity())
            .totalPrice(order.getTotalPrice())
            .status(order.getStatus().name())
            .createdAt(order.getCreatedAt())
            .build();

        return ResponseEntity.ok(response);
    }

    @lombok.Data
    @lombok.Builder
    private static class InventoryResponse {
        private Long productId;
        private Integer availableStock;
    }

    @lombok.Data
    @lombok.Builder
    private static class OrderStatusResponse {
        private String orderNumber;
        private String userId;
        private Long productId;
        private Integer quantity;
        private java.math.BigDecimal totalPrice;
        private String status;
        private java.time.LocalDateTime createdAt;
    }
}
