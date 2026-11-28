package iped.app.bootstrap;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

/**
 * Reads the Unicode command lines of Windows processes. Only call on Windows.
 */
final class WindowsCommandLine {

    private static final int PROCESS_QUERY_LIMITED_INFORMATION = 0x1000;

    /** ProcessCommandLineInformation, available since Windows 8.1. */
    private static final int PROCESS_COMMAND_LINE_INFORMATION = 60;

    private static final int STATUS_SUCCESS = 0;

    private interface Kernel32 extends StdCallLibrary {
        Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class, W32APIOptions.UNICODE_OPTIONS);

        Pointer GetCommandLineW();

        Pointer OpenProcess(int desiredAccess, boolean inheritHandle, int processId);

        boolean CloseHandle(Pointer handle);

        Pointer LocalFree(Pointer memory);
    }

    private interface Shell32 extends StdCallLibrary {
        Shell32 INSTANCE = Native.load("shell32", Shell32.class, W32APIOptions.UNICODE_OPTIONS);

        Pointer CommandLineToArgvW(WString commandLine, IntByReference numArgs);
    }

    private interface NtDll extends StdCallLibrary {
        NtDll INSTANCE = Native.load("ntdll", NtDll.class, W32APIOptions.DEFAULT_OPTIONS);

        int NtQueryInformationProcess(Pointer processHandle, int processInformationClass, Pointer processInformation,
                int processInformationLength, IntByReference returnLength);
    }

    private WindowsCommandLine() {
    }

    /** @return the arguments of this process, starting with the executable */
    static String[] currentProcessArgs() {
        return split(Kernel32.INSTANCE.GetCommandLineW().getWideString(0));
    }

    /**
     * @return the arguments of another process, starting with its executable, or
     *         null if they could not be read
     */
    static String[] processArgs(long pid) {
        Pointer handle = Kernel32.INSTANCE.OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, false, (int) pid);
        if (handle == null) {
            return null;
        }
        try {
            int size = 8192;
            for (int attempt = 0; attempt < 3; attempt++) {
                Memory buffer = new Memory(size);
                IntByReference needed = new IntByReference();
                int status = NtDll.INSTANCE.NtQueryInformationProcess(handle, PROCESS_COMMAND_LINE_INFORMATION, buffer,
                        size, needed);
                if (status == STATUS_SUCCESS) {
                    // UNICODE_STRING: USHORT Length (in bytes), USHORT MaximumLength, PWSTR Buffer (pointer aligned)
                    int length = buffer.getShort(0) & 0xFFFF;
                    Pointer chars = buffer.getPointer(Native.POINTER_SIZE);
                    if (chars == null) {
                        return null;
                    }
                    return split(new String(chars.getCharArray(0, length / 2)));
                }
                if (needed.getValue() <= size) {
                    return null;
                }
                size = needed.getValue();
            }
            return null;
        } finally {
            Kernel32.INSTANCE.CloseHandle(handle);
        }
    }

    /** Splits a command line with the same rules used by the C runtime. */
    static String[] split(String commandLine) {
        IntByReference count = new IntByReference();
        Pointer argv = Shell32.INSTANCE.CommandLineToArgvW(new WString(commandLine), count);
        if (argv == null) {
            return null;
        }
        try {
            Pointer[] pointers = argv.getPointerArray(0, count.getValue());
            String[] args = new String[pointers.length];
            for (int i = 0; i < pointers.length; i++) {
                args[i] = pointers[i].getWideString(0);
            }
            return args;
        } finally {
            Kernel32.INSTANCE.LocalFree(argv);
        }
    }
}
