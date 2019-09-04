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

import java.util.Iterator;

/**
 * <p>
 * An implementation of the Aho-Corasick string searching automaton. This
 * implementation of the
 * <a href="http://portal.acm.org/citation.cfm?id=360855&dl=ACM&coll=GUIDE"
 * target="_blank">Aho-Corasick</a> algorithm is optimized to work with bytes.
 * </p>
 * 
 * <p>
 * Example usage: <code><pre>
 AhoCorasick tree = new AhoCorasick();
 tree.add("hello", "hello");
 tree.add("world", "world");
 tree.prepare();

 Iterator searcher = tree.search("hello world".getBytes());
 while (searcher.hasNext()) {
   SearchResult result = searcher.next();
   System.out.println(result.getOutputs());
   System.out.println("Found at index: " + result.getLastIndex());
 }
 </pre></code>
 * </p>
 * 
 * <h2>Recent changes</h2>
 * <ul>
 * 
 * <li>Per user request from Carsten Kruege, I've changed the signature of
 * State.getOutputs() and SearchResults.getOutputs() to Sets rather than Lists.
 * </li>
 * 
 * </ul>
 */
public class AhoCorasick {
    public State root;
    private boolean prepared;

    public AhoCorasick() {
        this.root = new State(0);
        this.prepared = false;
    }

    /**
     * Adds a new keyword with the given output. During search, if the keyword is
     * matched, output will be one of the yielded elements in
     * SearchResults.getOutputs().
     */
    public void add(byte[] keyword, Object output) {
        if (this.prepared)
            throw new IllegalStateException("can't add keywords after prepare() is called");
        State lastState = this.root.extendAll(keyword);
        lastState.addOutput(output);
    }

    /**
     * Prepares the automaton for searching. This must be called before any
     * searching().
     */
    public void prepare() {
        this.prepareFailTransitions();
        this.prepared = true;
    }

    /**
     * Starts a new search, and returns an Iterator of SearchResults.
     */
    public Iterator<SearchResult> search(byte[] bytes) {
        return new Searcher(this, this.startSearch(bytes));
    }

    /**
     * DANGER DANGER: dense algorithm code ahead. Very order dependent. Initializes
     * the fail transitions of all states except for the root.
     */
    private void prepareFailTransitions() {
        Queue<State> q = new Queue<State>();

        for (int i = 0; i < 256; i++)
            if (this.root.get((byte) i) != null) {
                this.root.get((byte) i).setFail(this.root);
                q.add(this.root.get((byte) i));
            }

        this.prepareRoot();
        while (!q.isEmpty()) {
            State state = q.pop();
            byte[] keys = state.keys();
            for (int i = 0; i < keys.length; i++) {
                State r = state;
                byte a = keys[i];
                State s = r.get(a);
                q.add(s);
                r = r.getFail();
                while (r.get(a) == null)
                    r = r.getFail();
                s.setFail(r.get(a));
                // s.getOutputs().addAll(r.get(a).getOutputs());
                if (r.get(a).getOutputs() != null)
                    for (Object o : r.get(a).getOutputs())
                        s.getOutputs().add(o);
            }
        }
    }

    /**
     * Sets all the out transitions of the root to itself, if no transition yet
     * exists at this point.
     */
    private void prepareRoot() {
        for (int i = 0; i < 256; i++)
            if (this.root.get((byte) i) == null)
                this.root.put((byte) i, this.root);
    }

    /**
     * Returns the root of the tree.
     */
    State getRoot() {
        return this.root;
    }

    /**
     * Begins a new search using the raw interface.
     */
    SearchResult startSearch(byte[] bytes) {
        if (!this.prepared)
            throw new IllegalStateException("can't start search until prepare()");
        return continueSearch(new SearchResult(this.root, bytes, 0));
    }

    /**
     * Continues the search, given the initial state described by the lastResult.
     */
    public SearchResult continueSearch1(SearchResult lastResult) {
        byte[] bytes = lastResult.bytes;
        State state = lastResult.lastMatchedState;
        for (int i = lastResult.lastIndex; i < bytes.length; i++) {
            State resultState;
            while ((resultState = state.get(bytes[i])) == null)
                state = state.getFail();
            state = resultState;
            if (state.getOutputs() != null)
                return new SearchResult(state, bytes, i + 1);
        }

        return null;
    }

    public SearchResult continueSearch(SearchResult lastResult) {
        byte[] bytes = lastResult.bytes;
        State state = lastResult.lastMatchedState;
        State resultState;
        for (int i = lastResult.lastIndex; i < bytes.length; i++) {
            byte b = bytes[i];
            while ((resultState = state.edgeList.array[(int) b & 0xFF]) == null)
                state = state.fail;
            state = resultState;
            if (state.outputs != null)
                return new SearchResult(state, bytes, i + 1);
        }

        return null;
    }
}
