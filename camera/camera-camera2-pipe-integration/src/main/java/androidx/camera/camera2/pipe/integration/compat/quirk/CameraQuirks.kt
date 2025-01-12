/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.camera.camera2.pipe.integration.compat.quirk

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.camera.camera2.pipe.CameraMetadata
import androidx.camera.camera2.pipe.integration.compat.StreamConfigurationMapCompat
import androidx.camera.camera2.pipe.integration.config.CameraScope
import androidx.camera.core.impl.Quirk
import androidx.camera.core.impl.Quirks
import javax.inject.Inject

/** Provider of camera specific quirks. */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
@CameraScope
class CameraQuirks @Inject constructor(
    private val cameraMetadata: CameraMetadata,
    private val streamConfigurationMapCompat: StreamConfigurationMapCompat
) {

    /**
     * Goes through all defined camera specific quirks, then filters them to retrieve quirks
     * required for the camera identified by the provided [CameraMetadata].
     */
    val quirks: Quirks by lazy {
        val quirks: MutableList<Quirk> = mutableListOf()

        // Go through all defined camera quirks in lexicographical order,
        // and add them to `quirks` if they should be loaded
        if (AfRegionFlipHorizontallyQuirk.isEnabled(cameraMetadata)) {
            quirks.add(AfRegionFlipHorizontallyQuirk())
        }
        if (CamcorderProfileResolutionQuirk.isEnabled(cameraMetadata)) {
            quirks.add(CamcorderProfileResolutionQuirk(streamConfigurationMapCompat))
        }
        if (ImageCaptureFailWithAutoFlashQuirk.isEnabled(cameraMetadata)) {
            quirks.add(ImageCaptureFailWithAutoFlashQuirk())
        }
        if (JpegHalCorruptImageQuirk.isEnabled()) {
            quirks.add(JpegHalCorruptImageQuirk())
        }
        if (YuvImageOnePixelShiftQuirk.isEnabled()) {
            quirks.add(YuvImageOnePixelShiftQuirk())
        }

        Quirks(quirks)
    }
}
