/*
 * Copyright (c)  2023 Dario Lucia (https://www.dariolucia.eu)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package eu.dariolucia.reatmetric.driver.spacecraft.security;

import eu.dariolucia.ccsds.tmtc.algorithm.Crc16Algorithm;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.AbstractTransferFrame;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.AosTransferFrame;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TcTransferFrame;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TmTransferFrame;
import eu.dariolucia.reatmetric.api.common.exceptions.ReatmetricException;
import eu.dariolucia.reatmetric.api.value.StringUtil;
import eu.dariolucia.reatmetric.core.api.IServiceCoreContext;
import eu.dariolucia.reatmetric.driver.spacecraft.definition.SpacecraftConfiguration;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * This class is meant to be an example to show the use of the {@link ISecurityHandler} extension.
 * This class implements AES-based cryptography and SHA-256 hashing for securing the frame data.
 * The encoding uses a 6 bytes header, containing the security parameter index of the AES-256 key (string) used for the
 * encoding of the data field, and a 4 bytes initialisation vector.
 * The trailer is the less significant 4 bytes of the SHA-256, computed over the concatenation of transfer frame primary header field,
 * transfer frame secondary header field (if present), segment header (if present) and data field before the encryption.
 * Encryption/decryption is performed on all virtual channels.
 * <p />
 * It must be noted that, even if the approach is quite in line with the one described in CCSDS 355.0-B-2, not all aspects
 * are covered. Nevertheless, this class represents a starting point.
 */
public class AesHandler implements ISecurityHandler {

    private static final int KEY_LENGTH = 256;
    private static final int ITERATION_COUNT = 65536;
    private static final String SECRET_KEY_FACTORY = "PBKDF2WithHmacSHA256";
    private static final String ALGORITHM = "AES";
    private static final String CHIPER = "AES/CBC/PKCS5Padding";
    public static final int TRAILER_LENGTH = 4;
    public static final int IV_LENGTH = 4;

    public static final int HEADER_LENGTH = 2 + IV_LENGTH;

    private IServiceCoreContext context;
    private SpacecraftConfiguration configuration;
    private final Map<Integer, String> spi2key = new HashMap<>();
    private final SecureRandom randomizer = new SecureRandom();
    private byte[] salt;

    @Override
    public void initialise(IServiceCoreContext context, SpacecraftConfiguration configuration) {
        this.context = context;
        this.configuration = configuration;
        // Initialise keys
        String[] split = configuration.getSecurityDataLinkConfiguration().getConfiguration().split(";", -1);
        for(String e : split) {
            e = e.trim();
            String[] keyValue = e.split(":", -1);
            if(keyValue[0].equals("SALT")) {
                this.salt = StringUtil.toByteArray(keyValue[1]);
            } else {
                this.spi2key.put(Integer.parseInt(keyValue[0]), keyValue[1]);
            }
        }
    }

    @Override
    public int getSecurityHeaderLength(int spacecraftId, int virtualChannelId, Class<? extends AbstractTransferFrame> type) {
        // Big-Endian short number, indicating the AES key to use, plus IV
        return HEADER_LENGTH;
    }

    @Override
    public int getSecurityTrailerLength(int spacecraftId, int virtualChannelId, Class<? extends AbstractTransferFrame> type) {
        // Less significant four bytes of the SHA-256 encoding
        return TRAILER_LENGTH;
    }

    @Override
    public AbstractTransferFrame encrypt(AbstractTransferFrame frame) throws ReatmetricException {
        // Only TcTransferFrame are supported: encryption always performed with SPI 1.
        // A real implementation would need a way to change the key at runtime: this can be done by reading the value of
        // a parameter, injected directly in the model from e.g. the user interface, or by other means
        if(frame instanceof TcTransferFrame && ((TcTransferFrame) frame).isSecurityUsed()) {
            return encryptTcAes((TcTransferFrame) frame, spi2key.get(1), 1);
        } else {
            return frame;
        }
    }

