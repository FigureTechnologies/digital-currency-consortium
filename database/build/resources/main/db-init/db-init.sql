ALTER USER postgres PASSWORD 'password1';
CREATE DATABASE usdfconsortium;
GRANT ALL ON DATABASE usdfconsortium TO postgres;
CREATE SCHEMA IF NOT EXISTS usdfconsortium AUTHORIZATION postgres;
GRANT ALL ON SCHEMA usdfconsortium TO postgres;
