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

import gde.GDE;
import gde.comm.DeviceCommPort;
import gde.comm.IDeviceCommPort;
import gde.config.Settings;
import gde.data.Channel;
import gde.data.Channels;
import gde.data.Record;
import gde.data.RecordSet;
import gde.device.DeviceConfiguration;
import gde.device.IDevice;
import gde.exception.DataInconsitsentException;
import gde.messages.Messages;
import gde.ui.DataExplorer;
import java.io.FileNotFoundException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.bind.JAXBException;

/**
 * DataExplorer device entry point for live acquisition, read-only REC import
 * and native OSD restoration. Workers own serial I/O; callbacks marshal record
 * publication and widget updates onto SWT. Live samples publish incrementally,
 * while REC sets publish only after the complete pending import is prepared.
 * Each live/REC segment has a fixed probe-presence mask to prevent curves from
 * bridging missing-probe periods or storing fabricated temperatures.
 *
 * <p>Worker references remain set until their terminal UI callbacks run, keeping
 * Start/Stop and REC mutually exclusive in this device. The serial adapter also
 * enforces ownership across device instances through asynchronous port closure.
 */
public class TA612C extends DeviceConfiguration implements IDevice {
    private static final Logger LOG = Logger.getLogger(TA612C.class.getName());
    private final DataExplorer application;
    private TA612CSerialPort livePort;
    private TA612CGathererThread gatherer;
    private TA612CRecDownloader recDownloader;

    /**
     * Loads the XML configuration and registers a closed serial backend for core
     * port discovery. Menu setup is skipped in non-UI contexts such as tests.
     */
    public TA612C(String properties) throws FileNotFoundException, JAXBException {
        super(properties);
    		// initializing the resource bundle for this device
    		Messages.setDeviceResourceBundle("gde.device.tasi.messages", Settings.getInstance().getLocale(), this.getClass().getClassLoader()); //$NON-NLS-1$
        application = GDE.isWithUi() ? DataExplorer.getInstance() : null;
        // Core port discovery needs a registered backend before the first Start.
        // Construction keeps the physical port closed and sends no commands.
        livePort = new TA612CSerialPort(this, application);
        configureMenu();
    }

    /** Wraps an existing configuration with the same closed-backend/UI setup. */
    public TA612C(DeviceConfiguration configuration) {
        super(configuration);
    		// initializing the resource bundle for this device
    		Messages.setDeviceResourceBundle("gde.device.tasi.messages", Settings.getInstance().getLocale(), this.getClass().getClassLoader()); //$NON-NLS-1$
        application = GDE.isWithUi() ? DataExplorer.getInstance() : null;
        livePort = new TA612CSerialPort(this, application);
        configureMenu();
    }

    /** Connects core Start/Stop actions and the optional REC import menu to this device. */
    private void configureMenu() {
        if (application != null) {
            configureSerialPortMenu(DeviceCommPort.ICON_SET_START_STOP,
            		Messages.getString(MessageIds.GDE_MSGT4100), Messages.getString(MessageIds.GDE_MSGT4101));
            addRecMenu(application.getMenuBar().getImportMenu());
        }
    }

    /**
     * Adds one REC action per menu, identified by item data rather than its label.
     * Repeated setup must not accumulate duplicate entries. The action itself
     * checks that this device is still active before starting work.
     */
    void addRecMenu(Menu menu) {
        for (MenuItem item : menu.getItems()) if ("ta612c-rec".equals(item.getData())) return;
        new MenuItem(menu, SWT.SEPARATOR);
        MenuItem item = new MenuItem(menu, SWT.PUSH);
        item.setData(Messages.getString(MessageIds.GDE_MSGT4102));
        item.setText(Messages.getString(MessageIds.GDE_MSGT4103));
        item.addListener(SWT.Selection, event -> downloadRec());
    }

