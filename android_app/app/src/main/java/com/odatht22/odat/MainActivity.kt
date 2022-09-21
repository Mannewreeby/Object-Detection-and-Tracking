package com.odatht22.odat

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.TextureView
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dji.common.camera.SettingsDefinitions.CameraMode
import dji.common.camera.SettingsDefinitions.ShootPhotoMode
import dji.common.product.Model
import dji.sdk.base.BaseProduct
import dji.sdk.camera.Camera
import dji.sdk.camera.VideoFeeder
import dji.sdk.codec.DJICodecManager
import dji.sdk.products.Aircraft
import dji.sdk.products.HandHeld
import dji.sdk.sdkmanager.DJISDKManager
import java.nio.ByteBuffer
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.Externalizable
import java.io.File
import java.io.File.separator
import java.io.FileOutputStream
import java.io.OutputStream
import java.lang.Exception

/*
This activity provides an interface to access a connected DJI Product's camera and use
it to take photos and record videos
*/
class MainActivity : AppCompatActivity(), TextureView.SurfaceTextureListener, View.OnClickListener {

    // Class Variables
    private val TAG = MainActivity::class.java.name

    // listener that is used to receive video data coming from the connected DJI product
    private var receivedVideoDataListener: VideoFeeder.VideoDataListener? = null
    private var codecManager: DJICodecManager? =
            null // handles the encoding and decoding of video data

    private lateinit var videoSurface:
            TextureView // Used to display the DJI product's camera video stream
    private lateinit var captureBtn: Button
    private lateinit var shootPhotoModeBtn: Button
    private lateinit var recordVideoModeBtn: Button
    private lateinit var takeOffBtn: Button
    private lateinit var landBtn: Button
    private lateinit var recordBtn: ToggleButton
    private lateinit var recordingTime: TextView

    // Creating the Activity
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(
                R.layout.activity_main
        ) // inflating the activity_main.xml layout as the activity's view

        supportActionBar?.hide() // Hide app name bar
        initUi() // initializing the UI

        /*
        The receivedVideoDataListener receives the raw video data and the size of the data from the DJI product.
        It then sends this data to the codec manager for decoding.
        */
        receivedVideoDataListener =
                VideoFeeder.VideoDataListener { videoBuffer, size ->
                    codecManager?.sendDataToDecoder(videoBuffer, size)
                }

        /*
        NOTES:
        - getCameraInstance() is used to get an instance of the camera running on the DJI product as
          a Camera class object.

         - SystemState is a subclass of Camera that provides general information and current status of the camera.

         - Whenever a change in the camera's SystemState occurs, SystemState.Callback is an interface that
           asynchronously updates the camera's SystemState.

         - setSystemStateCallback is a method of the Camera class which allows us to define what else happens during
           the systemState callback. In this case, we update the UI on the UI thread whenever the SystemState shows
           that the camera is video recording.
        */

