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

import gde.data.Record;
import gde.data.RecordSet;
import gde.device.DataTypes;
import gde.exception.DataInconsitsentException;
import gde.messages.Messages;
import gde.utils.StringHelper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Prepares REC data before publication. Live and REC segments each store four
 * physical columns. A per-probe flag distinguishes an open input's zero
 * placeholder from a connected probe measuring zero degrees.
 * RecordSet operations belong to the caller's DataExplorer/UI context; this
 * class performs no transport operations.
 */
public final class TA612CRecImport {
    // Older readers use the legacy key to infer a compact row width.
    public static final String PRESENT = "ta612c_probe_present";
    private static final String LEGACY_PRESENT = "ta612c_rec_present";
    private static final int ALL_PROBES = 0x0F;

    private TA612CRecImport() { }

    /**
     * Converts explicit user-entered seconds to exact integral milliseconds,
     * accepting either decimal separator. Bounds are 1 ms through one day;
     * non-integral milliseconds are rejected rather than silently rounded.
     * This value is never read from or written to the meter.
     */
    public static long parseIntervalMs(String seconds) {
        try {
            long value = new BigDecimal(seconds.trim().replace(',', '.')).movePointRight(3).longValueExact();
            if (value < 1 || value > 86400000) throw new IllegalArgumentException();
            return value;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(Messages.getString(MessageIds.GDE_MSGE4124), e);
        }
    }

    /**
     * Builds all fixed-mask segments before the caller inserts any into a channel.
     * Each segment starts at relative zero. Its original sample indices and
     * inferred recording offset remain in the persisted description, preserving
     * all-open gaps without using a fictitious date. Epoch zero explicitly
     * denotes an unknown recording date. Empty/all-open input yields no RecordSets and
     * does not prove empty meter memory. The result's completeness caveat is
     * copied into every segment's persisted description.
     *
     * @param firstNumber number used for the first generated RecordSet name
     * @throws IllegalArgumentException on invalid interval, OSD time overflow or
     *         excessive segmentation; no prepared sets have been published
     * @throws DataInconsitsentException if the core rejects prepared point data
     */
    public static List<RecordSet> prepare(TA612C device, TA612CRecDownloader.Result result, long intervalMs, int firstNumber)
            throws DataInconsitsentException {
        if (intervalMs < 1 || intervalMs > 86400000) throw new IllegalArgumentException(Messages.getString(MessageIds.GDE_MSGE4125));
        List<TA612CRecDecoder.Sample> samples = result.samples();
        // The core OSD writer truncates variable times to signed int32 in 0.1 ms.
        if (Math.max(0, samples.size() - 1) * intervalMs > Integer.MAX_VALUE / 10L)
            throw new IllegalArgumentException(Messages.getString(MessageIds.GDE_MSGE4126));
        String absentRanges = allAbsentRanges(samples);
        List<RecordSet> prepared = new ArrayList<>();
				for (int first = 0; first < samples.size();) {
					int mask = samples.get(first).validMask();
					int end = first + 1;
					while (end < samples.size() && samples.get(end).validMask() == mask)
						++end;
					if (mask != 0) {
						if (prepared.size() >= 1000) throw new IllegalArgumentException(Messages.getString(MessageIds.GDE_MSGE4127));
						String name = (firstNumber + prepared.size()) + Messages.getString(MessageIds.GDE_MSGT4113, new String[] { probeNames(mask) });
						name = name.length() <= RecordSet.MAX_NAME_LENGTH ? name : name.substring(0, RecordSet.MAX_NAME_LENGTH);
						RecordSet records = RecordSet.createRecordSet(name, device, 1, true, false, false);
						configureMask(records, mask);
						records.setTimeStep_ms(-1);
						records.setStartTimeStamp(0); // Explicit unknown epoch placeholder, never download time.
						records.setRecordSetDescription(
								Messages.getString(MessageIds.GDE_MSGT4114, new Object[] { 
										BigDecimal.valueOf(intervalMs, 3).stripTrailingZeros().toPlainString(), 
										first, 
										(end - 1), 
										samples.size(),
										samples.get(first).sourceFrame(), 
										samples.get(end - 1).sourceFrame(), 
										probeNames(mask), 
										absentRanges, 
										result.completionDescription(), 
										result.frames(), 
										result.emptyFrames(),
										BigDecimal.valueOf(first * intervalMs, 3).stripTrailingZeros().toPlainString() }));
						for (int i = first; i < end; ++i) {
							records.addNoneCalculationRecordsPoints(samples.get(i).points(), (i - first) * intervalMs);
						}
						device.makeInActiveDisplayable(records);
						records.syncScaleOfSyncableRecords();
						prepared.add(records);
					}
					first = end;
				}
        return List.copyOf(prepared);
    }

    /** Sets physical storage and acquisition flags. Device XML supplies the scale references. */
    static void configureMask(RecordSet records, int mask) {
        applyMask(records, mask, true);
    }