    private TcTransferFrame encryptTcAes(TcTransferFrame frameObj, String key, int keyId) throws ReatmetricException {
        // Compute the initialisation vector
        byte[] ivArray = new byte[IV_LENGTH];
        this.randomizer.nextBytes(ivArray);
        // Compute the header: keyId as short plus iv
        byte[] header = ByteBuffer.allocate(HEADER_LENGTH).putShort((short) (keyId & 0xFFFF)).put(ivArray).array();
        if(header.length != frameObj.getSecurityHeaderLength()) {
            throw new ReatmetricException("Security error: security header on TC frame on SC: " + frameObj.getSpacecraftId() +
                    " VC: " + frameObj.getVirtualChannelId()
                    + " declared as " + frameObj.getSecurityHeaderLength() +
                    " but generated with length " + header.length);
        }
        // Run AES on data field
        byte[] encryptedDataField = aesEncrypt(frameObj.getFrame(), frameObj.getDataFieldStart(), frameObj.getDataFieldLength(), key, ivArray);
        // Compute the trailer
        byte[] trailer = computeTrailer(frameObj);
        if(trailer.length != frameObj.getSecurityTrailerLength()) {
            throw new ReatmetricException("Security error: security trailer on TC frame on SC: " + frameObj.getSpacecraftId() +
                    " VC: " + frameObj.getVirtualChannelId()
                    + " declared as " + frameObj.getSecurityTrailerLength() +
                    " but generated with length " + trailer.length);
        }
        // Now allocate a frame and write down the data
        byte[] newFrame = new byte[frameObj.getLength()];
        // Copy primary header data
        int currentOffset = 0;
        System.arraycopy(frameObj.getFrame(), currentOffset, newFrame, currentOffset, TcTransferFrame.TC_PRIMARY_HEADER_LENGTH);
        currentOffset += TcTransferFrame.TC_PRIMARY_HEADER_LENGTH;
        // Copy segment header
        if(frameObj.isSegmented()) {
            System.arraycopy(frameObj.getFrame(), currentOffset, newFrame, currentOffset, 1);
            currentOffset += 1;
        }
        // Now copy security header
        System.arraycopy(header, 0, newFrame, currentOffset, header.length);
        currentOffset += header.length;
        // Now copy the data field (encrypted)
        System.arraycopy(encryptedDataField, 0, newFrame, currentOffset, encryptedDataField.length);
        currentOffset += encryptedDataField.length;
        // Now copy the trailer
        System.arraycopy(trailer, 0, newFrame, currentOffset, trailer.length);
        // Now compute and set FECF if needed
        if(frameObj.isFecfPresent()) {
            short crc = Crc16Algorithm.getCrc16(newFrame, 0, newFrame.length - 2);
            newFrame[newFrame.length - 2] = (byte) (crc >> 8);
            newFrame[newFrame.length - 1] = (byte) (crc);
        }
        return new TcTransferFrame(newFrame, vc -> frameObj.isSegmented(), frameObj.isFecfPresent(), header.length, trailer.length);
    }

    private byte[] computeTrailer(TcTransferFrame frameObj) throws ReatmetricException {
        // Use primary header, if present, segment header and data field (without security header and trailer)
        int scopeLength = TcTransferFrame.TC_PRIMARY_HEADER_LENGTH + (frameObj.isSegmented() ? 1 : 0) + frameObj.getDataFieldStart();
        byte[] scope = new byte[scopeLength];
        int offset = 0;
        System.arraycopy(frameObj.getFrame(), 0, scope, offset, TcTransferFrame.TC_PRIMARY_HEADER_LENGTH);
        offset += TcTransferFrame.TC_PRIMARY_HEADER_LENGTH;
        if(frameObj.isSegmented()) {
            scope[TcTransferFrame.TC_PRIMARY_HEADER_LENGTH] = frameObj.getFrame()[TcTransferFrame.TC_PRIMARY_HEADER_LENGTH];
            offset += 1;
        }
        System.arraycopy(frameObj.getFrame(), frameObj.getDataFieldStart(), scope, offset, frameObj.getDataFieldLength());
        // Done, now compute SHA-256
        return computeSHA256(scope);
    }