    /**
     * Handles the REC menu action on SWT: a second invocation cancels the active
     * transfer. Otherwise obtains an explicit interval, rechecks state after the
     * modal dialog, and launches one worker. The completion callback prepares
     * every segment before channel insertion and honors cancellation requested
     * after reception but before the queued UI callback runs.
     */
    private synchronized void downloadRec() {
        if (application.getActiveDevice() != this) return;
        if (recDownloader != null) { recDownloader.requestStop(); return; }
        if (gatherer != null) {
            application.openMessageDialog(Messages.getString(MessageIds.GDE_MSGT4104));
            return;
        }
        Long intervalMs = TA612CRecDialog.open(application.getShell());
        // The modal dialog dispatches events: recheck ownership and the selected device afterwards.
        if (intervalMs == null || gatherer != null || recDownloader != null || application.getActiveDevice() != this) return;
        Channel channel = Channels.getInstance().getActiveChannel();
        if (channel == null || channel.getNumber() != 1 || getNumberOfMeasurements(1) != 4) return;
        livePort = new TA612CSerialPort(this, application);
        recDownloader = new TA612CRecDownloader(livePort, (result, failure, cancelled) -> {
            if (failure != null) LOG.log(Level.WARNING, "REC download failed", failure);
            if (GDE.display == null || GDE.display.isDisposed()) return;
            GDE.display.asyncExec(() -> {
                synchronized (TA612C.this) {
                    boolean stopped = cancelled || recDownloader.isStopRequested();
                    recDownloader = null;
                    if (application.getActiveDevice() != TA612C.this) return;
                    application.setPortConnected(false);
                    if (stopped) {
                        application.setStatusMessage(Messages.getString(MessageIds.GDE_MSGT4105));
                    } else if (failure != null) {
                        application.openMessageDialog(Messages.getString(MessageIds.GDE_MSGW4100, new String[] {failure.getMessage()}));
                    } else {
                        try {
                            List<RecordSet> prepared = TA612CRecImport.prepare(TA612C.this, result, intervalMs, channel.getNextRecordSetNumber());
                            // Publish only after all segments have been validated and prepared.
                            for (RecordSet records : prepared) channel.put(records.getName(), records);
                            if (!prepared.isEmpty()) {
                                RecordSet first = prepared.get(0);
                                channel.setActiveRecordSet(first.getName());
                                application.getMenuToolBar().updateRecordSetSelectCombo();
                                application.updateAllTabs(false);
                                application.updateStatisticsData();
                                application.updateDataTable(first.getName(), false);
                            }
                            String message = Messages.getString(MessageIds.GDE_MSGI4100, new Object[] {result.samples().size(), prepared.size()});
                            if (prepared.isEmpty()) message += Messages.getString(MessageIds.GDE_MSGI4101);
                            application.setStatusMessage(message);
                            application.openMessageDialog(message);
                        } catch (Exception error) {
                            LOG.log(Level.WARNING, "REC import failed", error);
                            application.openMessageDialog(Messages.getString(MessageIds.GDE_MSGE4100, new String[] {error.getMessage()}));
                        }
                    }
                }
            });
        });
        application.setPortConnected(true);
        application.setStatusMessage(Messages.getString(MessageIds.GDE_MSGI4102));
        recDownloader.start();
    }

    @Override
    public IDeviceCommPort getCommunicationPort() { return livePort; }

