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
package org.aoju.bus.health.software.windows;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.Advapi32Util.Account;
import com.sun.jna.platform.win32.Advapi32Util.EventLogIterator;
import com.sun.jna.platform.win32.Advapi32Util.EventLogRecord;
import com.sun.jna.platform.win32.BaseTSD.ULONG_PTRByReference;
import com.sun.jna.platform.win32.COM.WbemcliUtil.WmiQuery;
import com.sun.jna.platform.win32.COM.WbemcliUtil.WmiResult;
import com.sun.jna.platform.win32.Psapi.PERFORMANCE_INFORMATION;
import com.sun.jna.platform.win32.WinDef.DWORD;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinNT.HANDLEByReference;
import com.sun.jna.platform.win32.WinPerf.*;
import com.sun.jna.platform.win32.Wtsapi32.WTS_PROCESS_INFO_EX;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import org.aoju.bus.core.lang.Normal;
import org.aoju.bus.core.lang.Symbol;
import org.aoju.bus.health.Builder;
import org.aoju.bus.health.Config;
import org.aoju.bus.health.Memoizer;
import org.aoju.bus.health.common.windows.Kernel32;
import org.aoju.bus.health.common.windows.*;
import org.aoju.bus.health.common.windows.PerfWildcardQuery.PdhCounterWildcardProperty;
import org.aoju.bus.health.software.*;
import org.aoju.bus.logger.Logger;

import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * windows is a family of free operating systems most commonly used on personal
 * computers.
 *
 * @author Kimi Liu
 * @version 5.5.3
 * @since JDK 1.8+
 */
public class WindowsOS extends AbstractOS {

    private static final boolean IS_VISTA_OR_GREATER = VersionHelpers.IsWindowsVistaOrGreater();
    private static final boolean IS_WINDOWS7_OR_GREATER = VersionHelpers.IsWindows7OrGreater();
    private static final HkeyPerformanceData HKEY_PERFORMANCE_DATA;
    private static final String PROCESS_BASE_CLASS = "Win32_Process";
    /**
     * Windows event log name
     */
    private static Supplier<String> systemLog = Memoizer.memoize(WindowsOS::querySystemLog,
            TimeUnit.HOURS.toNanos(1));
    private static final long BOOTTIME = querySystemBootTime();

    static {
        HkeyPerformanceData data = null;
        try {
            data = new HkeyPerformanceData();
        } catch (InstantiationException e) {
            Logger.warn("{} Process statistics will be read from PDH or WMI.", e.getMessage());
        }
        HKEY_PERFORMANCE_DATA = data;
    }

    static {
        enableDebugPrivilege();
    }

    private final PerfWildcardQuery<ProcessPerformanceProperty> processPerformancePerfCounters = new PerfWildcardQuery<>(
            ProcessPerformanceProperty.class, "Process", "Win32_Process WHERE NOT Name LIKE\"%_Total\"",
            "Process Information");


    public WindowsOS() {
    }

    private static String parseVersion(WmiResult<OSVersionProperty> versionInfo, int suiteMask, String buildNumber) {

        // Initialize a default, sane value
        String version = System.getProperty("os.version");

        // Version is major.minor.build. Parse the version string for
        // major/minor and get the build number separately
        String[] verSplit = WmiUtils.getString(versionInfo, OSVersionProperty.Version, 0).split("\\D");
        int major = verSplit.length > 0 ? Builder.parseIntOrDefault(verSplit[0], 0) : 0;
        int minor = verSplit.length > 1 ? Builder.parseIntOrDefault(verSplit[1], 0) : 0;

        // see
        // http://msdn.microsoft.com/en-us/library/windows/desktop/ms724833%28v=vs.85%29.aspx
        boolean ntWorkstation = WmiUtils.getUint32(versionInfo, OSVersionProperty.ProductType,
                0) == WinNT.VER_NT_WORKSTATION;
        switch (major) {
            case 10:
                if (minor == 0) {
                    if (ntWorkstation) {
                        version = "10";
                    } else {
                        // Build numbers greater than 17762 is Server 2019 for OS
                        // Version 10.0
                        version = (Builder.parseLongOrDefault(buildNumber, 0L) > 17762) ? "Server 2019" : "Server 2016";
                    }
                }
                break;
            case 6:
                if (minor == 3) {
                    version = ntWorkstation ? "8.1" : "Server 2012 R2";
                } else if (minor == 2) {
                    version = ntWorkstation ? Symbol.EIGHT : "Server 2012";
                } else if (minor == 1) {
                    version = ntWorkstation ? Symbol.SEVEN : "Server 2008 R2";
                } else if (minor == 0) {
                    version = ntWorkstation ? "Vista" : "Server 2008";
                }
                break;
            case 5:
                if (minor == 2) {
                    if ((suiteMask & 0x00008000) != 0) {// VER_SUITE_WH_SERVER
                        version = "Home Server";
                    } else if (ntWorkstation) {
                        version = "XP"; // 64 bits
                    } else {
                        version = User32.INSTANCE.GetSystemMetrics(WinUser.SM_SERVERR2) != 0 ? "Server 2003"
                                : "Server 2003 R2";
                    }
                } else if (minor == 1) {
                    version = "XP"; // 32 bits
                } else if (minor == 0) {
                    version = "2000";
                }
                break;
            default:
                break;
        }

        String sp = WmiUtils.getString(versionInfo, OSVersionProperty.CSDVersion, 0);
        if (!sp.isEmpty() && !"unknown".equals(sp)) {
            version = version + Symbol.SPACE + sp.replace("Service Pack ", "SP");
        }

        return version;
    }

