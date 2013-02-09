/**
 *    Copyright 2009 Bram de Kruijff <bdekruijff [at] gmail [dot] com>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package net.atoom.android.tt2.util;

import java.util.LinkedList;

public final class BoundStack<T> {

	private final LinkedList<T> myList;
	private final int myStackSize;

	public BoundStack(final int stacksize) {
		myStackSize = stacksize;
		myList = new LinkedList<T>();
	}

	public void push(T value) {
		myList.addFirst(value);
		if (myList.size() > myStackSize) {
			myList.removeLast();
		}
	}

	public T pop() {
		T value = null;
		if (myList.size() > 0) {
			value = myList.getFirst();
			myList.removeFirst();
		}
		return value;
	}

	public int size() {
		return myList.size();
	}
}
