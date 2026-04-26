package edu.iuh.fit.se.commonservice.service.impl;

import edu.iuh.fit.se.commonservice.dto.CreateProductRequest;
import edu.iuh.fit.se.commonservice.dto.ProductDto;
import edu.iuh.fit.se.commonservice.dto.SimpleUserDto;
import edu.iuh.fit.se.commonservice.dto.UpdateProductRequest;
import edu.iuh.fit.se.commonservice.model.Product;
import edu.iuh.fit.se.commonservice.client.AuthServiceClient;
import edu.iuh.fit.se.commonservice.dto.UserDTO;
import edu.iuh.fit.se.commonservice.repository.ProductRepository;
import edu.iuh.fit.se.commonservice.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final AuthServiceClient authServiceClient;

    @Override
    public ProductDto createProduct(CreateProductRequest request, String sellerId) {
        UserDTO seller = null;
        try {
            seller = authServiceClient.getUserById(sellerId);
        } catch (Exception e) {}
        Product p = new Product();
        p.setTitle(request.getTitle());
        p.setDescription(request.getDescription());
        p.setPrice(request.getPrice());
        p.setCurrency(request.getCurrency() == null ? "USD" : request.getCurrency());
        p.setCondition(request.getCondition());
        p.setLocation(request.getLocation());
        p.setAddress(request.getAddress());
        p.setImages(request.getImages());
        p.setTags(request.getTags());
        p.setCategory(request.getCategory());
        p.setSellerId(sellerId);
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        Product saved = productRepository.save(p);
        return toDto(saved);
    }

    @Override
    public ProductDto updateProduct(String productId, UpdateProductRequest request, String sellerId) {
        Product p = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        if (p.getSellerId() == null || !p.getSellerId().equals(sellerId)) {
            throw new RuntimeException("Not allowed to update this product");
        }
        if (request.getTitle() != null) p.setTitle(request.getTitle());
        if (request.getDescription() != null) p.setDescription(request.getDescription());
        if (request.getPrice() != null) p.setPrice(request.getPrice());
        if (request.getCurrency() != null) p.setCurrency(request.getCurrency());
        if (request.getCondition() != null) p.setCondition(request.getCondition());
        if (request.getLocation() != null) p.setLocation(request.getLocation());
        if (request.getAddress() != null) p.setAddress(request.getAddress());
        if (request.getImages() != null) p.setImages(request.getImages());
        if (request.getTags() != null) p.setTags(request.getTags());
        if (request.getCategory() != null) p.setCategory(request.getCategory());
        if (request.getIsActive() != null) p.setIsActive(request.getIsActive());
        if (request.getIsSold() != null) p.setIsSold(request.getIsSold());
        p.setUpdatedAt(LocalDateTime.now());
        Product saved = productRepository.save(p);
        return toDto(saved);
    }

    @Override
    public void deleteProduct(String productId, String sellerId) {
        Product p = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        if (p.getSellerId() == null || !p.getSellerId().equals(sellerId)) {
            throw new RuntimeException("Not allowed to delete this product");
        }
        productRepository.delete(p);
    }

    @Override
    public ProductDto getProductById(String id) {
        Product p = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        return toDto(p);
    }

    @Override
    public Page<ProductDto> getAllProducts(int page, int size, String category, String search, String sortBy, String sortOrder) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));

        List<Product> filtered = productRepository.findAll().stream()
                // loại bỏ product đã sold
                .filter(p -> p.getIsSold() != null && !p.getIsSold())

                // filter category
                .filter(p -> (category == null || category.isBlank() ||
                        (p.getCategory() != null && p.getCategory().equalsIgnoreCase(category))))

                // filter search
                .filter(p -> (search == null || search.isBlank() ||
                        ((p.getTitle() != null && p.getTitle().toLowerCase().contains(search.toLowerCase())) ||
                                (p.getDescription() != null && p.getDescription().toLowerCase().contains(search.toLowerCase())))))

                .collect(Collectors.toList());

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), filtered.size());

        List<ProductDto> dtos = filtered.subList(start, end)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return new PageImpl<>(dtos, pageable, filtered.size());
    }

    @Override
    public Page<ProductDto> getUserProducts(String userId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
        List<Product> list = productRepository.findBySellerId(userId);
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), list.size());
        List<ProductDto> dtos = list.subList(start, end).stream().map(this::toDto).collect(Collectors.toList());
        return new PageImpl<>(dtos, pageable, list.size());
    }

    @Override
    public ProductDto markAsSold(String productId, String sellerId) {
        Product p = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        if (p.getSellerId() == null || !p.getSellerId().equals(sellerId)) {
            throw new RuntimeException("Not allowed to mark as sold");
        }
        p.setIsSold(Boolean.TRUE);
        p.setUpdatedAt(LocalDateTime.now());
        Product saved = productRepository.save(p);
        return toDto(saved);
    }

    private ProductDto toDto(Product p) {
        ProductDto dto = new ProductDto();
        dto.setId(p.getId());
        dto.setTitle(p.getTitle());
        dto.setDescription(p.getDescription());
        if (p.getSellerId() != null) {
            try {
                UserDTO seller = authServiceClient.getUserById(p.getSellerId());
                SimpleUserDto su = new SimpleUserDto();
                su.setId(seller.getId());
                su.setFullName(seller.getFullName() != null ? seller.getFullName() : seller.getUsername());
                su.setAvatar(seller.getAvatar());
                dto.setSeller(su);
            } catch (Exception e) {
                SimpleUserDto su = new SimpleUserDto();
                su.setId(p.getSellerId());
                su.setFullName("Unknown");
                dto.setSeller(su);
            }
        }
        dto.setPrice(p.getPrice());
        dto.setCurrency(p.getCurrency());
        dto.setCondition(p.getCondition());
        dto.setLocation(p.getLocation());
        dto.setAddress(p.getAddress());
        dto.setImages(p.getImages());
        dto.setTags(p.getTags());
        dto.setCategory(p.getCategory());
        dto.setSold(p.getIsSold() != null && p.getIsSold());
        dto.setActive(p.getIsActive() != null && p.getIsActive());
        dto.setCreatedAt(p.getCreatedAt());
        dto.setUpdatedAt(p.getUpdatedAt());
        return dto;
    }
}