    private static String parseCodeName(int suiteMask) {
        List<String> suites = new ArrayList<>();
        if ((suiteMask & 0x00000002) != 0) {
            suites.add("Enterprise");
        }
        if ((suiteMask & 0x00000004) != 0) {
            suites.add("BackOffice");
        }
        if ((suiteMask & 0x00000008) != 0) {
            suites.add("Communication Server");
        }
        if ((suiteMask & 0x00000080) != 0) {
            suites.add("Datacenter");
        }
        if ((suiteMask & 0x00000200) != 0) {
            suites.add("Home");
        }
        if ((suiteMask & 0x00000400) != 0) {
            suites.add("Web Server");
        }
        if ((suiteMask & 0x00002000) != 0) {
            suites.add("Storage Server");
        }
        if ((suiteMask & 0x00004000) != 0) {
            suites.add("Compute Cluster");
        }
        // 0x8000, Home Server, is included in main version name
        return String.join(Symbol.COMMA, suites);
    }

    private static long querySystemUptime() {
        // Uptime is in seconds so divide milliseconds
        // GetTickCount64 requires Vista (6.0) or later
        if (IS_VISTA_OR_GREATER) {
            return org.aoju.bus.health.common.windows.Kernel32.INSTANCE.GetTickCount64() / 1000L;
        } else {
            // 32 bit rolls over at ~ 49 days
            return org.aoju.bus.health.common.windows.Kernel32.INSTANCE.GetTickCount() / 1000L;
        }
    }

    private static long querySystemBootTime() {
        String eventLog = systemLog.get();
        if (eventLog != null) {
            try {
                EventLogIterator iter = new EventLogIterator(null, eventLog, WinNT.EVENTLOG_BACKWARDS_READ);
                // Get the most recent boot event (ID 12) from the Event log. If Windows "Fast
                // Startup" is enabled we may not see event 12, so also check for most recent ID
                // 6005 (Event log startup) as a reasonably close backup.
                long event6005Time = 0L;
                while (iter.hasNext()) {
                    EventLogRecord record = iter.next();
                    if (record.getStatusCode() == 12) {
                        // Event 12 is system boot. We want this value unless we find two 6005 events
                        // first (may occur with Fast Boot)
                        return record.getRecord().TimeGenerated.longValue();
                    } else if (record.getStatusCode() == 6005) {
                        // If we already found one, this means we've found a second one without finding
                        // an event 12. Return the latest one.
                        if (event6005Time > 0) {
                            return event6005Time;
                        }
                        // First 6005; tentatively assign
                        event6005Time = record.getRecord().TimeGenerated.longValue();
                    }
                }
                // Only one 6005 found, return
                if (event6005Time > 0) {
                    return event6005Time;
                }
            } catch (Win32Exception e) {
                Logger.warn("Can't open event log {}", eventLog);
            }
        }
        // If we get this far, event log reading has failed, either from no log or no
        // startup times. Subtract up time from current time as a reasonable proxy.
        return System.currentTimeMillis() / 1000L - querySystemUptime();
    }

    /**
     * Enables debug privileges for this process, required for OpenProcess() to get
     * processes other than the current user
     */
    private static void enableDebugPrivilege() {
        HANDLEByReference hToken = new HANDLEByReference();
        boolean success = Advapi32.INSTANCE.OpenProcessToken(org.aoju.bus.health.common.windows.Kernel32.INSTANCE.GetCurrentProcess(),
                WinNT.TOKEN_QUERY | WinNT.TOKEN_ADJUST_PRIVILEGES, hToken);
        if (!success) {
            Logger.error("OpenProcessToken failed. Error: {}", Native.getLastError());
            return;
        }
        WinNT.LUID luid = new WinNT.LUID();
        success = Advapi32.INSTANCE.LookupPrivilegeValue(null, WinNT.SE_DEBUG_NAME, luid);
        if (!success) {
            Logger.error("LookupprivilegeValue failed. Error: {}", Native.getLastError());
            org.aoju.bus.health.common.windows.Kernel32.INSTANCE.CloseHandle(hToken.getValue());
            return;
        }
        WinNT.TOKEN_PRIVILEGES tkp = new WinNT.TOKEN_PRIVILEGES(1);
        tkp.Privileges[0] = new WinNT.LUID_AND_ATTRIBUTES(luid, new DWORD(WinNT.SE_PRIVILEGE_ENABLED));
        success = Advapi32.INSTANCE.AdjustTokenPrivileges(hToken.getValue(), false, tkp, 0, null, null);
        if (!success) {
            Logger.error("AdjustTokenPrivileges failed. Error: {}", Native.getLastError());
        }
        org.aoju.bus.health.common.windows.Kernel32.INSTANCE.CloseHandle(hToken.getValue());
    }

    private static String querySystemLog() {
        String systemLog = Config.get("oshi.os.windows.eventlog", "System");
        if (systemLog.isEmpty()) {
            // Use faster boot time approximation
            return null;
        }
        // Check whether it works
        HANDLE h = Advapi32.INSTANCE.OpenEventLog(null, systemLog);
        if (h == null) {
            Logger.warn("Unable to open configured system Event log \"{}\". Calculating boot time from uptime.",
                    systemLog);
            return null;
        }
        return systemLog;
    }

    @Override
    public String queryManufacturer() {
        return "Microsoft";
    }

