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

	/*
	 * static interface Kernel32 extends Library {
	 * 
	 * public static Kernel32 INSTANCE = (Kernel32)
	 * Native.loadLibrary("kernel32", Kernel32.class);
	 * 
	 * public int GetProcessId(Long hProcess); }
	 * 
	 * public static int getPid(Process p) { Field f;
	 * 
	 * if (Platform.isWindows()) { try { f =
	 * p.getClass().getDeclaredField("handle"); f.setAccessible(true); long
	 * handLong = f.getLong(p); //WinNT.HANDLE handle = new
	 * WinNT.HANDLE(Pointer.createConstant(handLong)); int pid =
	 * Kernel32.INSTANCE.GetProcessId(handLong);
	 * 
	 * // System.out.println(Kernel.INSTANCE.GetProcessId(handLong) + " - " +
	 * pid);
	 * 
	 * return pid; } catch (Exception ex) { ex.printStackTrace(); } } else if
	 * (Platform.isLinux()) { try { f = p.getClass().getDeclaredField("pid");
	 * f.setAccessible(true); int pid = (Integer) f.get(p); return pid; } catch
	 * (Exception ex) { ex.printStackTrace(); } } return 0; }
	 */

	public static int getPidWin(int port) {
		String[] command = { "netstat", "-on" };
		try {
			Process netstat = Runtime.getRuntime().exec(command);

			StringBuilder conectionList = new StringBuilder();
			Reader reader = new InputStreamReader(netstat.getInputStream());
			char[] buffer = new char[1024];
			for (int n = reader.read(buffer); n != -1; n = reader.read(buffer))
				conectionList.append(buffer, 0, n);
			reader.close();
			String[] conections = conectionList.toString().split("\n");
			int portIdx = 10000;
			String pid = null;
			for (String conection : conections) {
				int idx = conection.indexOf(":" + port);
				if (idx == -1 || idx > portIdx)
					continue;
				String state = "ESTABLISHED";
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

	public static int getPidLinux(int port) {
		String[] command = { "netstat", "-anp" };
		try {
			Process netstat = Runtime.getRuntime().exec(command);

			StringBuilder conectionList = new StringBuilder();
			Reader reader = new InputStreamReader(netstat.getInputStream());
			char[] buffer = new char[1024];
			for (int n = reader.read(buffer); n != -1; n = reader.read(buffer))
				conectionList.append(buffer, 0, n);
			reader.close();
			String[] conections = conectionList.toString().split("\n");
			String pid = null;
			for (String conection : conections) {
				if (conection.contains(":" + port) && conection.contains("/soffice.bin")) {
					int idx = conection.indexOf("/soffice.bin");
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

	public static int getPid(int port) {
		if (System.getProperty("os.name").startsWith("Windows"))
			return getPidWin(port);
		else
			return getPidLinux(port);
	}

	public static void killProcess(int port) {

		int pid = getPid(port);
		if (pid == 0)
			return;

		String[] command = { "taskkill", "/F", "/T", "/PID", Integer.toString(pid) };
		if (System.getProperty("os.name").startsWith("Linux")) {
			String[] cmd = { "kill", "-9", Integer.toString(pid) };
			command = cmd;
		}

		try {
			Process killer = Runtime.getRuntime().exec(command);
			int result = killer.waitFor();
			System.out.println("Killed pid " + pid + " exitValue: " + result);

		} catch (IOException e1) {
			e1.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
