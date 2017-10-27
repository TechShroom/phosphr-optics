package com.techshroom.phosphr;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Deque;
import java.util.Iterator;

import com.google.protobuf.ByteString;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.Encoder;
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

    private static final ByteMatrix HELLO_WORLD;
    static {
        try {
            HELLO_WORLD = Encoder.encode("Hello, World!", ErrorCorrectionLevel.H).getMatrix();
        } catch (Exception e) {
            throw new IllegalStateException("Hello World failed to init.", e);
        }
    }

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
        return data.limit() / PACKET_SIZE;
    }

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
                return HELLO_WORLD;
        }
    }

    private ByteMatrix nextDataImage() {
        int next = likelyRecv.nextClearBit(0);
        likelyRecv.set(next);
        data.position(next * PACKET_SIZE);
        return MsgHelper.encode(PhosphrMessage.newBuilder()
                .setData(Data.newBuilder().setContent(ByteString.copyFrom(data, PACKET_SIZE)))
                .build());
    }

    private void changeState() {
        while (!unprocessed.isEmpty()) {
            PhosphrMessage msg = MsgHelper.decode(unprocessed.pollFirst());
            switch (state) {
                case DISP_START:
                    if (msg.hasStart() && msg.getSequence() == 1 && msg.getStart().getPacketCount() == getPacketCount()) {
                        state = State.DISP_DATA;
                    }
                    return;
                case DISP_DATA:
                    if (msg.hasRequest()) {
                        Request req = msg.getRequest();
                        req.getMissedPacketsList().forEach(likelyRecv::clear);
                    }
                    if (likelyRecv.cardinality() == getPacketCount()) {
                        state = State.DISP_END;
                    }
                    return;
                case DISP_END:
                    if (msg.hasEnd() && msg.getSequence() == 1) {
                        state = State.TERMINATED;
                    }
                    if (msg.hasRequest()) {
                        Request req = msg.getRequest();
                        req.getMissedPacketsList().forEach(likelyRecv::clear);
                        if (likelyRecv.cardinality() < getPacketCount()) {
                            state = State.DISP_DATA;
                        }
                    }
                    return;
                case TERMINATED:
                default:
                    return;
            }
        }
    }

}