    @Override
    public FamilyVersionInfo queryFamilyVersionInfo() {
        WmiQuery<OSVersionProperty> osVersionQuery = new WmiQuery<>("Win32_OperatingSystem", OSVersionProperty.class);
        WmiResult<OSVersionProperty> versionInfo = WmiQueryHandler.createInstance().queryWMI(osVersionQuery);
        if (versionInfo.getResultCount() < 1) {
            return new FamilyVersionInfo("Windows", new OSVersionInfo(System.getProperty("os.version"), null, null));
        }
        // Guaranteed that versionInfo is not null and lists non-empty
        // before calling the parse*() methods
        int suiteMask = WmiUtils.getUint32(versionInfo, OSVersionProperty.SuiteMask, 0);
        String buildNumber = WmiUtils.getString(versionInfo, OSVersionProperty.BuildNumber, 0);
        String version = parseVersion(versionInfo, suiteMask, buildNumber);
        String codeName = parseCodeName(suiteMask);
        return new FamilyVersionInfo("Windows", new OSVersionInfo(version, codeName, buildNumber));
    }

    @Override
    protected int queryBitness(int jvmBitness) {
        WmiQueryHandler wmiQueryHandler = WmiQueryHandler.createInstance();
        if (jvmBitness < 64 && System.getenv("ProgramFiles(x86)") != null && IS_VISTA_OR_GREATER) {
            WmiQuery<BitnessProperty> bitnessQuery = new WmiQuery<>("Win32_Processor", BitnessProperty.class);
            WmiResult<BitnessProperty> bitnessMap = wmiQueryHandler.queryWMI(bitnessQuery);
            if (bitnessMap.getResultCount() > 0) {
                return WmiUtils.getUint16(bitnessMap, BitnessProperty.AddressWidth, 0);
            }
        }
        return jvmBitness;
    }

    @Override
    public boolean queryElevated() {
        try {
            File dir = new File(System.getenv("windir") + "\\system32\\config\\systemprofile");
            return dir.isDirectory();
        } catch (SecurityException e) {
            return false;
        }
    }

    @Override
    public FileSystem getFileSystem() {
        return new WindowsFileSystem();
    }

    @Override
    public OSProcess[] getProcesses(int limit, ProcessSort sort, boolean slowFields) {
        List<OSProcess> procList = processMapToList(null, slowFields);
        List<OSProcess> sorted = processSort(procList, limit, sort);
        return sorted.toArray(new OSProcess[0]);
    }

    @Override
    public List<OSProcess> getProcesses(Collection<Integer> pids) {
        return processMapToList(pids, true);
    }

    @Override
    public OSProcess[] getChildProcesses(int parentPid, int limit, ProcessSort sort) {
        Set<Integer> childPids = new HashSet<>();
        // Get processes from ToolHelp API for parent PID
        Tlhelp32.PROCESSENTRY32.ByReference processEntry = new Tlhelp32.PROCESSENTRY32.ByReference();
        WinNT.HANDLE snapshot = org.aoju.bus.health.common.windows.Kernel32.INSTANCE.CreateToolhelp32Snapshot(Tlhelp32.TH32CS_SNAPPROCESS, new DWORD(0));
        try {
            while (org.aoju.bus.health.common.windows.Kernel32.INSTANCE.Process32Next(snapshot, processEntry)) {
                if (processEntry.th32ParentProcessID.intValue() == parentPid) {
                    childPids.add(processEntry.th32ProcessID.intValue());
                }
            }
        } finally {
            org.aoju.bus.health.common.windows.Kernel32.INSTANCE.CloseHandle(snapshot);
        }
        List<OSProcess> procList = getProcesses(childPids);
        List<OSProcess> sorted = processSort(procList, limit, sort);
        return sorted.toArray(new OSProcess[0]);
    }

    @Override
    public OSProcess getProcess(int pid, boolean slowFields) {
        List<OSProcess> procList = processMapToList(Arrays.asList(pid), slowFields);
        return procList.isEmpty() ? null : procList.get(0);
    }

