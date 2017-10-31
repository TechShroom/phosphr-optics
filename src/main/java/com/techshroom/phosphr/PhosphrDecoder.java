/*
 * This file is part of phosphr-optics, licensed under the MIT License (MIT).
 *
 * Copyright (c) TechShroom Studios <https://techshroom.com>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
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
