/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.camera.integration.core

import android.Manifest
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import androidx.camera.camera2.Camera2Config
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.integration.core.CameraXActivity.BIND_IMAGE_ANALYSIS
import androidx.camera.integration.core.CameraXActivity.BIND_IMAGE_CAPTURE
import androidx.camera.integration.core.CameraXActivity.BIND_PREVIEW
import androidx.camera.integration.core.CameraXActivity.BIND_VIDEO_CAPTURE
import androidx.camera.integration.core.util.StressTestUtil.HOME_TIMEOUT_MS
import androidx.camera.integration.core.util.StressTestUtil.STRESS_TEST_OPERATION_REPEAT_COUNT
import androidx.camera.integration.core.util.StressTestUtil.STRESS_TEST_REPEAT_COUNT
import androidx.camera.integration.core.util.StressTestUtil.VERIFICATION_TARGET_IMAGE_ANALYSIS
import androidx.camera.integration.core.util.StressTestUtil.VERIFICATION_TARGET_IMAGE_CAPTURE
import androidx.camera.integration.core.util.StressTestUtil.VERIFICATION_TARGET_PREVIEW
import androidx.camera.integration.core.util.StressTestUtil.VERIFICATION_TARGET_VIDEO_CAPTURE
import androidx.camera.integration.core.util.StressTestUtil.assumeCameraSupportUseCaseCombination
import androidx.camera.integration.core.util.StressTestUtil.createCameraSelectorById
import androidx.camera.integration.core.util.StressTestUtil.launchCameraXActivityAndWaitForPreviewReady
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.testing.CameraUtil
import androidx.camera.testing.CoreAppTestUtil
import androidx.camera.testing.LabTestRule
import androidx.camera.testing.StressTestRule
import androidx.camera.testing.fakes.FakeLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.UiDevice
import androidx.testutils.RepeatRule
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@LargeTest
@RunWith(Parameterized::class)
@SdkSuppress(minSdkVersion = 21)
class SwitchCameraStressTest(
    private val cameraId: String
) {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @get:Rule
    val useCamera = CameraUtil.grantCameraPermissionAndPreTest(
        CameraUtil.PreTestCameraIdList(Camera2Config.defaultConfig())
    )

    @get:Rule
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO
        )

    @get:Rule
    val labTest: LabTestRule = LabTestRule()

    @get:Rule
    val repeatRule = RepeatRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var camera: Camera
    private lateinit var cameraIdCameraSelector: CameraSelector

    companion object {
        @ClassRule
        @JvmField val stressTest = StressTestRule()

        @JvmStatic
        @get:Parameterized.Parameters(name = "cameraId = {0}")
        val parameters: Collection<String>
            get() = CameraUtil.getBackwardCompatibleCameraIdListOrThrow()
    }

    @Before
    fun setup(): Unit = runBlocking {
        Assume.assumeTrue(CameraUtil.deviceHasCamera())
        CoreAppTestUtil.assumeCompatibleDevice()
        // Clear the device UI and check if there is no dialog or lock screen on the top of the
        // window before start the test.
        CoreAppTestUtil.prepareDeviceUI(InstrumentationRegistry.getInstrumentation())
        // Use the natural orientation throughout these tests to ensure the activity isn't
        // recreated unexpectedly. This will also freeze the sensors until
        // mDevice.unfreezeRotation() in the tearDown() method. Any simulated rotations will be
        // explicitly initiated from within the test.
        device.setOrientationNatural()

        cameraProvider = ProcessCameraProvider.getInstance(context)[10000, TimeUnit.MILLISECONDS]

        cameraIdCameraSelector = createCameraSelectorById(cameraId)

        camera = withContext(Dispatchers.Main) {
            cameraProvider.bindToLifecycle(FakeLifecycleOwner(), cameraIdCameraSelector)
        }
    }

    @After
    fun tearDown(): Unit = runBlocking {
        if (::cameraProvider.isInitialized) {
            withContext(Dispatchers.Main) {
                cameraProvider.unbindAll()
                cameraProvider.shutdown()[10000, TimeUnit.MILLISECONDS]
            }
        }

        // Unfreeze rotation so the device can choose the orientation via its own policy. Be nice
        // to other tests :)
        device.unfreezeRotation()
        device.pressHome()
        device.waitForIdle(HOME_TIMEOUT_MS)
    }

    @LabTestRule.LabTestOnly
    @Test
    @RepeatRule.Repeat(times = STRESS_TEST_REPEAT_COUNT)
    fun switchCamera_checkPreviewInEachTime_withPreviewImageCapture() {
        val useCaseCombination = BIND_PREVIEW or BIND_IMAGE_CAPTURE
        switchCamera_checkOutput_repeatedly(
            cameraId,
            useCaseCombination,
            VERIFICATION_TARGET_PREVIEW
        )
    }

    @LabTestRule.LabTestOnly
    @Test
    @RepeatRule.Repeat(times = STRESS_TEST_REPEAT_COUNT)
    fun switchCamera_checkImageCaptureInEachTime_withPreviewImageCapture() {
        val useCaseCombination = BIND_PREVIEW or BIND_IMAGE_CAPTURE
        switchCamera_checkOutput_repeatedly(
            cameraId,
            useCaseCombination,
            VERIFICATION_TARGET_IMAGE_CAPTURE
        )
    }

    @LabTestRule.LabTestOnly
    @Test
    @RepeatRule.Repeat(times = STRESS_TEST_REPEAT_COUNT)
    fun switchCamera_checkPreviewInEachTime_withPreviewImageCaptureImageAnalysis() {
        val useCaseCombination = BIND_PREVIEW or BIND_IMAGE_CAPTURE or BIND_IMAGE_ANALYSIS
        switchCamera_checkOutput_repeatedly(
            cameraId,
            useCaseCombination,
            VERIFICATION_TARGET_PREVIEW
        )
    }

    @LabTestRule.LabTestOnly
    @Test
    @RepeatRule.Repeat(times = STRESS_TEST_REPEAT_COUNT)
    fun switchCamera_checkImageCaptureInEachTime_withPreviewImageCaptureImageAnalysis() {
        val useCaseCombination = BIND_PREVIEW or BIND_IMAGE_CAPTURE or BIND_IMAGE_ANALYSIS
        switchCamera_checkOutput_repeatedly(
            cameraId,
            useCaseCombination,
            VERIFICATION_TARGET_IMAGE_CAPTURE
        )
    }

    @LabTestRule.LabTestOnly
    @Test
    @RepeatRule.Repeat(times = STRESS_TEST_REPEAT_COUNT)
    fun switchCamera_checkImageAnalysisInEachTime_withPreviewImageCaptureImageAnalysis() {
        val useCaseCombination = BIND_PREVIEW or BIND_IMAGE_CAPTURE or BIND_IMAGE_ANALYSIS
        switchCamera_checkOutput_repeatedly(
            cameraId,
            useCaseCombination,
            VERIFICATION_TARGET_IMAGE_ANALYSIS
        )
    }

    @LabTestRule.LabTestOnly
    @Test
    @RepeatRule.Repeat(times = STRESS_TEST_REPEAT_COUNT)
    fun switchCamera_checkPreviewInEachTime_withPreviewVideoCapture() {
        val useCaseCombination = BIND_PREVIEW or BIND_VIDEO_CAPTURE
        switchCamera_checkOutput_repeatedly(
            cameraId,
            useCaseCombination,
            VERIFICATION_TARGET_PREVIEW
        )
    }

    @LabTestRule.LabTestOnly
    @Test
    @RepeatRule.Repeat(times = STRESS_TEST_REPEAT_COUNT)
    fun switchCamera_checkVideoCaptureInEachTime_withPreviewVideoCapture() {
        val useCaseCombination = BIND_PREVIEW or BIND_VIDEO_CAPTURE
        switchCamera_checkOutput_repeatedly(
            cameraId,
            useCaseCombination,
            VERIFICATION_TARGET_VIDEO_CAPTURE
        )
    }

    @LabTestRule.LabTestOnly
    @Test
    @RepeatRule.Repeat(times = STRESS_TEST_REPEAT_COUNT)
    fun switchCamera_checkPreviewInEachTime_withPreviewVideoCaptureImageCapture() {
        val useCaseCombination = BIND_PREVIEW or BIND_VIDEO_CAPTURE or BIND_IMAGE_CAPTURE
        assumeBothLensFacingCamerasSupportUseCaseCombination(camera, useCaseCombination)
        switchCamera_checkOutput_repeatedly(
            cameraId,
            useCaseCombination,
            VERIFICATION_TARGET_PREVIEW
        )
    }

    @LabTestRule.LabTestOnly
    @Test
    @RepeatRule.Repeat(times = STRESS_TEST_REPEAT_COUNT)
    fun switchCamera_checkVideoCaptureInEachTime_withPreviewVideoCaptureImageCapture() {
        val useCaseCombination = BIND_PREVIEW or BIND_VIDEO_CAPTURE or BIND_IMAGE_CAPTURE
        assumeBothLensFacingCamerasSupportUseCaseCombination(camera, useCaseCombination)
        switchCamera_checkOutput_repeatedly(
            cameraId,
            useCaseCombination,
            VERIFICATION_TARGET_VIDEO_CAPTURE
        )
    }

    @LabTestRule.LabTestOnly
    @Test
    @RepeatRule.Repeat(times = STRESS_TEST_REPEAT_COUNT)
    fun switchCamera_checkImageCaptureInEachTime_withPreviewVideoCaptureImageCapture() {
        val useCaseCombination = BIND_PREVIEW or BIND_VIDEO_CAPTURE or BIND_IMAGE_CAPTURE
        assumeBothLensFacingCamerasSupportUseCaseCombination(camera, useCaseCombination)
        switchCamera_checkOutput_repeatedly(
            cameraId,
            useCaseCombination,
            VERIFICATION_TARGET_IMAGE_CAPTURE
        )
    }

    @LabTestRule.LabTestOnly
    @Test
    @RepeatRule.Repeat(times = STRESS_TEST_REPEAT_COUNT)
    fun switchCamera_checkPreviewInEachTime_withPreviewVideoCaptureImageAnalysis() {
        val useCaseCombination = BIND_PREVIEW or BIND_VIDEO_CAPTURE or BIND_IMAGE_ANALYSIS
        assumeBothLensFacingCamerasSupportUseCaseCombination(camera, useCaseCombination)
        switchCamera_checkOutput_repeatedly(
            cameraId,
            useCaseCombination,
            VERIFICATION_TARGET_PREVIEW
        )
    }

    @LabTestRule.LabTestOnly
    @Test
    @RepeatRule.Repeat(times = STRESS_TEST_REPEAT_COUNT)
    fun switchCamera_checkVideoCaptureInEachTime_withPreviewVideoCaptureImageAnalysis() {
        val useCaseCombination = BIND_PREVIEW or BIND_VIDEO_CAPTURE or BIND_IMAGE_ANALYSIS
        assumeBothLensFacingCamerasSupportUseCaseCombination(camera, useCaseCombination)
        switchCamera_checkOutput_repeatedly(
            cameraId,
            useCaseCombination,
            VERIFICATION_TARGET_VIDEO_CAPTURE
        )
    }

    @LabTestRule.LabTestOnly
    @Test
    @RepeatRule.Repeat(times = STRESS_TEST_REPEAT_COUNT)
    fun switchCamera_checkImageAnalysisInEachTime_withPreviewVideoCaptureImageAnalysis() {
        val useCaseCombination = BIND_PREVIEW or BIND_VIDEO_CAPTURE or BIND_IMAGE_ANALYSIS
        assumeBothLensFacingCamerasSupportUseCaseCombination(camera, useCaseCombination)
        switchCamera_checkOutput_repeatedly(
            cameraId,
            useCaseCombination,
            VERIFICATION_TARGET_IMAGE_ANALYSIS
        )
    }

    /**
     * Repeatedly switch the cameras and checks the use cases' capture functions can work.
     */
    private fun switchCamera_checkOutput_repeatedly(
        cameraId: String,
        useCaseCombination: Int,
        verificationTarget: Int,
        repeatCount: Int = STRESS_TEST_OPERATION_REPEAT_COUNT
    ) {
        // Launches CameraXActivity and wait for the preview ready.
        val activityScenario =
            launchCameraXActivityAndWaitForPreviewReady(cameraId, useCaseCombination)

        // Repeatedly switches camera and checks the test target use case can capture images
        // successfully.
        with(activityScenario) {
            use {
                for (i in 1..repeatCount) {
                    // Switches camera and wait for preview idle.
                    switchCameraAndWaitForViewfinderIdle()

                    // Checks Preview can receive frames if it is the test target use case.
                    if (verificationTarget.and(VERIFICATION_TARGET_PREVIEW) != 0) {
                        waitForViewfinderIdle()
                    }

                    // Checks ImageCapture can take a picture if it is the test target use case.
                    if (verificationTarget.and(VERIFICATION_TARGET_IMAGE_CAPTURE) != 0) {
                        takePictureAndWaitForImageSavedIdle()
                    }

                    // Checks VideoCapture can record a video if it is the test target use case.
                    if (verificationTarget.and(VERIFICATION_TARGET_VIDEO_CAPTURE) != 0) {
                        recordVideoAndWaitForVideoSavedIdle()
                    }

                    // Checks ImageAnalysis can receive frames if it is the test target use case.
                    if (verificationTarget.and(VERIFICATION_TARGET_IMAGE_ANALYSIS) != 0) {
                        waitForImageAnalysisIdle()
                    }
                }
            }
        }
    }

    @LabTestRule.LabTestOnly
    @Test
    @RepeatRule.Repeat(times = STRESS_TEST_REPEAT_COUNT)
    fun checkPreview_afterSwitchCameraRepeatedly_withPreviewImageCapture() {
        val useCaseCombination = BIND_PREVIEW or BIND_IMAGE_CAPTURE
        switchCamera_repeatedly_thenCheckOutput(
            cameraId,
            useCaseCombination,
            VERIFICATION_TARGET_PREVIEW
        )
    }

    @LabTestRule.LabTestOnly
    @Test
    @RepeatRule.Repeat(times = STRESS_TEST_REPEAT_COUNT)
    fun checkImageCapture_afterSwitchCameraRepeatedly_withPreviewImageCapture() {
        val useCaseCombination = BIND_PREVIEW or BIND_IMAGE_CAPTURE
        switchCamera_repeatedly_thenCheckOutput(
            cameraId,
            useCaseCombination,
            VERIFICATION_TARGET_IMAGE_CAPTURE
        )
    }

    @LabTestRule.LabTestOnly
    @Test
    @RepeatRule.Repeat(times = STRESS_TEST_REPEAT_COUNT)
    fun checkPreview_afterSwitchCameraRepeatedly_withPreviewImageCaptureImageAnalysis() {
        val useCaseCombination = BIND_PREVIEW or BIND_IMAGE_CAPTURE or BIND_IMAGE_ANALYSIS
        switchCamera_repeatedly_thenCheckOutput(
            cameraId,
            useCaseCombination,
            VERIFICATION_TARGET_PREVIEW
        )
    }

    @LabTestRule.LabTestOnly
    @Test
    @RepeatRule.Repeat(times = STRESS_TEST_REPEAT_COUNT)
    fun checkImageCapture_afterSwitchCameraRepeatedly_withPreviewImageCaptureImageAnalysis() {
        val useCaseCombination = BIND_PREVIEW or BIND_IMAGE_CAPTURE or BIND_IMAGE_ANALYSIS
        switchCamera_repeatedly_thenCheckOutput(
            cameraId,
            useCaseCombination,
            VERIFICATION_TARGET_IMAGE_CAPTURE
        )
    }

    @LabTestRule.LabTestOnly
    @Test
    @RepeatRule.Repeat(times = STRESS_TEST_REPEAT_COUNT)
    fun checkImageAnalysis_afterSwitchCameraRepeatedly_withPreviewImageCaptureImageAnalysis() {
        val useCaseCombination = BIND_PREVIEW or BIND_IMAGE_CAPTURE or BIND_IMAGE_ANALYSIS
        switchCamera_repeatedly_thenCheckOutput(
            cameraId,
            useCaseCombination,
            VERIFICATION_TARGET_IMAGE_ANALYSIS
        )
    }

    @LabTestRule.LabTestOnly
    @Test
    @RepeatRule.Repeat(times = STRESS_TEST_REPEAT_COUNT)
    fun checkPreview_afterSwitchCameraRepeatedly_withPreviewVideoCapture() {
        val useCaseCombination = BIND_PREVIEW or BIND_VIDEO_CAPTURE
        switchCamera_repeatedly_thenCheckOutput(
            cameraId,
            useCaseCombination,
            VERIFICATION_TARGET_PREVIEW
        )
    }

    @LabTestRule.LabTestOnly
    @Test
    @RepeatRule.Repeat(times = STRESS_TEST_REPEAT_COUNT)
    fun checkVideoCapture_afterSwitchCameraRepeatedly_withPreviewVideoCapture() {
        val useCaseCombination = BIND_PREVIEW or BIND_VIDEO_CAPTURE
        switchCamera_repeatedly_thenCheckOutput(
            cameraId,
            useCaseCombination,
            VERIFICATION_TARGET_VIDEO_CAPTURE
        )
    }

    @LabTestRule.LabTestOnly
    @Test
    @RepeatRule.Repeat(times = STRESS_TEST_REPEAT_COUNT)
    fun checkPreview_afterSwitchCameraRepeatedly_withPreviewVideoCaptureImageCapture() {
        val useCaseCombination = BIND_PREVIEW or BIND_VIDEO_CAPTURE or BIND_IMAGE_CAPTURE
        assumeBothLensFacingCamerasSupportUseCaseCombination(camera, useCaseCombination)
        switchCamera_repeatedly_thenCheckOutput(
            cameraId,
            useCaseCombination,
            VERIFICATION_TARGET_PREVIEW
        )
    }

    @LabTestRule.LabTestOnly
    @Test
    @RepeatRule.Repeat(times = STRESS_TEST_REPEAT_COUNT)
    fun checkVideoCapture_afterSwitchCameraRepeatedly_withPreviewVideoCaptureImageCapture() {
        val useCaseCombination = BIND_PREVIEW or BIND_VIDEO_CAPTURE or BIND_IMAGE_CAPTURE
        assumeBothLensFacingCamerasSupportUseCaseCombination(camera, useCaseCombination)
        switchCamera_repeatedly_thenCheckOutput(
            cameraId,
            useCaseCombination,
            VERIFICATION_TARGET_VIDEO_CAPTURE
        )
    }

    @LabTestRule.LabTestOnly
    @Test
    @RepeatRule.Repeat(times = STRESS_TEST_REPEAT_COUNT)
    fun checkImageCapture_afterSwitchCameraRepeatedly_withPreviewVideoCaptureImageCapture() {
        val useCaseCombination = BIND_PREVIEW or BIND_VIDEO_CAPTURE or BIND_IMAGE_CAPTURE
        assumeBothLensFacingCamerasSupportUseCaseCombination(camera, useCaseCombination)
        switchCamera_repeatedly_thenCheckOutput(
            cameraId,
            useCaseCombination,
            VERIFICATION_TARGET_IMAGE_CAPTURE
        )
    }

    @LabTestRule.LabTestOnly
    @Test
    @RepeatRule.Repeat(times = STRESS_TEST_REPEAT_COUNT)
    fun checkVideoCapture_afterSwitchCameraRepeatedly_withPreviewVideoCaptureImageAnalysis() {
        val useCaseCombination = BIND_PREVIEW or BIND_VIDEO_CAPTURE or BIND_IMAGE_ANALYSIS
        assumeBothLensFacingCamerasSupportUseCaseCombination(camera, useCaseCombination)
        switchCamera_repeatedly_thenCheckOutput(
            cameraId,
            useCaseCombination,
            VERIFICATION_TARGET_VIDEO_CAPTURE
        )
    }

    @LabTestRule.LabTestOnly
    @Test
    @RepeatRule.Repeat(times = STRESS_TEST_REPEAT_COUNT)
    fun checkImageAnalysis_afterSwitchCameraRepeatedly_withPreviewVideoCaptureImageAnalysis() {
        val useCaseCombination = BIND_PREVIEW or BIND_VIDEO_CAPTURE or BIND_IMAGE_ANALYSIS
        assumeBothLensFacingCamerasSupportUseCaseCombination(camera, useCaseCombination)
        switchCamera_repeatedly_thenCheckOutput(
            cameraId,
            useCaseCombination,
            VERIFICATION_TARGET_IMAGE_ANALYSIS
        )
    }

    @LabTestRule.LabTestOnly
    @Test
    @RepeatRule.Repeat(times = STRESS_TEST_REPEAT_COUNT)
    fun checkPreview_afterSwitchCameraRepeatedly_withPreviewVideoCaptureImageAnalysis() {
        val useCaseCombination = BIND_PREVIEW or BIND_VIDEO_CAPTURE or BIND_IMAGE_ANALYSIS
        assumeBothLensFacingCamerasSupportUseCaseCombination(camera, useCaseCombination)
        switchCamera_repeatedly_thenCheckOutput(
            cameraId,
            useCaseCombination,
            VERIFICATION_TARGET_PREVIEW
        )
    }

    /**
     * Switch the cameras repeatedly,and then checks the use cases' capture functions can work.
     */
    private fun switchCamera_repeatedly_thenCheckOutput(
        cameraId: String,
        useCaseCombination: Int,
        verificationTarget: Int,
        repeatCount: Int = STRESS_TEST_OPERATION_REPEAT_COUNT
    ) {
        // Launches CameraXActivity and wait for the preview ready.
        val activityScenario =
            launchCameraXActivityAndWaitForPreviewReady(cameraId, useCaseCombination)

        // Switches camera repeatedly, and then checks the test target use case can capture images
        // successfully.
        with(activityScenario) {
            use {
                for (i in 1..repeatCount) {
                    // Switches camera and wait for preview idle.
                    switchCameraAndWaitForViewfinderIdle()
                }

                // Checks Preview can receive frames if it is the test target use case.
                if (verificationTarget.and(VERIFICATION_TARGET_PREVIEW) != 0) {
                    waitForViewfinderIdle()
                }

                // Checks ImageCapture can take a picture if it is the test target use case.
                if (verificationTarget.and(VERIFICATION_TARGET_IMAGE_CAPTURE) != 0) {
                    takePictureAndWaitForImageSavedIdle()
                }

                // Checks VideoCapture can record a video if it is the test target use case.
                if (verificationTarget.and(VERIFICATION_TARGET_VIDEO_CAPTURE) != 0) {
                    recordVideoAndWaitForVideoSavedIdle()
                }

                // Checks ImageAnalysis can receive frames if it is the test target use case.
                if (verificationTarget.and(VERIFICATION_TARGET_IMAGE_ANALYSIS) != 0) {
                    waitForImageAnalysisIdle()
                }
            }
        }
    }

    private fun assumeBothLensFacingCamerasSupportUseCaseCombination(
        camera: Camera,
        useCaseCombination: Int
    ): Unit = runBlocking {
        // Checks whether the input camera can support the use case combination
        assumeCameraSupportUseCaseCombination(camera, useCaseCombination)

        val camera2CameraInfo = Camera2CameraInfo.from(camera.cameraInfo)
        val lensFacing =
            camera2CameraInfo.getCameraCharacteristic(CameraCharacteristics.LENS_FACING)

        val otherLensFacingCameraSelector =
            if (lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

        val otherLensFacingCamera = withContext(Dispatchers.Main) {
            cameraProvider.bindToLifecycle(FakeLifecycleOwner(), otherLensFacingCameraSelector)
        }

        // Checks whether the camera of the other lens facing can support the use case combination
        assumeCameraSupportUseCaseCombination(otherLensFacingCamera, useCaseCombination)
    }
}