<?php

declare(strict_types=1);

namespace Cstv\Backend\Catalog;

use Cstv\Backend\Shared\Uuid;

/** Generates CSTV identities; no provider identifier ever escapes this boundary. */
final class ExternalMediaIdFactory
{
    public function create(): string { return Uuid::v4(); }
}
