-- Creates the dedicated database for the Gorse recommender.
-- Runs only on first boot of an empty data volume (docker-entrypoint-initdb.d contract).
-- For an already-initialized volume run manually:
--   docker compose exec postgres psql -U "$POSTGRES_USER" -c 'CREATE DATABASE gorse'
CREATE DATABASE gorse;