    /**
     * Start/Stop entry point. Cancels REC or stops live when a worker
     * exists; otherwise validates the four-probe variable-time configuration and
     * starts live acquisition. Stop requests do not join the worker on SWT, since
     * its sample callback may itself be waiting for SWT via syncExec.
     */
    @Override
    public synchronized void open_closeCommPort() {
        if (application == null) throw new UnsupportedOperationException(Messages.getString(MessageIds.GDE_MSGE4101));
        if (recDownloader != null) {
            recDownloader.requestStop();
            application.setStatusMessage(Messages.getString(MessageIds.GDE_MSGI4103));
            return;
        }
        if (gatherer != null) {
            gatherer.requestStop();
            application.setStatusMessage(Messages.getString(MessageIds.GDE_MSGI4104));
            return;
        }
        Channel channel = Channels.getInstance().getActiveChannel();
        if (channel == null) return;
        if (channel.getNumber() != 1 || getChannelCount() != 1 || getNumberOfMeasurements(1) != 4 || getTimeStep_ms() >= 0) {
            application.openMessageDialog(Messages.getString(MessageIds.GDE_MSGI4105));
            return;
        }
        livePort = new TA612CSerialPort(this, application);
        gatherer = new TA612CGathererThread(livePort, new TA612CGathererThread.Listener() {
            private RecordSet recordSet;
            private int probeMask = -1;

            /**
             * Marshals each worker sample synchronously to SWT. A mask change
             * finalizes the preceding segment; an all-open mask creates no set.
             * Rechecks cancellation/device selection before publishing queued data.
             */
            @Override
            public void onSample(TA612CFrameDecoder.Sample sample, double elapsedMs, long firstSampleEpochMs) {
                if (GDE.display == null || GDE.display.isDisposed()) {
                    gatherer.requestStop();
                    return;
                }
                GDE.display.syncExec(() -> {
                    if (gatherer.isStopRequested()) return;
                    if (application.getActiveDevice() != TA612C.this) {
                        gatherer.requestStop();
                        return;
                    }
                    if (sample.validMask() != probeMask) {
                        if (recordSet != null) {
                            makeInActiveDisplayable(recordSet);
                            recordSet.updateVisibleAndDisplayableRecordsForTable();
                            application.updateStatisticsData();
                            application.updateDataTable(recordSet.getName(), false);
                            recordSet = null;
                        }
                        probeMask = sample.validMask();
                        if (probeMask == 0) {
                            application.setStatusMessage(Messages.getString(MessageIds.GDE_MSGI4106));
                            application.updateAllTabs(false);
                            return;
                        }
                        String presentProbes = TA612CRecImport.probeNames(probeMask);
                        String name = channel.getNextRecordSetNumber() + Messages.getString(MessageIds.GDE_MSGT4106, new String[] {presentProbes});
                        name = name.length() <= RecordSet.MAX_NAME_LENGTH ? name : name.substring(0, RecordSet.MAX_NAME_LENGTH);
                        recordSet = RecordSet.createRecordSet(name, TA612C.this, 1, true, false, true);
                        channel.put(name, recordSet);
                        channel.setActiveRecordSet(name);
                        channel.applyTemplateBasics(name);
                        configureLiveSegment(recordSet, probeMask, firstSampleEpochMs);
                        application.getMenuToolBar().updateRecordSetSelectCombo();
                        if (probeMask == 0x0F) {
                            application.setStatusMessage(Messages.getString(MessageIds.GDE_MSGI4107));
                        } else {
                            application.setStatusMessage(Messages.getString(MessageIds.GDE_MSGI4108, new Object[] {sample.presentProbes(), sample.openProbes()}));
                        }
                    }
                    if (recordSet == null) return; // Consecutive all-open frames carry no temperature data.
                    try {
                        appendLiveSample(recordSet, sample, elapsedMs);
                    } catch (DataInconsitsentException e) {
                        throw new IllegalStateException(e);
                    }
                    updateVisibilityStatus(recordSet, true);
                    recordSet.updateVisibleAndDisplayableRecordsForTable();
                    application.updateAllTabs(false);
                });
            }

            /**
             * Queues final UI cleanup after transport close has been attempted.
             * Already-collected live points remain available on stop or failure.
             */
            @Override
            public void onStopped(String message, Exception failure) {
                if (failure != null) LOG.log(Level.WARNING, message, failure);
                if (GDE.display != null && !GDE.display.isDisposed()) {
                    GDE.display.asyncExec(() -> {
                        synchronized (TA612C.this) { gatherer = null; }
                        if (application.getActiveDevice() == TA612C.this) {
                            if (recordSet != null) {
                                makeInActiveDisplayable(recordSet);
                                application.updateStatisticsData();
                                application.updateDataTable(recordSet.getName(), false);
                            }
                            application.setPortConnected(false);
                            application.setStatusMessage(message);
                        }
                    });
                }
            }
        });
        application.setPortConnected(true);
        application.setStatusMessage(Messages.getString(MessageIds.GDE_MSGI4109));
        gatherer.start();
    }

    /**
     * Sets variable host time and sparse storage on a new segment after template
     * basics are applied. Every segment retains the first decoded frame's epoch,
     * including when that first frame was all-open; elapsed offsets are not reset
     * by a presence change, preserving periods with no temperature points.
     */
    static void configureLiveSegment(RecordSet recordSet, int probeMask, long firstSampleEpochMs) {
        if (probeMask < 1 || probeMask > 0x0F) throw new IllegalArgumentException(Messages.getString(MessageIds.GDE_MSGE4102));
        recordSet.setTimeStep_ms(-1);
        recordSet.setStartTimeStamp(firstSampleEpochMs);
				String dateTime = new SimpleDateFormat("yyyy-MM-dd, HH:mm:ss").format(firstSampleEpochMs); //$NON-NLS-1$
				String recordDescription = "TA612C" + GDE.STRING_MESSAGE_CONCAT + Messages.getString(gde.messages.MessageIds.GDE_MSGT0129) + dateTime
						+ Messages.getString(MessageIds.GDE_MSGT4107, new String[] {TA612CRecImport.probeNames(probeMask)});
				recordSet.setRecordSetDescription(recordDescription);
        TA612CRecImport.configureMask(recordSet, probeMask);
    }

