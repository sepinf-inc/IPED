/*
Copyright (c) 2005, 2008 Danny Yoo
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

  * Redistributions of source code must retain the above copyright
    notice, this list of conditions and the following disclaimer.

  * Redistributions in binary form must reproduce the above
    copyright notice, this list of conditions and the following disclaimer in
    the documentation and/or other materials provided with the distribution.

  * Neither the name of the Carnegie Institution of Washington nor
    the names of its contributors may be used to endorse or promote products
    derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.arabidopsis.ahocorasick;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Benchmark {
    public static void main(String[] args) throws IOException, InterruptedException {
        String[] words = { "Christmas", "Cains", "Marley", "spectre", "Ebenezer", "double-ironed", "supernatural",
                "SPIRITS", "Ding", "Ali Baba" };

        long t0 = System.currentTimeMillis();

        BufferedReader fr = new BufferedReader(
                new InputStreamReader(Benchmark.class.getResourceAsStream("christmas.txt")));
        String text = "";
        String line = fr.readLine();
        while (line != null) {
            text += line + "\n";
            line = fr.readLine();
        }

        System.out.println("Starting benchmark");
        long t1 = System.currentTimeMillis();

        AhoCorasick finder = new AhoCorasick();
        for (String word : words)
            finder.add(word.getBytes("windows-1252"), word.getBytes("windows-1252"));
        finder.prepare();

        Iterator it = finder.search(text.getBytes("windows-1252"));
        while (it.hasNext())
            it.next();

        long t2 = System.currentTimeMillis();

        String pattern = "";
        for (String word : words) {
            if (!pattern.equals(""))
                pattern += "|";
            pattern += word;
        }
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(text);
        while (m.find())
            continue;

        long t3 = System.currentTimeMillis();

        System.out.println("File reading: " + Long.toString(t1 - t0) + "ms");
        System.out.println("Aho-Corasick: " + Long.toString(t2 - t1) + "ms");
        System.out.println("Java-regexp: " + Long.toString(t3 - t2) + "ms");
    }
}
