<?php

declare(strict_types=1);

namespace Cstv\Backend\Catalog;

/**
 * F45 §7.7/§8.8: only this adapter knows TMDB image sizes. The cache/DB layer stores paths only —
 * this resolver turns `(path, context, device)` into the final `image.tmdb.org` URL at
 * serialization time, so `X-CSTV-Device-Type` never needs to fan out the cached payload.
 */
final readonly class TmdbImageUrlResolver
{
    private const BASE = 'https://image.tmdb.org/t/p/';

    /** @var array<string, array<string, string>> */
    private const SIZES = [
        'poster_media' => ['mobile' => 'w780', 'tablet' => 'w780', 'tv' => 'w780'],
        'poster_season' => ['mobile' => 'w500', 'tablet' => 'w780', 'tv' => 'w780'],
        'backdrop' => ['mobile' => 'w1280', 'tablet' => 'original', 'tv' => 'original'],
        'still_episode' => ['mobile' => 'w500', 'tablet' => 'w500', 'tv' => 'w500'],
    ];

    public function resolve(?string $path, ImageContext $context, DeviceType $device): ?string
    {
        if ($path === null || $path === '') return null;
        $size = self::SIZES[$this->contextKey($context)][$device->value];
        return self::BASE . $size . $path;
    }

    private function contextKey(ImageContext $context): string
    {
        return match ($context) {
            ImageContext::PosterMedia => 'poster_media',
            ImageContext::PosterSeason => 'poster_season',
            ImageContext::Backdrop => 'backdrop',
            ImageContext::StillEpisode => 'still_episode',
        };
    }
}
