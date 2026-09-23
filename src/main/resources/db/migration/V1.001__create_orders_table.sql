CREATE TABLE orders (
                        id UUID NOT NULL,
                        customer_id UUID NOT NULL,
                        status VARCHAR(30) NOT NULL,
                        amount NUMERIC(19, 4) NOT NULL,
                        version BIGINT NOT NULL DEFAULT 0,
                        created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

                        CONSTRAINT pk_orders PRIMARY KEY (id),
                        CONSTRAINT chk_orders_amount_positive CHECK (amount > 0)
);

-- Create indexes for frequent lookups
CREATE INDEX idx_orders_customer_id ON orders(customer_id);
CREATE INDEX idx_orders_status ON orders(status);