<?php

declare(strict_types=1);

namespace Cstv\Backend\Tests\Unit;

use Cstv\Backend\Auth\MailOtpSender;
use PHPUnit\Framework\TestCase;
use RuntimeException;

final class MailOtpSenderTest extends TestCase
{
    public function testItBuildsATextOnlyOtpEmail(): void
    {
        $sent = [];
        $sender = new MailOtpSender(
            'cstv@alwaysdata.net',
            'CSTV',
            static function (string $to, string $subject, string $body, array $headers) use (&$sent): bool {
                $sent = compact('to', 'subject', 'body', 'headers');
                return true;
            },
        );

        $sender->send('person@example.com', '123456');

        self::assertSame('person@example.com', $sent['to']);
        self::assertSame('Votre code de connexion CSTV', $sent['subject']);
        self::assertStringContainsString('123456', $sent['body']);
        self::assertSame([
            'From: CSTV <cstv@alwaysdata.net>',
            'MIME-Version: 1.0',
            'Content-Type: text/plain; charset=UTF-8',
        ], $sent['headers']);
    }

    public function testItReportsMailDeliveryFailure(): void
    {
        $sender = new MailOtpSender(
            'cstv@alwaysdata.net',
            'CSTV',
            static fn (): bool => false,
        );

        $this->expectException(RuntimeException::class);
        $this->expectExceptionMessage('Unable to send OTP email.');
        $sender->send('person@example.com', '123456');
    }
}
