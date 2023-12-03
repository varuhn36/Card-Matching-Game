package com.example.matching_game

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.content.pm.PackageManager
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.matching_game.models.BoardSize
import com.example.matching_game.utils.BitMapScaler
import com.example.matching_game.utils.EXTRA_BOARD_SIZE
import com.example.matching_game.utils.EXTRA_GAME_NAME
import com.example.matching_game.utils.isPermissionGranted
import com.example.matching_game.utils.requestPermission
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import java.io.ByteArrayOutputStream

class CreateActivity : AppCompatActivity() {

    companion object{
        private const val PICK_PHOTO_CODE = 321
        private const val READ_EXTERNAL_PHOTOS_CODE = 456
        private const val READ_PHOTOS_PERMISSION = android.Manifest.permission.READ_EXTERNAL_STORAGE
        private const val TAG = "CreateActivity"
        private const val MIN_GAME_NAME_LENGTH = 3
        private const val MAX_GAME_NAME_LENGTH = 15
    }

    private lateinit var adapter: ImagePickerAdapter
    private lateinit var boardSize: BoardSize
    private var numImagesRequired = -1
    private lateinit var imagePicker : RecyclerView
    private lateinit var editGameName : EditText
    private lateinit var saveButton : Button
    private lateinit var uploadingProgressBar : ProgressBar
    private val chosenImageUriList = mutableListOf<Uri>()
    private val storage = Firebase.storage
    private val dataBase = Firebase.firestore



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create)

        imagePicker = findViewById(R.id.image_picker)
        editGameName = findViewById(R.id.edit_game_name)
        saveButton = findViewById(R.id.save_button)
        uploadingProgressBar = findViewById(R.id.image_uploading_progress_bar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        boardSize = intent.getSerializableExtra(EXTRA_BOARD_SIZE) as BoardSize
        numImagesRequired = boardSize.getNumPairs()
        supportActionBar?.title = "Choose pics (0 / ${numImagesRequired})"

        saveButton.setOnClickListener{
            saveDataToFirebase()
        }


        editGameName.filters = arrayOf(InputFilter.LengthFilter(MAX_GAME_NAME_LENGTH))
        editGameName.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                saveButton.isEnabled = shouldEnableSaveButton()
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {}

        })

      adapter = ImagePickerAdapter(this, chosenImageUriList, boardSize, object: ImagePickerAdapter.ImageClickListener{
            override fun onPlaceHolderClicked() {
                if(isPermissionGranted(this@CreateActivity, READ_PHOTOS_PERMISSION))
                {
                    launchIntentForPhotos()
                }
                else
                {
                    requestPermission(this@CreateActivity, READ_PHOTOS_PERMISSION, READ_EXTERNAL_PHOTOS_CODE)
                }
            }
        })
        imagePicker.adapter = adapter
        imagePicker.setHasFixedSize(true)
        imagePicker.layoutManager = GridLayoutManager(this,boardSize.getWidth())
    }



    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if(requestCode == READ_EXTERNAL_PHOTOS_CODE)
        {
            if(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            {
                launchIntentForPhotos()
            }
            else
            {
                Toast.makeText(this, "This game requires access to your photos to create a custom game.", Toast.LENGTH_LONG).show()
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode != PICK_PHOTO_CODE || resultCode != Activity.RESULT_OK || data == null)
        {
            Log.w(TAG, "Did not get data back from activity")
            return
        }
        val selectedUri = data.data
        val clipData = data.clipData
        if(clipData != null)
        {
            Log.i(TAG, "clipData numImages ${clipData.itemCount} : $clipData")
            for(i in 0 until clipData.itemCount)
            {
                val clipItem = clipData.getItemAt(i)
                if(chosenImageUriList.size < numImagesRequired)
                {
                    chosenImageUriList.add(clipItem.uri)
                }
            }
        }
        else if(selectedUri != null)
        {
            Log.i(TAG, "data ${selectedUri}")
            chosenImageUriList.add(selectedUri)
        }
        adapter.notifyDataSetChanged()
        supportActionBar?.title = "Choose pics (${chosenImageUriList.size} / $numImagesRequired)"
        saveButton.isEnabled = shouldEnableSaveButton()
    }

    private fun shouldEnableSaveButton(): Boolean {
        if(chosenImageUriList.size != numImagesRequired)
        {
            return false
        }
        if(editGameName.text.isBlank() || editGameName.text.length < MIN_GAME_NAME_LENGTH)
        {
            return false
        }
        return true
    }

    private fun launchIntentForPhotos() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        startActivityForResult(Intent.createChooser(intent, "Choose pics"), PICK_PHOTO_CODE)
    }

    private fun saveDataToFirebase() {
        saveButton.isEnabled = false
        val customGameName = editGameName.text.toString()
        dataBase.collection("games").document(customGameName).get().addOnSuccessListener { document ->
            if(document != null && document.data != null)
            {
                AlertDialog.Builder(this).setTitle("Name Taken").setMessage("A game with this name already exists, please choose a different one.").setPositiveButton("OK", null).show()
                saveButton.isEnabled = true
            }
            else
            {
                handleImageUpload(customGameName)
            }
        }.addOnFailureListener { exception ->
            Log.e(TAG, "Error while saving game", exception)
            Toast.makeText(this, "Error while trying to save the game", Toast.LENGTH_SHORT).show()
            saveButton.isEnabled = true
        }

    }

    private fun handleImageUpload(gameName: String) {
        uploadingProgressBar.visibility = View.VISIBLE
        var didEncounterError = false
        val uploadedImageUrls = mutableListOf<String>()
        for((index, photoUri) in chosenImageUriList.withIndex())
        {
            val imageByteArray = getImageByteArray(photoUri)
            val filePath = "images/${gameName}/${System.currentTimeMillis()}-${index}.jpg"
            Log.d(TAG, "Uploading image to: $filePath")
            val photoReference = storage.reference.child(filePath)
            photoReference.putBytes(imageByteArray).continueWithTask { photoUploadTask ->
                Log.i(TAG, "Uploaded bytes: ${photoUploadTask.result?.bytesTransferred}")
                photoReference.downloadUrl
            }.addOnCompleteListener { downloadUrlTask->
                if(!downloadUrlTask.isSuccessful){
                    Log.e(TAG, "Exception with Firebase storage", downloadUrlTask.exception)
                    Toast.makeText(this, "Failed to upload image", Toast.LENGTH_SHORT).show()
                    didEncounterError = true
                    return@addOnCompleteListener
                }
                if(didEncounterError)
                {
                    uploadingProgressBar.visibility = View.GONE
                    return@addOnCompleteListener
                }
                val downloadUrl:String= downloadUrlTask.result.toString()
                uploadedImageUrls.add(downloadUrl)
                uploadingProgressBar.progress = uploadedImageUrls.size * 100 / chosenImageUriList.size
                Log.i(TAG, "Finished Uploading $photoUri, num uploaded ${uploadedImageUrls.size}")
                if(uploadedImageUrls.size == chosenImageUriList.size)
                {
                    handleAllImagesUploaded(gameName, uploadedImageUrls)
                }
            }
        }
    }

    private fun handleAllImagesUploaded(gameName: String, imageUrls: MutableList<String>) {
        dataBase.collection("games").document(gameName)
            .set(mapOf("images" to imageUrls)).addOnCompleteListener { gameCreationTask ->
                uploadingProgressBar.visibility = View.GONE
                if(!gameCreationTask.isSuccessful)
                {
                    Log.e(TAG, "Exception with game creation",gameCreationTask.exception)
                    Toast.makeText(this, "Failed game creation", Toast.LENGTH_SHORT).show()
                    return@addOnCompleteListener
                }
                Log.i(TAG, "Succesfully created game $gameName")
                AlertDialog.Builder(this).setTitle("Upload Complete! Let play $gameName")
                    .setPositiveButton("OK") { _, _ ->
                        val resultData = Intent()
                        resultData.putExtra(EXTRA_GAME_NAME, gameName)
                        setResult(Activity.RESULT_OK, resultData)
                        finish()
                    }.show()
            }
    }

    private fun getImageByteArray(photoUri: Uri): ByteArray {
        val originalBitMap =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        {
            val source = ImageDecoder.createSource(contentResolver, photoUri)
            ImageDecoder.decodeBitmap(source)
        }
        else
        {
            MediaStore.Images.Media.getBitmap(contentResolver, photoUri)
        }
        val scaledBitMap = BitMapScaler.scaleToFitHeight(originalBitMap, 250)
        val byteOutputStream = ByteArrayOutputStream()
        scaledBitMap.compress(Bitmap.CompressFormat.JPEG, 60, byteOutputStream)
        return byteOutputStream.toByteArray()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        if(item.itemId == android.R.id.home){
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}