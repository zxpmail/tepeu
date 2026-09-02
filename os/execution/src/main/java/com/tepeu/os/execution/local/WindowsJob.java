package com.tepeu.os.execution.local;


import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase.PROCESS_INFORMATION;
import com.sun.jna.platform.win32.WinBase.SECURITY_ATTRIBUTES;
import com.sun.jna.platform.win32.WinBase.STARTUPINFO;
import com.sun.jna.platform.win32.WinDef.DWORD;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Windows Job Object：CREATE_SUSPENDED 后再入 job，关闭 job 时杀进程树。
 * 不限制文件系统 → 隔离仍是 partial。JNA 5.19 Kernel32 无 Job API，单独映射。
 */
public final class WindowsJob {

    static final int JOB_OBJECT_EXTENDED_LIMIT_INFORMATION = 9;
    static final int JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE = 0x2000;
    static final int BASIC_LIMIT_FLAGS_OFFSET = 16;
    static final int EXTENDED_LIMIT_INFO_SIZE = 144;
    static final int CREATE_SUSPENDED = 0x00000004;
    static final int CREATE_UNICODE_ENVIRONMENT = 0x00000400;
    static final int STARTF_USESTDHANDLES = 0x00000100;
    static final int HANDLE_FLAG_INHERIT = 0x00000001;
    static final int GENERIC_READ = 0x80000000;
    static final int GENERIC_WRITE = 0x40000000;
    static final int FILE_SHARE_READ = 0x00000001;
    static final int FILE_SHARE_WRITE = 0x00000002;
    static final int CREATE_ALWAYS = 2;
    static final int OPEN_EXISTING = 3;
    static final int FILE_ATTRIBUTE_NORMAL = 0x80;
    static final int WAIT_TIMEOUT = 258;

    private WindowsJob() {
    }

    interface JobApi extends StdCallLibrary {
        JobApi INSTANCE = Native.load("kernel32", JobApi.class, W32APIOptions.DEFAULT_OPTIONS);

        HANDLE CreateJobObject(Pointer jobAttributes, String name);

        boolean SetInformationJobObject(HANDLE job, int infoClass, Pointer info, int size);

        boolean AssignProcessToJobObject(HANDLE job, HANDLE process);

        boolean IsProcessInJob(HANDLE process, HANDLE job, IntByReference result);

        boolean CreateProcessW(String applicationName, char[] commandLine,
                SECURITY_ATTRIBUTES processAttributes, SECURITY_ATTRIBUTES threadAttributes,
                boolean inheritHandles, int creationFlags, Pointer environment,
                String currentDirectory, STARTUPINFO startupInfo, PROCESS_INFORMATION processInformation);

        int ResumeThread(HANDLE thread);
    }

    public static boolean available() {
        try {
            HANDLE job = JobApi.INSTANCE.CreateJobObject(null, null);
            if (job == null || WinNT.INVALID_HANDLE_VALUE.equals(job)) {
                return false;
            }
            Kernel32.INSTANCE.CloseHandle(job);
            return true;
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            return false;
        }
    }

    static HANDLE create() {
        HANDLE job = JobApi.INSTANCE.CreateJobObject(null, null);
        if (job == null || WinNT.INVALID_HANDLE_VALUE.equals(job)) {
            throw new IllegalStateException("CreateJobObject failed err=" + Kernel32.INSTANCE.GetLastError());
        }
        Memory info = new Memory(EXTENDED_LIMIT_INFO_SIZE);
        info.clear();
        info.setInt(BASIC_LIMIT_FLAGS_OFFSET, JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE);
        boolean ok = JobApi.INSTANCE.SetInformationJobObject(
                job, JOB_OBJECT_EXTENDED_LIMIT_INFORMATION, info, EXTENDED_LIMIT_INFO_SIZE);
        if (!ok) {
            int err = Kernel32.INSTANCE.GetLastError();
            Kernel32.INSTANCE.CloseHandle(job);
            throw new IllegalStateException("SetInformationJobObject failed err=" + err);
        }
        return job;
    }

    static void close(HANDLE job) {
        if (job != null && !WinNT.INVALID_HANDLE_VALUE.equals(job)) {
            Kernel32.INSTANCE.CloseHandle(job);
        }
    }