    /**
     * Private method to do the heavy lifting for all the getProcess functions.
     *
     * @param pids       A collection of pids to query. If null, the entire process list
     *                   will be queried.
     * @param slowFields Whether to include fields that incur processor latency
     * @return A corresponding list of processes
     */
    private List<OSProcess> processMapToList(Collection<Integer> pids, boolean slowFields) {
        WmiQueryHandler wmiQueryHandler = WmiQueryHandler.createInstance();
        // Get data from the registry if possible, otherwise performance counters with
        // WMI backup
        Map<Integer, OSProcess> processMap = (HKEY_PERFORMANCE_DATA != null)
                ? HKEY_PERFORMANCE_DATA.buildProcessMapFromRegistry(this, pids)
                : buildProcessMapFromPerfCounters(pids);

        // define here to avoid object repeated creation overhead later
        List<String> groupList = new ArrayList<>();
        List<String> groupIDList = new ArrayList<>();
        int myPid = getProcessId();

        // Structure we'll fill from native memory pointer for Vista+
        Pointer pProcessInfo = null;
        WTS_PROCESS_INFO_EX[] processInfo = null;
        IntByReference pCount = new IntByReference(0);

        // WMI result we'll use for pre-Vista
        WmiResult<ProcessXPProperty> processWmiResult = null;

        // Get processes from WTS (post-XP)
        if (IS_WINDOWS7_OR_GREATER) {
            final PointerByReference ppProcessInfo = new PointerByReference();
            if (!Wtsapi32.INSTANCE.WTSEnumerateProcessesEx(Wtsapi32.WTS_CURRENT_SERVER_HANDLE,
                    new IntByReference(Wtsapi32.WTS_PROCESS_INFO_LEVEL_1), Wtsapi32.WTS_ANY_SESSION, ppProcessInfo,
                    pCount)) {
                Logger.error("Failed to enumerate Processes. Error code: {}", org.aoju.bus.health.common.windows.Kernel32.INSTANCE.GetLastError());
                return new ArrayList<>(0);
            }
            // extract the pointed-to pointer and create array
            pProcessInfo = ppProcessInfo.getValue();
            final WTS_PROCESS_INFO_EX processInfoRef = new WTS_PROCESS_INFO_EX(pProcessInfo);
            processInfo = (WTS_PROCESS_INFO_EX[]) processInfoRef.toArray(pCount.getValue());
        } else {
            // Pre-Vista we can't use WTSEnumerateProcessesEx so we'll grab the
            // same info from WMI and fake the array
            StringBuilder sb = new StringBuilder(PROCESS_BASE_CLASS);
            if (pids != null) {
                boolean first = true;
                for (Integer pid : pids) {
                    if (first) {
                        sb.append(" WHERE ProcessID=");
                        first = false;
                    } else {
                        sb.append(" OR ProcessID=");
                    }
                    sb.append(pid);
                }
            }
            WmiQuery<ProcessXPProperty> processQueryXP = new WmiQuery<>(sb.toString(), ProcessXPProperty.class);
            processWmiResult = wmiQueryHandler.queryWMI(processQueryXP);
        }

        // Store a subset of processes in a list to later return.
        List<OSProcess> processList = new ArrayList<>();

        int procCount = IS_WINDOWS7_OR_GREATER ? processInfo.length : processWmiResult.getResultCount();
        for (int i = 0; i < procCount; i++) {
            int pid = IS_WINDOWS7_OR_GREATER ? processInfo[i].ProcessId
                    : WmiUtils.getUint32(processWmiResult, ProcessXPProperty.ProcessId, i);
            OSProcess proc = null;
            // If the cache is empty, there was a problem with
            // filling the cache using performance information.
            if (processMap.isEmpty()) {
                if (pids != null && !pids.contains(pid)) {
                    continue;
                }
                proc = new OSProcess(this);
                proc.setProcessID(pid);
                proc.setName(IS_WINDOWS7_OR_GREATER ? processInfo[i].pProcessName
                        : WmiUtils.getString(processWmiResult, ProcessXPProperty.Name, i));
            } else {
                proc = processMap.get(pid);
                if (proc == null || pids != null && !pids.contains(pid)) {
                    continue;
                }
            }
            // For my own process, set CWD
            if (pid == myPid) {
                String cwd = new File(Symbol.DOT).getAbsolutePath();
                proc.setCurrentWorkingDirectory(cwd.isEmpty() ? Normal.EMPTY : cwd.substring(0, cwd.length() - 1));
            }

            if (IS_WINDOWS7_OR_GREATER) {
                WTS_PROCESS_INFO_EX procInfo = processInfo[i];
                proc.setKernelTime(procInfo.KernelTime.getValue() / 10000L);
                proc.setUserTime(procInfo.UserTime.getValue() / 10000L);
                proc.setThreadCount(procInfo.NumberOfThreads);
                proc.setVirtualSize(procInfo.PagefileUsage & 0xffff_ffffL);
                proc.setOpenFiles(procInfo.HandleCount);
            } else {
                proc.setKernelTime(WmiUtils.getUint64(processWmiResult, ProcessXPProperty.KernelModeTime, i) / 10000L);
                proc.setUserTime(WmiUtils.getUint64(processWmiResult, ProcessXPProperty.UserModeTime, i) / 10000L);
                proc.setThreadCount(WmiUtils.getUint32(processWmiResult, ProcessXPProperty.ThreadCount, i));
                // WMI Pagefile usage is in KB
                proc.setVirtualSize(1024
                        * (WmiUtils.getUint32(processWmiResult, ProcessXPProperty.PageFileUsage, i) & 0xffff_ffffL));
                proc.setOpenFiles(WmiUtils.getUint32(processWmiResult, ProcessXPProperty.HandleCount, i));
            }

            // Get a handle to the process for various extended info. Only gets
            // current user unless running as administrator
            final HANDLE pHandle = org.aoju.bus.health.common.windows.Kernel32.INSTANCE.OpenProcess(WinNT.PROCESS_QUERY_INFORMATION, false,
                    proc.getProcessID());
            if (pHandle != null) {
                proc.setBitness(this.getBitness());
                // Only test for 32-bit process on 64-bit windows
                if (IS_VISTA_OR_GREATER && this.getBitness() == 64) {
                    IntByReference wow64 = new IntByReference(0);
                    if (org.aoju.bus.health.common.windows.Kernel32.INSTANCE.IsWow64Process(pHandle, wow64)) {
                        proc.setBitness(wow64.getValue() > 0 ? 32 : 64);
                    }
                }
                // Full path
                final HANDLEByReference phToken = new HANDLEByReference();
                try {// EXECUTABLEPATH
                    proc.setPath(IS_WINDOWS7_OR_GREATER ? Kernel32Util.QueryFullProcessImageName(pHandle, 0)
                            : WmiUtils.getString(processWmiResult, ProcessXPProperty.ExecutablePath, i));
                    if (Advapi32.INSTANCE.OpenProcessToken(pHandle, WinNT.TOKEN_DUPLICATE | WinNT.TOKEN_QUERY,
                            phToken)) {
                        Account account = Advapi32Util.getTokenAccount(phToken.getValue());
                        proc.setUser(account.name);
                        proc.setUserID(account.sidString);
                        // Fetching group information incurs ~10ms per process.
                        if (slowFields) {
                            Account[] accounts = Advapi32Util.getTokenGroups(phToken.getValue());
                            // get groups
                            groupList.clear();
                            groupIDList.clear();
                            for (Account a : accounts) {
                                groupList.add(a.name);
                                groupIDList.add(a.sidString);
                            }
                            proc.setGroup(String.join(Symbol.COMMA, groupList));
                            proc.setGroupID(String.join(Symbol.COMMA, groupIDList));
                        }
                    } else {
                        int error = org.aoju.bus.health.common.windows.Kernel32.INSTANCE.GetLastError();
                        // Access denied errors are common. Fail silently.
                        if (error != WinError.ERROR_ACCESS_DENIED) {
                            Logger.error("Failed to get process token for process {}: {}", proc.getProcessID(),
                                    org.aoju.bus.health.common.windows.Kernel32.INSTANCE.GetLastError());
                        }
                    }
                } catch (Win32Exception e) {
                    handleWin32ExceptionOnGetProcessInfo(proc, e);
                } finally {
                    final HANDLE token = phToken.getValue();
                    if (token != null) {
                        org.aoju.bus.health.common.windows.Kernel32.INSTANCE.CloseHandle(token);
                    }
                }
                org.aoju.bus.health.common.windows.Kernel32.INSTANCE.CloseHandle(pHandle);
            }

            // There is no easy way to get ExecutuionState for a process.
            // The WMI value is null. It's possible to get thread Execution
            // State and possibly roll up.
            proc.setState(OSProcess.State.RUNNING);

            // Initialize default
            proc.setCommandLine(Normal.EMPTY);

            processList.add(proc);
        }
        // Clean up memory allocated in C (only Vista+ but null pointer
        // effectively tests)
        if (pProcessInfo != null && !Wtsapi32.INSTANCE.WTSFreeMemoryEx(Wtsapi32.WTS_PROCESS_INFO_LEVEL_1, pProcessInfo,
                pCount.getValue())) {
            Logger.error("Failed to Free Memory for Processes. Error code: {}", org.aoju.bus.health.common.windows.Kernel32.INSTANCE.GetLastError());
            return new ArrayList<>(0);
        }

        // Command Line only accessible via WMI.
        if (slowFields) {
            StringBuilder sb = new StringBuilder(PROCESS_BASE_CLASS);
            if (pids != null) {
                Set<Integer> pidsToQuery = new HashSet<>();
                for (OSProcess process : processList) {
                    pidsToQuery.add(process.getProcessID());
                }
                boolean first = true;
                for (Integer pid : pidsToQuery) {
                    if (first) {
                        sb.append(" WHERE ProcessID=");
                        first = false;
                    } else {
                        sb.append(" OR ProcessID=");
                    }
                    sb.append(pid);
                }
            }
            WmiQuery<ProcessProperty> processQuery = new WmiQuery<>(sb.toString(), ProcessProperty.class);
            WmiResult<ProcessProperty> commandLineProcs = wmiQueryHandler.queryWMI(processQuery);

            for (int p = 0; p < commandLineProcs.getResultCount(); p++) {
                int pid = WmiUtils.getUint32(commandLineProcs, ProcessProperty.ProcessId, p);
                if (processMap.containsKey(pid)) {
                    OSProcess proc = processMap.get(pid);
                    proc.setCommandLine(WmiUtils.getString(commandLineProcs, ProcessProperty.CommandLine, p));
                }
            }
        }
        return processList;
    }

