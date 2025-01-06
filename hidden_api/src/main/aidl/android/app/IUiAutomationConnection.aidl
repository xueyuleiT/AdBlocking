/**
 * amagi <https://github.com/gkd-kit/gkd>
 * Copyright (C) 2024 amagi
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package android.app;

import android.accessibilityservice.IAccessibilityServiceClient;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.view.InputEvent;
import android.os.ParcelFileDescriptor;

interface IUiAutomationConnection {
    void connect(IAccessibilityServiceClient client, int flags);
    void disconnect();
    boolean injectInputEvent(in InputEvent event, boolean sync);
    void syncInputTransactions();
    boolean setRotation(int rotation);
    Bitmap takeScreenshot(in Rect crop, int rotation);
    void executeShellCommand(String command, in ParcelFileDescriptor sink,
            in ParcelFileDescriptor source);
    oneway void shutdown();
}
