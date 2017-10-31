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

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Deque;
import java.util.Iterator;

import com.google.protobuf.ByteString;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.ReaderException;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.techshroom.protos.Data;
import com.techshroom.protos.End;
import com.techshroom.protos.PhosphrMessage;
import com.techshroom.protos.Request;
import com.techshroom.protos.Start;

public class StandardPhosphrEncoder implements PhosphrEncoder {

    public static StandardPhosphrEncoder fromBytes(byte[] data) {
        ByteBuffer buf = ByteBuffer.allocate(data.length);
        buf.put(data);
        buf.flip();
        return new StandardPhosphrEncoder(buf.asReadOnlyBuffer());
    }

    public static StandardPhosphrEncoder fromBytes(ByteBuffer data) {
        ByteBuffer buf = ByteBuffer.allocate(data.remaining());
        buf.put(data);
        buf.flip();
        return new StandardPhosphrEncoder(buf.asReadOnlyBuffer());
    }

    private enum State {

        DISP_START,
        DISP_DATA,
        DISP_END,
        TERMINATED

    }

    private static final int PACKET_SIZE = 2048;

    private final ByteMatrix startImage;
    private final ByteMatrix endImage;
    private final ByteBuffer data;
    private final Deque<BinaryBitmap> unprocessed = new ArrayDeque<>();
    private final BitSet likelyRecv;
    private State state = State.DISP_START;

    private StandardPhosphrEncoder(ByteBuffer data) {
        this.data = data;
        this.startImage = MsgHelper.encode(PhosphrMessage.newBuilder()
                .setSequence(0)
                .setStart(Start.newBuilder()
                        .setPacketSize(PACKET_SIZE)
                        .setPacketCount(getPacketCount())
                        .build())
                .build());
        this.endImage = MsgHelper.encode(PhosphrMessage.newBuilder()
                .setSequence(0)
                .setEnd(End.getDefaultInstance())
                .build());
        likelyRecv = new BitSet(getPacketCount());
    }

    private int getPacketCount() {
        return (data.limit() + (PACKET_SIZE - 1)) / PACKET_SIZE;
    }

    @Override
    public boolean isDataSendCompleted() {
        return state == State.TERMINATED;
    }

    @Override
    public ByteMatrix getNextImage(Iterator<BinaryBitmap> image) {
        image.forEachRemaining(unprocessed::addLast);
        changeState();
        switch (state) {
            case DISP_START:
                return startImage;
            case DISP_DATA:
                return nextDataImage();
            case DISP_END:
                return endImage;
            default:
                return endImage;
        }
    }

    private ByteMatrix nextDataImage() {
        int next = likelyRecv.nextClearBit(0);
        data.position(next * PACKET_SIZE);
        int size = Math.min(PACKET_SIZE, data.remaining());
        return MsgHelper.encode(PhosphrMessage.newBuilder()
                .setSequence(next)
                .setData(Data.newBuilder().setContent(ByteString.copyFrom(data, size)))
                .build());
    }

    private void changeState() {
        while (!unprocessed.isEmpty()) {
            PhosphrMessage msg;
            try {
                msg = MsgHelper.decode(unprocessed.pollFirst());
            } catch (RuntimeException e) {
                if (e.getCause() instanceof ReaderException) {
                    continue;
                }
                throw e;
            }
            switch (state) {
                case DISP_START:
                    if (msg.hasStart() && msg.getSequence() == 1 && msg.getStart().getPacketCount() == getPacketCount()) {
                        state = State.DISP_DATA;
                    }
                    break;
                case DISP_DATA:
                    if (msg.hasRequest()) {
                        processRequestMsg(msg);
                    }
                    break;
                case DISP_END:
                    if (msg.hasEnd() && msg.getSequence() == 1) {
                        state = State.TERMINATED;
                    }
                    if (msg.hasRequest()) {
                        processRequestMsg(msg);
                        if (likelyRecv.cardinality() < getPacketCount()) {
                            state = State.DISP_DATA;
                        }
                    }
                    break;
                case TERMINATED:
                default:
                    break;
            }
        }
        if (state == State.DISP_DATA) {
            if (likelyRecv.cardinality() == getPacketCount()) {
                state = State.DISP_END;
            }
        }
    }

    private void processRequestMsg(PhosphrMessage msg) {
        Request req = msg.getRequest();
        likelyRecv.set(0, getPacketCount());
        req.getMissedPacketsList().forEach(likelyRecv::clear);
    }

}