    /**
     * Appends only present temperatures with acquisition-relative milliseconds.
     * Checks both count and physical column identity before mutation: T1-only and
     * T2-only samples have equal widths but must not share the same segment.
     */
    static void appendLiveSample(RecordSet records, TA612CFrameDecoder.Sample sample, double elapsedMs)
            throws DataInconsitsentException {
        String[] stored = records.getNoneCalculationRecordNames();
        if (stored.length != Integer.bitCount(sample.validMask())) {
            throw new IllegalArgumentException(Messages.getString(MessageIds.GDE_MSGE4103));
        }
        int column = 0;
        for (int probe = 0; probe < TA612CFrameDecoder.PROBE_COUNT; ++probe) {
            if (sample.isValid(probe) && !stored[column++].equals(TA612CRecImport.probe(records, probe).getName())) {
                throw new IllegalArgumentException(Messages.getString(MessageIds.GDE_MSGE4103));
            }
        }
        records.addNoneCalculationRecordsPoints(sample.presentPoints(), elapsedMs);
    }

    /**
     * Implements the core's four-column conversion hook for one validated live
     * frame. Requires all probes; sparse live acquisition uses appendLiveSample.
     * The caller's point array is changed only after decoding succeeds.
     */
    @Override
    public int[] convertDataBytes(int[] points, byte[] frame) {
        if (points.length != 4) throw new IllegalArgumentException(Messages.getString(MessageIds.GDE_MSGE4104));
        int[] decoded = TA612CFrameDecoder.decode(frame).points();
        System.arraycopy(decoded, 0, points, 0, 4);
        return points;
    }

    /**
     * Restores native OSD data after the stored-column mask has been reconstructed.
     * OSD uses big-endian int32 thousandths Celsius, not the wire format. For
     * variable time, a block of count timestamps in 0.1 ms precedes the row-major
     * measurement block; fixed-step legacy files have no timestamp block.
     *
     * <p>Validates the entire length, timestamp order and absence of open-probe
     * sentinels before appending anything. The selected stored columns support
     * legacy four-probe OSD and newer sparse REC/live OSD through the same path.
     */
    @Override
    public void addDataBufferAsRawDataPoints(RecordSet records, byte[] buffer, int count, boolean showProgress)
            throws DataInconsitsentException {
        boolean variable = !records.isTimeStepConstant();
        String[] stored = records.getNoneCalculationRecordNames();
        long expected = (long) count * (stored.length * Integer.BYTES + (variable ? Integer.BYTES : 0));
        if (count < 0 || records.size() != 4 || stored.length < 1 || stored.length > 4
                || expected != buffer.length) {
            throw new DataInconsitsentException(Messages.getString(MessageIds.GDE_MSGE4105));
        }
        ByteBuffer data = ByteBuffer.wrap(buffer).order(ByteOrder.BIG_ENDIAN);
        int timestampBytes = variable ? count * Integer.BYTES : 0;
        // Validate the entire buffer before modifying records, so malformed input is atomic.
        int previousTime = -1;
        for (int i = 0; i < count; ++i) {
            if (variable) {
                int time = data.getInt(i * Integer.BYTES);
                if (time < 0 || time < previousTime) {
                    throw new DataInconsitsentException(Messages.getString(MessageIds.GDE_MSGE4106));
                }
                previousTime = time;
            }
            for (int probe = 0; probe < stored.length; ++probe) {
                int point = data.getInt(timestampBytes + (i * stored.length + probe) * Integer.BYTES);
                if (point == TA612CFrameDecoder.OPEN_PROBE_RAW * 100) {
                    throw new DataInconsitsentException(Messages.getString(MessageIds.GDE_MSGE4107));
                }
            }
        }
        String threadId = Long.toString(Thread.currentThread().threadId());
        int[] points = new int[stored.length];
        for (int i = 0; i < count; ++i) {
            for (int probe = 0; probe < stored.length; ++probe) {
                points[probe] = data.getInt(timestampBytes + (i * stored.length + probe) * Integer.BYTES);
            }
            if (variable) records.addTimeStep_ms(data.getInt(i * Integer.BYTES) / 10.0);
            records.addNoneCalculationRecordsPoints(points);
            if (showProgress && application != null && i % 100 == 0) {
                application.setProgress((int) (100L * i / count), threadId);
            }
        }
        makeInActiveDisplayable(records);
        records.syncScaleOfSyncableRecords();
        if (showProgress && application != null) application.setProgress(100, threadId);
    }

