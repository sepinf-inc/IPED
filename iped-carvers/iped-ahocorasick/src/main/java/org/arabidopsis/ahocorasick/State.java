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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A state represents an element in the Aho-Corasick tree.
 */
class State {
    // Arbitrarily chosen constant. If this state ends up getting
    // deeper than THRESHOLD_TO_USE_SPARSE, then we switch over to a
    // sparse edge representation. I did a few tests, and there's a
    // local minima here. We may want to choose a more sophisticated
    // strategy.
    private static final int THRESHOLD_TO_USE_SPARSE = Integer.MAX_VALUE;

    private int depth;
    public DenseEdgeList edgeList;
    public State fail;
    public List<Object> outputs;

    public State(int depth) {
        this.depth = depth;
        // if (depth > THRESHOLD_TO_USE_SPARSE)
        // this.edgeList = new SparseEdgeList();
        // else
        this.edgeList = new DenseEdgeList();
        this.fail = null;
    }

    public State extend(byte b) {
        if (this.edgeList.get(b) != null)
            return this.edgeList.get(b);

        State nextState = new State(this.depth + 1);
        this.edgeList.put(b, nextState);

        return nextState;
    }

    public State extendAll(byte[] bytes) {
        State state = this;

        for (int i = 0; i < bytes.length; i++) {
            if (state.edgeList.get(bytes[i]) != null)
                state = state.edgeList.get(bytes[i]);
            else
                state = state.extend(bytes[i]);
        }

        return state;
    }

    /**
     * Returns the size of the tree rooted at this State. Note: do not call this if
     * there are loops in the edgelist graph, such as those introduced by
     * AhoCorasick.prepare().
     */
    public int size() {
        byte[] keys = edgeList.keys();
        int result = 1;
        for (int i = 0; i < keys.length; i++)
            result += edgeList.get(keys[i]).size();

        return result;
    }

    public final State get(byte b) {
        return this.edgeList.get(b);
    }

    public void put(byte b, State s) {
        this.edgeList.put(b, s);
    }

    public byte[] keys() {
        return this.edgeList.keys();
    }

    public final State getFail() {
        return this.fail;
    }

    public void setFail(State f) {
        this.fail = f;
    }

    public void addOutput(Object o) {
        if (outputs == null)
            this.outputs = new ArrayList<Object>();
        this.outputs.add(o);
    }

    public final List<Object> getOutputs() {
        return this.outputs;
    }
}
