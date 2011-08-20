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
package net.atoom.android.tt2;

import android.view.MotionEvent;
import android.view.GestureDetector.OnGestureListener;

public class MainWebViewGestureDetector implements OnGestureListener {

	private final MainWebViewAnimator myMainWebViewAnimator;
	private long myLastScrollTime = System.currentTimeMillis();

	public MainWebViewGestureDetector(MainWebViewAnimator mainWebViewAnimator) {
		myMainWebViewAnimator = mainWebViewAnimator;
	}

	public boolean onDown(MotionEvent e) {
		return false;
	}

	public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
			float velocityY) {
		int xscroll = (int) (e1.getX() - e2.getX());
		if (Math.abs(xscroll) > 100) {
			if (System.currentTimeMillis() - myLastScrollTime > 500) {
				myLastScrollTime = System.currentTimeMillis();
				if (xscroll > 0) {
					myMainWebViewAnimator.loadNextPage();
				} else {
					myMainWebViewAnimator.loadPrevPage();
				}
			}
		}
		return false;
	}

	public void onLongPress(MotionEvent e) {
	}

	public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
			float distanceY) {
		int xscroll = (int) (e1.getX() - e2.getX());
		if (Math.abs(xscroll) > 100) {
			return true;
		}
		return false;
	}

	public void onShowPress(MotionEvent e) {
	}

	public boolean onSingleTapUp(MotionEvent e) {
		return false;
	}
}
