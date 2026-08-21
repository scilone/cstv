<?php

declare(strict_types=1);

namespace Cstv\Backend\Tests\Integration;

use Cstv\Backend\Database\Migrator;

final class MigrationTest extends IntegrationTestCase
{
    public function testMigrationsBuildAnEmptyPostgresqlSchemaAndAreIdempotent(): void
    {
        $schema = 'migration_test_' . bin2hex(random_bytes(6));
        $this->pdo->exec('CREATE SCHEMA ' . $schema);
        $this->pdo->exec('SET search_path TO ' . $schema);

        try {
            $migrator = new Migrator($this->pdo, dirname(__DIR__, 2) . '/migrations');
            self::assertSame(
                ['001_initial.sql', '002_namespace_snapshots.sql', '003_verify_throttle.sql', '004_account_iptv_credentials.sql', '005_playback_locks.sql', '006_media_metadata_cache.sql', '007_catalog_match_throttle.sql', '008_profile_max_age_rating.sql', '009_external_metadata.sql', '010_catalog_matching.sql', '011_catalog_match_unresolved_status.sql', '012_catalog_genre_cache.sql'],
                $migrator->migrate(),
            );
            self::assertSame([], $migrator->migrate());

            $tables = $this->pdo->query(
                "SELECT tablename FROM pg_tables WHERE schemaname = current_schema() ORDER BY tablename",
            )->fetchAll(\PDO::FETCH_COLUMN);
            self::assertSame(
                ['account_iptv_credentials', 'accounts', 'auth_verify_attempts', 'catalog_genre_cache', 'catalog_match_attempts', 'catalog_provider_rate_limit', 'external_media', 'media_metadata_cache', 'otp_codes', 'playback_locks', 'profile_objects', 'profiles', 'schema_migrations', 'tmdb_episodes', 'tmdb_media', 'tmdb_movies', 'tmdb_seasons', 'tmdb_series'],
                $tables,
            );

            $indexes = $this->pdo->query(
                "SELECT indexname FROM pg_indexes WHERE schemaname = current_schema() ORDER BY indexname",
            )->fetchAll(\PDO::FETCH_COLUMN);
            foreach ([
                'auth_verify_attempts_created_idx',
                'auth_verify_attempts_ip_created_idx',
                'media_metadata_cache_expiry_idx',
                'catalog_match_attempts_account_created_idx',
                'otp_codes_email_created_idx',
                'otp_codes_ip_created_idx',
                'profiles_account_idx',
            ] as $expectedIndex) {
                self::assertContains($expectedIndex, $indexes);
            }

            $columns = $this->pdo->query(
                "SELECT column_name FROM information_schema.columns "
                . "WHERE table_schema = current_schema() AND table_name = 'profile_objects' ORDER BY ordinal_position",
            )->fetchAll(\PDO::FETCH_COLUMN);
            self::assertNotContains('object_key', $columns);
        } finally {
            $this->pdo->exec('SET search_path TO public');
            $this->pdo->exec('DROP SCHEMA ' . $schema . ' CASCADE');
        }
    }
}
