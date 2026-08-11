<?php

declare(strict_types=1);

namespace Cstv\Backend\Database;

use PDO;
use RuntimeException;
use Throwable;

final readonly class Migrator
{
    public function __construct(private PDO $pdo, private string $migrationDirectory)
    {
    }

    /** @return list<string> */
    public function migrate(): array
    {
        $this->pdo->exec(
            'CREATE TABLE IF NOT EXISTS schema_migrations ('
            . 'version VARCHAR(255) PRIMARY KEY, applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW())',
        );

        $files = glob(rtrim($this->migrationDirectory, '/') . '/*.sql');
        if ($files === false) {
            throw new RuntimeException('Unable to read migration directory.');
        }
        sort($files, SORT_STRING);

        $applied = [];
        foreach ($files as $file) {
            $version = basename($file);
            $check = $this->pdo->prepare('SELECT 1 FROM schema_migrations WHERE version = :version');
            $check->execute(['version' => $version]);
            if ($check->fetchColumn() !== false) {
                continue;
            }

            $sql = file_get_contents($file);
            if ($sql === false) {
                throw new RuntimeException(sprintf('Unable to read migration %s.', $version));
            }

            $this->pdo->beginTransaction();
            try {
                $this->pdo->exec("SELECT pg_advisory_xact_lock(1122334455)");

                $recheck = $this->pdo->prepare('SELECT 1 FROM schema_migrations WHERE version = :version');
                $recheck->execute(['version' => $version]);
                if ($recheck->fetchColumn() === false) {
                    $this->pdo->exec($sql);
                    $insert = $this->pdo->prepare('INSERT INTO schema_migrations (version) VALUES (:version)');
                    $insert->execute(['version' => $version]);
                    $applied[] = $version;
                }
                $this->pdo->commit();
            } catch (Throwable $exception) {
                if ($this->pdo->inTransaction()) {
                    $this->pdo->rollBack();
                }
                throw $exception;
            }
        }

        return $applied;
    }
}
