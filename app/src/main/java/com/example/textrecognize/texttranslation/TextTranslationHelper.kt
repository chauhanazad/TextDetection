package com.example.textrecognize.texttranslation

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

class TextTranslationHelper(val context: Context, private val source: String) {

    private lateinit var englishGermanTranslator: Translator
    init {
        initTranslator()
    }

    private fun initTranslator() {
        val options = TranslatorOptions.Builder()
        when (source) {
            TEXT_RECOGNITION_LATIN -> {
                options.setSourceLanguage(TranslateLanguage.ENGLISH)
            }
            TEXT_RECOGNITION_KOREAN -> {
                options.setSourceLanguage(TranslateLanguage.KOREAN)
            }
            TEXT_RECOGNITION_JAPANESE -> {
                options.setSourceLanguage(TranslateLanguage.JAPANESE)
            }
            TEXT_RECOGNITION_CHINESE -> {
                options.setSourceLanguage(TranslateLanguage.CHINESE)
            }
//            TEXT_RECOGNITION_DEVANAGARI -> {
//                options.setSourceLanguage(TranslateLanguage.S)
//            }
        }

        options.setTargetLanguage(TranslateLanguage.ENGLISH)
        englishGermanTranslator = Translation.getClient(options.build())
        englishGermanTranslator.downloadModelIfNeeded()
            .addOnSuccessListener{
                Toast.makeText(context,"Model Downloaded",Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(context,"Unable to download model", Toast.LENGTH_SHORT).show()
            }
    }

    fun translate(text: String)
    {
        englishGermanTranslator.translate(text)
            .addOnSuccessListener {
                Toast.makeText(context,"After Translate: $it",Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(context,"Unable to Translate ${it.toString()}",Toast.LENGTH_SHORT).show()
                Log.d("Unable_Translate", it.toString())
            }
    }

    companion object {
        private const val TEXT_RECOGNITION_LATIN = "Latin"
        private const val TEXT_RECOGNITION_KOREAN = "Korean"
        private const val TEXT_RECOGNITION_JAPANESE = "Japanese"
        private const val TEXT_RECOGNITION_CHINESE = "Chinese"
        private const val TEXT_RECOGNITION_DEVANAGARI = "Devanagari"
    }
}