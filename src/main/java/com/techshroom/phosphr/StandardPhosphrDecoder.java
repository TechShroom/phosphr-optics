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

import static com.google.common.base.Preconditions.checkState;

import java.nio.ByteBuffer;
import java.util.BitSet;
import java.util.Iterator;
import java.util.Optional;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableList;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.ReaderException;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.techshroom.protos.Data;
import com.techshroom.protos.PhosphrMessage;
import com.techshroom.protos.Request;

/**
 * The usual implementation of the decoder.
 */
public class StandardPhosphrDecoder implements PhosphrDecoder {

    private int numPackets;
    private int packetSize;
    private BitSet foundPackets;
    private ByteBuffer data;
    private int maxIndex;
    private transient byte[] cachedData;

    @Override
    public Optional<ByteMatrix> consumeImage(BinaryBitmap image) {
        PhosphrMessage msg;
        try {
            msg = MsgHelper.decode(image);
        } catch (RuntimeException e) {
            if (e.getCause() instanceof ReaderException) {
                return Optional.empty();
            }
            throw e;
        }
        return processMessage(msg).map(MsgHelper::encode);
    }

    private Optional<PhosphrMessage> processMessage(PhosphrMessage msg) {
        switch (msg.getKindCase()) {
            case START:
                // reply with same message, sequence incremented
                numPackets = msg.getStart().getPacketCount();
                packetSize = msg.getStart().getPacketSize();
                foundPackets = new BitSet(numPackets);
                data = ByteBuffer.allocate(numPackets * packetSize);
                return Optional.of(msg.toBuilder().setSequence(msg.getSequence() + 1).build());
            case END:
                // if needed, reply with requests
                if (!hasResult()) {
                    break; // does request reply
                }
                // reply with end message, sequence incremented
                return Optional.of(msg.toBuilder().setSequence(msg.getSequence() + 1).build());
            case DATA:
                // copy in data, send back requests (handled in default)
                readData(msg.getSequence(), msg.getData());
                break;
            default:
                throw new IllegalStateException("Unexpected packet: " + msg.getKindCase());
        }
        return Optional.of(PhosphrMessage.newBuilder()
                .setSequence(0)
                .setRequest(Request.newBuilder()
                        .addAllMissedPackets(this::missingPacketIter)
                        .build())
                .build());
    }

    private Iterator<Integer> missingPacketIter() {
        return new AbstractIterator<Integer>() {

            private int index;

            @Override
            protected Integer computeNext() {
                while (foundPackets.get(index) && index < numPackets) {
                    index++;
                }
                if (index >= numPackets) {
                    return endOfData();
                }
                int ret = index;
                index++;
                return ret;
            }
        };
    }

    private void readData(int seq, Data d) {
        if (foundPackets == null) {
            return;
        }
        if (foundPackets.get(seq)) {
            // TODO should we validate?
            return;
        }
        System.err.println("get packet " + seq + ", missing " + ImmutableList.copyOf(missingPacketIter()));
        foundPackets.set(seq);
        data.position(seq * packetSize);
        checkState(d.getContent().size() <= packetSize, "incorrect size of packet, expected %s got %s", packetSize, d.getContent().size());
        data.put(d.getContent().asReadOnlyByteBuffer());
        maxIndex = Math.max(maxIndex, data.position());
    }

    private boolean hasResult() {
        if (foundPackets == null) {
            return false;
        }
        return foundPackets.cardinality() == numPackets;
    }

    @Override
    public Optional<byte[]> getResult() {
        if (!hasResult()) {
            return Optional.empty();
        }
        if (cachedData == null) {
            int pos = data.position();
            int lim = data.limit();
            data.position(0);
            data.limit(maxIndex);
            cachedData = new byte[data.remaining()];
            data.get(cachedData);
            data.position(pos);
            data.limit(lim);
        }
        return Optional.of(cachedData);
    }

}
