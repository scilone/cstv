ALTER TABLE profiles ADD COLUMN max_age_rating SMALLINT NULL
    CHECK (max_age_rating IN (0, 10, 12, 16, 18));
