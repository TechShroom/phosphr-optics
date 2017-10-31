package com.techshroom.phosphr;

import static org.junit.Assert.assertArrayEquals;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.encoder.ByteMatrix;

public class PhosphrBasicTest {

    public static void main(String[] args) {
        byte[] originalData = "Phosphr Basic Transmission Test".getBytes(StandardCharsets.UTF_8);
        PhosphrDecoder dec = new StandardPhosphrDecoder();
        PhosphrEncoder enc = StandardPhosphrEncoder.fromBytes(originalData);

        byte[] data = runTransfer(dec, enc);
        assertArrayEquals(originalData, data);
    }

    private static byte[] runTransfer(PhosphrDecoder dec, PhosphrEncoder enc) {
        List<BinaryBitmap> maps = new ArrayList<>();
        while (!enc.isDataSendCompleted()) {
            ByteMatrix nextImage = enc.getNextImage(maps.iterator());
            maps.clear();
            Optional<ByteMatrix> img = dec.consumeImage(bitmap(nextImage));
            if (img.isPresent()) {
                maps.add(bitmap(img.get()));
            }
        }
        return dec.getResult().get();
    }

    private static final int QZONE = 20;

    private static BinaryBitmap bitmap(ByteMatrix i) {
        BufferedImage rendered = new BufferedImage(i.getWidth() + QZONE * 2, i.getHeight() + QZONE * 2, BufferedImage.TYPE_BYTE_GRAY);
        Graphics g = rendered.getGraphics();
        g.setColor(Color.white);
        g.fillRect(0, 0, rendered.getWidth(), rendered.getHeight());
        g.dispose();
        for (int x = QZONE; x < i.getWidth() + QZONE; x++) {
            for (int y = QZONE; y < i.getHeight() + QZONE; y++) {
                if (i.get(x - QZONE, y - QZONE) == 1) {
                    rendered.setRGB(x, y, 0);
                }
            }
        }
        BinaryBitmap bm = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(rendered)));
        return bm;
    }

}
