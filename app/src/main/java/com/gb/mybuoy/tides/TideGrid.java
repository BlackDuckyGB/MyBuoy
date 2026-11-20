/*
 * MyBuoy — outdoor navigation & mark-tracking for boating, hiking, foraging and discovery walks.
 * Copyright (C) 2026 Golden-Block
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * For commercial licensing (use without AGPL obligations), contact:
 * licence@golden-block.fr
 */
/*
 * packed tide grid reader.
 *
 * Reads the compact binary produced by the converter. Only the ocean cells
 * are stored; an occupancy bitmap plus one row index gives random access
 * without holding anything in memory, so the file can be memory mapped
 * straight from storage.
 *
 * Layout, little endian throughout:
 *
 *   magic      8 bytes, "EOT20PK2"
 *   int32      number of constituents
 *   float64    first longitude, longitude step
 *   int32      number of columns
 *   float64    first latitude, latitude step
 *   int32      number of rows
 *   int32      number of ocean cells
 *   int64[]    occupancy bitmap, one bit per cell, row major
 *   int32[]    index of the first ocean cell of each row
 *   per constituent:
 *     8 bytes  name, null padded
 *     float32  real scale, imaginary scale, in centimetres per unit
 *     int16[]  real part, ocean cells only
 *     int16[]  imaginary part, ocean cells only
 */
package com.gb.mybuoy.tides;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.LongBuffer;
import java.nio.MappedByteBuffer;
import java.nio.ShortBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Bilinear sampler over the packed constituent grids. */
public final class TideGrid
{
    private static final String MAGIC = "EOT20PK2";

    private final double _lonMin;
    private final double _lonStep;
    private final int _columnCount;
    private final double _latMin;
    private final double _latStep;
    private final int _rowCount;
    private final int _oceanCount;

    private final LongBuffer _occupancy;
    private final int[] _rowStart;

    private final List<String> _names = new ArrayList<>();
    private final List<double[]> _scales = new ArrayList<>();
    private final List<ShortBuffer> _real = new ArrayList<>();
    private final List<ShortBuffer> _imaginary = new ArrayList<>();

    public TideGrid(String path) throws IOException
    {
        RandomAccessFile file = new RandomAccessFile(path, "r");
        FileChannel channel = file.getChannel();

        MappedByteBuffer buffer = channel.map(
                FileChannel.MapMode.READ_ONLY, 0L, channel.size());

        buffer.order(ByteOrder.LITTLE_ENDIAN);

        byte[] magic = new byte[8];
        buffer.get(magic);

        if (!MAGIC.equals(new String(magic, StandardCharsets.US_ASCII)))
        {
            file.close();
            throw new IOException("Not a packed tide grid: " + path);
        }

        int constituentCount = buffer.getInt();

        _lonMin = buffer.getDouble();
        _lonStep = buffer.getDouble();
        _columnCount = buffer.getInt();
        _latMin = buffer.getDouble();
        _latStep = buffer.getDouble();
        _rowCount = buffer.getInt();
        _oceanCount = buffer.getInt();

        int wordCount = (_rowCount * _columnCount + 63) / 64;

        LongBuffer occupancy = buffer.slice().order(ByteOrder.LITTLE_ENDIAN).asLongBuffer();
        occupancy.limit(wordCount);
        _occupancy = occupancy;
        buffer.position(buffer.position() + wordCount * 8);

        _rowStart = new int[_rowCount];

        for (int row = 0; row < _rowCount; row++)
        {
            _rowStart[row] = buffer.getInt();
        }

        for (int index = 0; index < constituentCount; index++)
        {
            byte[] rawName = new byte[8];
            buffer.get(rawName);

            String name = new String(rawName, StandardCharsets.US_ASCII)
                    .replace("\0", "")
                    .trim();

            double realScale = buffer.getFloat();
            double imaginaryScale = buffer.getFloat();

            _names.add(name);
            _scales.add(new double[] { realScale, imaginaryScale });

            ShortBuffer realValues = buffer.slice()
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .asShortBuffer();
            realValues.limit(_oceanCount);
            _real.add(realValues);
            buffer.position(buffer.position() + _oceanCount * 2);

            ShortBuffer imaginaryValues = buffer.slice()
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .asShortBuffer();
            imaginaryValues.limit(_oceanCount);
            _imaginary.add(imaginaryValues);
            buffer.position(buffer.position() + _oceanCount * 2);
        }

        file.close();
    }

    /** Names of the constituents held by this grid, in file order. */
    public List<String> names()
    {
        return _names;
    }

    /** Number of ocean cells actually stored. */
    public int oceanCellCount()
    {
        return _oceanCount;
    }

