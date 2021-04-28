package com.example.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.camera.databinding.ActivityResultBinding
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*


class ResultActivity : AppCompatActivity() {
    private var mBinding : ActivityResultBinding? = null
    private  val binding get() = mBinding!!
//    lateinit var resizedpath : String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (intent.hasExtra("uri")) {
            //Toast.makeText(this, intent.getStringExtra("filepath") ,Toast.LENGTH_SHORT).show()
//            var bm : Bitmap = BitmapFactory.decodeFile(intent.getStringExtra("filepath") )
//            // 원본 이미지 리사이즈
//            var blurRadius : Int = 7 //.toFloat()
//            bm=blur(getApplicationContext(),bm,blurRadius)
//            bm = Bitmap.createScaledBitmap(bm,512,512,true)

            //savePhoto(bm)
            //Toast.makeText(this,intent.getStringExtra("uri"),Toast.LENGTH_LONG).show()
            val uri : Uri = Uri.parse(intent.getStringExtra("uri"))
            Glide.with(getApplicationContext())
                .load(uri)
                .into(binding.ivInput)
            //binding.ivInput.setImageBitmap(bm)
            //binding.ivOutput.setImageBitmap()
        }
        else {
            Toast.makeText(this, "UriError!", Toast.LENGTH_SHORT).show()
        }
        if (intent.hasExtra("resulturi")) {

            val resulturi : Uri = Uri.parse(intent.getStringExtra("resulturi"))

            Glide.with(getApplicationContext())
                .load(intent.getStringExtra("resulturi"))
                .into(binding.ivOutput)

        }
        else {
            Toast.makeText(this, "resultError!", Toast.LENGTH_SHORT).show()
        }
        //setContentView(R.layout.activity_result)
    }

    //bitmap to uri
    private fun getImageUri(
        context: Context,
        inImage: Bitmap
    ): Uri? {
        val bytes = ByteArrayOutputStream()
        inImage.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
        val path = MediaStore.Images.Media.insertImage(
            context.contentResolver,
            inImage,
            "Title",
            null
        )
        return Uri.parse(path)
    }


    override fun onDestroy() {
        // onDestroy 에서 binding class 인스턴스 참조를 정리해주어야 한다.
        mBinding = null
        super.onDestroy()
    }

    private fun savePhoto(bitmap: Bitmap) {
        val absolutePath = "/storage/emulated/0/"
        val folderPath = "$absolutePath/Pictures/Resized/"
        val timestamp =  SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val fileName = "${timestamp}.jpeg"
        val folder = File(folderPath)
        if(!folder.isDirectory) {//현재 해당 경로에 폴더가 존재하지 않는다면
            folder.mkdirs()
        }

        // 실제적인 저장 처리
        val out =  FileOutputStream(folderPath + fileName)
        bitmap.compress(Bitmap.CompressFormat.JPEG,100,out)
        // Toast.makeText(this, "사진이 앨범에 저장되었습니다." , Toast.LENGTH_SHORT).show()
//        resizedpath=folderPath+fileName
    }
}