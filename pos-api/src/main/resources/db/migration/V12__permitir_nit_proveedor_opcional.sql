-- Mantiene la unicidad de NIT informado, pero permite proveedores sin NIT.
-- PostgreSQL permite multiples valores NULL en una restriccion UNIQUE.

ALTER TABLE proveedores
ALTER COLUMN nit DROP NOT NULL;
