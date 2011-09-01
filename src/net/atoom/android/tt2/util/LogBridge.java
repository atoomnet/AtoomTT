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

import net.atoom.android.tt2.TTActivity;
import android.util.Log;

public final class LogBridge {

	// switch to false before release
	private static final boolean doLog = true;

	public static void i(final String m) {
		if (doLog)
			Log.i(TTActivity.LOGGING_TAG, m);
	}

	public static void w(final String m) {
		if (doLog)
			Log.w(TTActivity.LOGGING_TAG, m);
	}

	public static boolean isLoggable() {
		return doLog
				&& (Log.isLoggable(TTActivity.LOGGING_TAG, Log.INFO) || Log
						.isLoggable(TTActivity.LOGGING_TAG, Log.WARN));
	}
}