    public byte[] aesEncrypt(byte[] data, int offset, int length, String key, byte[] iv) throws ReatmetricException {
        try {
            IvParameterSpec ivspec = new IvParameterSpec(iv);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(SECRET_KEY_FACTORY);
            KeySpec spec = new PBEKeySpec(key.toCharArray(), this.salt, ITERATION_COUNT, KEY_LENGTH);
            SecretKey tmp = factory.generateSecret(spec);
            SecretKeySpec secretKeySpec = new SecretKeySpec(tmp.getEncoded(), ALGORITHM);

            Cipher cipher = Cipher.getInstance(CHIPER);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivspec);
            return cipher.doFinal(data, offset, length);
        } catch (Exception e) {
            throw new ReatmetricException(e);
        }
    }

    private byte[] aesDecrypt(byte[] data, int offset, int length, String key, byte[] iv) throws ReatmetricException {
        try {
            IvParameterSpec ivspec = new IvParameterSpec(iv);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(SECRET_KEY_FACTORY);
            KeySpec spec = new PBEKeySpec(key.toCharArray(), this.salt, ITERATION_COUNT, KEY_LENGTH);
            SecretKey tmp = factory.generateSecret(spec);
            SecretKeySpec secretKeySpec = new SecretKeySpec(tmp.getEncoded(), ALGORITHM);

            Cipher cipher = Cipher.getInstance(CHIPER);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivspec);
            return cipher.doFinal(data, offset, length);
        } catch (Exception e) {
            throw new ReatmetricException(e);
        }
    }

    @Override
    public AbstractTransferFrame decrypt(AbstractTransferFrame frame) throws ReatmetricException {
        // Only TmTransferFrame and AosTransferFrame are supported
        if(frame instanceof TmTransferFrame) {
            return decryptTmAes((TmTransferFrame) frame);
        } else if(frame instanceof AosTransferFrame) {
            return decryptAosAes((AosTransferFrame) frame);
        } else {
            return frame;
        }
    }

    private AbstractTransferFrame decryptAosAes(AosTransferFrame frame) throws ReatmetricException {
        // Get the security header: after primary and secondary header (if present)
        int secHeaderOffset = AosTransferFrame.AOS_PRIMARY_HEADER_LENGTH + (frame.isFrameHeaderErrorControlPresent() ? AosTransferFrame.AOS_PRIMARY_HEADER_FHEC_LENGTH : 0) +
                frame.getInsertZoneLength();
        ByteBuffer secHeaderWrap = ByteBuffer.wrap(frame.getFrame(), secHeaderOffset, HEADER_LENGTH);
        short spi = secHeaderWrap.getShort();
        String password = spi2key.get((int) spi);
        byte[] iv = Arrays.copyOfRange(frame.getFrame(), secHeaderOffset + 2, secHeaderOffset + HEADER_LENGTH);
        // Now decrypt the body
        int dataFieldLength = frame.getLength() - secHeaderOffset - HEADER_LENGTH - TRAILER_LENGTH - (frame.isOcfPresent() ? 4 : 0) - (frame.isFecfPresent() ? 2 : 0);
        byte[] decryptedDataField = aesDecrypt(frame.getFrame(), secHeaderOffset + HEADER_LENGTH, dataFieldLength, password, iv);
        // Now verify that the trailer matches with the data
        // Use primary header, if present, FHEC and data field (without security header and trailer, without insert zone)
        byte[] trailer = computeTrailer(frame.getFrame(),
                AosTransferFrame.AOS_PRIMARY_HEADER_LENGTH + (frame.isFrameHeaderErrorControlPresent() ? AosTransferFrame.AOS_PRIMARY_HEADER_FHEC_LENGTH : 0),
                decryptedDataField);
        if(!Arrays.equals(frame.getFrame(), secHeaderOffset + HEADER_LENGTH + decryptedDataField.length, secHeaderOffset + HEADER_LENGTH + decryptedDataField.length + TRAILER_LENGTH,
                trailer, 0, trailer.length)) {
            throw new ReatmetricException("Trailer mismatch, AOS frame corrupted on SC: " + frame.getSpacecraftId() +
                    "VC:" + frame.getVirtualChannelId());
        }
        // Now compose the decrypted frame
        byte[] newFrame = new byte[frame.getLength()];
        int currentOffset = 0;
        System.arraycopy(frame.getFrame(), 0, newFrame, currentOffset, secHeaderOffset); // Primary header, FHEC if present, insert zone
        currentOffset += secHeaderOffset;
        // Security header
        System.arraycopy(frame.getFrame(), secHeaderOffset, newFrame, currentOffset, HEADER_LENGTH); // security header
        currentOffset += HEADER_LENGTH;
        // Data
        System.arraycopy(decryptedDataField, 0, newFrame, currentOffset, decryptedDataField.length);
        currentOffset += decryptedDataField.length;
        // Trailer
        System.arraycopy(trailer, 0, newFrame, currentOffset, trailer.length);
        currentOffset += trailer.length;
        // If OCF, copy
        if(frame.isOcfPresent()) {
            System.arraycopy(frame.getFrame(), frame.getOcfStart(), newFrame, currentOffset, 4);
        }
        // If FECF, recompute
        if(frame.isFecfPresent()) {
            short crc = Crc16Algorithm.getCrc16(newFrame, 0, newFrame.length - 2);
            newFrame[newFrame.length - 2] = (byte) (crc >> 8);
            newFrame[newFrame.length - 1] = (byte) (crc);
        }

        return new AosTransferFrame(newFrame, frame.isFrameHeaderErrorControlPresent(), frame.getInsertZoneLength(), frame.getUserDataType(), frame.isOcfPresent(), frame.isFecfPresent(), HEADER_LENGTH, trailer.length);
    }

    private AbstractTransferFrame decryptTmAes(TmTransferFrame frame) throws ReatmetricException {
        // Get the security header: after primary and secondary header (if present)
        int secHeaderOffset = TmTransferFrame.TM_PRIMARY_HEADER_LENGTH + (frame.isSecondaryHeaderPresent() ? frame.getSecondaryHeaderLength() : 0);
        ByteBuffer secHeaderWrap = ByteBuffer.wrap(frame.getFrame(), secHeaderOffset, getSecurityHeaderLength(frame.getSpacecraftId(), frame.getVirtualChannelId(), TmTransferFrame.class));
        short spi = secHeaderWrap.getShort();
        String password = spi2key.get((int) spi);
        byte[] iv = Arrays.copyOfRange(frame.getFrame(), secHeaderOffset + 2, secHeaderOffset + HEADER_LENGTH);
        // Now decrypt the body
        int dataFieldLength = frame.getLength() - secHeaderOffset - HEADER_LENGTH - TRAILER_LENGTH - (frame.isOcfPresent() ? 4 : 0) - (frame.isFecfPresent() ? 2 : 0);
        byte[] decryptedDataField = aesDecrypt(frame.getFrame(), secHeaderOffset + HEADER_LENGTH, dataFieldLength, password, iv);
        // Now verify that the trailer matches with the data
        // Use primary header, if present, secondary header and data field (without security header and trailer)
        byte[] trailer = computeTrailer(frame.getFrame(), secHeaderOffset, decryptedDataField);
        if(!Arrays.equals(frame.getFrame(), secHeaderOffset + HEADER_LENGTH + decryptedDataField.length, secHeaderOffset + HEADER_LENGTH + decryptedDataField.length + TRAILER_LENGTH,
                trailer, 0, trailer.length)) {
            throw new ReatmetricException("Trailer mismatch, TM frame corrupted on SC: " + frame.getSpacecraftId() +
                    "VC:" + frame.getVirtualChannelId());
        }
        // Now compose the decrypted frame
        byte[] newFrame = new byte[frame.getLength()];
        int currentOffset = 0;
        System.arraycopy(frame.getFrame(), 0, newFrame, currentOffset, secHeaderOffset); // Primary header and secondary header if present
        currentOffset += secHeaderOffset;
        // Security header
        System.arraycopy(frame.getFrame(), secHeaderOffset, newFrame, currentOffset, HEADER_LENGTH); // security header
        currentOffset += HEADER_LENGTH;
        // Data
        System.arraycopy(decryptedDataField, 0, newFrame, currentOffset, decryptedDataField.length);
        currentOffset += decryptedDataField.length;
        // Trailer
        System.arraycopy(trailer, 0, newFrame, currentOffset, trailer.length);
        currentOffset += trailer.length;
        // If OCF, copy
        if(frame.isOcfPresent()) {
            System.arraycopy(frame.getFrame(), frame.getOcfStart(), newFrame, currentOffset, 4);
        }
        // If FECF, recompute
        if(frame.isFecfPresent()) {
            short crc = Crc16Algorithm.getCrc16(newFrame, 0, newFrame.length - 2);
            newFrame[newFrame.length - 2] = (byte) (crc >> 8);
            newFrame[newFrame.length - 1] = (byte) (crc);
        }

        return new TmTransferFrame(newFrame, frame.isFecfPresent(), HEADER_LENGTH, trailer.length);
    }

    private byte[] computeTrailer(byte[] frame, int headerLength, byte[] decryptedDataField) throws ReatmetricException {
        int scopeLength = headerLength + decryptedDataField.length;
        byte[] scope = new byte[scopeLength];
        int offset = 0;
        System.arraycopy(frame, 0, scope, offset, headerLength);
        offset += headerLength;
        System.arraycopy(decryptedDataField, 0, scope, offset, decryptedDataField.length);
        // Done, now compute SHA-256
        return computeSHA256(scope);
    }

    private static byte[] computeSHA256(byte[] scope) throws ReatmetricException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.reset();
            md.update(scope);
            byte[] hashSignature = md.digest();
            return Arrays.copyOfRange(hashSignature, hashSignature.length - TRAILER_LENGTH, hashSignature.length);
        } catch (NoSuchAlgorithmException e) {
            throw new ReatmetricException("Hash function not defined: SHA-256", e);
        }
    }

    @Override
    public void dispose() {
        // Nothing
    }
}
