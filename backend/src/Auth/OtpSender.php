<?php

declare(strict_types=1);

namespace Cstv\Backend\Auth;

interface OtpSender
{
    public function send(string $email, string $code): void;
}
