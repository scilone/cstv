<?php

declare(strict_types=1);

namespace Cstv\Backend\Catalog;

/** F45 §7.7: the four image slots CSTV serves, each sized independently per device. */
enum ImageContext
{
    case PosterMedia;
    case PosterSeason;
    case Backdrop;
    case StillEpisode;
}
