package com.example.textrecognize.languageidentifier

import android.content.Context
import android.widget.Toast
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentifier

class LanguageIdentifierHelper(val context: Context) {
    private var languageIdentifier: LanguageIdentifier = LanguageIdentification.getClient()

    fun identify(text: String)
    {
        languageIdentifier.identifyLanguage(text)
            .addOnSuccessListener {
                Toast.makeText(context,it,Toast.LENGTH_SHORT).show()

            }
            .addOnFailureListener {
                Toast.makeText(context,"Unable to Identify",Toast.LENGTH_SHORT).show()
            }
    }
}