package com.example.textrecognize.ocr

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.textrecognize.CameraXViewModel
import com.example.textrecognize.R
import com.example.textrecognize.databinding.FragmentOcrBinding
import com.example.textrecognize.fragment.CameraFragment
import com.example.textrecognize.textdetector.VisionImageProcessor
import com.example.textrecognize.utils.PreferenceUtils
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.demo.kotlin.textdetector.TextRecognitionProcessor
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class OcrFragment : Fragment() {

    lateinit var binding: FragmentOcrBinding
    private var cameraProvider: ProcessCameraProvider? = null
    private var previewUseCase: Preview? = null
    private var analysisUseCase: ImageAnalysis? = null
    private var imageProcessor: VisionImageProcessor? = null
    private var needUpdateGraphicOverlayImageSourceInfo = false
    private var selectedModel = TEXT_RECOGNITION_LATIN
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var cameraSelector: CameraSelector? = null
    private lateinit var bitmapBuffer: Bitmap

    private lateinit var executor: ExecutorService
    var text: Text? = null
    lateinit var imageCapture: ImageCapture
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding= FragmentOcrBinding.inflate(layoutInflater)
        if (requireContext().checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED || requireContext().checkSelfPermission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
//            findNavController().navigate(androidx.camera.core.R.id.action_cameraFragment_to_permissionFragment)
        }
        executor = Executors.newSingleThreadExecutor()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        //        adapter = TextAdapter(requireContext(), ArrayList())
        imageCapture = buildImageCaptureUseCase()
//        binding.rvText.adapter = adapter
        if (savedInstanceState != null) {
            selectedModel =
                savedInstanceState.getString(STATE_SELECTED_MODEL,
                    TEXT_RECOGNITION_LATIN)
        }
        cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        ViewModelProvider(this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application))[CameraXViewModel::class.java]
            .processCameraProvider
            .observe(
                viewLifecycleOwner) {
                cameraProvider = it
                bindAllCameraUseCases()
            }

//        binding.graphicOverlay.setOnItemClickListener(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_SELECTED_MODEL, selectedModel)
    }

    override fun onResume() {
        super.onResume()
        bindAllCameraUseCases()
//        viewModel.imageBitmap.observe(viewLifecycleOwner) {
//            if (it != null) {
////                findNavController().navigate(R.id.action_cameraFragment_to_staticCameraFragment)
//            }
//        }
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
            // As required by CameraX API, unbinds all use cases before trying to re-bind any of them.
            cameraProvider!!.unbindAll()
            bindPreviewUseCase()
            bindAnalysisUseCase()
        }
    }

    private fun bindPreviewUseCase() {
        val builder = Preview.Builder()
        val targetResolution = PreferenceUtils.getCameraXTargetResolution(requireContext(), lensFacing)
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
                        TextRecognitionProcessor(requireContext(),
                            TextRecognizerOptions.Builder().build())
                    }
                    else -> throw IllegalStateException("Invalid model name")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Can not create image processor: $selectedModel", e)
                return
            }

        val builder = ImageAnalysis.Builder()
        val targetResolution = PreferenceUtils.getCameraXTargetResolution(requireContext(), lensFacing)
        if (targetResolution != null) {
            builder.setTargetResolution(targetResolution)
        }
        analysisUseCase = builder.build()

        needUpdateGraphicOverlayImageSourceInfo = true

        analysisUseCase?.setAnalyzer(
            ContextCompat.getMainExecutor(requireContext()),
            ImageAnalysis.Analyzer { imageProxy: ImageProxy ->
                if (needUpdateGraphicOverlayImageSourceInfo) {
                    val rotationDegrees = imageProxy.imageInfo.rotationDegrees
//                    if (rotationDegrees == 0 || rotationDegrees == 180) {
//                        binding.graphicOverlay.setCameraInfo(imageProxy.width,
//                            imageProxy.height,
//                            Camera.CameraInfo.CAMERA_FACING_FRONT)
//                    } else {
//                        binding.graphicOverlay.setCameraInfo(imageProxy.height,
//                            imageProxy.width,
//                            Camera.CameraInfo.CAMERA_FACING_BACK)
//                    }
                    needUpdateGraphicOverlayImageSourceInfo = false
                }
                try {
//                    imageProcessor!!.processImageProxy(imageProxy, binding.graphicOverlay)
                } catch (e: MlKitException) {
                    Log.e(TAG, "Failed to process image. Error: " + e.localizedMessage)
                    Toast.makeText(requireContext(), e.localizedMessage, Toast.LENGTH_SHORT)
                        .show()
                }
            }
        )
        cameraProvider!!.bindToLifecycle(/* lifecycleOwner= */ this,
            cameraSelector!!,
            previewUseCase, imageCapture, analysisUseCase)
    }

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
            .setTargetResolution(PreferenceUtils.getCameraXTargetResolution(requireContext(), lensFacing)!!)
            .build()
    }


    companion object {
        private const val TAG = "CameraXLivePreview"
        private const val TEXT_RECOGNITION_LATIN = "Text Recognition Latin"
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