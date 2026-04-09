// ...existing code...
package edu.iuh.fit.se.commonservice.service;

import edu.iuh.fit.se.commonservice.dto.CreateProductRequest;
import edu.iuh.fit.se.commonservice.dto.ProductDto;
import edu.iuh.fit.se.commonservice.dto.UpdateProductRequest;
import org.springframework.data.domain.Page;

public interface ProductService {
    ProductDto createProduct(CreateProductRequest request, String sellerId);
    ProductDto updateProduct(String productId, UpdateProductRequest request, String sellerId);
    void deleteProduct(String productId, String sellerId);
    ProductDto getProductById(String id);
    Page<ProductDto> getAllProducts(int page, int size, String category, String search, String sortBy, String sortOrder);
    Page<ProductDto> getUserProducts(String userId, int page, int size);
    ProductDto markAsSold(String productId, String sellerId);
}
// ...existing code...
