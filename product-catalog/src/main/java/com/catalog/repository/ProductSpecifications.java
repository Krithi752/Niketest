package com.catalog.repository;

import com.catalog.model.Product;
import com.catalog.util.CursorCodec.KeysetCursor;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class ProductSpecifications {

    private ProductSpecifications() {}

    public static Specification<Product> withFilters(
            String category,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            KeysetCursor cursor
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Only return active (non-deleted) products
            predicates.add(cb.isTrue(root.get("active")));

            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("category")), category.trim().toLowerCase()));
            }

            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), minPrice));
            }

            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), maxPrice));
            }

            // Keyset / cursor condition: (price > lastPrice) OR (price = lastPrice AND id > lastId)
            if (cursor != null) {
                Predicate priceGreater = cb.greaterThan(root.get("price"), cursor.price());
                Predicate priceEqualAndIdGreater = cb.and(
                        cb.equal(root.get("price"), cursor.price()),
                        cb.greaterThan(root.get("id"), cursor.id())
                );
                predicates.add(cb.or(priceGreater, priceEqualAndIdGreater));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
