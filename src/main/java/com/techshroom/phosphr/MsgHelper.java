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

import java.util.Base64;

import com.google.common.base.Throwables;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.Result;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.QRCodeReader;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.Encoder;
import com.techshroom.protos.PhosphrMessage;

public final class MsgHelper {

    public static ByteMatrix encode(PhosphrMessage pm) {
        byte[] data = pm.toByteArray();
        String strData = Base64.getEncoder().encodeToString(data);
        try {
            return Encoder.encode(strData, ErrorCorrectionLevel.L).getMatrix();
        } catch (WriterException e) {
            throw new RuntimeException(e);
        }
    }

    public static PhosphrMessage decode(BinaryBitmap mat) {
        Result result;
        try {
            result = new QRCodeReader().decode(mat);
        } catch (Exception e) {
            Throwables.throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
        String strData = result.getText();
        byte[] data = Base64.getDecoder().decode(strData);
        PhosphrMessage msg;
        try {
            msg = PhosphrMessage.parseFrom(data);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
        return msg;
    }

    private MsgHelper() {
    }
}
