<?php

declare(strict_types=1);

namespace Cstv\Backend\Auth;

use Closure;
use RuntimeException;

final readonly class MailOtpSender implements OtpSender
{
    /** @var Closure(string, string, string, array<string, string>): bool */
    private Closure $deliver;

    /**
     * @param null|Closure(string, string, string, array<string, string>): bool $deliver
     */
    public function __construct(
        private string $fromEmail,
        private string $fromName,
        ?Closure $deliver = null,
    ) {
        $this->deliver = $deliver ?? static fn (string $to, string $subject, string $body, array $headers): bool => mail(
            $to,
            $subject,
            $body,
            implode("\r\n", $headers),
        );
    }

    public function send(string $email, string $code): void
    {
        $headers = [
            sprintf('From: %s <%s>', $this->fromName, $this->fromEmail),
            'MIME-Version: 1.0',
            'Content-Type: text/plain; charset=UTF-8',
        ];
        $body = sprintf(
            "Votre code de connexion CSTV est : %s\n\nCe code expire dans cinq minutes. Si vous n'avez pas demande ce code, ignorez cet e-mail.",
            $code,
        );

        if (!(($this->deliver)($email, 'Votre code de connexion CSTV', $body, $headers))) {
            throw new RuntimeException('Unable to send OTP email.');
        }
    }
}
