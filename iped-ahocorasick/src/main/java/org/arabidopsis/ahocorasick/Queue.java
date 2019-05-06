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

/**
 * Quick-and-dirty queue class. Essentially uses two lists to represent a queue.
 */
class Queue<T> {
  ArrayList<T> l1;
  ArrayList<T> l2;

  public Queue() {
    l1 = new ArrayList<T>();
    l2 = new ArrayList<T>();
  }

  public void add(T s) {
    l2.add(s);
  }

  public boolean isEmpty() {
    return l1.isEmpty() && l2.isEmpty();
  }

  public T pop() {
    if (isEmpty())
      throw new IllegalStateException("Popping empty queue.");

    if (l1.isEmpty()) {
      for (int i = l2.size() - 1; i >= 0; i--)
        l1.add(l2.remove(i));

      assert l2.isEmpty();
      assert !l1.isEmpty();
    }

    return l1.remove(l1.size() - 1);
  }
}
