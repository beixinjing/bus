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
package org.aoju.bus.health.software.mac;

import com.sun.jna.Native;
import com.sun.jna.platform.unix.LibCAPI;
import com.sun.jna.ptr.PointerByReference;
import org.aoju.bus.core.lang.Symbol;
import org.aoju.bus.health.Builder;
import org.aoju.bus.health.Command;
import org.aoju.bus.health.common.mac.SystemB;
import org.aoju.bus.health.common.unix.CLibrary;
import org.aoju.bus.health.software.AbstractNetwork;
import org.aoju.bus.logger.Logger;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * <p>
 * MacNetworkParams class.
 * </p>
 *
 * @author Kimi Liu
 * @version 5.5.3
 * @since JDK 1.8+
 */
public class MacNetwork extends AbstractNetwork {

    private static final SystemB SYS = SystemB.INSTANCE;

    private static final String IPV6_ROUTE_HEADER = "Internet6:";

    private static final String DEFAULT_GATEWAY = "default";

    @Override
    public String getDomainName() {
        CLibrary.Addrinfo hint = new CLibrary.Addrinfo();
        hint.ai_flags = CLibrary.AI_CANONNAME;
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            Logger.error("Unknown host exception when getting address of local host: {}", e);
            return "";
        }
        PointerByReference ptr = new PointerByReference();
        int res = SYS.getaddrinfo(hostname, null, hint, ptr);
        if (res > 0) {
            Logger.error("Failed getaddrinfo(): {}", SYS.gai_strerror(res));
            return "";
        }
        CLibrary.Addrinfo info = new CLibrary.Addrinfo(ptr.getValue());
        String canonname = info.ai_canonname.trim();
        SYS.freeaddrinfo(ptr.getValue());
        return canonname;
    }

    @Override
    public String getHostName() {
        byte[] hostnameBuffer = new byte[LibCAPI.HOST_NAME_MAX + 1];
        if (0 != SYS.gethostname(hostnameBuffer, hostnameBuffer.length)) {
            return super.getHostName();
        }
        return Native.toString(hostnameBuffer);
    }

    @Override
    public String getIpv4DefaultGateway() {
        return searchGateway(Command.runNative("route -n get default"));
    }

    @Override
    public String getIpv6DefaultGateway() {
        List<String> lines = Command.runNative("netstat -nr");
        boolean v6Table = false;
        for (String line : lines) {
            if (v6Table && line.startsWith(DEFAULT_GATEWAY)) {
                String[] fields = Builder.whitespaces.split(line);
                if (fields.length > 2 && fields[2].contains("G")) {
                    return fields[1].split(Symbol.PERCENT)[0];
                }
            } else if (line.startsWith(IPV6_ROUTE_HEADER)) {
                v6Table = true;
            }
        }
        return "";
    }
}
