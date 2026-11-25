CREATE TABLE products (
    id UUID PRIMARY KEY,
    sku VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    category VARCHAR(100) NOT NULL,
    price DECIMAL(12, 2) NOT NULL CHECK (price >= 0),
    stock_quantity INT NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Partial composite B-tree index tailored for read-heavy category + price filtering & deterministic keyset pagination
CREATE INDEX idx_products_active_cat_price_id
    ON products (category, price, id)
    WHERE active = TRUE;

-- Fallback index for global price-sorted queries without a category filter
CREATE INDEX idx_products_active_price_id
    ON products (price, id)
    WHERE active = TRUE;
