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

/**
 *  Linked list implementation of the EdgeList should be less memory-intensive.
 */
class SparseEdgeList implements EdgeList {
  private Cons head;

  public SparseEdgeList() {
    head = null;
  }

  public State get(byte b) {
    Cons c = head;
    while (c != null) {
      if (c.b == b)
        return c.s;
      c = c.next;
    }

    return null;
  }

  public void put(byte b, State s) {
    this.head = new Cons(b, s, head);
  }

  public byte[] keys() {
    int length = 0;
    Cons c = head;
    while (c != null) {
      length++;
      c = c.next;
    }

    byte[] result = new byte[length];
    c = head;
    int j = 0;
    while (c != null) {
      result[j] = c.b;
      j++;
      c = c.next;
    }

    return result;
  }

  static private class Cons {
    byte b;
    State s;
    Cons next;

    public Cons(byte b, State s, Cons next) {
      this.b = b;
      this.s = s;
      this.next = next;
    }
  }
}
