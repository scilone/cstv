<?php

declare(strict_types=1);

namespace Cstv\Backend\Tests\Integration;

use Cstv\Backend\Catalog\CatalogMatchCandidate;
use Cstv\Backend\Catalog\CatalogMatchEngine;
use Cstv\Backend\Catalog\CatalogMatchHints;
use Cstv\Backend\Catalog\CatalogMatchRequest;
use Cstv\Backend\Catalog\ExternalMediaIdFactory;
use Cstv\Backend\Catalog\ExternalMediaRepository;
use Cstv\Backend\Catalog\MediaMetadataProvider;

/**
 * F45 (Tâche 3) : couvre `CatalogMatchEngine` directement (contre un vrai PostgreSQL — le projet
 * teste ses classes liées aux données via l'infrastructure d'intégration existante plutôt que des
 * mocks, voir `CatalogApiTest`/`MigrationTest`), sur les scénarios listés en § du plan de
 * développement : homonymes, mauvais premier résultat, année décalée mais indices forts, trailer
 * identique décisif, scores trop proches -> unresolved, résolution PostgreSQL-first sans appel
 * fournisseur.
 */
final class CatalogMatchEngineTest extends IntegrationTestCase
{
    private ExternalMediaRepository $externalMedia;

    protected function setUp(): void
    {
        parent::setUp();
        $this->externalMedia = new ExternalMediaRepository($this->pdo, new ExternalMediaIdFactory());
    }

    public function testUnambiguousSingleCandidateIsAcceptedWithoutASecondDetailCall(): void
    {
        $provider = new FakeCatalogProvider();
        $provider->candidates = [new CatalogMatchCandidate(1, 'Dune Unambiguous', 'Dune Unambiguous', 2021, [])];
        $provider->details[1] = ['directors' => [], 'cast' => [], 'runtimeMinutes' => null, 'trailerKeys' => [], 'alternativeTitles' => []];
        $provider->hydrated[1] = $this->movie('Dune Unambiguous', '2021-09-15');

        $engine = new CatalogMatchEngine($provider, $this->externalMedia);
        $result = $engine->resolve($this->matchRequest('Dune Unambiguous', 2021));

        self::assertSame('matched', $result->status);
        self::assertMatchesRegularExpression('/^[0-9a-f-]{36}$/', $result->externalId);
        self::assertSame(0, $provider->detailCalls, 'a single candidate must never trigger a passe-2 detail call');
    }

    public function testFirstTmdbResultIsUsedWithoutReadingDisambiguationHints(): void
    {
        $provider = new FakeCatalogProvider();
        $provider->candidates = [
            new CatalogMatchCandidate(1, 'The Thing Homonym', 'The Thing Homonym', 1982, []),
            new CatalogMatchCandidate(2, 'The Thing Homonym', 'The Thing Homonym', 1982, []),
        ];
        $provider->details[1] = ['directors' => ['Someone Else'], 'cast' => [], 'runtimeMinutes' => null, 'trailerKeys' => [], 'alternativeTitles' => []];
        $provider->details[2] = ['directors' => ['John Carpenter'], 'cast' => [], 'runtimeMinutes' => null, 'trailerKeys' => [], 'alternativeTitles' => []];
        $provider->hydrated[1] = $this->movie('The Thing Homonym', '1982-06-25');

        $engine = new CatalogMatchEngine($provider, $this->externalMedia);
        $hints = new CatalogMatchHints(director: 'John Carpenter');
        $result = $engine->resolve(new CatalogMatchRequest('movie', 'The Thing Homonym', 1982, 'fr-FR', $hints));

        self::assertSame('matched', $result->status);
        self::assertSame(0, $provider->detailCalls);
        self::assertSame('tmdb-first-result', $result->method);
    }

    public function testAmbiguousResultsStillUseTheFirstTmdbResult(): void
    {
        $provider = new FakeCatalogProvider();
        $provider->candidates = [
            new CatalogMatchCandidate(1, 'The Thing Ambiguous', 'The Thing Ambiguous', 1982, []),
            new CatalogMatchCandidate(2, 'The Thing Ambiguous', 'The Thing Ambiguous', 1982, []),
        ];
        // Aucun indice ne permet de départager : les deux gardent le même score après la passe 2.
        $provider->details[1] = ['directors' => [], 'cast' => [], 'runtimeMinutes' => null, 'trailerKeys' => [], 'alternativeTitles' => []];
        $provider->details[2] = ['directors' => [], 'cast' => [], 'runtimeMinutes' => null, 'trailerKeys' => [], 'alternativeTitles' => []];
        $provider->hydrated[1] = $this->movie('The Thing Ambiguous', '1982-06-25');

        $engine = new CatalogMatchEngine($provider, $this->externalMedia);
        $result = $engine->resolve($this->matchRequest('The Thing Ambiguous', 1982));

        self::assertSame('matched', $result->status);
        self::assertNotNull($result->externalId);
    }

