package com.techshroom.phosphr;

import java.util.Optional;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.qrcode.encoder.ByteMatrix;

/**
 * Decodes a stream of images into bytes using the phosphr protocol.
 */
public interface PhosphrDecoder {

    /**
     * Consume the next captured image in the stream.
     * 
     * @param image
     *            - the captured image
     * @return the image to send back, if any
     */
    Optional<ByteMatrix> consumeImage(BinaryBitmap image);

    /**
     * When the data is fully loaded, the result will become available here.
     * 
     * @return an Optional wrapping the result data
     */
    Optional<byte[]> getResult();

}
