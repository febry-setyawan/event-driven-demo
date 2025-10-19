-- Initialize database schema for Event-Driven Architecture POC

-- Create orders table
CREATE TABLE IF NOT EXISTS orders (
    id BIGSERIAL PRIMARY KEY,
    customer_id VARCHAR(255) NOT NULL,
    product_id VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create payments table
CREATE TABLE IF NOT EXISTS payments (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_orders_customer_id ON orders(customer_id);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_payments_order_id ON payments(order_id);
CREATE INDEX IF NOT EXISTS idx_payments_status ON payments(status);

-- Unique constraint for idempotency
CREATE UNIQUE INDEX IF NOT EXISTS idx_payments_order_id_unique ON payments(order_id);

-- Create saga_state table for distributed transaction management
CREATE TABLE IF NOT EXISTS saga_state (
    id BIGSERIAL PRIMARY KEY,
    saga_id VARCHAR(36) NOT NULL UNIQUE,
    order_id BIGINT NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL,
    current_step VARCHAR(50),
    payment_id BIGINT,
    timeout_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for saga_state
CREATE INDEX IF NOT EXISTS idx_saga_state_saga_id ON saga_state(saga_id);
CREATE INDEX IF NOT EXISTS idx_saga_state_order_id ON saga_state(order_id);
CREATE INDEX IF NOT EXISTS idx_saga_state_status ON saga_state(status);

-- Create saga_events table for audit trail
CREATE TABLE IF NOT EXISTS saga_events (
    id BIGSERIAL PRIMARY KEY,
    saga_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    event_data TEXT,
    status VARCHAR(20) DEFAULT 'LOGGED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for saga_events
CREATE INDEX IF NOT EXISTS idx_saga_events_saga_id ON saga_events(saga_id);
CREATE INDEX IF NOT EXISTS idx_saga_events_created_at ON saga_events(created_at);

-- Insert sample data for testing
INSERT INTO orders (customer_id, product_id, quantity, amount, status) VALUES
('customer-001', 'product-001', 2, 99.99, 'COMPLETED'),
('customer-002', 'product-002', 1, 149.99, 'PENDING'),
('customer-003', 'product-001', 3, 299.97, 'COMPLETED')
ON CONFLICT DO NOTHING;

INSERT INTO payments (order_id, amount, status) VALUES
(1, 99.99, 'COMPLETED'),
(3, 299.97, 'COMPLETED')
ON CONFLICT DO NOTHING;

INSERT INTO saga_state (saga_id, order_id, status, current_step, payment_id) VALUES
('00000000-0000-0000-0000-000000000001', 1, 'COMPLETED', 'PAYMENT_COMPLETED', 1),
('00000000-0000-0000-0000-000000000003', 3, 'COMPLETED', 'PAYMENT_COMPLETED', 2)
ON CONFLICT DO NOTHING;
