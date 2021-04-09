package com.example.camera

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.camera.databinding.ActivityMainBinding
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.TedPermission
import org.pytorch.Module
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    val REQUEST_IMAGE_CAPTURE = 1 // 카메라 사진 촬영 요청 코드
    val REQUEST_GALLERY_IMAGE = 2 // 갤러리 이미지 불러오기
    lateinit var curPhotoPath : String //문자열 형태의 사진 경로 값
    private var mBinding : ActivityMainBinding? = null
    private val binding get () = mBinding!!

    lateinit var filepath : String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //setContentView(R.layout.activity_main)
        mBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setPermission() // 권한을 체크하는 테스트 수행

        binding.btnCamera.setOnClickListener {
            takeCapture() // 기본 카메라 앱을 실행하여 사진 촬영
        }
        binding.btnGallery.setOnClickListener {
            // 사진 불러오는 함수 실행
            goToAlbum()
        }
        binding.btnConvert.setOnClickListener {
            // 결과 액티비티 실행
            if (this::filepath.isInitialized) {
                val nextIntent = Intent(this, ResultActivity::class.java)
                nextIntent.putExtra("filepath", filepath)

                // model 사용하는 process 실행
                // 실행 결과를 저장하여 path 반환 받으면 nextIntent에 넣어서 결과 화면으로 전송
                var bm: Bitmap = BitmapFactory.decodeFile(nextIntent.getStringExtra("filepath"))
                bm = Bitmap.createScaledBitmap(bm, 512, 512, true)
                UseModel(bm, applicationContext).process()

                startActivity(nextIntent)
            } else {
                Toast.makeText(this, "이미지를 선택해주세요.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     *  카메라 촬영
     */
    private fun takeCapture() {
        // 기본 카메라 앱 실행
            Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            takePictureIntent.resolveActivity(packageManager)?.also {
                val photoFile : File? = try {
                    createImageFile()
                } catch(ex:IOException){
                    null
              }
                photoFile?.also {
                    val photoURI : Uri = FileProvider.getUriForFile(
                        this,
                        "com.example.camera.fileprovider",
                         it
                    )
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT,photoURI)
                    startActivityForResult(takePictureIntent,REQUEST_IMAGE_CAPTURE)
                }
            }
        }
    }

    /**
     * 이미지 파일 생성
     */
    private fun createImageFile(): File? {

        val timestamp : String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir : File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)

        return File.createTempFile("JPEG_${timestamp}_",".jpg",storageDir)
            .apply{ curPhotoPath = absolutePath }
    }

    /**
     *  테드 퍼미션 설정
     */
    private fun setPermission() {
        val permission = object : PermissionListener{
            override fun onPermissionGranted() { // 설정해놓은 위험 권한들이 허용 되었을 경우 수행
                Toast.makeText(this@MainActivity, "권한이 허용 되었습니다.",Toast.LENGTH_SHORT).show()
            }
            override fun onPermissionDenied(deniedPermissions: MutableList<String>?) { // 설정해놓은 위험 권한이 거부 되었을 경우 수행
                Toast.makeText(this@MainActivity, "권한이 거부 되었습니다.",Toast.LENGTH_SHORT).show()
            }
        }

        TedPermission.with(this)
            .setPermissionListener(permission)
            .setRationaleMessage("카메라 앱을 사용하시려면 권한을 허용해주세요.")
            .setDeniedMessage("권한을 거부하셨습니다. [앱 설정] -> [권한] 항목에서 허용해주세요.")
            .setPermissions(android.Manifest.permission.WRITE_EXTERNAL_STORAGE,android.Manifest.permission.CAMERA)
            .check()
    }

    /***
     *  갤러리 이미지 불러오기
     */
    private  fun goToAlbum() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = MediaStore.Images.Media.CONTENT_TYPE
        startActivityForResult(intent, REQUEST_GALLERY_IMAGE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        //startActivityForResult를 통해서 기본 카메라 앱으로부터 받아온 사진 결과 값
        super.onActivityResult(requestCode, resultCode, data)

        if(requestCode == REQUEST_IMAGE_CAPTURE && resultCode == Activity.RESULT_OK) {
            // 이미지를  성공적으로 가져왔다면
            val bitmap: Bitmap
            val file = File(curPhotoPath)
            if (Build.VERSION.SDK_INT < 28) {
                //안드로이드 9.0보다 낮은 경우
                bitmap = MediaStore.Images.Media.getBitmap(contentResolver, Uri.fromFile(file))
                binding.ivPicture.setImageBitmap(bitmap) // 이미지 뷰에 촬영한 사진 기록
            } else {
                val decode = ImageDecoder.createSource(
                    this.contentResolver,
                    Uri.fromFile(file)
                )
                bitmap = ImageDecoder.decodeBitmap(decode)
                binding.ivPicture.setImageBitmap(bitmap)
            }
            savePhoto(bitmap)

        }
        else if(requestCode == REQUEST_GALLERY_IMAGE && resultCode == Activity.RESULT_OK)
        {
            val uri: Uri? = data?.data
            val bitmap : Bitmap

            bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
            // Log.d(TAG, String.valueOf(bitmap));
            Toast.makeText(this,getPathFromUri(uri),Toast.LENGTH_SHORT).show()  // 저장 경로 확인
            binding.ivPicture.setImageBitmap(bitmap)
        }

    }

    // 갤러리에 저장된 사진 불러올 때 Uri - > filepath 구하기
    fun getPathFromUri(uri: Uri?): String? {
        val cursor =
            uri?.let { contentResolver.query(it, null, null, null, null) }
        cursor!!.moveToNext()
        val path = cursor.getString(cursor.getColumnIndex("_data"))
        cursor.close()
        filepath=path
        return path
    }

    /**
     *  갤러리에 저장
     */
    private fun savePhoto(bitmap: Bitmap) {
        val absolutePath = "/storage/emulated/0/"
        val folderPath = "$absolutePath/Pictures/"
        //val folderPath = Environment.getExternalStorageDirectory().absolutePath + "/Pictures/" // 사진 폴더로 저장하기 위한 경로 선언
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
        Toast.makeText(this , folderPath+ fileName, Toast.LENGTH_SHORT).show()
        filepath=folderPath+fileName
        // 저장 경로 확인

    }

    override fun onDestroy() {
        mBinding = null
        super.onDestroy()
    }
}