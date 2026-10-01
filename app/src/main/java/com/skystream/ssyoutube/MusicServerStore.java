package com.skystream.ssyoutube;

import android.content.Context;
import android.os.Build;
import android.security.KeyPairGeneratorSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.util.Calendar;
import java.util.Date;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.x500.X500Principal;

/** Encrypted, backup-excluded credentials. All access is serialized on the music worker. */
final class MusicServerStore {
    private static final String KEY_ALIAS = "ssyoutube.music-server";
    private static final int LEGACY_FORMAT = 1;
    private static final int OAEP_FORMAT = 2;
    private final Context context;
    private final AtomicFile sessionFile;
    private final AtomicFile pendingFile;

    MusicServerStore(Context context) {
        this.context = context.getApplicationContext();
        sessionFile = file("music-session");
        pendingFile = file("music-login");
    }

    private AtomicFile file(String name) {
        return new AtomicFile(new File(context.getNoBackupFilesDir(), name));
    }

    MusicServerProtocol.Session session() throws IOException {
        byte[] data = read(sessionFile);
        if (data == null) {
            return null;
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(data))) {
            return new MusicServerProtocol.Session(input.readUTF(), input.readUTF(), input.readLong());
        }
    }

    MusicServerProtocol.Pending pending() throws IOException {
        byte[] data = read(pendingFile);
        if (data == null) {
            return null;
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(data))) {
            return new MusicServerProtocol.Pending(input.readUTF(), input.readUTF(),
                    input.readUTF(), input.readLong());
        }
    }

    void save(MusicServerProtocol.Session session) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeUTF(session.origin);
            output.writeUTF(session.token);
            output.writeLong(session.expiresAt);
        }
        write(sessionFile, bytes.toByteArray());
    }

    void save(MusicServerProtocol.Pending pending) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeUTF(pending.origin);
            output.writeUTF(pending.verifier);
            output.writeUTF(pending.state);
            output.writeLong(pending.createdAt);
        }
        write(pendingFile, bytes.toByteArray());
    }

    void clearSession() {
        sessionFile.delete();
    }

    void clearPending() {
        pendingFile.delete();
    }

    private byte[] read(AtomicFile file) throws IOException {
        if (!file.getBaseFile().exists()
                && !new File(file.getBaseFile().getPath() + ".bak").exists()) {
            return null;
        }
        try {
            byte[] encrypted = file.readFully();
            if (encrypted.length > 16_384) {
                throw new IOException("Invalid music server storage");
            }
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(encrypted));
            int format = input.readInt();
            if (format != LEGACY_FORMAT && format != OAEP_FORMAT) {
                throw new IOException("Invalid music server storage");
            }
            int keySize = input.readInt();
            if (keySize != 256) {
                throw new IOException("Invalid music server storage");
            }
            byte[] wrappedKey = new byte[keySize];
            byte[] iv = new byte[12];
            input.readFully(wrappedKey);
            input.readFully(iv);
            Cipher rsa = wrappingCipher(format);
            rsa.init(Cipher.DECRYPT_MODE, (PrivateKey) keys().getKey(alias(format), null));
            byte[] key = rsa.doFinal(wrappedKey);
            Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
            aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(128, iv));
            int offset = encrypted.length - input.available();
            return aes.doFinal(encrypted, offset, input.available());
        } catch (GeneralSecurityException | RuntimeException e) {
            throw new IOException("Cannot read music server credentials");
        }
    }

    private void write(AtomicFile file, byte[] data) throws IOException {
        FileOutputStream output = null;
        try {
            int format = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    ? OAEP_FORMAT : LEGACY_FORMAT;
            KeyStore keyStore = keys();
            if (!keyStore.containsAlias(alias(format))) {
                createKey(format);
                keyStore = keys();
            }
            byte[] key = new byte[32];
            byte[] iv = new byte[12];
            SecureRandom random = new SecureRandom();
            random.nextBytes(key);
            random.nextBytes(iv);
            Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
            aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(128, iv));
            byte[] ciphertext = aes.doFinal(data);
            Cipher rsa = wrappingCipher(format);
            rsa.init(Cipher.ENCRYPT_MODE, keyStore.getCertificate(alias(format)).getPublicKey());
            byte[] wrappedKey = rsa.doFinal(key);
            output = file.startWrite();
            DataOutputStream encoded = new DataOutputStream(output);
            encoded.writeInt(format);
            encoded.writeInt(wrappedKey.length);
            encoded.write(wrappedKey);
            encoded.write(iv);
            encoded.write(ciphertext);
            encoded.flush();
            file.finishWrite(output);
        } catch (GeneralSecurityException | RuntimeException e) {
            if (output != null) {
                file.failWrite(output);
            }
            throw new IOException("Cannot save music server credentials");
        } catch (IOException e) {
            if (output != null) {
                file.failWrite(output);
            }
            throw e;
        }
    }

    private KeyStore keys() throws GeneralSecurityException, IOException {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        return keyStore;
    }

    private static String alias(int format) {
        return format == OAEP_FORMAT ? KEY_ALIAS + ".oaep" : KEY_ALIAS;
    }

    private static Cipher wrappingCipher(int format) throws GeneralSecurityException {
        if (format == OAEP_FORMAT) {
            return Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");
        }
        // API 21/22 Keystore cannot decrypt OAEP. This compatibility path wraps only a random
        // AES key in app-private storage, with no remote decryption or padding-error oracle.
        // The payload is authenticated by GCM and all restoration failures discard credentials.
        return Cipher.getInstance("RSA/ECB/PKCS1Padding");
    }

    @SuppressWarnings("deprecation")
    private void createKey(int format) throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && format == OAEP_FORMAT) {
            generator.initialize(new KeyGenParameterSpec.Builder(alias(format),
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(2048)
                    .setDigests(KeyProperties.DIGEST_SHA1)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                    .build());
            generator.generateKeyPair();
            return;
        }
        Calendar end = Calendar.getInstance();
        end.add(Calendar.YEAR, 30);
        generator.initialize(new KeyPairGeneratorSpec.Builder(context)
                .setAlias(alias(format))
                .setKeySize(2048)
                .setSubject(new X500Principal("CN=ssYoutube Music Server"))
                .setSerialNumber(BigInteger.ONE)
                .setStartDate(new Date())
                .setEndDate(end.getTime())
                .build());
        generator.generateKeyPair();
    }
}