    /**
     * Bilinearly interpolates one constituent at the given position, in
     * centimetres, as { real, imaginary }. Returns null when none of the four
     * surrounding cells holds data. Cells that are missing individually drop
     * out of the weighted mean, so a coastal point still resolves from the
     * ocean cells around it.
     */
    public double[] sample(int constituentIndex, double latitude, double longitude)
    {
        double columnPosition = (_wrapLongitude(longitude) - _lonMin) / _lonStep;
        double rowPosition = (latitude - _latMin) / _latStep;

        int column = (int) Math.floor(columnPosition);
        int row = (int) Math.floor(rowPosition);

        if (row < 0 || row >= _rowCount - 1)
        {
            return null;
        }

        double columnFraction = columnPosition - column;
        double rowFraction = rowPosition - row;

        ShortBuffer realValues = _real.get(constituentIndex);
        ShortBuffer imaginaryValues = _imaginary.get(constituentIndex);
        double[] scale = _scales.get(constituentIndex);

        double realSum = 0.0;
        double imaginarySum = 0.0;
        double weightSum = 0.0;

        for (int rowOffset = 0; rowOffset <= 1; rowOffset++)
        {
            for (int columnOffset = 0; columnOffset <= 1; columnOffset++)
            {
                int sampleRow = row + rowOffset;
                int sampleColumn = _wrapColumn(column + columnOffset);

                int slot = _slotOf(sampleRow, sampleColumn);

                if (slot < 0)
                {
                    continue;
                }

                double rowWeight = rowOffset == 0 ? 1.0 - rowFraction : rowFraction;
                double columnWeight = columnOffset == 0
                        ? 1.0 - columnFraction
                        : columnFraction;

                double weight = rowWeight * columnWeight;

                realSum += weight * realValues.get(slot) * scale[0];
                imaginarySum += weight * imaginaryValues.get(slot) * scale[1];
                weightSum += weight;
            }
        }

        if (weightSum <= 0.0)
        {
            return null;
        }

        return new double[] { realSum / weightSum, imaginarySum / weightSum };
    }

    /**
     * Distance in cells to the nearest stored ocean cell, searched outwards
     * in rings. Returns -1 when nothing is found within the limit.
     */
    public int distanceToData(double latitude, double longitude, int limit)
    {
        int column = (int) Math.round((_wrapLongitude(longitude) - _lonMin) / _lonStep);
        int row = (int) Math.round((latitude - _latMin) / _latStep);

        for (int ring = 0; ring <= limit; ring++)
        {
            if (_ringHoldsData(row, column, ring) != null)
            {
                return ring;
            }
        }

        return -1;
    }

    /** Nearest stored ocean cell as { latitude, longitude }, or null. */
    public double[] nearestDataCell(double latitude, double longitude, int limit)
    {
        int column = (int) Math.round((_wrapLongitude(longitude) - _lonMin) / _lonStep);
        int row = (int) Math.round((latitude - _latMin) / _latStep);

        for (int ring = 0; ring <= limit; ring++)
        {
            int[] found = _ringHoldsData(row, column, ring);

            if (found != null)
            {
                return new double[] {
                        _latMin + found[0] * _latStep,
                        _lonMin + found[1] * _lonStep
                };
            }
        }

        return null;
    }

    private int[] _ringHoldsData(int row, int column, int ring)
    {
        for (int rowOffset = -ring; rowOffset <= ring; rowOffset++)
        {
            for (int columnOffset = -ring; columnOffset <= ring; columnOffset++)
            {
                boolean onRing = Math.abs(rowOffset) == ring
                        || Math.abs(columnOffset) == ring;

                if (!onRing)
                {
                    continue;
                }

                int sampleRow = row + rowOffset;

                if (sampleRow < 0 || sampleRow >= _rowCount)
                {
                    continue;
                }

                int sampleColumn = _wrapColumn(column + columnOffset);

                if (_slotOf(sampleRow, sampleColumn) >= 0)
                {
                    return new int[] { sampleRow, sampleColumn };
                }
            }
        }

        return null;
    }

    /**
     * Position of a cell inside the packed arrays, or -1 when the cell holds
     * no data. The row index gives the first slot of the row, and the bits of
     * the row before the column say how many stored cells precede it.
     */
    private int _slotOf(int row, int column)
    {
        long bitIndex = (long) row * _columnCount + column;

        if ((_occupancy.get((int) (bitIndex >>> 6)) & (1L << (bitIndex & 63))) == 0L)
        {
            return -1;
        }

        long rowFirstBit = (long) row * _columnCount;
        int slot = _rowStart[row];

        int firstWord = (int) (rowFirstBit >>> 6);
        int targetWord = (int) (bitIndex >>> 6);

        // Bits of the row that live in the same word as its first bit but
        // before it must not be counted.
        long leadingMask = -1L << (rowFirstBit & 63);

        if (firstWord == targetWord)
        {
            long mask = leadingMask & ((1L << (bitIndex & 63)) - 1L);
            return slot + Long.bitCount(_occupancy.get(firstWord) & mask);
        }

        slot += Long.bitCount(_occupancy.get(firstWord) & leadingMask);

        for (int word = firstWord + 1; word < targetWord; word++)
        {
            slot += Long.bitCount(_occupancy.get(word));
        }

        long trailingMask = (1L << (bitIndex & 63)) - 1L;

        return slot + Long.bitCount(_occupancy.get(targetWord) & trailingMask);
    }

    private int _wrapColumn(int column)
    {
        int wrapped = column % _columnCount;

        if (wrapped < 0)
        {
            wrapped += _columnCount;
        }

        return wrapped;
    }

    /** Brings a longitude into the range covered by the grid axis. */
    private double _wrapLongitude(double longitude)
    {
        double wrapped = longitude;

        while (wrapped < _lonMin)
        {
            wrapped += 360.0;
        }

        while (wrapped >= _lonMin + 360.0)
        {
            wrapped -= 360.0;
        }

        return wrapped;
    }
}
