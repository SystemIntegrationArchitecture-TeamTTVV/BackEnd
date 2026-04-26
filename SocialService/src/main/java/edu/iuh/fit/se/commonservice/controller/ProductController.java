package edu.iuh.fit.se.commonservice.controller;

import edu.iuh.fit.se.commonservice.dto.CreateProductRequest;
import edu.iuh.fit.se.commonservice.dto.PagedResponse;
import edu.iuh.fit.se.commonservice.dto.ProductDto;
import edu.iuh.fit.se.commonservice.dto.UpdateProductRequest;
import edu.iuh.fit.se.commonservice.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Validated
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<PagedResponse<ProductDto>> getAllProducts(
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "pageSize", required = false, defaultValue = "20") int pageSize,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "sortBy", required = false) String sortBy,
            @RequestParam(value = "sortOrder", required = false) String sortOrder
    ) {
        return ResponseEntity.ok(new PagedResponse<>(productService.getAllProducts(page, pageSize, category, search, sortBy, sortOrder)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductDto> getById(@PathVariable String id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<PagedResponse<ProductDto>> getUserProducts(
            @PathVariable String userId,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "pageSize", required = false, defaultValue = "20") int pageSize
    ) {
        return ResponseEntity.ok(new PagedResponse<>(productService.getUserProducts(userId, page, pageSize)));
    }

    @PostMapping("/{userId}")
    public ResponseEntity<ProductDto> createProduct(@PathVariable String userId,
                                                    @Valid @RequestBody CreateProductRequest request) {
        ProductDto dto = productService.createProduct(request, userId);
        return ResponseEntity.ok(dto);
    }

    @PutMapping("/{userId}/{id}")
    public ResponseEntity<ProductDto> updateProduct(@PathVariable String userId,
                                                    @PathVariable String id,
                                                    @Valid @RequestBody UpdateProductRequest request) {
        ProductDto dto = productService.updateProduct(id, request, userId);
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/{userId}/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable String userId,
                                              @PathVariable String id) {
        productService.deleteProduct(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{userId}/{id}/mark-sold")
    public ResponseEntity<ProductDto> markAsSold(@PathVariable String userId,
                                                 @PathVariable String id) {
        ProductDto dto = productService.markAsSold(id, userId);
        return ResponseEntity.ok(dto);
    }
}