    /** Restores sparse storage before the core sizes or lazy-loads the OSD payload. */
    @Override
    public String[] crossCheckMeasurements(String[] properties, RecordSet records) {
        return TA612CRecImport.restoreMask(properties, records);
    }

    /** Reapplies the saved mask at the core's measurement-specialties restore hook. */
    @Override
    public void applyMeasurementSpecialties(String[] properties, RecordSet records) {
        TA612CRecImport.restoreMask(properties, records);
    }

    /** Applies core calibration to an engineering value; raw point scaling is elsewhere. */
    @Override
    public double translateValue(Record record, double value) {
        return (value - record.getReduction()) * record.getFactor() + record.getOffset();
    }

    /** Inverts calibration using the configured factor, which must be nonzero. */
    @Override
    public double reverseTranslateValue(Record record, double value) {
        return (value - record.getOffset()) / record.getFactor() + record.getReduction();
    }

    /** Fills displayed measurement cells in core order, leaving time column zero intact. */
    @Override
    public String[] prepareDataTableRow(RecordSet records, String[] row, int index) {
        int column = 1;
        for (Record record : records.getVisibleAndDisplayableRecordsForTable()) {
            row[column++] = record.getFormattedTableValue(index);
        }
        return row;
    }

    /**
     * Recomputes displayability from explicit presence and activity, optionally
     * requiring stored points. Temperature sign/magnitude does not decide validity.
     * User visibility is separate from this eligibility and from OSD storage.
     */
    @Override
    public void updateVisibilityStatus(RecordSet records, boolean checkData) {
        int visible = 0;
        for (int i = 0; i < records.size(); ++i) {
            Record record = TA612CRecImport.probe(records, i);
            // Zero and negative temperatures are valid; do not use hasReasonableData().
            boolean displayable = !TA612CRecImport.isAbsent(record) && Boolean.TRUE.equals(record.isActive()) && (!checkData || record.realSize() > 0);
            record.setDisplayable(displayable);
            if (displayable) ++visible;
        }
        records.setConfiguredDisplayable(visible);
    }

    /** Refreshes display/table eligibility after acquisition or restoration. */
    @Override
    public void makeInActiveDisplayable(RecordSet records) {
        updateVisibilityStatus(records, true);
        records.updateVisibleAndDisplayableRecordsForTable();
    }

    /** Declares calibration, scale and presence properties that must survive OSD round trips. */
    @Override
    public String[] getUsedPropertyKeys() { return new String[] {OFFSET, FACTOR, REDUCTION, SYNC_ORDINAL, TA612CRecImport.PRESENT}; }

    /** Rejects LogView key mapping; no TA612C LogView import contract is defined. */
    @Override
    public HashMap<String, String> getLovKeyMappings(HashMap<String, String> map) { throw lovUnsupported(); }
    /** Rejects LogView configuration conversion instead of guessing a format. */
    @Override
    public String getConvertedRecordConfigurations(HashMap<String, String> header, HashMap<String, String> map, int channel) { throw lovUnsupported(); }
    /** Reports no supported LogView data layout. */
    @Override
    public int getLovDataByteSize() { return 0; }
    /** Rejects LogView data before any points can be appended. */
    @Override
    public void addConvertedLovDataBufferAsRawDataPoints(RecordSet records, byte[] buffer, int count, boolean progress) { throw lovUnsupported(); }
    /** Gives all unsupported LogView entry points the same explicit failure. */
    private static UnsupportedOperationException lovUnsupported() {
        return new UnsupportedOperationException(Messages.getString(MessageIds.GDE_MSGE4108));
    }
}
