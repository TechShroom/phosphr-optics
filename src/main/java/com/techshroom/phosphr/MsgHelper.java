package com.techshroom.phosphr;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

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
        String strData = StandardCharsets.ISO_8859_1.decode(ByteBuffer.wrap(data)).toString();
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
        PhosphrMessage msg;
        try {
            msg = PhosphrMessage.parseFrom(result.getRawBytes());
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
        return msg;
    }

    private MsgHelper() {
    }
}
