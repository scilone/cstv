<?php

declare(strict_types=1);

namespace Cstv\Backend\Auth;

final readonly class LogOtpSender implements OtpSender
{
    public function __construct(private string $environment)
    {
    }

    public function send(string $email, string $code): void
    {
        if ($this->environment === 'dev') {
            error_log(sprintf('[CSTV OTP] email=%s code=%s', $email, $code));
        }
    }
}
