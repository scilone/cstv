<?php

declare(strict_types=1);

namespace Cstv\Backend\Catalog;

/**
 * F45 §7.11/§8.6: never trusts `results[0]`. PostgreSQL-first, then a wide passe-1 score (title +
 * year + genres) over the search results, then — only when the top two are too close to call — a
 * targeted passe-2 detail fetch (director/cast/runtime/trailer/alt titles) on the 2-3 best
 * candidates. Accepts only above an absolute floor AND with enough margin over the runner-up,
 * unless a decisive signal (identical trailer + a compatible title) overrides the margin rule.
 */
final readonly class CatalogMatchEngine
{
    public const ALGORITHM_VERSION = 1;

    private const ACCEPT_THRESHOLD = 65; // PROBABLE floor (§7.11)
    private const MIN_MARGIN = 12;
    private const CANDIDATE_POOL = 5;
    private const DETAIL_POOL = 3;

    public function __construct(
        private MediaMetadataProvider $provider,
        private ExternalMediaRepository $externalMedia,
        private CatalogItemPresenter $presenter = new CatalogItemPresenter(),
    ) {}

    public function resolve(CatalogMatchRequest $request): CatalogMatchResult
    {
        $reused = $this->resolveFromConsolidated($request);
        if ($reused !== null) return $reused;

        $candidates = $this->provider->searchCandidates($request->kind, $request->title, $request->year, $request->locale);
        if ($candidates === []) return CatalogMatchResult::notFound(self::ALGORITHM_VERSION);

        $genreNames = $request->hints->genres !== [] ? $this->provider->genreNames($request->kind, $request->locale) : [];
        $scored = [];
        foreach (array_slice($candidates, 0, self::CANDIDATE_POOL) as $candidate) {
            $scored[] = ['candidate' => $candidate, 'score' => $this->scorePass1($request, $candidate, $genreNames)];
        }
        usort($scored, static fn (array $a, array $b): int => $b['score']->value <=> $a['score']->value);

        $top = array_slice($scored, 0, self::DETAIL_POOL);
        // F45-R3 : un écart de pass 1 déjà large (titre+année) ne prouve rien contre un remake/homonyme
        // — director/cast/durée/trailer sont les signaux qui peuvent réellement le contredire, donc la
        // passe 2 tourne dès que la requête en fournit au moins un, même hors ambiguïté apparente.
        $hasDisambiguatingHints = $request->hints->director !== null || $request->hints->actors !== []
            || $request->hints->runtimeMinutes !== null || $request->hints->youtubeTrailerKey !== null;
        $ambiguous = count($top) > 1 && ($top[0]['score']->value - $top[1]['score']->value) < self::MIN_MARGIN;
        if ($top !== [] && ($ambiguous || $hasDisambiguatingHints)) {
            foreach ($top as $index => $entry) {
                $detail = $this->provider->candidateDetail($request->kind, $entry['candidate']->tmdbId, $request->locale);
                $top[$index]['score'] = $this->scorePass2($request, $entry['score'], $detail);
            }
            usort($top, static fn (array $a, array $b): int => $b['score']->value <=> $a['score']->value);
        }

        $best = $top[0];
        $runnerUpValue = $top[1]['score']->value ?? 0;
        $margin = $best['score']->value - $runnerUpValue;
        if ($best['score']->value < self::ACCEPT_THRESHOLD || ($margin < self::MIN_MARGIN && !$best['score']->decisive)) {
            return CatalogMatchResult::unresolved(self::ALGORITHM_VERSION);
        }

        return $this->accept($request, $best['candidate'], $best['score']);
    }

    /**
     * F45-R3 : la résolution PostgreSQL-first (§8.6) score désormais chaque ligne déjà consolidée
     * comme un candidat à part entière — titre (principal/original/alternatifs), année, genres —
     * puis exige le même seuil ET la même marge que la voie fournisseur avant de réutiliser
     * l'`externalId`. L'ancien `findConsolidated()` acceptait aveuglément un homonyme unique, y
     * compris sans date stockée du tout (traitée comme une année concordante).
     */
    private function resolveFromConsolidated(CatalogMatchRequest $request): ?CatalogMatchResult
    {
        $rows = $this->externalMedia->findConsolidatedCandidates($request->kind, $request->title, $request->year);
        if ($rows === []) return null;

        $scored = array_map(fn (array $row): array => ['row' => $row, 'score' => $this->scoreInternalCandidate($request, $row)], $rows);
        usort($scored, static fn (array $a, array $b): int => $b['score']->value <=> $a['score']->value);

        $best = $scored[0];
        $runnerUpValue = $scored[1]['score']->value ?? 0;
        $margin = $best['score']->value - $runnerUpValue;
        if ($best['score']->value < self::ACCEPT_THRESHOLD || $margin < self::MIN_MARGIN) return null;

        $item = $this->presentStored($request->kind, $best['row']['external_id']);
        if ($item === null) return null;
        return CatalogMatchResult::reused($best['row']['external_id'], $best['score']->confidence(), $best['score']->method(), self::ALGORITHM_VERSION, $item);
    }

    /** @param array{external_id: string, title: string, original_title: ?string, alternative_titles: list<string>, media_date: ?string, genres: list<string>} $row */
    private function scoreInternalCandidate(CatalogMatchRequest $request, array $row): CatalogMatchScore
    {
        $score = new CatalogMatchScore(0, []);
        $titles = array_filter([$row['title'], $row['original_title'], ...$row['alternative_titles']], static fn (?string $t): bool => $t !== null && $t !== '');
        $normalizedHint = TitleNormalizer::normalize($request->title);
        $best = 0.0;
        foreach ($titles as $candidateTitle) {
            similar_text($normalizedHint, TitleNormalizer::normalize($candidateTitle), $percent);
            $best = max($best, $percent / 100);
        }
        $score = $score->addingSignal('title', (int) round($best * 70));

        $candidateYear = $row['media_date'] !== null && preg_match('/^(\d{4})-/', $row['media_date'], $match) === 1 ? (int) $match[1] : null;
        if ($request->year !== null && $candidateYear !== null) {
            $diff = abs($request->year - $candidateYear);
            $score = match (true) {
                $diff === 0 => $score->addingSignal('year', 20),
                $diff === 1 => $score->addingSignal('year', 12),
                $diff === 2 => $score->addingSignal('year', -15),
                default => $score->addingSignal('year', -30),
            };
        }

        if ($request->hints->genres !== [] && $row['genres'] !== []) {
            $overlap = $this->overlapCount($request->hints->genres, $row['genres']);
            if ($overlap > 0) $score = $score->addingSignal('genre', min(15, $overlap * 8));
        }

        return $score;
    }

    private function accept(CatalogMatchRequest $request, CatalogMatchCandidate $candidate, CatalogMatchScore $score): CatalogMatchResult
    {
        $externalId = $this->externalMedia->findOrCreateForTmdb($request->kind, $candidate->tmdbId);
        $hydrated = $this->provider->hydrate($request->kind, $candidate->tmdbId, $request->locale);
        // Une recommandation obtient une identité sans hydratation de fiche (§8.2) : jamais de N+1.
        $hydrated['recommendations'] = array_map(
            fn (int $tmdbId): string => $this->externalMedia->findOrCreateForTmdb($request->kind, $tmdbId),
            $hydrated['recommendations'] ?? [],
        );
        if ($request->kind === 'movie') {
            $this->externalMedia->persistMovie($externalId, $hydrated);
        } else {
            $this->externalMedia->persistSeries($externalId, $hydrated);
        }

        $item = $this->presentStored($request->kind, $externalId);
        if ($item === null) return CatalogMatchResult::unresolved(self::ALGORITHM_VERSION); // ne devrait jamais arriver juste après persist

        return CatalogMatchResult::matched($externalId, $score->confidence(), $score->method(), self::ALGORITHM_VERSION, $item);
    }

    private function scorePass1(CatalogMatchRequest $request, CatalogMatchCandidate $candidate, array $genreNames): CatalogMatchScore
    {
        $score = new CatalogMatchScore(0, []);
        // Le titre porte l'essentiel du score : un candidat unique au titre quasi identique doit
        // pouvoir être accepté même sans année (hint absent ou non fournie par l'IPTV) — l'absence
        // d'ambiguïté (aucun autre candidat proche) est elle-même un signal, géré par la marge.
        $score = $score->addingSignal('title', (int) round($this->titleSimilarity($request->title, $candidate) * 70));

        if ($request->year !== null && $candidate->year !== null) {
            $diff = abs($request->year - $candidate->year);
            $score = match (true) {
                $diff === 0 => $score->addingSignal('year', 20),
                $diff === 1 => $score->addingSignal('year', 12),
                $diff === 2 => $score->addingSignal('year', -15),
                default => $score->addingSignal('year', -30),
            };
        }

        if ($request->hints->genres !== [] && $candidate->genreIds !== []) {
            $candidateGenreNames = array_values(array_filter(array_map(
                static fn (int $id): ?string => $genreNames[$id] ?? null,
                $candidate->genreIds,
            )));
            $overlap = $this->overlapCount($request->hints->genres, $candidateGenreNames);
            if ($overlap > 0) $score = $score->addingSignal('genre', min(15, $overlap * 8));
        }

        return $score;
    }

    /** @param array<string, mixed> $detail */
    private function scorePass2(CatalogMatchRequest $request, CatalogMatchScore $score, array $detail): CatalogMatchScore
    {
        $hints = $request->hints;

        if ($hints->director !== null && $this->matchesAny($hints->director, $detail['directors'] ?? [])) {
            $score = $score->addingSignal('director', 15);
        }

        if ($hints->actors !== []) {
            $overlap = $this->overlapCount($hints->actors, $detail['cast'] ?? []);
            if ($overlap > 0) $score = $score->addingSignal('cast', min(10, $overlap * 3));
        }

        if ($hints->runtimeMinutes !== null && is_int($detail['runtimeMinutes'] ?? null) && abs($hints->runtimeMinutes - $detail['runtimeMinutes']) <= 5) {
            $score = $score->addingSignal('runtime', 8);
        }

        if ($hints->youtubeTrailerKey !== null && in_array($hints->youtubeTrailerKey, $detail['trailerKeys'] ?? [], true)) {
            // Preuve déterminante (§7.11) seulement si le titre reste compatible : un trailer isolé
            // ne suffit jamais si le titre ne se ressemble pas du tout.
            $decisive = in_array('title', $score->signals, true) && $score->value >= 40;
            $score = $score->addingSignal('trailer', 20, decisive: $decisive);
        }

        if ($this->overlapCount([$request->title], $detail['alternativeTitles'] ?? []) > 0) {
            $score = $score->addingSignal('alt-title', 10);
        }

        return $score;
    }

    private function titleSimilarity(string $hintTitle, CatalogMatchCandidate $candidate): float
    {
        $normalizedHint = TitleNormalizer::normalize($hintTitle);
        $best = 0.0;
        foreach (array_filter([$candidate->title, $candidate->originalTitle]) as $candidateTitle) {
            similar_text($normalizedHint, TitleNormalizer::normalize($candidateTitle), $percent);
            $best = max($best, $percent / 100);
        }
        return $best;
    }

    private function matchesAny(string $hint, array $candidates): bool
    {
        $normalizedHint = TitleNormalizer::normalize($hint);
        foreach ($candidates as $candidate) {
            if (is_string($candidate) && TitleNormalizer::normalize($candidate) === $normalizedHint) return true;
        }
        return false;
    }

    /** @param list<string> $hintValues @param list<string> $candidateValues */
    private function overlapCount(array $hintValues, array $candidateValues): int
    {
        $normalizedCandidates = array_map(static fn (string $value): string => TitleNormalizer::normalize($value), $candidateValues);
        $count = 0;
        foreach ($hintValues as $hint) {
            if (in_array(TitleNormalizer::normalize($hint), $normalizedCandidates, true)) $count++;
        }
        return $count;
    }

    /** @return array<string, mixed>|null */
    private function presentStored(string $kind, string $externalId): ?array
    {
        $row = $kind === 'movie' ? $this->externalMedia->getMovie($externalId) : $this->externalMedia->getSeries($externalId);
        return $row === null ? null : $this->presenter->present($kind, $externalId, $row);
    }
}
