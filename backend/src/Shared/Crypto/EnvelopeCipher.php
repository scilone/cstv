<?php

declare(strict_types=1);

namespace Cstv\Backend\Shared\Crypto;

use Cstv\Backend\Shared\ApiException;
use JsonException;

final readonly class EnvelopeCipher
{
    private const VERSION = "\x01";

    public function __construct(private KeyRing $keys)
    {
    }

    /** @param array{host: string, port: int, username: string, password: string} $credentials */
    public function encrypt(string $accountId, array $credentials): array
    {
        try {
            $plaintext = json_encode($credentials, JSON_THROW_ON_ERROR | JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
            $keyId = $this->keys->writeKeyId;
            $key = $this->keys->writeKey();
            $nonce = random_bytes(SODIUM_CRYPTO_AEAD_XCHACHA20POLY1305_IETF_NPUBBYTES);
            $ciphertext = sodium_crypto_aead_xchacha20poly1305_ietf_encrypt($plaintext, $this->aad($accountId, $keyId), $nonce, $key);
            return ['keyId' => $keyId, 'payload' => self::VERSION . $nonce . $ciphertext];
        } catch (JsonException|\SodiumException|\ValueError $exception) {
            throw new ApiException(500, 'IPTV_CREDENTIALS_ENCRYPTION_FAILED', 'IPTV credentials could not be stored.');
        } finally {
            if (isset($plaintext) && is_string($plaintext)) sodium_memzero($plaintext);
            if (isset($key) && is_string($key)) sodium_memzero($key);
        }
    }

    /** @return array{host: string, port: int, username: string, password: string} */
    public function decrypt(string $accountId, string $keyId, string $payload): array
    {
        try {
            $nonceLength = SODIUM_CRYPTO_AEAD_XCHACHA20POLY1305_IETF_NPUBBYTES;
            if (strlen($payload) < 1 + $nonceLength + SODIUM_CRYPTO_AEAD_XCHACHA20POLY1305_IETF_ABYTES || !hash_equals(self::VERSION, $payload[0])) {
                throw new \SodiumException('Invalid ciphertext.');
            }
            $key = $this->keys->key($keyId);
            $nonce = substr($payload, 1, $nonceLength);
            $ciphertext = substr($payload, 1 + $nonceLength);
            $plaintext = sodium_crypto_aead_xchacha20poly1305_ietf_decrypt($ciphertext, $this->aad($accountId, $keyId), $nonce, $key);
            if ($plaintext === false) throw new \SodiumException('Invalid ciphertext.');
            /** @var mixed $decoded */
            $decoded = json_decode($plaintext, true, 512, JSON_THROW_ON_ERROR);
            if (!is_array($decoded) || !isset($decoded['host'], $decoded['port'], $decoded['username'], $decoded['password']) || !is_string($decoded['host']) || !is_int($decoded['port']) || !is_string($decoded['username']) || !is_string($decoded['password'])) {
                throw new \SodiumException('Invalid plaintext.');
            }
            return $decoded;
        } catch (JsonException|\SodiumException|\ValueError $exception) {
            throw new ApiException(422, 'IPTV_CREDENTIALS_UNREADABLE', 'Saved IPTV credentials cannot be restored.');
        } finally {
            if (isset($plaintext) && is_string($plaintext)) sodium_memzero($plaintext);
            if (isset($key) && is_string($key)) sodium_memzero($key);
        }
    }

    private function aad(string $accountId, string $keyId): string
    {
        return 'v1|' . $accountId . '|' . $keyId;
    }
}
