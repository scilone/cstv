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
 * mocks, voir `CatalogApiTest`/`MigrationTest`), sur les scénarios du plan de développement :
 * homonymes, résolution TMDB premier résultat, réutilisation directe.
 *
 * T28 : ajoute le backend-first PostgreSQL (§8.2, unicité titre/titre alternatif + année exacte,
 * R1-R8) et la réutilisation d'une fiche déjà hydratée après un `/search` TMDB (§8.3), avec
 * assertions explicites sur `searchCalls`/`hydrateCalls` (§10 Tâche 3-5, Tâche 8).
 */
final class CatalogMatchEngineTest extends IntegrationTestCase
{
    private ExternalMediaRepository $externalMedia;

    protected function setUp(): void
    {
        parent::setUp();
        $this->externalMedia = new ExternalMediaRepository($this->pdo, new ExternalMediaIdFactory());
        // T28 : `TestDatabase::reset()` ne vide pas les tables catalogue (partagées, coûteuses à
        // re-seed) — nécessaire ici car le backend-first (§8.2) lit désormais `tmdb_movies`/
        // `tmdb_series` par titre, et les fixtures d'un test ne doivent jamais être visibles par un
        // autre.
        $this->pdo->exec('TRUNCATE TABLE external_media, tmdb_media, tmdb_movies, tmdb_series RESTART IDENTITY CASCADE');
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

    public function testIdenticalRequestsAreServedBackendFirstOnTheSecondCall(): void
    {
        // T28 §8.4 : la première requête hydrate normalement depuis TMDB ; une requête identique
        // ensuite doit être résolue par le backend-first PostgreSQL sans jamais rappeler le
        // fournisseur — c'est exactement le gain que T28 introduit (avant T28, elle rappelait TMDB).
        $provider = new FakeCatalogProvider();
        $provider->candidates = [new CatalogMatchCandidate(1, 'Dune Reuse', 'Dune Reuse', 2021, [])];
        $provider->details[1] = ['directors' => [], 'cast' => [], 'runtimeMinutes' => null, 'trailerKeys' => [], 'alternativeTitles' => []];
        $provider->hydrated[1] = $this->movie('Dune Reuse', '2021-09-15');

        $engine = new CatalogMatchEngine($provider, $this->externalMedia);
        $first = $engine->resolve($this->matchRequest('Dune Reuse', 2021));
        self::assertSame(1, $provider->searchCalls);

        $second = $engine->resolve($this->matchRequest('Dune Reuse', 2021));

        self::assertSame($first->externalId, $second->externalId);
        self::assertSame('postgresql-exact-title-year', $second->method);
        self::assertSame(0, $second->confidence);
        self::assertSame(1, $provider->searchCalls, 'the second identical request must not call the provider at all');
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

    // --- T28 : backend-first PostgreSQL, réutilisation après search TMDB --------------------------

    public function testStrictBackendMatchAvoidsProviderEntirely(): void
    {
        $stored = $this->seedStoredMovie(501, 'Backend First Exact', '2021-05-01');

        $provider = new FakeCatalogProvider();
        $engine = new CatalogMatchEngine($provider, $this->externalMedia);
        $result = $engine->resolve($this->matchRequest('Backend First Exact', 2021));

        self::assertSame('matched', $result->status);
        self::assertSame($stored, $result->externalId);
        self::assertSame('postgresql-exact-title-year', $result->method);
        self::assertSame(3, $result->version);
        self::assertSame(0, $provider->searchCalls, 'a strict backend hit must never call the provider');
        self::assertSame(0, $provider->hydrateCalls);
    }

    public function testStrictBackendMatchRequiresExactYear(): void
    {
        $this->seedStoredMovie(502, 'Backend Year Guard', '2019-01-01');

        $provider = new FakeCatalogProvider(); // candidates = [] -> not_found si le fallback est tenté
        $engine = new CatalogMatchEngine($provider, $this->externalMedia);
        $result = $engine->resolve($this->matchRequest('Backend Year Guard', 2020));

        self::assertSame('not_found', $result->status);
        self::assertSame(1, $provider->searchCalls, 'a year mismatch must fall back to the provider');
    }

    public function testStrictBackendMatchRequiresYearPresent(): void
    {
        $this->seedStoredMovie(503, 'Backend No Year', '2020-01-01');

        $provider = new FakeCatalogProvider();
        $engine = new CatalogMatchEngine($provider, $this->externalMedia);
        $result = $engine->resolve($this->matchRequest('Backend No Year', null));

        self::assertSame('not_found', $result->status);
        self::assertSame(1, $provider->searchCalls, 'a missing IPTV year must fall back to the provider (R3)');
    }

    public function testStrictBackendMatchSkipsWhenTitleAndYearAreAmbiguous(): void
    {
        $this->seedStoredMovie(504, 'Ambiguous Backend', '2018-03-01');
        $this->seedStoredMovie(505, 'Ambiguous Backend', '2018-11-20');

        $provider = new FakeCatalogProvider();
        $provider->candidates = [new CatalogMatchCandidate(506, 'Ambiguous Backend', 'Ambiguous Backend', 2018, [])];
        $provider->hydrated[506] = $this->movie('Ambiguous Backend', '2018-06-01');

        $engine = new CatalogMatchEngine($provider, $this->externalMedia);
        $result = $engine->resolve($this->matchRequest('Ambiguous Backend', 2018));

        self::assertSame('matched', $result->status);
        self::assertSame('tmdb-first-result', $result->method);
        self::assertSame(1, $provider->searchCalls, 'two candidates sharing title+year must not be resolved silently (R4)');
    }

    public function testStrictBackendMatchFallsBackToAlternativeTitle(): void
    {
        $stored = $this->seedStoredMovie(507, 'Original Title Only', '2017-01-01', alternativeTitles: ['Alt Title Match']);

        $provider = new FakeCatalogProvider();
        $engine = new CatalogMatchEngine($provider, $this->externalMedia);
        $result = $engine->resolve($this->matchRequest('Alt Title Match', 2017));

        self::assertSame('matched', $result->status);
        self::assertSame($stored, $result->externalId);
        self::assertSame('postgresql-alternative-title-year', $result->method);
        self::assertSame(0, $provider->searchCalls);
    }

    public function testStrictBackendMatchSkipsAmbiguousAlternativeTitle(): void
    {
        $this->seedStoredMovie(508, 'First Owner', '2016-01-01', alternativeTitles: ['Shared Alt']);
        $this->seedStoredMovie(509, 'Second Owner', '2016-06-01', alternativeTitles: ['Shared Alt']);

        $provider = new FakeCatalogProvider();
        $provider->candidates = [new CatalogMatchCandidate(510, 'Shared Alt', 'Shared Alt', 2016, [])];
        $provider->hydrated[510] = $this->movie('Shared Alt', '2016-01-01');

        $engine = new CatalogMatchEngine($provider, $this->externalMedia);
        $result = $engine->resolve($this->matchRequest('Shared Alt', 2016));

        self::assertSame('matched', $result->status);
        self::assertSame('tmdb-first-result', $result->method);
        self::assertSame(1, $provider->searchCalls, 'a title alternative shared by two rows must fall back to the provider');
    }

    public function testStrictBackendMatchNeverCrossesMovieAndSeries(): void
    {
        $this->seedStoredMovie(511, 'Cross Kind Title', '2016-01-01');

        $provider = new FakeCatalogProvider();
        $engine = new CatalogMatchEngine($provider, $this->externalMedia);
        $result = $engine->resolve(new CatalogMatchRequest('series', 'Cross Kind Title', 2016, 'fr-FR', new CatalogMatchHints()));

        self::assertSame('not_found', $result->status);
        self::assertSame(1, $provider->searchCalls, 'a movie row must never satisfy a series request (R1)');
    }

    public function testTmdbSearchResultAlreadyHydratedSkipsHydrateCall(): void
    {
        $stored = $this->seedStoredMovie(512, 'Previously Consolidated', '2015-01-01');

        $provider = new FakeCatalogProvider();
        // Titre différent de celui stocké : la voie backend-first (titre) ne peut pas matcher, seule
        // la réutilisation par tmdbId après le `/search` TMDB le peut.
        $provider->candidates = [new CatalogMatchCandidate(512, 'Different IPTV Wording', 'Previously Consolidated', 2015, [])];

        $engine = new CatalogMatchEngine($provider, $this->externalMedia);
        $result = $engine->resolve($this->matchRequest('Different IPTV Wording', 2015));

        self::assertSame('matched', $result->status);
        self::assertSame($stored, $result->externalId);
        self::assertSame('tmdb-first-result-existing', $result->method);
        self::assertSame(1, $provider->searchCalls);
        self::assertSame(0, $provider->hydrateCalls, 'a tmdbId already hydrated in PostgreSQL must not trigger a detail call');
    }

    public function testTmdbSearchResultWithIdentityOnlyStillHydrates(): void
    {
        // `findOrCreateForTmdb` sans `persistMovie` : la ligne `tmdb_media` existe, mais aucune fiche
        // `tmdb_movies` (R6) — l'hydratation doit toujours avoir lieu.
        $identityOnly = $this->externalMedia->findOrCreateForTmdb('movie', 513);

        $provider = new FakeCatalogProvider();
        $provider->candidates = [new CatalogMatchCandidate(513, 'Identity Only Title', 'Identity Only Title', 2015, [])];
        $provider->hydrated[513] = $this->movie('Identity Only Title', '2015-01-01');

        $engine = new CatalogMatchEngine($provider, $this->externalMedia);
        $result = $engine->resolve($this->matchRequest('Identity Only Title', 2015));

        self::assertSame('matched', $result->status);
        self::assertSame($identityOnly, $result->externalId, 'the pre-existing identity must be reused, not recreated');
        self::assertSame('tmdb-first-result', $result->method);
        self::assertSame(1, $provider->searchCalls);
        self::assertSame(1, $provider->hydrateCalls);
    }

    // --- T28 review R1 : la réutilisation ne doit jamais traverser une frontière de locale --------

    public function testStrictBackendMatchNeverReusesAFicheHydratedInAnotherLocale(): void
    {
        $this->seedStoredMovie(514, 'Locale Guard', '2014-01-01', locale: 'fr-FR');

        $provider = new FakeCatalogProvider();
        $provider->candidates = [new CatalogMatchCandidate(515, 'Locale Guard', 'Locale Guard', 2014, [])];
        $provider->hydrated[515] = $this->movie('Locale Guard', '2014-01-01');

        $engine = new CatalogMatchEngine($provider, $this->externalMedia);
        $result = $engine->resolve($this->matchRequest('Locale Guard', 2014, 'en-US'));

        self::assertSame('matched', $result->status);
        self::assertSame('tmdb-first-result', $result->method, 'a fr-FR row must not satisfy an en-US backend-first lookup');
        self::assertSame(1, $provider->searchCalls);
    }

    public function testTmdbSearchResultAlreadyHydratedInAnotherLocaleStillHydrates(): void
    {
        $stored = $this->seedStoredMovie(516, 'Reuse Locale Guard', '2013-01-01', locale: 'fr-FR');

        $provider = new FakeCatalogProvider();
        $provider->candidates = [new CatalogMatchCandidate(516, 'Reuse Locale Guard EN', 'Reuse Locale Guard', 2013, [])];
        $provider->hydrated[516] = $this->movie('Reuse Locale Guard EN', '2013-01-01');

        $engine = new CatalogMatchEngine($provider, $this->externalMedia);
        $result = $engine->resolve($this->matchRequest('Reuse Locale Guard EN', 2013, 'en-US'));

        self::assertSame('matched', $result->status);
        self::assertSame($stored, $result->externalId, 'same identity, re-hydrated in the requested locale');
        self::assertSame('tmdb-first-result', $result->method, 'a tmdbId hydrated in fr-FR must not short-circuit an en-US request');
        self::assertSame(1, $provider->searchCalls);
        self::assertSame(1, $provider->hydrateCalls, 'the en-US fiche must be fetched, not reused from the fr-FR one');
    }

    public function testStrictBackendMatchSucceedsWhenTheStoredLocaleMatchesTheRequest(): void
    {
        // Non-régression : le contrôle de locale (R1) ne doit pas casser le cas nominal déjà couvert.
        $stored = $this->seedStoredMovie(517, 'Same Locale', '2012-01-01', locale: 'en-US');

        $provider = new FakeCatalogProvider();
        $engine = new CatalogMatchEngine($provider, $this->externalMedia);
        $result = $engine->resolve($this->matchRequest('Same Locale', 2012, 'en-US'));

        self::assertSame('matched', $result->status);
        self::assertSame($stored, $result->externalId);
        self::assertSame('postgresql-exact-title-year', $result->method);
        self::assertSame(0, $provider->searchCalls);
    }

    // --- T28 review R3 : le chemin backend-first série n'était couvert par aucun test de succès ----

    public function testStrictBackendMatchAvoidsProviderEntirelyForSeries(): void
    {
        $stored = $this->seedStoredSeries(601, 'Backend Series Exact', '2020-09-01');

        $provider = new FakeCatalogProvider();
        $engine = new CatalogMatchEngine($provider, $this->externalMedia);
        $result = $engine->resolve($this->seriesMatchRequest('Backend Series Exact', 2020));

        self::assertSame('matched', $result->status);
        self::assertSame($stored, $result->externalId);
        self::assertSame('postgresql-exact-title-year', $result->method);
        self::assertSame(0, $provider->searchCalls);
        self::assertSame(0, $provider->hydrateCalls);
    }

    public function testStrictBackendMatchFallsBackToAlternativeTitleForSeries(): void
    {
        $stored = $this->seedStoredSeries(602, 'Original Series Name', '2019-01-01', alternativeTitles: ['Alt Series Match']);

        $provider = new FakeCatalogProvider();
        $engine = new CatalogMatchEngine($provider, $this->externalMedia);
        $result = $engine->resolve($this->seriesMatchRequest('Alt Series Match', 2019));

        self::assertSame('matched', $result->status);
        self::assertSame($stored, $result->externalId);
        self::assertSame('postgresql-alternative-title-year', $result->method);
        self::assertSame(0, $provider->searchCalls);
    }

    public function testTmdbSearchResultAlreadyHydratedSkipsHydrateCallForSeries(): void
    {
        $stored = $this->seedStoredSeries(603, 'Previously Consolidated Series', '2018-01-01');

        $provider = new FakeCatalogProvider();
        $provider->candidates = [new CatalogMatchCandidate(603, 'Different IPTV Series Wording', 'Previously Consolidated Series', 2018, [])];

        $engine = new CatalogMatchEngine($provider, $this->externalMedia);
        $result = $engine->resolve($this->seriesMatchRequest('Different IPTV Series Wording', 2018));

        self::assertSame('matched', $result->status);
        self::assertSame($stored, $result->externalId);
        self::assertSame('tmdb-first-result-existing', $result->method);
        self::assertSame(1, $provider->searchCalls);
        self::assertSame(0, $provider->hydrateCalls, 'a series tmdbId already hydrated in PostgreSQL must not trigger a detail call');
    }

    /** @param list<string> $alternativeTitles */
    private function seedStoredMovie(int $tmdbId, string $title, string $releaseDate, array $alternativeTitles = [], string $locale = 'fr-FR'): string
    {
        $externalId = $this->externalMedia->findOrCreateForTmdb('movie', $tmdbId);
        $this->externalMedia->persistMovie($externalId, [
            ...$this->movie($title, $releaseDate),
            'alternativeTitles' => $alternativeTitles,
        ], $locale);
        return $externalId;
    }

    /** @param list<string> $alternativeTitles */
    private function seedStoredSeries(int $tmdbId, string $name, string $firstAirDate, array $alternativeTitles = [], string $locale = 'fr-FR'): string
    {
        $externalId = $this->externalMedia->findOrCreateForTmdb('series', $tmdbId);
        $this->externalMedia->persistSeries($externalId, [
            ...$this->series($name, $firstAirDate),
            'alternativeTitles' => $alternativeTitles,
        ], $locale);
        return $externalId;
    }

    private function matchRequest(string $title, ?int $year, string $locale = 'fr-FR'): CatalogMatchRequest
    {
        return new CatalogMatchRequest('movie', $title, $year, $locale, new CatalogMatchHints());
    }

    private function seriesMatchRequest(string $title, ?int $year, string $locale = 'fr-FR'): CatalogMatchRequest
    {
        return new CatalogMatchRequest('series', $title, $year, $locale, new CatalogMatchHints());
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

    /** @return array<string, mixed> */
    private function series(string $name, string $firstAirDate): array
    {
        return [
            'name' => $name, 'originalName' => $name, 'originalLanguage' => 'en', 'overview' => null,
            'posterPath' => null, 'backdropPath' => null, 'firstAirDate' => $firstAirDate, 'lastAirDate' => null,
            'numberOfEpisodes' => 10, 'numberOfSeasons' => 1, 'inProduction' => false, 'nextEpisodeToAir' => null,
            'ageRating' => null, 'adult' => false, 'status' => 'Ended', 'tagline' => null, 'voteAverage' => null,
            'voteCount' => null, 'episodeRunTimes' => [], 'genres' => [], 'originCountries' => [], 'keywords' => [],
            'alternativeTitles' => [], 'recommendations' => [], 'videos' => [],
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
    public int $hydrateCalls = 0;

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
        $this->hydrateCalls++;
        return $this->hydrated[$tmdbId] ?? throw new \RuntimeException('Unexpected hydrate() for tmdbId ' . $tmdbId);
    }

    public function seasonDetail(int $seriesTmdbId, int $seasonNumber, string $locale): array
    {
        return ['seasonNumber' => $seasonNumber, 'name' => 'Season', 'overview' => null, 'posterPath' => null, 'airDate' => null, 'voteAverage' => null, 'episodes' => []];
    }
}
