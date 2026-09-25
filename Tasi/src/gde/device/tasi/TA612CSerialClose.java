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

import gde.comm.IDeviceCommPort;
import gde.messages.Messages;
import gde.comm.DeviceJavaSerialCommPortImpl;

/** DataExplorer closes serial asynchronously. Keep TA612C ownership until it finishes.
 * The core exposes no public close-wait API. This narrow version-specific reflection also
 * works when the plugin and core use different class loaders.
 */
public final class TA612CSerialClose {
    /** Utility class; the core backend owns the close thread. */
    private TA612CSerialClose() { }
    /**
     * Waits at most two seconds for the Java serial backend's current close
     * thread. Other backend types need no handling here. Interrupts, including
     * worker cancellation, are remembered and restored after waiting so they
     * cannot release ownership while the asynchronous close is still running.
     *
     * @throws IllegalStateException if reflection fails or closure times out;
     *         the caller must retain its ownership guard on either failure
     */
    public static void await(IDeviceCommPort port) {
        if (!(port instanceof DeviceJavaSerialCommPortImpl serial)) return;
        Thread closing;
        try {
            var field = DeviceJavaSerialCommPortImpl.class.getDeclaredField("closeThread");
            field.setAccessible(true);
            closing = (Thread) field.get(serial);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException(Messages.getString(MessageIds.GDE_MSGE4134), e);
        }
        if (closing == null) return;
        boolean interrupted = Thread.interrupted();
        long deadline = System.nanoTime() + 2_000_000_000L;
        try {
            while (closing.isAlive()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw new IllegalStateException(Messages.getString(MessageIds.GDE_MSGE4135));
                try { closing.join(Math.max(1, remaining / 1_000_000)); }
                catch (InterruptedException e) { interrupted = true; }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }
}