    protected void handleWin32ExceptionOnGetProcessInfo(OSProcess proc, Win32Exception ex) {
        Logger.warn("Failed to set path or get user/group on PID {}. It may have terminated. {}", proc.getProcessID(),
                ex.getMessage());
    }

    private Map<Integer, OSProcess> buildProcessMapFromPerfCounters(Collection<Integer> pids) {
        Map<Integer, OSProcess> processMap = new HashMap<>();
        Map<ProcessPerformanceProperty, List<Long>> valueMap = this.processPerformancePerfCounters
                .queryValuesWildcard();
        long now = System.currentTimeMillis(); // 1970 epoch
        List<String> instances = this.processPerformancePerfCounters.getInstancesFromLastQuery();
        List<Long> pidList = valueMap.get(ProcessPerformanceProperty.ProcessId);
        List<Long> ppidList = valueMap.get(ProcessPerformanceProperty.ParentProcessId);
        List<Long> priorityList = valueMap.get(ProcessPerformanceProperty.Priority);
        List<Long> ioReadList = valueMap.get(ProcessPerformanceProperty.ReadTransferCount);
        List<Long> ioWriteList = valueMap.get(ProcessPerformanceProperty.WriteTransferCount);
        List<Long> workingSetSizeList = valueMap.get(ProcessPerformanceProperty.PrivatePageCount);
        List<Long> creationTimeList = valueMap.get(ProcessPerformanceProperty.CreationDate);

        for (int inst = 0; inst < instances.size(); inst++) {
            int pid = pidList.get(inst).intValue();
            if (pids == null || pids.contains(pid)) {
                OSProcess proc = new OSProcess(this);
                processMap.put(pid, proc);

                proc.setProcessID(pid);
                proc.setName(instances.get(inst));
                proc.setParentProcessID(ppidList.get(inst).intValue());
                proc.setPriority(priorityList.get(inst).intValue());
                // if creation time value is less than current millis, it's in 1970 epoch,
                // otherwise it's 1601 epoch and we must convert
                long ctime = creationTimeList.get(inst);
                if (ctime > now) {
                    ctime = WinBase.FILETIME.filetimeToDate((int) (ctime >> 32), (int) (ctime & 0xffffffffL)).getTime();
                }
                proc.setUpTime(now - ctime);
                proc.setStartTime(ctime);
                proc.setBytesRead(ioReadList.get(inst));
                proc.setBytesWritten(ioWriteList.get(inst));
                proc.setResidentSetSize(workingSetSizeList.get(inst));
            }
        }
        return processMap;
    }

