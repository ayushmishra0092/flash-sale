package com.flashsale.grpc;

import com.flashsale.entity.Order;
import com.flashsale.service.InventoryService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.Optional;

/**
 * gRPC service implementation for high-performance inventory operations
 */
@Slf4j
@GrpcService
@RequiredArgsConstructor
public class InventoryServiceImpl extends InventoryServiceGrpc.InventoryServiceImplBase {

    private final InventoryService inventoryService;

    @Override
    public void bookInventory(BookingRequest request, StreamObserver<BookingResponse> responseObserver) {
        log.info("gRPC BookInventory request - User: {}, Product: {}, Quantity: {}",
            request.getUserId(), request.getProductId(), request.getQuantity());

        try {
            InventoryService.BookingResult result = inventoryService.bookInventory(
                request.getUserId(),
                request.getProductId(),
                request.getQuantity()
            );

            BookingResponse.Builder responseBuilder = BookingResponse.newBuilder()
                .setSuccess(result.isSuccess())
                .setMessage(result.getMessage());

            if (result.isSuccess()) {
                responseBuilder
                    .setOrderNumber(result.getOrderNumber())
                    .setStatus(BookingStatus.BOOKING_SUCCESS);
            } else {
                switch (result.getType()) {
                    case INSUFFICIENT_STOCK:
                        responseBuilder.setStatus(BookingStatus.INSUFFICIENT_STOCK);
                        break;
                    case FAILED:
                        responseBuilder.setStatus(BookingStatus.BOOKING_FAILED);
                        break;
                }
            }

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error processing gRPC booking request", e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void checkInventory(CheckInventoryRequest request, StreamObserver<CheckInventoryResponse> responseObserver) {
        log.debug("gRPC CheckInventory request - Product: {}, Quantity: {}",
            request.getProductId(), request.getQuantity());

        try {
            int availableStock = inventoryService.getAvailableInventory(request.getProductId());
            boolean available = availableStock >= request.getQuantity();

            CheckInventoryResponse response = CheckInventoryResponse.newBuilder()
                .setAvailable(available)
                .setAvailableStock(availableStock)
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error checking inventory via gRPC", e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void getOrderStatus(OrderStatusRequest request, StreamObserver<OrderStatusResponse> responseObserver) {
        log.debug("gRPC GetOrderStatus request - Order: {}", request.getOrderNumber());

        try {
            Optional<Order> orderOpt = inventoryService.getOrderStatus(request.getOrderNumber());

            OrderStatusResponse.Builder responseBuilder = OrderStatusResponse.newBuilder();

            if (orderOpt.isPresent()) {
                Order order = orderOpt.get();
                responseBuilder
                    .setFound(true)
                    .setOrderNumber(order.getOrderNumber())
                    .setStatus(order.getStatus().name())
                    .setProductId(order.getProductId())
                    .setQuantity(order.getQuantity());
            } else {
                responseBuilder.setFound(false);
            }

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error getting order status via gRPC", e);
            responseObserver.onError(e);
        }
    }
}
