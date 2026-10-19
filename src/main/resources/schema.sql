-- Executed automatically on startup (after Hibernate DDL). Idempotent.
-- Defence in depth: a vehicle can never have two open tickets, even if application locking is bypassed.
CREATE UNIQUE INDEX IF NOT EXISTS uq_open_ticket_per_vehicle
ON ticket (vehicle_id) WHERE status IN ('ACTIVE', 'PAYMENT_PENDING');