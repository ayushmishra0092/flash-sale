package com.flashsale.service;

import com.flashsale.dto.BookingEvent;
import com.flashsale.entity.Order;
import com.flashsale.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaOrderConsumer {

    private final OrderRepository orderRepository;

    @KafkaListener(
        topics = "${flashsale.kafka.topic.order}",
        groupId = "${spring.kafka.consumer.group-id}",
        concurrency = "3"
    )
    @Transactional
    public void consumeOrderEvent(
        @Payload BookingEvent event,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset
    ) {
        log.info("Consumed order topic event - Order: {}, Partition: {}, Offset: {}",
            event.getOrderNumber(), partition, offset);

        if (orderRepository.findByOrderNumber(event.getOrderNumber()).isPresent()) {
            log.warn("Order already exists, skipping insert: {}", event.getOrderNumber());
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
        log.info("Persisted order from Kafka topic: {}", event.getOrderNumber());
    }
}
