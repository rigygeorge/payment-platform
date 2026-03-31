-- init-scripts/01-init-databases.sql

-- payment_db
CREATE DATABASE payment_db
    WITH OWNER = payment_user
    ENCODING = 'UTF8';

-- transaction_db 
CREATE DATABASE transaction_db
    WITH OWNER = payment_user
    ENCODING = 'UTF8';

-- merchant_db already created by POSTGRES_DB env var, no need to create again