package com.flashsale.controller;

import com.flashsale.entity.Product;
import com.flashsale.repository.ProductRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductRepository productRepository;

    @PostMapping
    public ResponseEntity<Product> createProduct(@RequestBody CreateProductRequest request) {
        Product product = Product.builder()
            .name(request.getName())
            .description(request.getDescription())
            .price(request.getPrice())
            .totalStock(request.getTotalStock())
            .availableStock(request.getTotalStock())
            .saleStartTime(request.getSaleStartTime() != null ? request.getSaleStartTime() : LocalDateTime.now())
            .saleEndTime(request.getSaleEndTime() != null ? request.getSaleEndTime() : LocalDateTime.now().plusDays(7))
            .active(true)
            .build();

        Product saved = productRepository.save(product);
        log.info("Product created: {}", saved.getId());

        return ResponseEntity.ok(saved);
    }

    @GetMapping
    public ResponseEntity<List<Product>> getAllProducts() {
        return ResponseEntity.ok(productRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> getProduct(@PathVariable Long id) {
        return productRepository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/stock")
    public ResponseEntity<Product> updateStock(@PathVariable Long id, @RequestBody UpdateStockRequest request) {
        return productRepository.findById(id)
            .map(product -> {
                product.setAvailableStock(request.getAvailableStock());
                Product updated = productRepository.save(product);
                log.info("Product {} stock updated to {}", id, request.getAvailableStock());
                return ResponseEntity.ok(updated);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        if (productRepository.existsById(id)) {
            productRepository.deleteById(id);
            log.info("Product deleted: {}", id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    @Data
    public static class CreateProductRequest {
        private String name;
        private String description;
        private BigDecimal price;
        private Integer totalStock;
        private LocalDateTime saleStartTime;
        private LocalDateTime saleEndTime;
    }

    @Data
    public static class UpdateStockRequest {
        private Integer availableStock;
    }
}