    @Override
    public long getProcessAffinityMask(int processId) {
        final HANDLE pHandle = org.aoju.bus.health.common.windows.Kernel32.INSTANCE.OpenProcess(WinNT.PROCESS_QUERY_INFORMATION, false, processId);
        if (pHandle != null) {
            ULONG_PTRByReference processAffinity = new ULONG_PTRByReference();
            ULONG_PTRByReference systemAffinity = new ULONG_PTRByReference();
            if (org.aoju.bus.health.common.windows.Kernel32.INSTANCE.GetProcessAffinityMask(pHandle, processAffinity, systemAffinity)) {
                return Pointer.nativeValue(processAffinity.getValue().toPointer());
            }
        }
        return 0L;
    }

    @Override
    public int getProcessId() {
        return org.aoju.bus.health.common.windows.Kernel32.INSTANCE.GetCurrentProcessId();
    }

    @Override
    public int getProcessCount() {
        PERFORMANCE_INFORMATION perfInfo = new PERFORMANCE_INFORMATION();
        if (!Psapi.INSTANCE.GetPerformanceInfo(perfInfo, perfInfo.size())) {
            Logger.error("Failed to get Performance Info. Error code: {}", org.aoju.bus.health.common.windows.Kernel32.INSTANCE.GetLastError());
            return 0;
        }
        return perfInfo.ProcessCount.intValue();
    }

    @Override
    public int getThreadCount() {
        PERFORMANCE_INFORMATION perfInfo = new PERFORMANCE_INFORMATION();
        if (!Psapi.INSTANCE.GetPerformanceInfo(perfInfo, perfInfo.size())) {
            Logger.error("Failed to get Performance Info. Error code: {}", Kernel32.INSTANCE.GetLastError());
            return 0;
        }
        return perfInfo.ThreadCount.intValue();
    }

    @Override
    public long getSystemUptime() {
        return querySystemUptime();
    }

    @Override
    public long getSystemBootTime() {
        return BOOTTIME;
    }

    @Override
    public NetworkParams getNetworkParams() {
        return new WindowsNetwork();
    }

    @Override
    public OSService[] getServices() {
        try (W32ServiceManager sm = new W32ServiceManager()) {
            sm.open(Winsvc.SC_MANAGER_ENUMERATE_SERVICE);
            Winsvc.ENUM_SERVICE_STATUS_PROCESS[] services = sm.enumServicesStatusExProcess(WinNT.SERVICE_WIN32,
                    Winsvc.SERVICE_STATE_ALL, null);
            OSService[] svcArray = new OSService[services.length];
            for (int i = 0; i < services.length; i++) {
                OSService.State state;
                switch (services[i].ServiceStatusProcess.dwCurrentState) {
                    case 1:
                        state = OSService.State.STOPPED;
                        break;
                    case 4:
                        state = OSService.State.RUNNING;
                        break;
                    default:
                        state = OSService.State.OTHER;
                        break;
                }
                svcArray[i] = new OSService(services[i].lpDisplayName, services[i].ServiceStatusProcess.dwProcessId,
                        state);
            }
            return svcArray;
        } catch (com.sun.jna.platform.win32.Win32Exception ex) {
            Logger.error("Win32Exception: {}", ex.getMessage());
            return new OSService[0];
        }
    }

    enum OSVersionProperty {
        Version, ProductType, BuildNumber, CSDVersion, SuiteMask
    }

    enum BitnessProperty {
        AddressWidth
    }

    enum ProcessProperty {
        ProcessId, CommandLine
    }

    // Properties to get from WMI if WTSEnumerateProcesses doesn't work
    enum ProcessXPProperty {
        ProcessId, Name, KernelModeTime, UserModeTime, ThreadCount, PageFileUsage, HandleCount, ExecutablePath;
    }

    enum ProcessPerformanceProperty implements PdhCounterWildcardProperty {
        // First element defines WMI instance name field and PDH instance filter
        Name(PerfCounterQuery.NOT_TOTAL_INSTANCES),
        // Remaining elements define counters
        Priority("Priority Base"),
        CreationDate("Elapsed Time"),
        ProcessId("ID Process"),
        ParentProcessId("Creating Process ID"),
        ReadTransferCount("IO Read Bytes/sec"),
        WriteTransferCount("IO Write Bytes/sec"),
        PrivatePageCount("Working Set - Private");

        private final String counter;

        ProcessPerformanceProperty(String counter) {
            this.counter = counter;
        }

        @Override
        public String getCounter() {
            return counter;
        }
    }

    private static class HkeyPerformanceData {
        /*
         * Grow as needed but persist
         */
        private int perfDataBufferSize = 8192;
        /*
         * Process counter index in integer and string form
         */
        private int processIndex;
        private String processIndexStr;

        private int priorityBaseOffset; // 92
        private int elapsedTimeOffset; // 96
        private int idProcessOffset; // 104
        private int creatingProcessIdOffset; // 108
        private int ioReadOffset; // 160
        private int ioWriteOffset; // 168
        private int workingSetPrivateOffset; // 192

