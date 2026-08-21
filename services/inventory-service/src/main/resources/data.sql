INSERT INTO inventory (product_id, available_quantity, updated_at)
VALUES ('product-1', 100, CURRENT_TIMESTAMP)
ON CONFLICT (product_id) DO NOTHING;