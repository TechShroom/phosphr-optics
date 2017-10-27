package com.techshroom.phosphr;

import static com.google.common.base.Preconditions.checkState;

import java.nio.ByteBuffer;
import java.util.BitSet;
import java.util.Iterator;
import java.util.Optional;

import com.google.common.collect.AbstractIterator;
import com.google.zxing.BinaryBitmap;
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
    private transient byte[] cachedData;

    @Override
    public Optional<ByteMatrix> consumeImage(BinaryBitmap image) {
        PhosphrMessage msg;
        try {
            msg = MsgHelper.decode(image);
        } catch (Exception e) {
            return Optional.empty();
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
        if (!hasResult()) {
            return Optional.of(PhosphrMessage.newBuilder()
                    .setSequence(0)
                    .setRequest(Request.newBuilder()
                            .addAllMissedPackets(this::missingPacketIter)
                            .build())
                    .build());
        }
        return Optional.empty();
    }

    private Iterator<Integer> missingPacketIter() {
        return new AbstractIterator<Integer>() {

            private int index;

            @Override
            protected Integer computeNext() {
                while (foundPackets.get(index)) {
                    index++;
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
        foundPackets.set(seq);
        data.position(seq * packetSize);
        checkState(d.getContent().size() == packetSize, "incorrect size of packet, expected %s got %s", packetSize, d.getContent().size());
        data.put(d.getContent().asReadOnlyByteBuffer());
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
            data.mark();
            data.position(0);
            cachedData = new byte[data.remaining()];
            data.get(cachedData);
            data.reset();
        }
        return Optional.of(cachedData);
    }

}