        private HkeyPerformanceData() throws InstantiationException {
            // Get the title indices
            int priorityBaseIndex = 0;
            int elapsedTimeIndex = 0;
            int idProcessIndex = 0;
            int creatingProcessIdIndex = 0;
            int ioReadIndex = 0;
            int ioWriteIndex = 0;
            int workingSetPrivateIndex = 0;

            try {
                final String ENGLISH_COUNTER_KEY = "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Perflib\\009";
                final String ENGLISH_COUNTER_VALUE = "Counter";

                // Look up list of english names and ids
                String[] counters = Advapi32Util.registryGetStringArray(WinReg.HKEY_LOCAL_MACHINE, ENGLISH_COUNTER_KEY,
                        ENGLISH_COUNTER_VALUE);
                for (int i = 1; i < counters.length; i += 2) {
                    if (counters[i].equals("Process")) {
                        this.processIndex = Integer.parseInt(counters[i - 1]);
                    } else if (counters[i].equals("Priority Base")) {
                        priorityBaseIndex = Integer.parseInt(counters[i - 1]);
                    } else if (counters[i].equals("Elapsed Time")) {
                        elapsedTimeIndex = Integer.parseInt(counters[i - 1]);
                    } else if (counters[i].equals("ID Process")) {
                        idProcessIndex = Integer.parseInt(counters[i - 1]);
                    } else if (counters[i].equals("Creating Process ID")) {
                        creatingProcessIdIndex = Integer.parseInt(counters[i - 1]);
                    } else if (counters[i].equals("IO Read Bytes/sec")) {
                        ioReadIndex = Integer.parseInt(counters[i - 1]);
                    } else if (counters[i].equals("IO Write Bytes/sec")) {
                        ioWriteIndex = Integer.parseInt(counters[i - 1]);
                    } else if (counters[i].equals("Working Set - Private")) {
                        workingSetPrivateIndex = Integer.parseInt(counters[i - 1]);
                    }
                }
            } catch (NumberFormatException e) {
                // Unexpected but handle anyway
                throw new InstantiationException("Failed to parse counter index/name array.");
            } catch (Win32Exception e) {
                throw new InstantiationException("Unable to locate English counter names in registry Perflib 009.");
            }
            // If any of the indices are 0, we failed
            if (this.processIndex == 0 || priorityBaseIndex == 0 || elapsedTimeIndex == 0 || idProcessIndex == 0
                    || creatingProcessIdIndex == 0 || ioReadIndex == 0 || ioWriteIndex == 0
                    || workingSetPrivateIndex == 0) {
                throw new InstantiationException("Failed to parse counter index/name array.");
            }
            this.processIndexStr = Integer.toString(this.processIndex);

            // now load the Process registry to match up the offsets
            // Sequentially increase the buffer until everything fits.
            // Save this buffer size for later use
            IntByReference lpcbData = new IntByReference(this.perfDataBufferSize);
            Pointer pPerfData = new Memory(this.perfDataBufferSize);
            int ret = Advapi32.INSTANCE.RegQueryValueEx(WinReg.HKEY_PERFORMANCE_DATA, this.processIndexStr, 0, null,
                    pPerfData, lpcbData);
            if (ret != WinError.ERROR_SUCCESS && ret != WinError.ERROR_MORE_DATA) {
                throw new InstantiationException("Error " + ret + " reading HKEY_PERFORMANCE_DATA from the registry.");
            }
            while (ret == WinError.ERROR_MORE_DATA) {
                this.perfDataBufferSize += 4096;
                lpcbData.setValue(this.perfDataBufferSize);
                pPerfData = new Memory(this.perfDataBufferSize);
                ret = Advapi32.INSTANCE.RegQueryValueEx(WinReg.HKEY_PERFORMANCE_DATA, this.processIndexStr, 0, null,
                        pPerfData, lpcbData);
            }

            PERF_DATA_BLOCK perfData = new PERF_DATA_BLOCK(pPerfData.share(0));

            // See format at
            // https://msdn.microsoft.com/en-us/library/windows/desktop/aa373105(v=vs.85).aspx
            // [ ] Object Type
            // [ ][ ][ ] Multiple counter definitions
            // Then multiple:
            // [ ] Instance Definition
            // [ ] Instance name
            // [ ] Counter Block
            // [ ][ ][ ] Counter data for each definition above

            long perfObjectOffset = perfData.HeaderLength;

            // Iterate object types. For Process should only be one here
            for (int obj = 0; obj < perfData.NumObjectTypes; obj++) {
                PERF_OBJECT_TYPE perfObject = new PERF_OBJECT_TYPE(pPerfData.share(perfObjectOffset));
                // Identify where counter definitions start
                long perfCounterOffset = perfObjectOffset + perfObject.HeaderLength;
                // If this isn't the Process object, ignore
                if (perfObject.ObjectNameTitleIndex == this.processIndex) {
                    for (int counter = 0; counter < perfObject.NumCounters; counter++) {
                        PERF_COUNTER_DEFINITION perfCounter = new PERF_COUNTER_DEFINITION(
                                pPerfData.share(perfCounterOffset));
                        if (perfCounter.CounterNameTitleIndex == priorityBaseIndex) {
                            this.priorityBaseOffset = perfCounter.CounterOffset;
                        } else if (perfCounter.CounterNameTitleIndex == elapsedTimeIndex) {
                            this.elapsedTimeOffset = perfCounter.CounterOffset;
                        } else if (perfCounter.CounterNameTitleIndex == creatingProcessIdIndex) {
                            this.creatingProcessIdOffset = perfCounter.CounterOffset;
                        } else if (perfCounter.CounterNameTitleIndex == idProcessIndex) {
                            this.idProcessOffset = perfCounter.CounterOffset;
                        } else if (perfCounter.CounterNameTitleIndex == ioReadIndex) {
                            this.ioReadOffset = perfCounter.CounterOffset;
                        } else if (perfCounter.CounterNameTitleIndex == ioWriteIndex) {
                            this.ioWriteOffset = perfCounter.CounterOffset;
                        } else if (perfCounter.CounterNameTitleIndex == workingSetPrivateIndex) {
                            this.workingSetPrivateOffset = perfCounter.CounterOffset;
                        }
                        // Increment for next Counter
                        perfCounterOffset += perfCounter.ByteLength;
                    }
                    // We're done, break the loop
                    break;
                }
                // Increment for next object (should never need this)
                perfObjectOffset += perfObject.TotalByteLength;
            }
        }

