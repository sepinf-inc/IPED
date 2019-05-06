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

import java.util.List;
import java.util.Set;

/**
 *  <p>Holds the result of the search so far.  Includes the outputs where the search finished as
 *  well as the last index of the matching.</p>
 *
 *   <p>(Internally, it also holds enough state to continue a running search, though this is not
 *   exposed for public use.)</p>
 */
public class SearchResult {
  public State lastMatchedState;
  byte[] bytes;
  int lastIndex;

  public SearchResult(State s, byte[] bs, int i) {
    this.lastMatchedState = s;
    this.bytes = bs;
    this.lastIndex = i;
  }

  /**
   * Returns a list of the outputs of this match.
   */
  public List<Object> getOutputs() {
    return lastMatchedState.getOutputs();
  }

  /**
   * Returns the index where the search terminates.  Note that this is one byte after the last
   * matching byte.
   */
  public int getLastIndex() {
    return lastIndex;
  }
}
