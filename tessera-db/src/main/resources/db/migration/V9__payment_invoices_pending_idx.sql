CREATE INDEX idx_invoices_pending_created ON payment_invoices(status, created_at) WHERE status = 'PENDING';
