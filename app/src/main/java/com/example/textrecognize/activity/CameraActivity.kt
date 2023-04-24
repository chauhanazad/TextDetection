package com.example.textrecognize.activity

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.textrecognize.CameraXViewModel
import com.example.textrecognize.utils.PreferenceUtils
import com.example.textrecognize.R
import com.example.textrecognize.databinding.ActivityCameraBinding
import com.example.textrecognize.languageidentifier.LanguageIdentifierHelper
import com.example.textrecognize.textdetector.TextGraphic
import com.example.textrecognize.textdetector.VisionImageProcessor
import com.example.textrecognize.texttranslation.TextTranslationHelper
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.demo.kotlin.textdetector.TextRecognitionProcessor
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    lateinit var binding: ActivityCameraBinding
    private var cameraProvider: ProcessCameraProvider? = null
    private var previewUseCase: Preview? = null
    private var analysisUseCase: ImageAnalysis? = null
    private var imageProcessor: VisionImageProcessor? = null
    private var needUpdateGraphicOverlayImageSourceInfo = false
    private var selectedModel = TEXT_RECOGNITION_LATIN
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var cameraSelector: CameraSelector? = null
    private lateinit var executor: ExecutorService
    var text: Text? = null
    lateinit var imageCapture: ImageCapture
    private var gestureDetector: GestureDetector? = null
    private lateinit var languageIdentifierHelper: LanguageIdentifierHelper
    private lateinit var translationHelper: TextTranslationHelper
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED || checkSelfPermission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
//            findNavController().navigate(R.id.action_cameraFragment_to_permissionFragment)
        }
        val bundle = intent.getBundleExtra("lang")
        selectedModel = bundle?.getString("lang")!!

        executor = Executors.newSingleThreadExecutor()
        languageIdentifierHelper = LanguageIdentifierHelper(this)
        translationHelper = TextTranslationHelper(this,selectedModel)
        imageCapture = buildImageCaptureUseCase()
        if (savedInstanceState != null) {
            selectedModel =
                savedInstanceState.getString(STATE_SELECTED_MODEL,
                    TEXT_RECOGNITION_LATIN)
        }
        gestureDetector = GestureDetector(this, CaptureGestureListener())
        cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        ViewModelProvider(this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application))[CameraXViewModel::class.java]
            .processCameraProvider
            .observe(
                this) {
                cameraProvider = it
                bindAllCameraUseCases()
            }
    }

    /**
     * onTap is called to speak the tapped TextBlock, if any, out loud.
     *
     * @param rawX - the raw position of the tap
     * @param rawY - the raw position of the tap.
     * @return true if the tap was on a TextBlock
     */
    private fun onTap(rawX: Float, rawY: Float): Boolean {
        val graphic: TextGraphic? = binding.graphicOverlay.getGraphicAtLocation(rawX, rawY)
        var text: Text.TextBlock? = null
        if (graphic != null) {
            text = graphic.textBlock
            if (text != null && text.text != null) {
                Log.d(TAG,
                    "text data is being spoken! " + text.text)
                Toast.makeText(this, text.text, Toast.LENGTH_SHORT).show()
                languageIdentifierHelper.identify(text.text)
                translationHelper.translate(text.text)

            } else {
                Log.d(TAG,
                    "text data is null")
            }
        } else {
            Log.d(TAG,
                "no text detected")
        }
        return text != null
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        val b: Boolean = gestureDetector?.onTouchEvent(event)!!
        return b||super.onTouchEvent(event)
    }
    private inner class CaptureGestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            return onTap(e.rawX, e.rawY) || super.onSingleTapConfirmed(e)
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_SELECTED_MODEL, selectedModel)
    }

    override fun onResume() {
        super.onResume()
        bindAllCameraUseCases()
    }

    override fun onPause() {
        super.onPause()

        imageProcessor?.run { this.stop() }
    }

    override fun onDestroy() {
        super.onDestroy()
        imageProcessor?.run { this.stop() }
    }

    private fun bindAllCameraUseCases() {
        if (cameraProvider != null) {
            cameraProvider!!.unbindAll()
            bindPreviewUseCase()
            bindAnalysisUseCase()
        }
    }

    private fun bindPreviewUseCase() {
        val builder = Preview.Builder()
        val targetResolution = PreferenceUtils.getCameraXTargetResolution(this, lensFacing)
        if (targetResolution != null) {
            builder.setTargetResolution(targetResolution)
        }
        previewUseCase = builder.build()
        previewUseCase!!.setSurfaceProvider(binding.previewView.surfaceProvider)
        cameraProvider!!.bindToLifecycle(/* lifecycleOwner= */ this,
            cameraSelector!!,
            previewUseCase)
    }

    private fun bindAnalysisUseCase() {
        if (cameraProvider == null) {
            return
        }
        if (analysisUseCase != null) {
            cameraProvider!!.unbind(analysisUseCase)
        }
        if (imageProcessor != null) {
            imageProcessor!!.stop()
        }
        imageProcessor =
            try {
                when (selectedModel) {
                    TEXT_RECOGNITION_LATIN -> {
                        Log.i(TAG, "Using on-device Text recognition Processor for Latin")
                        TextRecognitionProcessor(this,
                            TextRecognizerOptions.Builder().build())
                    }
                    TEXT_RECOGNITION_CHINESE ->{
                        Log.i(TAG, "Using on-device Text recognition Processor for Chinese")
                        TextRecognitionProcessor(this,
                            ChineseTextRecognizerOptions.Builder().build())
                    }
                    TEXT_RECOGNITION_DEVANAGARI->{
                        Log.i(TAG, "Using on-device Text recognition Processor for Chinese")
                        TextRecognitionProcessor(this,
                            DevanagariTextRecognizerOptions.Builder().build())
                    }
                    TEXT_RECOGNITION_JAPANESE ->{
                        Log.i(TAG, "Using on-device Text recognition Processor for Chinese")
                        TextRecognitionProcessor(this,
                            JapaneseTextRecognizerOptions.Builder().build())
                    }
                    TEXT_RECOGNITION_KOREAN ->{
                        Log.i(TAG, "Using on-device Text recognition Processor for Chinese")
                        TextRecognitionProcessor(this,
                            KoreanTextRecognizerOptions.Builder().build())
                    }
                    else -> throw IllegalStateException("Invalid model name")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Can not create image processor: $selectedModel", e)
                return
            }

        val builder = ImageAnalysis.Builder()
        val targetResolution = PreferenceUtils.getCameraXTargetResolution(this, lensFacing)
        if (targetResolution != null) {
            builder.setTargetResolution(targetResolution)
        }
        analysisUseCase = builder.build()

        needUpdateGraphicOverlayImageSourceInfo = true

        analysisUseCase?.setAnalyzer(
            ContextCompat.getMainExecutor(this),
            ImageAnalysis.Analyzer { imageProxy: ImageProxy ->
                if (needUpdateGraphicOverlayImageSourceInfo) {
                    val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                    if (rotationDegrees == 0 || rotationDegrees == 180) {
                        binding.graphicOverlay.setImageSourceInfo(imageProxy.width,
                            imageProxy.height,
                            true)
                    } else {
                        binding.graphicOverlay.setImageSourceInfo(imageProxy.height,
                            imageProxy.width,
                            false)
                    }
                    needUpdateGraphicOverlayImageSourceInfo = false
                }
                try {
                    imageProcessor!!.processImageProxy(imageProxy, binding.graphicOverlay)
                } catch (e: MlKitException) {
                    Log.e(TAG, "Failed to process image. Error: " + e.localizedMessage)
                    Toast.makeText(this, e.localizedMessage, Toast.LENGTH_SHORT)
                        .show()
                }
            }
        )
        cameraProvider!!.bindToLifecycle(/* lifecycleOwner= */ this,
            cameraSelector!!,
            previewUseCase, imageCapture, analysisUseCase)
    }

//    override fun onDetect(text: Text) {
//        this.text = text
//        textList = ArrayList()
//        for (textBlock in text.textBlocks) {
//            textList.add(DetectedText(textBlock.text))
//        }
////        adapter.updateList(textList)
//    }


//    override fun onClick(x: Float, y: Float, width:Int, height: Int) {
//        viewModel.coordinate.postValue(TextCoordinate(x,y,0,0))
//        val outputDirectory = getOutputDirectory(requireContext())
//
//        imageCapture.let {
//            val photoFile = createFile(outputDirectory,
//                FILENAME,
//                PHOTO_EXTENSION)
//
//            // Setup image capture metadata
//            val metadata = ImageCapture.Metadata().apply {
//                // Mirror image when using the front camera
//                isReversedHorizontal = lensFacing == CameraSelector.LENS_FACING_FRONT
//            }
//
//            // Create output options object which contains file + metadata
//            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile)
//                .setMetadata(metadata)
//                .build()
//
//            imageCapture.takePicture(outputOptions, executor, object : ImageCapture
//            .OnImageCapturedCallback(), ImageCapture.OnImageSavedCallback {
//                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
//                    val savedUri = outputFileResults.savedUri ?: Uri.fromFile(photoFile)
//                    val bitmap =
//                        BitmapFactory.decodeStream(requireContext().contentResolver.openInputStream(
//                            savedUri))
////                    viewModel.imageBitmap.postValue(bitmap)
//                }
//
//                override fun onError(exception: ImageCaptureException) {
//                    super.onError(exception)
//                    Log.d(TAG, exception.toString())
//                }
//            })
//        }
//    }

    private fun getOutputDirectory(context: Context): File {
        val appContext = context.applicationContext
        val mediaDir = context.externalMediaDirs.firstOrNull()?.let {
            File(it, appContext.resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else appContext.filesDir
    }

    private fun buildImageCaptureUseCase(): ImageCapture {
        return ImageCapture.Builder()
            .setTargetResolution(PreferenceUtils.getCameraXTargetResolution(this, lensFacing)!!)
            .build()
    }


    companion object {
        private const val TAG = "CameraXLivePreview"
        private const val TEXT_RECOGNITION_LATIN = "Latin"
        private const val TEXT_RECOGNITION_KOREAN = "Korean"
        private const val TEXT_RECOGNITION_JAPANESE = "Japanese"
        private const val TEXT_RECOGNITION_CHINESE = "Chinese"
        private const val TEXT_RECOGNITION_DEVANAGARI = "Devanagari"
        private const val STATE_SELECTED_MODEL = "selected_model"

        const val FILENAME = "yyyy-MM-dd-HH-mm-ss-SSS"
        const val PHOTO_EXTENSION = ".jpg"
        const val RATIO_4_3_VALUE = 4.0 / 3.0
        const val RATIO_16_9_VALUE = 16.0 / 9.0

        /** Helper function used to create a timestamped file */
        fun createFile(baseFolder: File, format: String, extension: String) =
            File(baseFolder, SimpleDateFormat(format, Locale.US)
                .format(System.currentTimeMillis()) + extension)
    }
}