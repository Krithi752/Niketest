package com.catalog.config;

import com.catalog.model.Product;
import com.catalog.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Seeds sample catalog data on startup if the database is empty so reviewers
 * can immediately benchmark filtering and cursor pagination at scale.
 */
@Component
public class CatalogDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CatalogDataSeeder.class);

    private static final List<String> CATEGORIES = List.of(
            "running-shoes", "apparel", "accessories", "basketball", "training"
    );

    private static final List<String> ADJECTIVES = List.of(
            "Air", "Zoom", "Pro", "Elite", "Flex", "Aero", "Velocity", "Court"
    );

    private final ProductRepository productRepository;
    private final boolean seedEnabled;
    private final int seedCount;

    public CatalogDataSeeder(
            ProductRepository productRepository,
            @Value("${app.seed.enabled:true}") boolean seedEnabled,
            @Value("${app.seed.count:10000}") int seedCount
    ) {
        this.productRepository = productRepository;
        this.seedEnabled = seedEnabled;
        this.seedCount = seedCount;
    }

    @Override
    public void run(String... args) {
        if (!seedEnabled || productRepository.count() > 0) {
            return;
        }

        log.info("Seeding {} sample catalog products...", seedCount);
        Random random = new Random(42);
        List<Product> batch = new ArrayList<>(1000);

        for (int i = 1; i <= seedCount; i++) {
            String category = CATEGORIES.get(i % CATEGORIES.size());
            String adj = ADJECTIVES.get(random.nextInt(ADJECTIVES.size()));
            double rawPrice = 19.99 + (random.nextDouble() * 280.0);

            Product p = new Product();
            p.setSku(String.format("SKU-%06d", i));
            p.setName(adj + " " + category + " Edition " + i);
            p.setDescription("High-performance " + category + " product engineered for durability and comfort.");
            p.setCategory(category);
            p.setPrice(BigDecimal.valueOf(rawPrice).setScale(2, RoundingMode.HALF_UP));
            p.setStockQuantity(random.nextInt(500));
            batch.add(p);

            if (batch.size() == 1000) {
                productRepository.saveAll(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            productRepository.saveAll(batch);
        }
        log.info("Completed seeding {} products.", seedCount);
    }
}