        private Map<Integer, OSProcess> buildProcessMapFromRegistry(OperatingSystem os, Collection<Integer> pids) {
            Map<Integer, OSProcess> processMap = new HashMap<>();
            // Grab the PERF_DATA_BLOCK from the registry.
            // Sequentially increase the buffer until everything fits.
            IntByReference lpcbData = new IntByReference(this.perfDataBufferSize);
            Pointer pPerfData = new Memory(this.perfDataBufferSize);
            int ret = Advapi32.INSTANCE.RegQueryValueEx(WinReg.HKEY_PERFORMANCE_DATA, this.processIndexStr, 0, null,
                    pPerfData, lpcbData);
            if (ret != WinError.ERROR_SUCCESS && ret != WinError.ERROR_MORE_DATA) {
                Logger.error("Error {} reading HKEY_PERFORMANCE_DATA from the registry.", ret);
                return processMap;
            }
            while (ret == WinError.ERROR_MORE_DATA) {
                this.perfDataBufferSize += 4096;
                lpcbData.setValue(this.perfDataBufferSize);
                pPerfData = new Memory(this.perfDataBufferSize);
                ret = Advapi32.INSTANCE.RegQueryValueEx(WinReg.HKEY_PERFORMANCE_DATA, this.processIndexStr, 0, null,
                        pPerfData, lpcbData);
            }

            PERF_DATA_BLOCK perfData = new PERF_DATA_BLOCK(pPerfData.share(0));
            long perfTime100nSec = perfData.PerfTime100nSec.getValue(); // 1601
            long now = System.currentTimeMillis(); // 1970 epoch

            // See format at
            // https://msdn.microsoft.com/en-us/library/windows/desktop/aa373105(v=vs.85).aspx
            // [ ] Object Type
            // [ ][ ][ ] Multiple counter definitions
            // Then multiple:
            // [ ] Instance Definition
            // [ ] Instance name
            // [ ] Counter Block
            // [ ][ ][ ] Counter data for each definition above
            long perfObjectOffset = perfData.HeaderLength;

            // Iterate object types. For Process should only be one here
            for (int obj = 0; obj < perfData.NumObjectTypes; obj++) {
                PERF_OBJECT_TYPE perfObject = new PERF_OBJECT_TYPE(pPerfData.share(perfObjectOffset));
                // If this isn't the Process object, ignore
                if (perfObject.ObjectNameTitleIndex == this.processIndex) {
                    // Skip over counter definitions
                    // There will be many of these, this points to the first one
                    long perfInstanceOffset = perfObjectOffset + perfObject.DefinitionLength;

                    // We need this for every process, initialize outside loop to
                    // save overhead
                    PERF_COUNTER_BLOCK perfCounterBlock = null;
                    // Iterate instances.
                    // The last instance is _Total so subtract 1 from max
                    for (int inst = 0; inst < perfObject.NumInstances - 1; inst++) {
                        PERF_INSTANCE_DEFINITION perfInstance = new PERF_INSTANCE_DEFINITION(
                                pPerfData.share(perfInstanceOffset));
                        long perfCounterBlockOffset = perfInstanceOffset + perfInstance.ByteLength;

                        int pid = pPerfData.getInt(perfCounterBlockOffset + this.idProcessOffset);
                        if (pids == null || pids.contains(pid)) {
                            OSProcess proc = new OSProcess(os);
                            processMap.put(pid, proc);

                            proc.setProcessID(pid);
                            proc.setName(pPerfData.getWideString(perfInstanceOffset + perfInstance.NameOffset));
                            long upTime = (perfTime100nSec
                                    - pPerfData.getLong(perfCounterBlockOffset + this.elapsedTimeOffset)) / 10_000L;
                            proc.setUpTime(upTime < 1L ? 1L : upTime);
                            proc.setStartTime(now - upTime);
                            proc.setBytesRead(pPerfData.getLong(perfCounterBlockOffset + this.ioReadOffset));
                            proc.setBytesWritten(pPerfData.getLong(perfCounterBlockOffset + this.ioWriteOffset));
                            proc.setResidentSetSize(
                                    pPerfData.getLong(perfCounterBlockOffset + this.workingSetPrivateOffset));
                            proc.setParentProcessID(
                                    pPerfData.getInt(perfCounterBlockOffset + this.creatingProcessIdOffset));
                            proc.setPriority(pPerfData.getInt(perfCounterBlockOffset + this.priorityBaseOffset));
                        }

                        // Increment to next instance
                        perfCounterBlock = new PERF_COUNTER_BLOCK(pPerfData.share(perfCounterBlockOffset));
                        perfInstanceOffset = perfCounterBlockOffset + perfCounterBlock.ByteLength;
                    }
                    // We've found the process object and are done, no need to look at any other
                    // objects (shouldn't be any). Break the loop
                    break;
                }
                // Increment for next object (should never need this)
                perfObjectOffset += perfObject.TotalByteLength;
            }
            return processMap;
        }
    }
}