    static Spawned run(Path workspace, List<String> argv, Map<String, String> env, Path stdout,
            int timeoutSeconds, int outputLimit) {
        if (argv == null || argv.isEmpty()) {
            throw new IllegalArgumentException("spawn argv empty");
        }
        HANDLE job = create();
        HANDLE out = null;
        HANDLE nul = null;
        PROCESS_INFORMATION pi = new PROCESS_INFORMATION();
        boolean resumed = false;
        try {
            SECURITY_ATTRIBUTES inherit = inheritable();
            out = Kernel32.INSTANCE.CreateFile(
                    stdout.toAbsolutePath().toString(), GENERIC_WRITE,
                    FILE_SHARE_READ, inherit, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, null);
            if (out == null || WinNT.INVALID_HANDLE_VALUE.equals(out)) {
                throw new IllegalStateException("CreateFile stdout err=" + Kernel32.INSTANCE.GetLastError());
            }
            nul = Kernel32.INSTANCE.CreateFile(
                    "NUL", GENERIC_READ, FILE_SHARE_READ | FILE_SHARE_WRITE, inherit,
                    OPEN_EXISTING, 0, null);
            STARTUPINFO si = new STARTUPINFO();
            si.cb = new DWORD(si.size());
            si.dwFlags = STARTF_USESTDHANDLES;
            si.hStdInput = nul;
            si.hStdOutput = out;
            si.hStdError = out;
            String app = argv.get(0);
            char[] cmd = Native.toCharArray(joinCommand(argv));
            Memory block = environmentBlock(env);
            int flags = CREATE_SUSPENDED | CREATE_UNICODE_ENVIRONMENT;
            boolean created = JobApi.INSTANCE.CreateProcessW(
                    app, cmd, null, null, true, flags, block,
                    workspace.toAbsolutePath().toString(), si, pi);
            if (!created) {
                throw new IllegalStateException("CreateProcessW failed err=" + Kernel32.INSTANCE.GetLastError());
            }
            if (!JobApi.INSTANCE.AssignProcessToJobObject(job, pi.hProcess)) {
                throw new IllegalStateException(
                        "AssignProcessToJobObject failed err=" + Kernel32.INSTANCE.GetLastError());
            }
            IntByReference inJob = new IntByReference();
            if (!JobApi.INSTANCE.IsProcessInJob(pi.hProcess, job, inJob) || inJob.getValue() == 0) {
                throw new IllegalStateException("process not in job before resume");
            }
            if (JobApi.INSTANCE.ResumeThread(pi.hThread) == -1) {
                throw new IllegalStateException("ResumeThread failed err=" + Kernel32.INSTANCE.GetLastError());
            }
            resumed = true;
            int wait = Kernel32.INSTANCE.WaitForSingleObject(pi.hProcess, timeoutSeconds * 1000);
            if (wait == WAIT_TIMEOUT) {
                Kernel32.INSTANCE.TerminateProcess(pi.hProcess, 1);
                return new Spawned(true, 1, SpawnIo.readCapped(stdout, outputLimit));
            }
            IntByReference exit = new IntByReference();
            if (!Kernel32.INSTANCE.GetExitCodeProcess(pi.hProcess, exit)) {
                throw new IllegalStateException("GetExitCodeProcess err=" + Kernel32.INSTANCE.GetLastError());
            }
            return new Spawned(false, exit.getValue(), SpawnIo.readCapped(stdout, outputLimit));
        } catch (RuntimeException e) {
            if (pi.hProcess != null && !WinNT.INVALID_HANDLE_VALUE.equals(pi.hProcess)) {
                Kernel32.INSTANCE.TerminateProcess(pi.hProcess, 1);
            }
            throw e;
        } finally {
            closeHandle(pi.hThread);
            closeHandle(pi.hProcess);
            closeHandle(out);
            closeHandle(nul);
            close(job);
            if (!resumed && pi.hProcess != null) {
                // job close already killed the suspended process if assigned
            }
        }
    }

    private static SECURITY_ATTRIBUTES inheritable() {
        SECURITY_ATTRIBUTES sa = new SECURITY_ATTRIBUTES();
        sa.dwLength = new DWORD(sa.size());
        sa.bInheritHandle = true;
        sa.lpSecurityDescriptor = null;
        return sa;
    }

    private static void closeHandle(HANDLE handle) {
        if (handle != null && !WinNT.INVALID_HANDLE_VALUE.equals(handle)) {
            Kernel32.INSTANCE.CloseHandle(handle);
        }
    }

    private static Memory environmentBlock(Map<String, String> env) {
        StringBuilder text = new StringBuilder();
        if (env != null) {
            for (Map.Entry<String, String> e : env.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) {
                    continue;
                }
                text.append(e.getKey()).append('=').append(e.getValue()).append('\0');
            }
        }
        text.append('\0');
        byte[] bytes = text.toString().getBytes(StandardCharsets.UTF_16LE);
        Memory memory = new Memory(Math.max(2, bytes.length));
        memory.clear();
        memory.write(0, bytes, 0, bytes.length);
        return memory;
    }

    static String joinCommand(List<String> argv) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < argv.size(); i++) {
            if (i > 0) {
                line.append(' ');
            }
            line.append(quote(argv.get(i)));
        }
        return line.toString();
    }

    static String quote(String value) {
        if (value.isEmpty()) {
            return "\"\"";
        }
        boolean need = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == ' ' || c == '\t' || c == '"') {
                need = true;
                break;
            }
        }
        if (!need) {
            return value;
        }
        StringBuilder out = new StringBuilder("\"");
        int slashes = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\') {
                slashes++;
                continue;
            }
            if (c == '"') {
                out.append("\\".repeat(slashes * 2 + 1)).append('"');
            } else {
                out.append("\\".repeat(slashes)).append(c);
            }
            slashes = 0;
        }
        out.append("\\".repeat(slashes * 2)).append('"');
        return out.toString();
    }

    record Spawned(boolean timeout, int exitCode, String output) {
    }
}
