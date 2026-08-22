<?php

declare(strict_types=1);

namespace Cstv\Backend\Shared;

/**
 * Horloge **monotone** injectable : mesure de durées écoulées uniquement, jamais d'heure murale.
 *
 * Un budget de temps ne doit pas s'appuyer sur `microtime()`/`time()` — un ajustement NTP ou un
 * changement d'heure ferait sauter (ou reculer) la deadline en pleine requête. Interface plutôt que
 * `hrtime()` en dur pour que les tests fassent avancer le temps sans attendre réellement.
 */
interface MonotonicClock
{
    /** Nanosecondes écoulées depuis un point d'origine arbitraire mais stable pour tout le processus. */
    public function nanos(): int;
}
