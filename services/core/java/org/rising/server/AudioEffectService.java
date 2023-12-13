/*
 * Copyright (C) 2023 The RisingOS Android Project
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

package org.rising.server;

import android.content.Context;
import com.android.server.SystemService;

import org.rising.server.AudioEffectsUtils;

public final class AudioEffectService extends SystemService {

    private static final String TAG = "AudioEffectService";
    private AudioEffectsUtils audioEffectsUtils;
    private Context mContext;

    @Override
    public void onStart() {
        audioEffectsUtils = new AudioEffectsUtils(mContext);
        audioEffectsUtils.enableEqualizer();
        audioEffectsUtils.enableDynamicMode();
    }

    public AudioEffectService(Context context) {
        super(context);
        mContext = context;
    }
}
