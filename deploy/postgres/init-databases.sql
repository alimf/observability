-- Database per service: each service owns its data and can only log in to its own database.
-- Runs once, when the postgres-data volume is first created. Passwords are local-dev defaults;
-- the services read DB_USERNAME / DB_PASSWORD, so override both in any shared environment.

CREATE ROLE user_service LOGIN PASSWORD 'user_service';
CREATE
DATABASE user_db OWNER user_service;

CREATE ROLE order_service LOGIN PASSWORD 'order_service';
CREATE
DATABASE order_db OWNER order_service;

-- New databases are connectable by PUBLIC by default; restrict them to their owners.
REVOKE CONNECT ON DATABASE user_db FROM PUBLIC;
REVOKE CONNECT ON DATABASE order_db FROM PUBLIC;