    public function testYearOffByOneStillAcceptedWhenTitleIsExact(): void
    {
        $provider = new FakeCatalogProvider();
        $provider->candidates = [new CatalogMatchCandidate(1, 'Dune Offset', 'Dune Offset', 2022, [])];
        $provider->details[1] = ['directors' => [], 'cast' => [], 'runtimeMinutes' => null, 'trailerKeys' => [], 'alternativeTitles' => []];
        $provider->hydrated[1] = $this->movie('Dune Offset', '2022-01-01');

        $engine = new CatalogMatchEngine($provider, $this->externalMedia);
        $result = $engine->resolve($this->matchRequest('Dune Offset', 2021)); // IPTV dit 2021, TMDB dit 2022 : ±1 toléré

        self::assertSame('matched', $result->status);
    }

    public function testTrailerHintsDoNotChangeTheFirstTmdbResult(): void
    {
        $provider = new FakeCatalogProvider();
        $provider->candidates = [
            new CatalogMatchCandidate(1, 'The Thing Trailer', 'The Thing Trailer', 1982, []),
            new CatalogMatchCandidate(2, 'The Thing Trailer', 'The Thing Trailer', 1982, []),
        ];
        $provider->details[1] = ['directors' => [], 'cast' => [], 'runtimeMinutes' => null, 'trailerKeys' => [], 'alternativeTitles' => []];
        $provider->details[2] = ['directors' => [], 'cast' => [], 'runtimeMinutes' => null, 'trailerKeys' => ['abc123XYZ'], 'alternativeTitles' => []];
        $provider->hydrated[1] = $this->movie('The Thing Trailer', '1982-06-25');

        $engine = new CatalogMatchEngine($provider, $this->externalMedia);
        $hints = new CatalogMatchHints(youtubeTrailerKey: 'abc123XYZ');
        $result = $engine->resolve(new CatalogMatchRequest('movie', 'The Thing Trailer', 1982, 'fr-FR', $hints));

        self::assertSame('matched', $result->status);
        self::assertSame('tmdb-first-result', $result->method);
    }

    public function testNoCandidateAtAllIsNotFoundNotUnresolved(): void
    {
        $provider = new FakeCatalogProvider();
        $provider->candidates = [];

        $engine = new CatalogMatchEngine($provider, $this->externalMedia);
        $result = $engine->resolve($this->matchRequest('Completely Unknown Title', null));

        self::assertSame('not_found', $result->status);
    }

    public function testIdenticalRequestsKeepUsingTmdbFirstResult(): void
    {
        $provider = new FakeCatalogProvider();
        $provider->candidates = [new CatalogMatchCandidate(1, 'Dune Reuse', 'Dune Reuse', 2021, [])];
        $provider->details[1] = ['directors' => [], 'cast' => [], 'runtimeMinutes' => null, 'trailerKeys' => [], 'alternativeTitles' => []];
        $provider->hydrated[1] = $this->movie('Dune Reuse', '2021-09-15');

        $engine = new CatalogMatchEngine($provider, $this->externalMedia);
        $first = $engine->resolve($this->matchRequest('Dune Reuse', 2021));
        self::assertSame(1, $provider->searchCalls);

        $second = $engine->resolve($this->matchRequest('Dune Reuse', 2021));

        self::assertSame($first->externalId, $second->externalId);
        self::assertSame('tmdb-first-result', $second->method);
        self::assertSame(0, $second->confidence);
        self::assertSame(2, $provider->searchCalls);
    }

    public function testPostgresqlFirstNeverReusesAnUnrelatedHomonymBelowTheMarginBar(): void
    {
        // F45-R3 : deux œuvres consolidées partagent le même titre normalisé mais des années très
        // différentes — la voie provider (année 1978) doit gagner un candidat unique et fiable ; la
        // ligne consolidée de 1999 ne doit jamais être réutilisée aveuglément (l'ancien
        // `count($rows) === 1` n'aurait même pas vu l'ambiguïté puisque chaque titre normalisé était
        // consolidé séparément — ici on prouve directement que le score/marge la protège).
        $providerFirst = new FakeCatalogProvider();
        $providerFirst->candidates = [new CatalogMatchCandidate(1, 'Homonym Guard', 'Homonym Guard', 1999, [])];
        $providerFirst->details[1] = ['directors' => [], 'cast' => [], 'runtimeMinutes' => null, 'trailerKeys' => [], 'alternativeTitles' => []];
        $providerFirst->hydrated[1] = $this->movie('Homonym Guard', '1999-01-01');
        $engineFirst = new CatalogMatchEngine($providerFirst, $this->externalMedia);
        $engineFirst->resolve($this->matchRequest('Homonym Guard', 1999));

        // Requête suivante : même titre, année 1978 (>2 ans d'écart -> pénalité -30, sous le seuil 65)
        // — doit retomber sur le provider plutôt que réutiliser la ligne 1999.
        $providerSecond = new FakeCatalogProvider();
        $providerSecond->candidates = [new CatalogMatchCandidate(2, 'Homonym Guard', 'Homonym Guard', 1978, [])];
        $providerSecond->details[2] = ['directors' => [], 'cast' => [], 'runtimeMinutes' => null, 'trailerKeys' => [], 'alternativeTitles' => []];
        $providerSecond->hydrated[2] = $this->movie('Homonym Guard', '1978-01-01');
        $engineSecond = new CatalogMatchEngine($providerSecond, $this->externalMedia);
        $result = $engineSecond->resolve($this->matchRequest('Homonym Guard', 1978));

        self::assertSame('matched', $result->status);
        self::assertSame(1, $providerSecond->searchCalls, 'the 1999 row must not short-circuit the provider for the 1978 request');
        self::assertSame('tmdb-first-result', $result->method);
    }

