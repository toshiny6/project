package com.example.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.camera.databinding.ActivityResultBinding
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ResultActivity : AppCompatActivity() {
    private var mBinding : ActivityResultBinding? = null
    private  val binding get() = mBinding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (intent.hasExtra("filepath")) {
            //Toast.makeText(this, intent.getStringExtra("filepath") ,Toast.LENGTH_SHORT).show()
            var bm : Bitmap = BitmapFactory.decodeFile(intent.getStringExtra("filepath") );
            // resize 전 이미지
            // binding.ivInput.setImageBitmap(bm)
            // 원본 이미지 리사이즈 , 저장
            bm = Bitmap.createScaledBitmap(bm,512,512,true)
            savePhoto(bm)
            binding.ivInput.setImageBitmap(bm)
        }
        else {
            Toast.makeText(this, "Error!", Toast.LENGTH_SHORT).show()
        }
        //setContentView(R.layout.activity_result)
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
    }


}