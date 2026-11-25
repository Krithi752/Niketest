package com.catalog.repository;

import com.catalog.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    Optional<Product> findByIdAndActiveTrue(UUID id);

    boolean existsBySkuAndActiveTrue(String sku);

    boolean existsBySkuAndIdNotAndActiveTrue(String sku, UUID id);
}
