-- Smart CSV import: remember the detected header row and multi-column description per profile.
-- Forward-only; existing profiles keep working (NULL header_row_index -> hasHeader; NULL
-- description_indexes -> single description_index).
ALTER TABLE import_profiles
    ADD COLUMN header_row_index  INTEGER,
    ADD COLUMN description_indexes TEXT;