        getCameraInstance()?.let { camera ->
            camera.setSystemStateCallback {
                it.let { systemState ->
                    // Getting elapsed video recording time in minutes and seconds, then converting
                    // into a time string
                    val recordTime = systemState.currentVideoRecordingTimeInSeconds
                    val minutes = (recordTime % 3600) / 60
                    val seconds = recordTime % 60
                    val timeString = String.format("%02d:%02d", minutes, seconds)

                    // Accessing the UI thread to update the activity's UI
                    runOnUiThread {
                        // If the camera is video recording, display the time string on the
                        // recordingTime TextView
                        recordingTime.text = timeString
                        if (systemState.isRecording) {
                            recordingTime.visibility = View.VISIBLE
                        } else {
                            recordingTime.visibility = View.INVISIBLE
                        }
                    }
                }
            }
        }
    }

    // Function to initialize the activity's UI elements
    private fun initUi() {
        // referencing the layout views using their resource ids
        videoSurface = findViewById(R.id.video_previewer_surface)
        recordingTime = findViewById(R.id.timer)
        captureBtn = findViewById(R.id.btn_capture)
        recordBtn = findViewById(R.id.btn_record)
        takeOffBtn = findViewById(R.id.takeOffBtn)
        landBtn = findViewById(R.id.LandBtn)
        shootPhotoModeBtn = findViewById(R.id.btn_shoot_photo_mode)
        recordVideoModeBtn = findViewById(R.id.btn_record_video_mode)

        /*
        Giving videoSurface a listener that checks for when a surface texture is available.
        The videoSurface will then display the surface texture, which in this case is a camera video stream.
        */
        videoSurface.surfaceTextureListener = this

        // Giving the non-toggle button elements a click listener
        captureBtn.setOnClickListener(this)
        shootPhotoModeBtn.setOnClickListener(this)
        recordVideoModeBtn.setOnClickListener(this)
        landBtn.setOnClickListener(this)
        takeOffBtn.setOnClickListener(this)

        recordingTime.visibility = View.INVISIBLE

        /*
        recordBtn is a ToggleButton that when checked, the DJI product's camera starts video recording.
        When unchecked, the camera stops video recording.
        */
        recordBtn.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                startRecord()
            } else {
                stopRecord()
            }
        }
    }

    // Function to make the DJI drone's camera start video recording
    private fun startRecord() {
        val camera =
                getCameraInstance() ?: return // get camera instance or null if it doesn't exist

        /*
        starts the camera video recording and receives a callback. If the callback returns an error that
        is null, the operation is successful.
        */
        camera.startRecordVideo {
            if (it == null) {
                showToast("Record Video: Success")
            } else {
                showToast("Record Video Error: ${it.description}")
            }
        }
    }

    // Function to make the DJI product's camera stop video recording
    private fun stopRecord() {
        val camera =
                getCameraInstance() ?: return // get camera instance or null if it doesn't exist

        /*
        stops the camera video recording and receives a callback. If the callback returns an error that
        is null, the operation is successful.
        */
        camera.stopRecordVideo {
            if (it == null) {
                showToast("Stop Recording: Success")
            } else {
                showToast("Stop Recording: Error ${it.description}")
            }
        }
    }

    // Function that initializes the display for the videoSurface TextureView
    private fun initPreviewer() {

        // gets an instance of the connected DJI product (null if nonexistent)
        val product: BaseProduct = getProductInstance() ?: return

        // if DJI product is disconnected, alert the user
        if (!product.isConnected) {
            showToast(getString(R.string.disconnected))
        } else {
            /*
            if the DJI product is connected and the aircraft model is not unknown, add the
            receivedVideoDataListener to the primary video feed.
            */
            videoSurface.surfaceTextureListener = this
            if (product.model != Model.UNKNOWN_AIRCRAFT) {
                receivedVideoDataListener?.let {
                    VideoFeeder.getInstance().primaryVideoFeed.addVideoDataListener(it)
                }
            }
        }
    }

    // Function that uninitializes the display for the videoSurface TextureView
    private fun uninitPreviewer() {
        val camera: Camera = getCameraInstance() ?: return
    }

    // Function that displays toast messages to the user
    private fun showToast(msg: String?) {
        runOnUiThread { Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show() }
    }

    // When the MainActivity is created or resumed, initialize the video feed display
    override fun onResume() {
        super.onResume()
        initPreviewer()
    }

    // When the MainActivity is paused, clear the video feed display
    override fun onPause() {
        uninitPreviewer()
        super.onPause()
    }

    // When the MainActivity is destroyed, clear the video feed display
    override fun onDestroy() {
        uninitPreviewer()
        super.onDestroy()
    }

    // When a TextureView's SurfaceTexture is ready for use, use it to initialize the codecManager
    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        if (codecManager == null) {
            codecManager = DJICodecManager(this, surface, width, height)
        }
    }

    // when a SurfaceTexture's size changes...
    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

    // when a SurfaceTexture is about to be destroyed, uninitialize the codedManager
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        codecManager?.cleanSurface()
        codecManager = null
        return false
    }

    // When a SurfaceTexture is updated...
    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

    // Handling what happens when certain layout views are clicked
    override fun onClick(v: View?) {
        when (v?.id) {
            // If the capture button is pressed, take a photo with the DJI product's camera
            R.id.btn_capture -> {
                // captureAction()
                runBlocking {

                    getImageData()
                }
            }
            // If the shoot photo mode button is pressed, set camera to only take photos
            R.id.btn_shoot_photo_mode -> {
                switchCameraMode(CameraMode.SHOOT_PHOTO)
            }
            // If the record video mode button is pressed, set camera to only record videos
            R.id.btn_record_video_mode -> {
                switchCameraMode(CameraMode.RECORD_VIDEO)
            }
            // R.id.takeOffBtn -> {
            //     (getProductInstance() as Aircraft).flightController?.let { flightController ->
            //         flightController.startTakeoff { err ->
            //             if (err != null) {
            //                 Log.i(TAG, "${err.description}")
            //                 showToast("Error taking off")
            //             } else {
            //                 showToast("Taking off")

            //             }
            //         }

            //     }
            // }
            R.id.LandBtn -> {
                (getProductInstance() as Aircraft).flightController?.let { flightController ->
                    flightController.startLanding { err ->
                        if (err != null) {
                            Log.i(TAG, "Error starting landing ${err.description}")
                            showToast("Error starting landing")
                        } else {
                            showToast("Landing started")
                        }
                    }
                }
            }
            else -> {}
        }
    }

    // Function for taking a a single photo using the DJI Product's camera
    private fun captureAction() {
        val camera: Camera = getCameraInstance() ?: return

        /*
        Setting the camera capture mode to SINGLE, and then taking a photo using the camera.
        If the resulting callback for each operation returns an error that is null, then the
        two operations are successful.
        */
        val photoMode = ShootPhotoMode.SINGLE
        camera.setShootPhotoMode(photoMode) { djiError ->
            if (djiError == null) {
                lifecycleScope.launch {
                    camera.startShootPhoto { djiErrorSecond ->
                        if (djiErrorSecond == null) {
                            showToast("take photo: success")
                        } else {
                            showToast("Take Photo Failure: ${djiError?.description}")
                        }
                    }
                }
            }
        }
    }

    /*
    Function for setting the camera mode. If the resulting callback returns an error that
    is null, then the operation was successful.
    */
    private fun switchCameraMode(cameraMode: CameraMode) {
        val camera: Camera = getCameraInstance() ?: return

        camera.setMode(cameraMode) { error ->
            if (error == null) {
                showToast("Switch Camera Mode Succeeded")
            } else {
                showToast("Switch Camera Error: ${error.description}")
            }
        }
    }

    /*
    Note:
    Depending on the DJI product, the mobile device is either connected directly to the drone,
    or it is connected to a remote controller (RC) which is then used to control the drone.
    */

    // Function used to get the DJI product that is directly connected to the mobile device
    private fun getProductInstance(): BaseProduct? {
        return DJISDKManager.getInstance().product
    }

    /*
    Function used to get an instance of the camera in use from the DJI product
    */
    private fun getCameraInstance(): Camera? {
        if (getProductInstance() == null) return null

        return when {
            getProductInstance() is Aircraft -> {
                (getProductInstance() as Aircraft).camera
            }
            getProductInstance() is HandHeld -> {
                (getProductInstance() as HandHeld).camera
            }
            else -> null
        }
    }

    // Function that returns True if a DJI aircraft is connected
    private fun isAircraftConnected(): Boolean {
        return getProductInstance() != null && getProductInstance() is Aircraft
    }

    // Function that returns True if a DJI product is connected
    private fun isProductModuleAvailable(): Boolean {
        return (getProductInstance() != null)
    }

    // Function that returns True if a DJI product's camera is available
    private fun isCameraModuleAvailable(): Boolean {
        return isProductModuleAvailable() && (getProductInstance()?.camera != null)
    }

    // Function that returns True if a DJI camera's playback feature is available
    private fun isPlaybackAvailable(): Boolean {
        return isCameraModuleAvailable() && (getProductInstance()?.camera?.playbackManager != null)
    }

    private suspend fun getImageData() {


        runBlocking {

            val videoWidth: Int? = codecManager?.videoWidth
            val videoHeight: Int? = codecManager?.videoHeight
            Log.i(TAG, "Video size: $videoWidth x $videoHeight")
            if (videoWidth == null || videoHeight == null) {
                return@runBlocking
            }
            val imageData: ByteArray = codecManager?.getRgbaData(videoWidth, videoHeight) ?: return@runBlocking
            val bmp: Bitmap = Bitmap.createBitmap(videoWidth, videoHeight, Bitmap.Config.ARGB_8888)
            val buffer: ByteBuffer = ByteBuffer.wrap(imageData)
            bmp.copyPixelsFromBuffer(buffer)
            val size: Int = 10

            saveImage(bmp, this@MainActivity, "ODaT")
            /**
             * launch {
            // val pixels = IntArray(bmp.width * bmp.height)
            val pixels = IntArray(size * size)
            val pix = IntArray(videoWidth * videoHeight)
            bmp.getPixels(pix, 0, videoWidth, 0, 0, videoWidth, videoHeight)



            val imagePixelData = Matrix3D(videoWidth, videoHeight, 3)



            var R: Int
            var G: Int
            var B: Int
            var Y: Int

            for (y in 0 until videoHeight) {
            for (x in 0 until videoWidth) {
            val index: Int = y * videoWidth + x
            R = pix[index] shr 16 and 0xff // bitwise shifting
            G = pix[index] shr 8 and 0xff
            B = pix[index] and 0xff
            Log.i(TAG, "RGB: $R, $G, $B")
            imagePixelData[x, y, 0] = R
            imagePixelData[x, y, 1] = G
            imagePixelData[x, y, 2] = B

            // R,G.B - Red, Green, Blue
            // to restore the values after RGB modification, use
            // next statement
            pix[index] = -0x1000000 or (R shl 16) or (G shl 8) or B
            }
            }


            }
             */


        }


    }


    private fun saveImage(bitmap: Bitmap, context: Context, folderName: String) {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val values = contentValues()
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/" + folderName)
            values.put(MediaStore.Images.Media.IS_PENDING, true)
            // RELATIVE_PATH and IS_PENDING are introduced in API 29.

            val uri: Uri? = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                saveImageToStream(bitmap, context.contentResolver.openOutputStream(uri))
                values.put(MediaStore.Images.Media.IS_PENDING, false)
                context.contentResolver.update(uri, values, null, null)
            }
        } else {
            val directory = File(Environment.getExternalStorageDirectory().toString() + separator + folderName)
            //val directory = File(Environment.getStorageDirectory().toString() + separator + folderName)
            // getExternalStorageDirectory is deprecated in API 29

            if (!directory.exists()) {
                directory.mkdirs()
            }
            val fileName = System.currentTimeMillis().toString() + ".png"
            val file = File(directory, fileName)
            saveImageToStream(bitmap, FileOutputStream(file))
            if (file.absolutePath != null) {
                val values = contentValues()
                values.put(MediaStore.Images.Media.DATA, file.absolutePath)
                // .DATA is deprecated in API 29
                context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            }
        }
    }

    private fun contentValues() : ContentValues {
        val values = ContentValues()
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        values.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000);
        values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
        return values
    }

    private fun saveImageToStream(bitmap: Bitmap, outputStream: OutputStream?) {
        if (outputStream != null) {
            try {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                outputStream.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

