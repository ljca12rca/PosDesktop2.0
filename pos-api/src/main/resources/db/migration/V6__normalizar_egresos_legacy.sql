UPDATE cierres_diarios
SET egresos = 0
WHERE egresos IS NULL;

ALTER TABLE cierres_diarios
ALTER COLUMN egresos SET DEFAULT 0,
ALTER COLUMN egresos SET NOT NULL;
