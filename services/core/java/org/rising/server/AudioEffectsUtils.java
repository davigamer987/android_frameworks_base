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
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.PresetReverb;
import android.media.audiofx.Virtualizer;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AudioEffectsUtils {

    private Context context;
    private Equalizer equalizer;
    private BassBoost bassBoost;
    private PresetReverb presetReverb;
    private Virtualizer virtualizer;

    private AudioManager audioManager;
    private AudioTrack audioTrack;
    private ScheduledExecutorService dynamicModeScheduler;

    private boolean isEQEnabled = false;
    private boolean isBassBoostEnabled = false;
    private boolean isReverbEnabled = false;
    private boolean isDynamicModeEnabled = false;

    public AudioEffectsUtils(Context context) {
        this.context = context;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        initializeAudioEffects(AudioManager.AUDIO_SESSION_ID_GENERATE);
    }

    private void initializeAudioEffects(int audioSessionId) {
        this.equalizer = new Equalizer(0, audioSessionId);
        this.bassBoost = new BassBoost(0, audioSessionId);
        this.presetReverb = new PresetReverb(0, audioSessionId);
        this.virtualizer = new Virtualizer(0, audioSessionId);
    }

    public void enableEqualizer() {
        if (!isEQEnabled) {
            equalizer.setEnabled(true);
            isEQEnabled = true;
        }
    }

    public void disableEqualizer() {
        if (isEQEnabled) {
            equalizer.setEnabled(false);
            isEQEnabled = false;
        }
    }

    public void setEqualization(short band, short level) {
        if (isEQEnabled) {
            short currentLevel = equalizer.getBandLevel(band);
            if (currentLevel != level) {
                equalizer.setBandLevel(band, level);
            }
        }
    }

    // Bass boost audio effect
    public void enableBassBoost() {
        if (!isBassBoostEnabled) {
            bassBoost.setEnabled(true);
            isBassBoostEnabled = true;
        }
    }

    public void disableBassBoost() {
        if (isBassBoostEnabled) {
            bassBoost.setEnabled(false);
            isBassBoostEnabled = false;
        }
    }

    public void setBassBoostStrength(short strength) {
        if (isBassBoostEnabled && bassBoost.getRoundedStrength() != strength) {
            bassBoost.setStrength(strength);
        }
    }

    // soft reverb
    public void enableSoftMode() {
        if (!isReverbEnabled) {
            presetReverb.setPreset(PresetReverb.PRESET_SMALLROOM);
            presetReverb.setEnabled(true);
            isReverbEnabled = true;
        }
    }

    public void disableSoftMode() {
        if (isReverbEnabled) {
            presetReverb.setEnabled(false);
            isReverbEnabled = false;
        }
    }

    // dynamic equalizer mode
    public void enableDynamicMode() {
        if (!isDynamicModeEnabled) {
            isDynamicModeEnabled = true;
            enableDynamicEqMode();
        }
    }

    public void disableDynamicMode() {
        if (isDynamicModeEnabled) {
            isDynamicModeEnabled = false;
            if (dynamicModeScheduler != null && !dynamicModeScheduler.isShutdown()) {
                dynamicModeScheduler.shutdown();
            }
        }
    }

    private void enableDynamicEqMode() {
        if (dynamicModeScheduler == null || dynamicModeScheduler.isShutdown()) {
            dynamicModeScheduler = Executors.newSingleThreadScheduledExecutor();
        }
        dynamicModeScheduler.scheduleWithFixedDelay(new Runnable() {
            private int lastVolumeLevel = -1;
            @Override
            public void run() {
                if (!isDynamicModeEnabled) {
                    dynamicModeScheduler.shutdownNow();
                    return;
                }
                int currentVolume = getCurrentVolumeLevel();
                if (currentVolume != lastVolumeLevel) {
                    applyDynamicFrequencyResponse(currentVolume);
                    lastVolumeLevel = currentVolume;
                }
            }
        }, 0, 500, TimeUnit.MILLISECONDS);
    }

    // get the current volume of the device
    private int getCurrentVolumeLevel() {
        return (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) * 100) /
               audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
    }

    // Dynamic Frequency Response
    private void applyDynamicFrequencyResponse(int volumeLevel) {
        final short minEQLevel = equalizer.getBandLevelRange()[0];
        final short maxEQLevel = equalizer.getBandLevelRange()[1];
        short numberOfBands = equalizer.getNumberOfBands();
        for (short i = 0; i < numberOfBands; i++) {
            short newLevel = calculateBandLevel(i, numberOfBands, minEQLevel, maxEQLevel, volumeLevel);
            short currentLevel = equalizer.getBandLevel(i);
            if (currentLevel != newLevel) {
                equalizer.setBandLevel(i, newLevel);
            }
        }
        applyDynamicBassBoost(volumeLevel);
        applyDynamicReverb(volumeLevel);
        applyDynamicSpatialAudio(volumeLevel);
    }

    private short calculateBandLevel(short band, short numberOfBands, short minEQLevel, short maxEQLevel, int volumeLevel) {
        double lowFrequencyAdjustment = 1.1; 
        double midHighFrequencyAdjustment = 1.2;
        double volumeAdjustment = 0.85 + (volumeLevel / 100.0 * 0.6);
        double adjustmentFactor = band < numberOfBands / 2 ? lowFrequencyAdjustment : midHighFrequencyAdjustment;
        return (short) Math.min(maxEQLevel, Math.max(minEQLevel,
                minEQLevel + (adjustmentFactor * volumeAdjustment * (maxEQLevel - minEQLevel))));
    }

    private short calculateOutputGain(int volumeLevel) {
        double gainFactor = 1.0 + (1.0 - volumeLevel / 100.0);
        short gain = (short) (gainFactor * (equalizer.getBandLevelRange()[1] - equalizer.getBandLevelRange()[0]) / 5);
        return (short) Math.min(gain, equalizer.getBandLevelRange()[1]);
    }

    private void applyDynamicBassBoost(int volumeLevel) {
        if (bassBoost != null) {
            short strength = (short) Math.max(0, 1000 - (volumeLevel * 5));
            bassBoost.setStrength(strength);
        }
    }

    private void applyDynamicReverb(int volumeLevel) {
        if (presetReverb != null) {
            short newPreset;
            if (volumeLevel < 33) {
                newPreset = PresetReverb.PRESET_SMALLROOM;
            } else if (volumeLevel < 66) {
                newPreset = PresetReverb.PRESET_MEDIUMROOM;
            } else {
                newPreset = PresetReverb.PRESET_LARGEROOM;
            }
            presetReverb.setPreset(newPreset);
        }
    }
    
    private void applyDynamicSpatialAudio(int volumeLevel) {
        virtualizer.setStrength((short) Math.min(1500, 800 + volumeLevel * 2));
    }
}
