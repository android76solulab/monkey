/*
 * Copyright 2017 Zhihu Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.miraclehen.monkey.utils;

import android.content.Context;
import android.util.DisplayMetrics;
import android.util.TypedValue;


public class UIUtils {

    public static int spanCount(Context context, int gridExpectedSize) {
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        float expected = (float) screenWidth / (float) gridExpectedSize;
        int spanCount = Math.round(expected);
        if (spanCount == 0) {
            spanCount = 1;
        }
        return spanCount;
    }

    /**
     * Converts the given device independent pixels (DIP) value into the corresponding pixels
     * value for the current screen.
     *
     * @param context Context instance
     * @param dip     The DIP value to convert
     * @return The pixels value for the current screen of the given DIP value.
     */
    public static int convertDIPToPixels(Context context, float dip) {
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dip, displayMetrics);
    }

    /**
     * Converts the given pixels value into the corresponding device independent pixels (DIP)
     * value for the current screen.
     *
     * @param context Context instance
     * @param pixels  The pixels value to convert
     * @return The DIP value for the current screen of the given pixels value.
     */
    public static float convertPixelsToDIP(Context context, int pixels) {
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        return pixels / (displayMetrics.densityDpi / 160f);
    }

}