    private static void applyMask(RecordSet records, int mask, boolean newSegment) {
        if (records.size() != 4 || mask < 1 || mask > ALL_PROBES) {
            throw new IllegalArgumentException(Messages.getString(MessageIds.GDE_MSGE4128));
        }
        String[] stored = new String[4];
        for (int p = 0; p < stored.length; ++p) {
            Record record = probe(records, p);
            boolean present = (mask & (1 << p)) != 0;
            if (record.getProperty(PRESENT) == null) record.createProperty(PRESENT, DataTypes.BOOLEAN, present);
            else record.getProperty(PRESENT).setValue(Boolean.toString(present));
            record.setActive(present);
            record.setDisplayable(present);
            // Templates can inherit the preceding segment's hidden curves.
            // On reopen, retain the saved selection of the connected probes.
            if (newSegment || !present) record.setVisible(present);
            stored[p] = record.getName();
        }
        records.setNoneCalculationRecordNames(stored);
    }

    /** Finds a physical zero-based probe ordinal regardless of UI name order. */
    static Record probe(RecordSet records, int ordinal) {
        for (Record record : records.getValues()) if (record.getOrdinal() == ordinal) return record;
        throw new IllegalArgumentException("TA612C probe ordinal missing: " + ordinal);
    }

    /**
     * Reads the saved flags by physical probe, before the core sizes the OSD payload.
     * Unmarked four-column files use the standard reader path. Reject the older
     * marked format even for an all-connected first set: a later set may be compact.
     */
    static String[] restoreMask(String[] properties, RecordSet records) {
        if (properties.length != 4 || records.size() != 4) throw new IllegalArgumentException(Messages.getString(MessageIds.GDE_MSGE4129));
        String[] recordKeys = new String[properties.length];
        int seen = 0, current = 0, legacy = 0, present = 0;
        for (int i = 0; i < properties.length; ++i) {
            HashMap<String, String> standard = StringHelper.splitString(properties[i], Record.DELIMITER, Record.propertyKeys);
            String name = standard.get(Record.NAME);
            Record record = records.get(name);
            if (record == null) throw new IllegalArgumentException(Messages.getString(MessageIds.GDE_MSGE4130, new String[] {name}));
            int bit = 1 << record.getOrdinal();
            if ((seen & bit) != 0) throw new IllegalArgumentException(Messages.getString(MessageIds.GDE_MSGE4131));
            seen |= bit;
            recordKeys[i] = name;
            for (String entry : properties[i].split("\\|")) {
                String key;
                if (entry.startsWith(PRESENT + "_")) key = PRESENT;
                else if (entry.startsWith(LEGACY_PRESENT + "_")) key = LEGACY_PRESENT;
                else continue;

                if (((current | legacy) & bit) != 0) throw new IllegalArgumentException(Messages.getString(MessageIds.GDE_MSGE4131));
                if (entry.equals(key + "_BOOLEAN=true")) present |= bit;
                else if (!entry.equals(key + "_BOOLEAN=false")) throw new IllegalArgumentException(Messages.getString(MessageIds.GDE_MSGE4132));
                if (key.equals(PRESENT)) current |= bit;
                else legacy |= bit;
            }
        }
        if (current == 0 && legacy == 0) return recordKeys;
        if (current != ALL_PROBES && legacy != ALL_PROBES) throw new IllegalArgumentException(Messages.getString(MessageIds.GDE_MSGE4133));
        if (legacy == ALL_PROBES) throw new IllegalArgumentException(Messages.getString(MessageIds.GDE_MSGE4137));
        applyMask(records, present, false);
        return recordKeys;
    }

    /** Only an explicit false marker denotes absence; legacy records lack it. */
    static boolean isAbsent(Record record) {
        return record.getProperty(PRESENT) != null && "false".equals(record.getProperty(PRESENT).getValue());
    }
    /** Formats a presence mask as physical names such as T2+T4 for provenance. */
    static String probeNames(int mask) {
        List<String> names = new ArrayList<>();
        for (int p = 0; p < 4; ++p) if ((mask & (1 << p)) != 0) names.add("T" + (p + 1));
        return String.join("+", names);
    }
    /**
     * Lists inclusive, zero-based all-open sample ranges for every segment's
     * description. These groups create no points but still occupy inferred time.
     */
    private static String allAbsentRanges(List<TA612CRecDecoder.Sample> samples) {
        List<String> ranges = new ArrayList<>();
        for (int i = 0; i < samples.size(); ++i) if (samples.get(i).validMask() == 0) {
            int first = i;
            while (i + 1 < samples.size() && samples.get(i + 1).validMask() == 0) ++i;
            ranges.add(first == i ? "" + first : first + ".." + i);
        }
        return ranges.isEmpty() ? Messages.getString(MessageIds.GDE_MSGT4115) : String.join(", ", ranges);
    }
}