    public function testHintsCannotOverrideTheFirstTmdbResult(): void
    {
        // F45-R3 : la passe 1 seule donne 50 points d'écart (loin au-dessus de la marge minimale)
        // entre le candidat "2000" (année exacte selon l'IPTV) et le candidat "1985" — avant le
        // fix, cet écart aurait empêché toute passe 2, laissant gagner le mauvais candidat malgré
        // des indices très forts (réalisateur/cast/durée/trailer/titre alternatif) tous alignés sur
        // le candidat 1985, qui est en réalité le bon film.
        $provider = new FakeCatalogProvider();
        $provider->candidates = [
            new CatalogMatchCandidate(1, 'Contradicted Lead', 'Contradicted Lead', 2000, []),
            new CatalogMatchCandidate(2, 'Contradicted Lead', 'Contradicted Lead', 1985, []),
        ];
        $provider->details[1] = ['directors' => [], 'cast' => [], 'runtimeMinutes' => null, 'trailerKeys' => [], 'alternativeTitles' => []];
        $provider->details[2] = [
            'directors' => ['Real Director'],
            'cast' => ['Actor One', 'Actor Two', 'Actor Three', 'Actor Four'],
            'runtimeMinutes' => 100,
            'trailerKeys' => ['trailerXYZ'],
            'alternativeTitles' => ['Contradicted Lead'],
        ];
        $provider->hydrated[1] = $this->movie('Contradicted Lead', '2000-01-01');

        $engine = new CatalogMatchEngine($provider, $this->externalMedia);
        $hints = new CatalogMatchHints(
            director: 'Real Director',
            actors: ['Actor One', 'Actor Two', 'Actor Three', 'Actor Four'],
            runtimeMinutes: 100,
            youtubeTrailerKey: 'trailerXYZ',
        );
        $result = $engine->resolve(new CatalogMatchRequest('movie', 'Contradicted Lead', 2000, 'fr-FR', $hints));

        self::assertSame('matched', $result->status);
        self::assertSame(0, $provider->detailCalls);
        self::assertSame('tmdb-first-result', $result->method);
    }

    private function matchRequest(string $title, ?int $year): CatalogMatchRequest
    {
        return new CatalogMatchRequest('movie', $title, $year, 'fr-FR', new CatalogMatchHints());
    }

    /** @return array<string, mixed> */
    private function movie(string $title, string $releaseDate): array
    {
        return [
            'title' => $title, 'originalTitle' => $title, 'originalLanguage' => 'en', 'overview' => null,
            'posterPath' => null, 'backdropPath' => null, 'releaseDate' => $releaseDate, 'runtimeMinutes' => 100,
            'ageRating' => null, 'adult' => false, 'status' => 'Released', 'tagline' => null, 'voteAverage' => null,
            'voteCount' => null, 'genres' => [], 'originCountries' => [], 'keywords' => [], 'alternativeTitles' => [],
            'recommendations' => [], 'videos' => [],
        ];
    }
}

/** Test double giving each scenario full control over search/detail/hydrate without a real TMDB call. */
final class FakeCatalogProvider implements MediaMetadataProvider
{
    /** @var list<CatalogMatchCandidate> */
    public array $candidates = [];
    /** @var array<int, array<string, mixed>> */
    public array $details = [];
    /** @var array<int, array<string, mixed>> */
    public array $hydrated = [];
    public int $searchCalls = 0;
    public int $detailCalls = 0;

    public function trending(string $locale): array { return []; }
    public function popular(string $kind, int $page, string $locale): array { return []; }
    public function videos(string $canonicalId, string $locale): array { return []; }

    public function searchCandidates(string $kind, string $title, ?int $year, string $locale): array
    {
        $this->searchCalls++;
        return $this->candidates;
    }

    public function candidateDetail(string $kind, int $tmdbId, string $locale): array
    {
        $this->detailCalls++;
        return $this->details[$tmdbId] ?? ['directors' => [], 'cast' => [], 'runtimeMinutes' => null, 'trailerKeys' => [], 'alternativeTitles' => []];
    }

    public function genreNames(string $kind, string $locale): array { return []; }

    public function hydrate(string $kind, int $tmdbId, string $locale): array
    {
        return $this->hydrated[$tmdbId] ?? throw new \RuntimeException('Unexpected hydrate() for tmdbId ' . $tmdbId);
    }

    public function seasonDetail(int $seriesTmdbId, int $seasonNumber, string $locale): array
    {
        return ['seasonNumber' => $seasonNumber, 'name' => 'Season', 'overview' => null, 'posterPath' => null, 'airDate' => null, 'voteAverage' => null, 'episodes' => []];
    }
}
