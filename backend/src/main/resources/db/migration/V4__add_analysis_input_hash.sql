ALTER TABLE analyses
    ADD COLUMN input_hash VARCHAR(64);

ALTER TABLE analyses
    ADD CONSTRAINT chk_analyses_input_hash_length
        CHECK (input_hash IS NULL OR char_length(input_hash) = 64);

CREATE UNIQUE INDEX uq_analyses_input_fingerprint
    ON analyses (input_hash, provider, model, prompt_version)
    WHERE input_hash IS NOT NULL;

-- Existing analyses cannot be backfilled safely because their source records may
-- have changed since creation. New analyses always receive a SHA-256 input hash.
