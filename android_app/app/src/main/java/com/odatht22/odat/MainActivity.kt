package com.odatht22.odat

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
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
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatSpinner
import androidx.core.view.isVisible
import dji.common.flightcontroller.virtualstick.FlightControlData
import dji.common.flightcontroller.virtualstick.RollPitchControlMode
import dji.common.flightcontroller.virtualstick.VerticalControlMode
import dji.common.gimbal.*
import dji.common.product.Model
import dji.sdk.base.BaseProduct
import dji.sdk.camera.Camera
import dji.sdk.camera.VideoFeeder
import dji.sdk.codec.DJICodecManager
import dji.sdk.flightcontroller.FlightController
import dji.sdk.products.Aircraft
import dji.sdk.products.HandHeld
import dji.sdk.sdkmanager.DJISDKManager
import dji.ux.widget.ReturnHomeWidget
import dji.ux.widget.TakeOffWidget
import org.tensorflow.lite.task.gms.vision.detector.Detection
import java.io.File
import java.io.File.separator
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.text.DecimalFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.concurrent.schedule
import kotlin.math.*


/*
This activity provides an interface to access a connected DJI Product's camera and use
it to take photos and record videos
*/
class MainActivity : AppCompatActivity(), TextureView.SurfaceTextureListener, View.OnClickListener,
    ObjectDetectorHelper.DetectorListener {

    // Class Variables
    private val TAG = MainActivity::class.java.name

    // listener that is used to receive video data coming from the connected DJI product
    private var receivedVideoDataListener: VideoFeeder.VideoDataListener? = null
    private var codecManager: DJICodecManager? =
        null // handles the encoding and decoding of video data

    // Used to display the DJI product's camera video stream
    private lateinit var videoSurface:
            TextureView
    // UI elements
    private lateinit var takeOffBtn: TakeOffWidget
    private lateinit var landBtn: ReturnHomeWidget
    private lateinit var recordingTime: TextView
    private lateinit var bottomSheetLayout: View
    private lateinit var detectBtn: Button
    private lateinit var distanceToTargetGroup: LinearLayout

    // Object detection variables
    private var detectionResults: MutableList<Detection>? = null
    private var shouldFollow: Boolean = false
    private var shouldDetectObjects: Boolean = false
    private lateinit var objectDetectionHelper: ObjectDetectorHelper
    private var hasLostTarget: Boolean = false
    private var trackedObjectLastLocations: ArrayList<Detection> = arrayListOf()

    // Drone stats
    private var droneGimbalPitchInDegrees: Float = 0f
    private var droneGimbalYawInDegrees: Float = 0f



    // Inference and flight controller threads
    private lateinit var inferenceExecutor: ExecutorService
    private lateinit var flightControllerExecutor: ExecutorService


    // Gimbal parameters
    private var gimbalMaxAnglePerSecond: Float = 10f
    private var followDistance: Float = 7f


    override fun onInitialized() {
        objectDetectionHelper.setupObjectDetector()
        // Initialize our background executors
        inferenceExecutor = Executors.newSingleThreadExecutor()
        flightControllerExecutor = Executors.newSingleThreadExecutor()
        setupListeners()
        // In case the gimbal was not aligned properly on application start, for instance if we restarted the app but left the aircraft on
        resetCameraGimbalYaw()


    }

    // Handle errors from the object detection
    override fun onError(error: String) {

        Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
    }


    // Setup necessary listeners such as updating the gimbal pitch and yaw, also listen for 'emergency stop' button presses
    private fun setupListeners() {

        getAircraftInstance().gimbal.setStateCallback { gimbalState ->
            droneGimbalPitchInDegrees = gimbalState.attitudeInDegrees.pitch
            droneGimbalYawInDegrees = gimbalState.yawRelativeToAircraftHeading

            /* droneGimbalYawInDegrees =
                 gimbalState.attitudeInDegrees.yaw - getAircraftInstance().flightController.compass.heading - compassMissalignment
 */
            //Log.i(TAG, "Gimbal yaw in degrees $droneGimbalYawInDegrees")

        }

        getAircraftInstance().remoteController.setHardwareStateCallback { state ->


            if (shouldFollow && ((state.c1Button != null && state.c1Button!!.isClicked) || (state.c2Button != null && state.c2Button!!.isClicked))) {
                stopFollow()
                showToast("Emergency stop")
            }

            if (state.fiveDButton != null && state.fiveDButton!!.isClicked) {

                resetCameraGimbalYaw()
            }


        }

        /*
       The receivedVideoDataListener receives the raw video data and the size of the data from the DJI product.
       It then sends this data to the codec manager for decoding.
       */
        receivedVideoDataListener =
            VideoFeeder.VideoDataListener { videoBuffer, size ->
                codecManager?.sendDataToDecoder(videoBuffer, size)
            }


    }

    // Function that points the camera straight forward in the yaw axis
    private fun resetCameraGimbalYaw() {
        getAircraftInstance().gimbal.reset(Axis.YAW, ResetDirection.CENTER, null)
    }


    // Creating the Activity, initialize variables
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        this.window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        setContentView(
            R.layout.activity_main
        ) // inflating the activity_main.xml layout as the activity's view

        supportActionBar?.hide() // Hide app name bar
        initUi() // initializing the UI




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


        objectDetectionHelper = ObjectDetectorHelper(context = this, objectDetectorListener = this)


    }


    // Function to initialize the activity's UI elements
    @SuppressLint("ClickableViewAccessibility")
    private fun initUi() {
        // referencing the layout views using their resource ids
        videoSurface = findViewById(R.id.video_previewer_surface)
        recordingTime = findViewById(R.id.timer)
        takeOffBtn = findViewById(R.id.takeOffBtn)
        landBtn = findViewById(R.id.LandBtn)
        bottomSheetLayout = findViewById(R.id.bottom_sheet_layout)
        detectBtn = findViewById(R.id.inference_button)

        distanceToTargetGroup = findViewById(R.id.distance_to_target_group)
        distanceToTargetGroup.isVisible = false
        distanceToTargetGroup.visibility = View.INVISIBLE

        findViewById<OverlayView>(R.id.overlay).setOnClickListener(this)

        if (!isAircraftConnected()) {
            detectBtn.isClickable = false
            detectBtn.isEnabled = false
        }


        /*
        Giving videoSurface a listener that checks for when a surface texture is available.
        The videoSurface will then display the surface texture, which in this case is a camera video stream.
        */
        videoSurface.surfaceTextureListener = this


        // Giving the non-toggle button elements a click listener

        landBtn.setOnClickListener(this)
        takeOffBtn.setOnClickListener(this)
        detectBtn.setOnClickListener(this)

        recordingTime.visibility = View.INVISIBLE

        findViewById<OverlayView>(R.id.overlay).setOnTouchListener { view, motionEvent ->

            // X and Y values are fetched
            val mX = motionEvent.x
            val mY = motionEvent.y
            val overlay: OverlayView = findViewById(R.id.overlay)

            if (detectionResults != null) {
                val scaleFactor = overlay.scaleFactor

                for (detection in detectionResults!!) {
                    val dBox: RectF = detection.boundingBox
                    val onScreenBox = RectF(
                        dBox.left * scaleFactor,
                        dBox.top * scaleFactor,
                        dBox.right * scaleFactor,
                        dBox.bottom * scaleFactor
                    )
                    if (onScreenBox.contains(mX, mY))
                        findViewById<OverlayView>(R.id.overlay).trackedObject = detection
                }

            }


            view.performClick()
            true
        }



        initBottomSheetControls()


    }


    // Helper-function to start object detection
    private fun detectObjects(bitmap: Bitmap) {
        // Pass Bitmap to the object detector helper for processing and detection
        objectDetectionHelper.detect(bitmap)
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


    // Initialize components tied to the bottom controls sheetw
    private fun initBottomSheetControls() {
        // When clicked, increase the number of objects that can be detected at a time

        findViewById<AppCompatImageButton>(R.id.max_results_plus).setOnClickListener {
            objectDetectionHelper.maxResults++
            findViewById<TextView>(R.id.max_results_value).text =
                objectDetectionHelper.maxResults.toString()
        }

        findViewById<AppCompatImageButton>(R.id.max_results_minus).setOnClickListener {
            if (objectDetectionHelper.maxResults - 1 > 0) {
                objectDetectionHelper.maxResults--
                findViewById<TextView>(R.id.max_results_value).text =
                    objectDetectionHelper.maxResults.toString()
            }

        }
        val df = DecimalFormat("#.#")


        findViewById<AppCompatImageButton>(R.id.gimbal_move_time_minus).setOnClickListener {
            if (followDistance > 2) followDistance -= 1
            findViewById<TextView>(R.id.gimbal_move_speed_value).text = df.format(followDistance)
        }

        findViewById<AppCompatImageButton>(R.id.gimbal_move_time_plus).setOnClickListener {
            followDistance += 1
            findViewById<TextView>(R.id.gimbal_move_speed_value).text = df.format(followDistance)
        }


        findViewById<AppCompatImageButton>(R.id.max_angle_minus).setOnClickListener {
            if (gimbalMaxAnglePerSecond > 1) gimbalMaxAnglePerSecond -= 0.5f
            findViewById<TextView>(R.id.max_angle_value).text = gimbalMaxAnglePerSecond.toString()
        }

        findViewById<AppCompatImageButton>(R.id.max_angle_plus).setOnClickListener {
            if (gimbalMaxAnglePerSecond < 6) gimbalMaxAnglePerSecond += 0.5f
            findViewById<TextView>(R.id.max_angle_value).text = gimbalMaxAnglePerSecond.toString()
        }








        findViewById<AppCompatSpinner>(R.id.spinner_model).onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                    if (!objectDetectionHelper.isInitialized()) {
                        return
                    }
                    objectDetectionHelper.currentModel = p2
                    objectDetectionHelper.clearObjectDetector()
                    var modelName: String = objectDetectionHelper.setupObjectDetector()
                    showToast("Changed to $modelName model")

                }

                override fun onNothingSelected(p0: AdapterView<*>?) {
                    /*Do nothing*/
                }

            }

        findViewById<TextView>(R.id.max_angle_value).text = gimbalMaxAnglePerSecond.toString()
        findViewById<TextView>(R.id.gimbal_move_speed_value).text = followDistance.toString()


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


    /*Rotate the drones camera gimbal in yaw and pitch */
    private fun rotateGimbal(yaw: Float, pitch: Float, time: Double) {
        val builder = Rotation.Builder().mode(RotationMode.RELATIVE_ANGLE).time(time)
        builder.yaw(yaw)
        builder.pitch(pitch)
        getProductInstance()?.gimbal?.rotate(builder.build()) { x ->
            if (x != null) Log.e("rotateGimbal error", x.toString())
        }
    }


    private fun calculateDistanceToDetection(target: Detection): Float {

        val overlay: OverlayView = findViewById(R.id.overlay)

        // Calculate distance to target
        val droneHeight: Float =
            (getProductInstance() as Aircraft).flightController.state.ultrasonicHeightInMeters
        val gimbalPitch: Float = droneGimbalPitchInDegrees


/*
        // Pixel distance x
        val centerX: Int = overlay.width / 2
        val targetCenterX: Float = target.boundingBox.centerY() * overlay.scaleFactor
        val pixelDistanceX: Float = targetCenterX - centerX
*/

        // Pixel distance y
        val centerY = overlay.height / 2
        val targetCenterY = target.boundingBox.bottom * overlay.scaleFactor
        val pixelDistanceY = (centerY - targetCenterY)


        // Assumption: Target object height is 0m
        //var objectHeight = 0f
        // Approximation of angle towards target relative to the gimbals orientation, the DJI camera fov is 77º horizontally and assumed 40º vertical
        // Calculate horizontal angle towards object
        // var horizontalAngle: Float = ( 77f / overlay.width)*pixelDistanceX
        val verticalAngle: Float = (40f / overlay.height) * pixelDistanceY

        val trueVerticalAngle = (verticalAngle + gimbalPitch).absoluteValue.toDouble() + 3 //

        val distanceToTarget: Float = droneHeight / tan(Math.toRadians(trueVerticalAngle)).toFloat()

        return distanceToTarget

    }


    /* Starts a new thread responsible for sending control signals to the drone in an effort to keep up with the target being tracked*/
    private fun startFollowingObject() {
        flightControllerExecutor.execute {

            val rotationSpeed: Float = gimbalMaxAnglePerSecond


            (getProductInstance() as Aircraft).flightController.rollPitchControlMode =
                RollPitchControlMode.VELOCITY;
            (getProductInstance() as Aircraft).flightController.verticalControlMode =
                VerticalControlMode.VELOCITY;

            var cycleTime: Long? = null
            while (true) {

                val start: Long = System.currentTimeMillis()
                if (cycleTime == null){
                    cycleTime = 50
                }



                val droneHeading = getAircraftInstance().flightController.compass.heading


                if (!shouldFollow) {


                    getAircraftInstance().flightController.sendVirtualStickFlightControlData(
                        FlightControlData(0f, 0f, droneHeading, 0f), null
                    )
                    break
                }

                if (hasLostTarget) {
                    getAircraftInstance().flightController.sendVirtualStickFlightControlData(
                        FlightControlData(0f, 0f, droneHeading, 0f), null
                    )
                    Thread.sleep(40)
                    continue
                }


                val overlay: OverlayView = findViewById(R.id.overlay)








                if (shouldFollow && overlay.trackedObject != null) {

                    val target: Detection = overlay.trackedObject!!

                    val flightController: FlightController =
                        (getProductInstance() as Aircraft).flightController

                    val centerX: Int = overlay.width / 2
                    val targetCenterX: Float = target.boundingBox.centerX() * overlay.scaleFactor
                    val pixelDistanceX: Float = (targetCenterX - centerX)
                    // val degreesPerPixel: Float = 77f /  overlay.width.toFloat()
                    var rotationX: Float = (2f / ( 1f + exp(-pixelDistanceX/200.0)) - 1).toFloat() * rotationSpeed
                    Log.i(TAG, "Rotation angle before time scale $rotationX")
                    rotationX /= (100f / cycleTime.toFloat()) * 3
                    Log.i(TAG, "Pixel distance X $pixelDistanceX")
                    Log.i(TAG, "Rotation step X $rotationX")


                    val centerY = overlay.height / 2
                    val targetCenterY = target.boundingBox.centerY() * overlay.scaleFactor
                    val pixelDistanceY = (targetCenterY - centerY)
                    var rotationY: Float = (2f / ( 1f + exp(-pixelDistanceX/200.0)) - 1).toFloat() * rotationSpeed / 3
                    rotationY /= (100f / cycleTime.toFloat()) * 3

                    Log.i(TAG, "Rotation speed ${cycleTime / 1000}")
                    rotateGimbal(rotationX, rotationY, cycleTime.toDouble() / 1000)

                    val distanceToTarget: Float = calculateDistanceToDetection(target)
                    val df: DecimalFormat = DecimalFormat("#.#")
                    //Log.i(TAG, "Distance to target is: $distanceToTarget")
                    """${df.format(distanceToTarget)}m""".also {
                        findViewById<TextView>(R.id.dtt_value).text = it
                    }


                    /*val maxAcceptableGimbalYawRotation: Float = 5f*/
                    val gimbalCompensationRotationSpeedMax: Float = .2f
                    var gimbalCompensationRotationSpeed = (pixelDistanceX / 500).absoluteValue
                    if (gimbalCompensationRotationSpeed > gimbalCompensationRotationSpeedMax) {
                        gimbalCompensationRotationSpeed = gimbalCompensationRotationSpeedMax
                    }




                    if ((distanceToTarget - followDistance).absoluteValue > 0) {
                        val droneMaxSpeed: Float = 5f // m/s
                        var diff = distanceToTarget - followDistance
                        if (diff == 0f) {
                            diff = 0.01f
                        }

                        val droneSpeed: Float = (2f / (1f + exp(-(diff.absoluteValue*1.3 - 3))) - .06).toFloat() / 2f * droneMaxSpeed*diff.sign


                        var heading = droneHeading


                        heading += (2 / (1 + exp(-(droneGimbalYawInDegrees.absoluteValue - 7)/2)) - .06f) * 2 * droneGimbalYawInDegrees.sign




                        val droneSpeedNorth =
                            droneSpeed * sin(Math.toRadians(droneHeading.toDouble())).toFloat()
                        val droneSpeedEast =
                            droneSpeed * cos(Math.toRadians(droneHeading.toDouble())).toFloat()



                        // FlightControlData(North, East, droneHeading, verticalSpeed)
                        (getProductInstance() as Aircraft).flightController.sendVirtualStickFlightControlData(
                            FlightControlData(droneSpeedNorth, droneSpeedEast, heading, 0f),
                            null
                        )
                    } else {
                        var heading = droneHeading

                        Log.i(TAG, "Drone body angle correction ${(2 / (1 + exp(-droneGimbalYawInDegrees)) - .38f) * 6}")

                        //if (droneGimbalYawInDegrees.absoluteValue > maxAcceptableGimbalYawRotation) {
                            heading += (2 / (1 + exp(-(droneGimbalYawInDegrees.absoluteValue - 7)/2)) - .06f) * 2 * droneGimbalYawInDegrees.sign
                            //rotateGimbal(-droneGimbalYawInDegrees * gimbalCompensationRotationSpeed, 0f, .05)
                       // }


                        getAircraftInstance().flightController.sendVirtualStickFlightControlData(
                            FlightControlData(0f, 0f, heading, 0f), null
                        )
                    }


                }

                Thread.sleep(40)
                cycleTime = System.currentTimeMillis() - start
                Log.i(TAG, "Cycle time: $cycleTime")

            }


        }
    }


    /*Starts a new thread responsible for running inference in the image data retrieved from the drones video feed*/
    private fun startObjectDetection() {

        inferenceExecutor.execute {
            while (true) {

                if (!shouldDetectObjects) {

                    val overlay: OverlayView = findViewById(R.id.overlay)

                    overlay.clearResults()
                    overlay.clear()
                    overlay.invalidate()
                    break
                }


                getImageData()?.let {

                    detectObjects(
                        it
                    )

                    it.recycle()

                }
            }


        }
    }


    /*
        Overridden inherited method called every time the object detector helper has a new result
        If we have set a tracked object the algorithm will go though all detections and check if



    Also invalidates the old overlay to force a redraw with the updated object predictions

     */
    override fun onResults(
        results: MutableList<Detection>?,
        inferenceTime: Long,
        imageHeight: Int,
        imageWidth: Int
    ) {

        //detectionResults?:return
        detectionResults = results


        val overlay: OverlayView = findViewById(R.id.overlay)
        if (overlay.trackedObject != null) {

            var closesDistance: Double = 10.0.pow(10)
            var optimalCandidate: Detection? = null

            for (detection in detectionResults!!) {

                val pixelDistanceToTarget: Double = sqrt(
                    ((detection.boundingBox.centerX() - overlay.trackedObject!!.boundingBox.centerX()).toDouble()
                        .pow(2.0)) + ((detection.boundingBox.centerY() - overlay.trackedObject!!.boundingBox.centerY()).toDouble()
                        .pow(2.0))
                )
                if (pixelDistanceToTarget < closesDistance && detection.categories[0].label == overlay.trackedObject!!.categories[0].label) {
                    closesDistance = pixelDistanceToTarget
                    optimalCandidate = detection
                }

            }

            if (optimalCandidate == null || closesDistance > 200) {

                // Do nothing
                hasLostTarget = true

            } else {
                overlay.trackedObject = optimalCandidate

                hasLostTarget = false

                // In case we want to use a rolling average
                trackedObjectLastLocations.add(optimalCandidate)
                if (trackedObjectLastLocations.size > 5) {
                    trackedObjectLastLocations.removeFirst()
                }
            }


        }

        // Instruct the UI thread to re-render
        runOnUiThread {


            val textView: TextView = findViewById<TextView>(R.id.inference_time_val)
            textView.text = String.format("%d ms", inferenceTime)

            // Pass necessary information to OverlayView for drawing on the canvas
            //Log.i(TAG, "results: $results")
            // n      m, results?.removeIf { detection -> detection.categories[0].label != "person" }

            overlay.setResults(
                results ?: LinkedList<Detection>(),
                imageHeight,
                imageWidth
            )


            // Force a redraw
            overlay.invalidate()
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
            R.id.inference_button -> {
                shouldDetectObjects = !shouldDetectObjects
                when (shouldDetectObjects) {

                    true -> {

                        //startFollow()
                        Log.i(TAG, "Starting inference process")
                        detectBtn.text = "Stop"
                        (getProductInstance() as Aircraft).flightController.setVirtualStickModeEnabled(
                            true,
                            null
                        )

                        shouldFollow = true
                        startObjectDetection()
                        startFollowingObject()
                        distanceToTargetGroup.isVisible = true
                        /*distanceToTargetGroup.visibility = View.VISIBLE*/

                    }
                    false -> {
                        stopFollow()
                    }

                }

            }

        }
    }

    private fun startFollow() {

        Log.i(TAG, "Starting inference process")
        detectBtn.text = "Stop"
        (getProductInstance() as Aircraft).flightController.setVirtualStickModeEnabled(true, null)

        shouldFollow = true

        startObjectDetection()
        startFollowingObject()
        distanceToTargetGroup.isVisible = true
        //distanceToTargetGroup.visibility = View.VISIBLE
    }

    private fun stopFollow() {
        (getProductInstance() as Aircraft).flightController.setVirtualStickModeEnabled(false, null)

        getAircraftInstance().gimbal.reset(Axis.YAW, ResetDirection.CENTER, null)


        Log.i(TAG, "Stopping inference process")
        detectBtn.text = "Detect"
        val overlay: OverlayView = findViewById(R.id.overlay)
        overlay.trackedObject = null
        shouldFollow = false
        shouldDetectObjects = false
        Timer().schedule(100) {
            overlay.clearResults()
            overlay.clear()
            overlay.invalidate()
            //distanceToTargetGroup.isVisible = false
            //distanceToTargetGroup.visibility = View.INVISIBLE
        }
    }


    // Function used to get the DJI product that is directly connected to the mobile device
    private fun getProductInstance(): BaseProduct? {
        return DJISDKManager.getInstance().product
    }

    private fun getAircraftInstance(): Aircraft {
        return getProductInstance() as Aircraft
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

    private fun getImageData(): Bitmap? {


        val videoWidth: Int? = codecManager?.videoWidth
        val videoHeight: Int? = codecManager?.videoHeight
        if (videoWidth == null || videoHeight == null) {
            return null
        }

        val imageData: ByteArray = codecManager?.getRgbaData(videoWidth, videoHeight) ?: return null

        val bmp: Bitmap = Bitmap.createBitmap(videoWidth, videoHeight, Bitmap.Config.ARGB_8888)
        val buffer: ByteBuffer = ByteBuffer.wrap(imageData)
        bmp.copyPixelsFromBuffer(buffer)

        return bmp
        //saveImage(bmp, this@MainActivity, "ODaT")


        // Code to get RGB values from a bitmap
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


    // Saves a given bitmap to the device's local storage (images) under the folderName folder
    private fun saveImage(bitmap: Bitmap, context: Context, folderName: String) {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val values = contentValues()
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/" + folderName)
            values.put(MediaStore.Images.Media.IS_PENDING, true)
            // RELATIVE_PATH and IS_PENDING are introduced in API 29.

            val uri: Uri? =
                context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                saveImageToStream(bitmap, context.contentResolver.openOutputStream(uri))
                values.put(MediaStore.Images.Media.IS_PENDING, false)
                context.contentResolver.update(uri, values, null, null)
            }
        } else {
            val directory =
                File(Environment.getExternalStorageDirectory().toString() + separator + folderName)
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

    private fun contentValues(): ContentValues {
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

