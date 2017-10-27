package com.techshroom.phosphr;

import java.util.Iterator;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.qrcode.encoder.ByteMatrix;

/**
 * Encoder for the phosphr protocol. Takes bytes and an image stream, and
 * provides protocol images.
 */
public interface PhosphrEncoder {

    // bytes are passed in the constructors

    /**
     * Takes the images received since last call, and provides the next image to
     * display.
     * 
     * @param image
     *            - the images received
     * @return the next image to show
     */
    ByteMatrix getNextImage(Iterator<BinaryBitmap> image);

}
