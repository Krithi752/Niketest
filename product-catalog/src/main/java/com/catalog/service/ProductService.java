package com.catalog.service;

import com.catalog.dto.CursorPageResponse;
import com.catalog.dto.ProductRequest;
import com.catalog.dto.ProductResponse;
import com.catalog.exception.BadRequestException;
import com.catalog.exception.ConflictException;
import com.catalog.exception.ResourceNotFoundException;
import com.catalog.model.Product;
import com.catalog.repository.ProductRepository;
import com.catalog.repository.ProductSpecifications;
import com.catalog.util.CursorCodec;
import com.catalog.util.CursorCodec.KeysetCursor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class ProductService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    @CachePut(value = "productById", key = "#result.id()")
    public ProductResponse createProduct(ProductRequest request) {
        String normalizedSku = request.sku().trim().toUpperCase();
        if (productRepository.existsBySkuAndActiveTrue(normalizedSku)) {
            throw new ConflictException("Active product with SKU '" + normalizedSku + "' already exists");
        }

        Product product = new Product();
        product.setSku(normalizedSku);
        product.setName(request.name().trim());
        product.setDescription(request.description());
        product.setCategory(request.category().trim().toLowerCase());
        product.setPrice(request.price());
        product.setStockQuantity(request.stockQuantity());

        Product saved = productRepository.save(product);
        return ProductResponse.from(saved);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "productById", key = "#id")
    public ProductResponse getProductById(UUID id) {
        Product product = productRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        return ProductResponse.from(product);
    }

    @Transactional(readOnly = true)
    @Cacheable(
            value = "productSearch",
            key = "T(java.util.Objects).hash(#category, #minPrice, #maxPrice, #cursor, #limit)"
    )
    public CursorPageResponse<ProductResponse> listProducts(
            String category,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String cursor,
            Integer limit
    ) {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BadRequestException("minPrice cannot be greater than maxPrice");
        }

        int pageSize = (limit == null) ? DEFAULT_LIMIT : Math.min(Math.max(limit, 1), MAX_LIMIT);
        KeysetCursor decodedCursor = (cursor != null && !cursor.isBlank()) ? CursorCodec.decode(cursor) : null;

        // Fetch pageSize + 1 records to accurately determine if another page exists without a COUNT(*) query
        PageRequest pageable = PageRequest.of(
                0,
                pageSize + 1,
                Sort.by(Sort.Order.asc("price"), Sort.Order.asc("id"))
        );

        List<Product> results = productRepository.findAll(
                ProductSpecifications.withFilters(category, minPrice, maxPrice, decodedCursor),
                pageable
        ).getContent();

        boolean hasMore = results.size() > pageSize;
        List<Product> pageItems = hasMore ? results.subList(0, pageSize) : results;

        String nextCursor = null;
        if (hasMore && !pageItems.isEmpty()) {
            Product last = pageItems.getLast();
            nextCursor = CursorCodec.encode(last.getPrice(), last.getId());
        }

        List<ProductResponse> responses = pageItems.stream()
                .map(ProductResponse::from)
                .toList();

        return new CursorPageResponse<>(responses, nextCursor, hasMore, pageSize);
    }

    @Transactional
    @CachePut(value = "productById", key = "#id")
    public ProductResponse updateProduct(UUID id, ProductRequest request) {
        Product existing = productRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        String normalizedSku = request.sku().trim().toUpperCase();
        if (productRepository.existsBySkuAndIdNotAndActiveTrue(normalizedSku, id)) {
            throw new ConflictException("Another active product already uses SKU '" + normalizedSku + "'");
        }

        existing.setSku(normalizedSku);
        existing.setName(request.name().trim());
        existing.setDescription(request.description());
        existing.setCategory(request.category().trim().toLowerCase());
        existing.setPrice(request.price());
        existing.setStockQuantity(request.stockQuantity());

        Product updated = productRepository.save(existing);
        return ProductResponse.from(updated);
    }

    @Transactional
    @CacheEvict(value = "productById", key = "#id")
    public void deleteProduct(UUID id) {
        Product existing = productRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        // Soft-delete preserves referential integrity for historical orders/analytics
        existing.setActive(false);
        productRepository.save(existing);
    }
}
