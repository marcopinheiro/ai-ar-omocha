package pt.edp.dlai.demo

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.os.Bundle
import android.os.HandlerThread
import android.util.Log
import android.util.Rational
import android.util.Size
import android.view.*
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import pt.edp.dlai.demo.common.*
import pt.edp.dlai.demo.ml.MLProcessor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MLActivity : AppCompatActivity() {
    private val TAG = "MLActivity"

    private val REQUEST_CODE_PERMISSIONS = 10
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.INTERNET)

    private var globals: Globals? = null

    private lateinit var cameraProviderFuture : ListenableFuture<ProcessCameraProvider>
    private lateinit var cameraPreviewView: PreviewView
    private lateinit var overlay: GraphicOverlay

    private var facingCameraX = Constants.FACING_CAMERAX
    private var objectDetectAwaitSecond = 200L
    private val imageLabelerAwaidSecond = Constants.IMAGE_LABELER_AWAIT_MILLISECOND

    private lateinit var mlTargetSpinner: Spinner
    private lateinit var mlClassifierSpinner: Spinner
    private lateinit var mlClassifier: String
    private var mlTarget = "Full Screen"

    private lateinit var drawView: DrawView

    private lateinit var activeModelName: String

    private lateinit var mlProcessor: MLProcessor

    private var mlThread = HandlerThread("MLThread")
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            if (!allPermissionsGranted()) {
                ActivityCompat.requestPermissions(
                    this,
                    REQUIRED_PERMISSIONS,
                    REQUEST_CODE_PERMISSIONS
                )
                return
            }
            mlClassifier = intent.getStringExtra("algorithm")!!
            updateActiveModelName()

            setContentView(R.layout.activity_ml)
            initializeGlobals()
            objectDetectAwaitSecond = globals!!.objectDetector!!.awaitMilliSecond
            mlProcessor = MLProcessor(globals!!)

            cameraPreviewView = findViewById<PreviewView>(R.id.cameraPreviewView)
            overlay = findViewById(R.id.overlay)
            drawView = findViewById(R.id.camera_drawview)
            drawView.isFocusable = false

            //configureSpinner()

            cameraExecutor = Executors.newSingleThreadExecutor()
            cameraPreviewView.post { startCameraX() }
            cameraPreviewView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                updateTransform()
            }
            setSupportActionBar(findViewById(R.id.my_toolbar))
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
        }
        catch (ex: Exception){
            Utils.logAndToast(this,
                TAG,
                "Failed to start CameraX",
                "e",
                Toast.LENGTH_SHORT,
                Gravity.TOP,
                ex)
        }
    }

    private fun initializeGlobals(){
        if (globals == null){
            globals = application as Globals
            globals!!.initialize(this)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.vision, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_fullscreen -> {
                mlTarget = Constants.MLTARGET_FULL_SCREEN
                drawView.makeInvisible()
            }
            R.id.menu_object_detection -> {
                mlTarget = Constants.MLTARGET_OBJECT_DETECTION
                drawView.makeInvisible()
            }
            R.id.menu_draw_rectangle -> {
                mlTarget = Constants.MLTARGET_Rectangle
                drawView.makeVisible()
            }
            else -> {
                super.onOptionsItemSelected(item)
                return false
            }
        }
        Log.i(TAG, "Selected mlTarget ${mlTarget}")
        cameraPreviewView.post { startCameraX() }
        return true
    }

    private fun updateActiveModelName(){
        activeModelName = when(mlClassifier){
            "Quant ImageNet" -> Constants.IMAGE_CLASSIFIER.MOBILENETV2_IMAGE_CLASSIFIER.modelName
            "Float ImageNet" -> Constants.IMAGE_CLASSIFIER.MNASNET_IMAGE_CLASSIFIER.modelName
            "Image Labeler" -> "Image Labeler"
            else -> "Image Labeler"
        }
    }

    /*private fun configureSpinner(){
        mlTargetSpinner = findViewById(R.id.mlTarget)
        val mlTargetAdapter = ArrayAdapter(
            applicationContext,
            android.R.layout.simple_spinner_item,
            Constants.MLTARGET_ARRAY
        )
        mlTargetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        mlTargetSpinner.adapter = mlTargetAdapter
        mlTarget = Constants.MLTARGET_FULL_SCREEN
        mlTargetSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener{
            override fun onItemSelected(parent: AdapterView<*>?,
                                        view: View?,
                                        position: Int,
                                        id: Long) {
                val spinnerParent = parent as Spinner
                mlTarget = spinnerParent.selectedItem as String
                when(mlTarget){
                    Constants.MLTARGET_Rectangle ->{
                        drawView.makeVisible()
                    }
                    else -> {
                        drawView.makeInvisible()
                    }
                }
                Log.i(TAG, "Selected mlTarget ${mlTarget}")
                cameraPreviewView.post { startCameraX() }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
                mlTarget = Constants.MLTARGET_FULL_SCREEN
            }
        }

        /*mlClassifierSpinner = findViewById(R.id.mlClassifier)
        val mlClassifierAdapter = ArrayAdapter(
            applicationContext,
            android.R.layout.simple_spinner_item,
            Constants.MLCLASSIFIER_ARRAY
        )
        mlClassifierAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        mlClassifierSpinner.adapter = mlClassifierAdapter
        mlClassifier = Constants.MLCLASSIFIER_QUANT_IMAGENET
        mlClassifierSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener{
            override fun onItemSelected(parent: AdapterView<*>?,
                                        view: View?,
                                        position: Int,
                                        id: Long) {
                val spinnerParent = parent as Spinner
                mlClassifier = spinnerParent.selectedItem as String
                updateActiveModelName()
                Log.i(TAG, "Selected mlClassifier ${mlClassifier}")
                cameraPreviewView.post { startCameraX() }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
                mlClassifier = Constants.MLCLASSIFIER_QUANT_IMAGENET
            }
        }*/
        updateActiveModelName()
    }*/


    /*private fun startCameraXX() {
        CameraX.unbindAll()
        val screenSize = Size(cameraTextureView.width, cameraTextureView.height)
        val screenAspectRatio = Rational(1, 1)
        Log.i(TAG, "Screen size: (${screenSize.width}, ${screenSize.height}).")

        val previewConfig = buildPreviewConfig(screenSize,
            screenAspectRatio)

        val preview = Preview(previewConfig)
        preview.setOnPreviewOutputUpdateListener {
            val parent = cameraTextureView.parent as ViewGroup
            parent.removeView(cameraTextureView)
            cameraTextureView.setSurfaceTexture(it.surfaceTexture)
            parent.addView(cameraTextureView, 0)
            updateTransform()
        }

        val analyzerConfig = buildAnalyzerConfig()

        val imageAnalysis = ImageAnalysis(analyzerConfig)
        imageAnalysis.setAnalyzer(cameraExecutor, ImageAnalysis.Analyzer {
                image: ImageProxy, rotationDegrees: Int ->
            mlImageAnalysis(image, rotationDegrees)
        })

        CameraX.bindToLifecycle(this, preview, imageAnalysis)
    }*/

    private fun startCameraX() {
        val screenSize = Size(cameraPreviewView.width, cameraPreviewView.height)
        val screenAspectRatio = Rational(1, 1)
        Log.i(TAG, "Screen size: (${screenSize.width}, ${screenSize.height}).")

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .setTargetResolution(screenSize)
                .setTargetRotation(windowManager.defaultDisplay.rotation)
                .setTargetRotation(cameraPreviewView.display.rotation)
                .build()
                .also {
                    it.setSurfaceProvider(cameraPreviewView.surfaceProvider)
                }

            // Select back camera as a default
            var cameraSelector : CameraSelector = CameraSelector.Builder()
                .requireLensFacing(facingCameraX)
                .build()

            val imageAnalysis = ImageAnalysis.Builder()
                //.setTargetResolution(Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(cameraExecutor, ImageAnalysis.Analyzer { image ->
                    val rotationDegrees = image.imageInfo.rotationDegrees
                    mlImageAnalysis(image, rotationDegrees)
                    image.close()
                })}

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis, preview)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }


    private fun updateTransform() {
        val matrix = Matrix()
        val centerX = cameraPreviewView.width / 2f
        val centerY = cameraPreviewView.height / 2f

        val rotationDegrees = when (cameraPreviewView.display.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> return
        }
        matrix.postRotate(-rotationDegrees.toFloat(), centerX, centerY)
        //TODO cameraPreviewView.setTransform(matrix)
    }

    private fun mlImageAnalysis(image: ImageProxy, rotationDegrees: Int) {
        when (mlTarget){
            Constants.MLTARGET_FULL_SCREEN -> {
                overlay.setConfiguration(400,
                    380,
                    Color.TRANSPARENT)
                when (activeModelName) {
                    Constants.IMAGE_CLASSIFIER.MOBILENETV2_IMAGE_CLASSIFIER.modelName,
                    Constants.IMAGE_CLASSIFIER.MNASNET_IMAGE_CLASSIFIER.modelName -> {
                        mlProcessor.classifyAwait(
                            image,
                            overlay,
                            activeModelName)
                    }
                    Constants.MLCLASSIFIER_IMAGE_LABELER -> {
                        mlProcessor.labelImageAwait(
                            image,
                            rotationDegrees,
                            overlay,
                            imageLabelerAwaidSecond)
                    }
                    else -> {
                        Log.e(TAG, "Wrong configuration for mlClassifier")
                    }
                }
            }
            Constants.MLTARGET_OBJECT_DETECTION -> {
                when (activeModelName) {
                    Constants.IMAGE_CLASSIFIER.MOBILENETV2_IMAGE_CLASSIFIER.modelName,
                    Constants.IMAGE_CLASSIFIER.MNASNET_IMAGE_CLASSIFIER.modelName -> {
                        mlProcessor.classifyFromDetectionAwait(
                            image,
                            rotationDegrees,
                            overlay,
                            activeModelName,
                            objectDetectAwaitSecond)
                    }
                    Constants.MLCLASSIFIER_IMAGE_LABELER -> {
                        mlProcessor.labelImageFromDetectionAwait(
                            image,
                            rotationDegrees,
                            overlay,
                            objectDetectAwaitSecond,
                            imageLabelerAwaidSecond)
                    }
                    else -> {
                        Log.e(TAG, "Wrong configuration for mlClassifier")
                    }
                }
            }
            Constants.MLTARGET_Rectangle -> {
                val bitmap = ImageUtils.imageToBitmap(image)
                val croppedImage: Bitmap =
                    if (drawView.drawId == null) bitmap
                    else ImageUtils.cropImageFromPoints(bitmap, drawView.points)

                when (activeModelName) {
                    Constants.IMAGE_CLASSIFIER.MOBILENETV2_IMAGE_CLASSIFIER.modelName,
                    Constants.IMAGE_CLASSIFIER.MNASNET_IMAGE_CLASSIFIER.modelName -> {
                        mlProcessor.classifyAwait(
                            croppedImage!!,
                            overlay,
                            activeModelName)
                    }
                    Constants.MLCLASSIFIER_IMAGE_LABELER -> {
                        mlProcessor.labelImageAwait(
                            croppedImage!!,
                            rotationDegrees,
                            overlay,
                            imageLabelerAwaidSecond)
                    }
                    else -> {
                        Log.e(TAG, "Wrong configuration for mlClassifier")
                    }
                }
            }
            else -> {
                Log.e(TAG, "Wrong configuration for mlTarget")
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (!allPermissionsGranted()) {
                Utils.logAndToast(this,
                    TAG,
                    "Permissions not granted by the user.",
                    "e",
                    Toast.LENGTH_SHORT,
                    Gravity.TOP)
                finish()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        for (permission in REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(
                    this, permission) != PackageManager.PERMISSION_GRANTED) {
                Utils.logAndToast(this,
                    TAG,
                    "Permissions not granted by the user.",
                    "e",
                    Toast.LENGTH_SHORT,
                    Gravity.TOP)
                return false
            }
        }
        Log.i(TAG, "Permitted to use camera")
        return true
    }

    override fun onStop() {
        super.onStop()
        //mlThread.interrupt()
    }

    override fun onDestroy() {
        super.onDestroy()
        //mlThread.quitSafely()
        cameraExecutor.shutdown()
    }

}