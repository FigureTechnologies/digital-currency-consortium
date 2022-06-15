ALTER USER postgres PASSWORD 'password1';

CREATE DATABASE digital_currency_consortium;
GRANT ALL ON DATABASE digital_currency_consortium TO postgres;
CREATE SCHEMA IF NOT EXISTS digital_currency_consortium AUTHORIZATION postgres;
GRANT ALL ON SCHEMA digital_currency_consortium TO postgres;

CREATE DATABASE digital_currency_report;
GRANT ALL ON DATABASE digital_currency_report TO postgres;
CREATE SCHEMA IF NOT EXISTS digital_currency_report AUTHORIZATION postgres;
GRANT ALL ON SCHEMA digital_currency_report TO postgres;
