/**************************************************************************************
  	This file is part of GNU DataExplorer.

    GNU DataExplorer is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    DataExplorer is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with GNU DataExplorer.  If not, see <https://www.gnu.org/licenses/>.

    Copyright (c) 2026 Jyri Wennström, Oikumene Works
    refer to https://github.com/oikumene-works/tasi/tree/main/ta612c/software/dataexplorer-plugin
    Copyright (c) 2026 Winfried Bruegmann
****************************************************************************************/
package gde.device.tasi;

import gde.exception.TimeOutException;
import gde.messages.Messages;

import java.io.IOException;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Owns a live acquisition's connect/request/read/close lifecycle off the SWT
 * thread. Time is host receipt time, not device time. The existing live policy
 * permits a bounded number of repeated live requests after silence; this is
 * distinct from REC, which never retries a download automatically. Transport
 * and listener seams allow deterministic device-free lifecycle tests.
 */
public final class TA612CGathererThread extends Thread {
    /** Serial operations used by this worker; close must tolerate failed opens. */
    public interface Transport {
        /** Acquires exclusive ownership and opens the configured connection. */
        void connect() throws Exception;
        /** Sends the live request, initially or for a bounded silence restart. */
        void startLive() throws IOException;
        /** Returns decoded samples; an empty list or timeout gives no new sample. */
        List<TA612CFrameDecoder.Sample> readSamples() throws IOException, TimeOutException;
        /** Closes the owned transport before the terminal callback is delivered. */
        void close();
    }

    /** Worker-thread callbacks; UI implementations must marshal SWT work. */
    public interface Listener {
        /**
         * Receives every valid frame, including all-open frames. Elapsed time is
         * monotonic milliseconds from the first frame of the current probe-mask
         * segment. Its epoch is the acquisition's wall-clock anchor plus that
         * frame's monotonic offset, rounded to the nearest whole millisecond.
         * All-open frames still terminate a segment but create no RecordSet.
         */
        void onSample(TA612CFrameDecoder.Sample sample, double elapsedMs, long firstSampleEpochMs);
        /** Reports termination after attempted cleanup, including close failures. */
        void onStopped(String message, Exception failure);
    }

    private final Transport transport;
    private final Listener listener;
    private final long silenceNanos;
    private final int maxRestarts;
    private final LongSupplier nanoTime;
    private final LongSupplier wallTimeMillis;
    private volatile boolean stopRequested;

    /** Configures the accepted live policy: 1.5 s silence and three restarts. */
    public TA612CGathererThread(Transport transport, Listener listener) {
        this(transport, listener, 1500, 3);
    }

    /** Injects silence/restart bounds for tests without changing the wire command. */
    TA612CGathererThread(Transport transport, Listener listener, long silenceMs, int maxRestarts) {
        this(transport, listener, silenceMs, maxRestarts, System::nanoTime, System::currentTimeMillis);
    }

    /** Injects clocks for deterministic time-model tests; production uses host clocks. */
    TA612CGathererThread(Transport transport, Listener listener, long silenceMs, int maxRestarts,
            LongSupplier nanoTime, LongSupplier wallTimeMillis) {
        super("TA612C live acquisition");
        this.transport = transport;
        this.listener = listener;
        this.silenceNanos = silenceMs * 1_000_000L;
        this.maxRestarts = maxRestarts;
        this.nanoTime = nanoTime;
        this.wallTimeMillis = wallTimeMillis;
    }

    /** Requests cooperative cancellation and wakes sleeps; the worker owns close. */
    public void requestStop() {
        stopRequested = true;
        interrupt();
    }

    public boolean isStopRequested() { return stopRequested; }

    /**
     * Reads until stopped or failed, retaining data already delivered to the
     * listener. Any valid sample resets the silence-restart count, even when
     * all probes are open. Timeouts count as silence here; other read failures
     * terminate acquisition. Cleanup also runs after cancellation before open.
     */
    @Override
    public void run() {
        String message = Messages.getString(MessageIds.GDE_MSGI4110);
        Exception failure = null;
        long firstSampleNanos = 0;
        long firstSampleEpochMs = 0;
        boolean firstSample = true;
        long segmentStartNanos = 0;
        long segmentEpochMs = 0;
        int segmentMask = -1;
        try {
            if (stopRequested) return;
            transport.connect();
            if (stopRequested) return;
            transport.startLive();
            long lastActivity = nanoTime.getAsLong();
            int restarts = 0;
            while (!stopRequested) {
                List<TA612CFrameDecoder.Sample> samples;
                try {
                    samples = transport.readSamples();
                } catch (TimeOutException timeout) {
                    samples = List.of();
                }
                for (TA612CFrameDecoder.Sample sample : samples) {
                    if (stopRequested) break;
                    long now = nanoTime.getAsLong();
                    if (firstSample) {
                        firstSampleNanos = now;
                        firstSampleEpochMs = wallTimeMillis.getAsLong();
                        firstSample = false;
                    }
                    if (sample.validMask() != segmentMask) {
                        segmentMask = sample.validMask();
                        segmentStartNanos = now;
                        // Keep the acquisition's monotonic timeline, including periods
                        // with no probes. Re-reading wall time here could introduce
                        // clock-adjustment jumps or UI-processing delays into the data.
                        segmentEpochMs = firstSampleEpochMs
                                + Math.round((now - firstSampleNanos) / 1_000_000.0);
                    }
                    listener.onSample(sample, (now - segmentStartNanos) / 1_000_000.0, segmentEpochMs);
                    lastActivity = now;
                    restarts = 0;
                }
                if (stopRequested) break;
                if (nanoTime.getAsLong() - lastActivity >= silenceNanos) {
                    if (restarts == maxRestarts) {
                        throw new IOException(Messages.getString(MessageIds.GDE_MSGE4111, new Object[] {maxRestarts}));
                    }
                    transport.startLive();
                    ++restarts;
                    lastActivity = nanoTime.getAsLong();
                }
                Thread.sleep(10);
            }
        } catch (InterruptedException interrupted) {
            if (!stopRequested) {
                failure = interrupted;
                message = Messages.getString(MessageIds.GDE_MSGW4101);
            }
            Thread.currentThread().interrupt();
        } catch (Exception error) {
            if (!stopRequested) {
                failure = error;
                message = Messages.getString(MessageIds.GDE_MSGE4112, new Object[] {error.getMessage()});
            }
        } finally {
            stopRequested = true;
            try {
                transport.close();
            } catch (RuntimeException closeError) {
                if (failure == null) failure = closeError;
                else failure.addSuppressed(closeError);
                message += Messages.getString(MessageIds.GDE_MSGE4113);
            }
            listener.onStopped(message, failure);
        }
    }
}
