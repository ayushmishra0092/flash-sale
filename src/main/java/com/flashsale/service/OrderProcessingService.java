package com.flashsale.service;

import com.flashsale.dto.BookingEvent;
import com.flashsale.entity.Order;
import com.flashsale.entity.Product;
import com.flashsale.repository.OrderRepository;
import com.flashsale.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderProcessingService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    @KafkaListener(
        topics = "${flashsale.kafka.topic.booking}",
        groupId = "${spring.kafka.consumer.group-id}",
        concurrency = "3"
    )
    @Transactional
    public void processBooking(
        @Payload BookingEvent event,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset
    ) {
        log.info("Processing booking event - Order: {}, Partition: {}, Offset: {}",
            event.getOrderNumber(), partition, offset);

        try {
            Optional<Product> productOpt = productRepository.findById(event.getProductId());
            if (productOpt.isEmpty()) {
                log.error("Product not found for order: {}", event.getOrderNumber());
                createFailedOrder(event, "Product not found");
                return;
            }

            Product product = productOpt.get();

            int updated = productRepository.decrementStock(event.getProductId(), event.getQuantity());

            if (updated == 0) {
                log.error("Failed to decrement stock in database for order: {}", event.getOrderNumber());
                createFailedOrder(event, "Database stock inconsistency");
                return;
            }

            Order order = Order.builder()
                .orderNumber(event.getOrderNumber())
                .userId(event.getUserId())
                .productId(event.getProductId())
                .quantity(event.getQuantity())
                .totalPrice(event.getTotalPrice())
                .status(Order.OrderStatus.CONFIRMED)
                .build();

            orderRepository.save(order);

            log.info("Order confirmed and persisted: {}", event.getOrderNumber());

        } catch (Exception e) {
            log.error("Error processing booking event: {}", event.getOrderNumber(), e);
            createFailedOrder(event, "Processing error: " + e.getMessage());
        }
    }

    private void createFailedOrder(BookingEvent event, String reason) {
        try {
            Order order = Order.builder()
                .orderNumber(event.getOrderNumber())
                .userId(event.getUserId())
                .productId(event.getProductId())
                .quantity(event.getQuantity())
                .totalPrice(event.getTotalPrice())
                .status(Order.OrderStatus.FAILED)
                .build();

            orderRepository.save(order);

            log.info("Failed order recorded: {} - Reason: {}", event.getOrderNumber(), reason);
        } catch (Exception e) {
            log.error("Failed to create failed order record: {}", event.getOrderNumber(), e);
        }
    }
}
