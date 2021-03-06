/*
 * The MIT License
 *
 * Copyright (c) 2015-2020 aoju.org All rights reserved.
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
package org.aoju.bus.health.hardware;

import org.aoju.bus.health.Memoizer;

import java.util.function.Supplier;

/**
 * Computer System data.
 *
 * @author Kimi Liu
 * @version 5.5.3
 * @since JDK 1.8+
 */
public abstract class AbstractComputerSystem implements ComputerSystem {

    private final Supplier<Firmware> firmware = Memoizer.memoize(this::createFirmware);

    private final Supplier<Baseboard> baseboard = Memoizer.memoize(this::createBaseboard);

    @Override
    public Firmware getFirmware() {
        return firmware.get();
    }

    /**
     * Instantiates the platform-specific {@link Firmware} object
     *
     * @return platform-specific {@link Firmware} object
     */
    protected abstract Firmware createFirmware();

    @Override
    public Baseboard getBaseboard() {
        return baseboard.get();
    }

    /**
     * Instantiates the platform-specific {@link Baseboard} object
     *
     * @return platform-specific {@link Baseboard} object
     */
    protected abstract Baseboard createBaseboard();

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("manufacturer=").append(getManufacturer()).append(", ");
        sb.append("model=").append(getModel()).append(", ");
        sb.append("serial number=").append(getSerialNumber());
        return sb.toString();
    }

}
