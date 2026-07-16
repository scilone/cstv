package com.poc.iptvxtream.data.local.storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Abstraction du chiffrement des credentials, pour pouvoir tester
 * CredentialsManager en JVM pure avec un cipher factice (le Keystore
 * Android n'existe pas hors device).
 */
interface CredentialsCipher {
    /** Chiffre [plaintext] et retourne le payload autoportant (IV inclus). */
    fun encrypt(plaintext: ByteArray): ByteArray

    /**
     * Déchiffre un payload produit par [encrypt].
     * @throws java.security.GeneralSecurityException si le payload est
     * corrompu ou la clé absente/régénérée.
     */
    fun decrypt(payload: ByteArray): ByteArray
}

/**
 * Implémentation AES-256-GCM avec clé non exportable dans l'Android Keystore
 * (remplace EncryptedSharedPreferences de security-crypto, déprécié).
 * Format du payload : IV de 12 octets préfixé au ciphertext (le tag GCM de
 * 16 octets est inclus par le provider à la fin du ciphertext).
 * Requiert API 23+ (minSdk 23 depuis l'audit #4, voir AGENTS.md).
 */
class KeystoreCredentialsCipher : CredentialsCipher {

    // KeyStore.load() est un aller-retour IPC vers le daemon keystore : coûteux
    // à répéter. CredentialsManager déchiffre 5 champs par getCredentials() ;
    // sans ce cache, c'était 5 aller-retours Keystore synchrones à chaque
    // lancement (appelé sur le thread principal depuis LoginViewModel.init),
    // perceptible comme un freeze au démarrage. Un seul round-trip par process
    // (l'instance vit le temps du process, portée par CredentialsManager
    // @Singleton).
    private val secretKey: SecretKey by lazy { getOrCreateKey() }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    override fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        return cipher.iv + cipher.doFinal(plaintext)
    }

    override fun decrypt(payload: ByteArray): ByteArray {
        require(payload.size > IV_SIZE) { "Payload trop court" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey,
            GCMParameterSpec(GCM_TAG_BITS, payload, 0, IV_SIZE)
        )
        return cipher.doFinal(payload, IV_SIZE, payload.size - IV_SIZE)
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "credentials_master_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val GCM_TAG_BITS = 128
    }
}
