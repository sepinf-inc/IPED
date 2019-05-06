/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de Evidências Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package dpf.sp.gpinf.indexer.util;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

public class ProcessUtil {

    @Deprecated
	public static int getPidWin(int port) {
		String[] command = { "netstat", "-on" }; //$NON-NLS-1$ //$NON-NLS-2$
		try {
			Process netstat = Runtime.getRuntime().exec(command);

			StringBuilder conectionList = new StringBuilder();
			Reader reader = new InputStreamReader(netstat.getInputStream());
			char[] buffer = new char[1024];
			for (int n = reader.read(buffer); n != -1; n = reader.read(buffer))
				conectionList.append(buffer, 0, n);
			reader.close();
			String[] conections = conectionList.toString().split("\n"); //$NON-NLS-1$
			int portIdx = 10000;
			String pid = null;
			for (String conection : conections) {
				int idx = conection.indexOf(":" + port); //$NON-NLS-1$
				if (idx == -1 || idx > portIdx)
					continue;
				String state = "ESTABLISHED"; //$NON-NLS-1$
				int stateIdx = conection.indexOf(state);
				if (stateIdx == -1)
					continue;
				portIdx = idx;
				idx = stateIdx + state.length();
				pid = conection.substring(idx).trim();
			}
			if (pid != null)
				return Integer.valueOf(pid);

		} catch (Exception e) {
		}

		return 0;

	}

	@Deprecated
	public static int getPidLinux(int port) {
		String[] command = { "netstat", "-anp" }; //$NON-NLS-1$ //$NON-NLS-2$
		try {
			Process netstat = Runtime.getRuntime().exec(command);

			StringBuilder conectionList = new StringBuilder();
			Reader reader = new InputStreamReader(netstat.getInputStream());
			char[] buffer = new char[1024];
			for (int n = reader.read(buffer); n != -1; n = reader.read(buffer))
				conectionList.append(buffer, 0, n);
			reader.close();
			String[] conections = conectionList.toString().split("\n"); //$NON-NLS-1$
			String pid = null;
			for (String conection : conections) {
				if (conection.contains(":" + port) && conection.contains("/soffice.bin")) { //$NON-NLS-1$ //$NON-NLS-2$
					int idx = conection.indexOf("/soffice.bin"); //$NON-NLS-1$
					int idx2 = idx;
					while (Character.isDigit(conection.charAt(--idx2)))
						;
					pid = conection.substring(idx2 + 1, idx);
				}
			}
			if (pid != null)
				return Integer.valueOf(pid);

		} catch (Exception e) {
		}

		return 0;
	}

	@Deprecated
	public static int getPid(int port) {
		if (System.getProperty("os.name").startsWith("Windows")) //$NON-NLS-1$ //$NON-NLS-2$
			return getPidWin(port);
		else
			return getPidLinux(port);
	}

	@Deprecated
	public static void killProcess(int port) {

		int pid = getPid(port);
		if (pid == 0)
			return;

		String[] command = { "taskkill", "/F", "/T", "/PID", Integer.toString(pid) }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		if (System.getProperty("os.name").startsWith("Linux")) { //$NON-NLS-1$ //$NON-NLS-2$
			String[] cmd = { "kill", "-9", Integer.toString(pid) }; //$NON-NLS-1$ //$NON-NLS-2$
			command = cmd;
		}

		try {
			Process killer = Runtime.getRuntime().exec(command);
			int result = killer.waitFor();
			System.out.println("Killed pid " + pid + " exitValue: " + result); //$NON-NLS-1$ //$NON-NLS-2$

		} catch (IOException e1) {
			e1.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	private static int getPidWin(String name, String arg) {
        String[] command = { "wmic", "process", "where", "caption=\""+name+"\"", "get", "name,commandline,processid" }; //$NON-NLS-1$ //$NON-NLS-2$
        try {
            Process netstat = Runtime.getRuntime().exec(command);

            StringBuilder conectionList = new StringBuilder();
            Reader reader = new InputStreamReader(netstat.getInputStream());
            char[] buffer = new char[1024];
            for (int n = reader.read(buffer); n != -1; n = reader.read(buffer))
                conectionList.append(buffer, 0, n);
            reader.close();
            String[] conections = conectionList.toString().split("\n"); //$NON-NLS-1$
            String pid = null;
            for (String conection : conections) {
                if(conection.contains(name) && conection.contains(arg)) {
                    String[] strs = conection.trim().split(" ");
                    pid = strs[strs.length - 1];
                    return Integer.valueOf(pid);
                }
            }

        } catch (Exception e) {
        }

        return 0;

    }

	private static int getPidLinux(String name, String arg) {
        String[] command = { "ps", "ax" }; //$NON-NLS-1$ //$NON-NLS-2$
        try {
            Process netstat = Runtime.getRuntime().exec(command);

            StringBuilder conectionList = new StringBuilder();
            Reader reader = new InputStreamReader(netstat.getInputStream());
            char[] buffer = new char[1024];
            for (int n = reader.read(buffer); n != -1; n = reader.read(buffer))
                conectionList.append(buffer, 0, n);
            reader.close();
            String[] conections = conectionList.toString().split("\n"); //$NON-NLS-1$
            String pid = null;
            for (String conection : conections) {
                if(conection.contains(name) && conection.contains(arg)) {
                    String[] strs = conection.trim().split(" ");
                    pid = strs[0];
                    return Integer.valueOf(pid);
                }
            }

        } catch (Exception e) {
        }

        return 0;
    }

    private static int getPid(String name, String arg) {
        if (System.getProperty("os.name").toLowerCase().startsWith("windows")) //$NON-NLS-1$ //$NON-NLS-2$
            return getPidWin(name, arg);
        else
            return getPidLinux(name, arg);
    }

    public static void killProcess(String name, String arg) {

        int pid = getPid(name, arg);
        if (pid == 0) {
            return;
        }

        String[] command = { "taskkill", "/F", "/T", "/PID", Integer.toString(pid) }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        if (!System.getProperty("os.name").startsWith("Windows")) { //$NON-NLS-1$ //$NON-NLS-2$
            String[] cmd = { "kill", "-9", Integer.toString(pid) }; //$NON-NLS-1$ //$NON-NLS-2$
            command = cmd;
        }

        try {
            Process killer = Runtime.getRuntime().exec(command);
            int result = killer.waitFor();
            System.out.println("Killed pid " + pid + " exitValue: " + result); //$NON-NLS-1$ //$NON-NLS-2$

        } catch (IOException e1) {
            e1.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
