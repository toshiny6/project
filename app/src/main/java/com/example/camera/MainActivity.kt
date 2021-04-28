package com.example.camera

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.example.camera.databinding.ActivityMainBinding
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.TedPermission
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    val REQUEST_IMAGE_CAPTURE = 1 // 카메라 사진 촬영 요청 코드
    val REQUEST_GALLERY_IMAGE = 2 // 갤러리 이미지 불러오기
    lateinit var curPhotoPath : String //문자열 형태의 사진 경로 값
    private var mBinding : ActivityMainBinding? = null
    private val binding get () = mBinding!!
    private var mModule: Module? = null

    lateinit var filepath : String
    lateinit var _bm : Bitmap
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //setContentView(R.layout.activity_main)
        mBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setPermission() // 권한을 체크하는 테스트 수행
        loadModel() // model load

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

                binding.progressBar.visibility=View.VISIBLE

                val nextIntent = Intent(this, ResultActivity::class.java)
                nextIntent.putExtra("filepath", filepath)

                // 실행 결과를 저장하여 path 반환 받으면 nextIntent에 넣어서 결과 화면으로 전송
                var bm: Bitmap = BitmapFactory.decodeFile(nextIntent.getStringExtra("filepath"))

                // 블러, 리사이즈
                var blurRadius : Int = 5 //.toFloat()
                bm=blur(getApplicationContext(),bm,blurRadius)
                bm = Bitmap.createScaledBitmap(bm, 480 , 640, true)

                //savePhoto(bm)

                //블러,리사이즈 처리된 input image uri
                nextIntent.putExtra("uri", getImageUri(getApplicationContext(),bm).toString())


                // model 사용하는 process 실행
               // val outPutImage = UseModel(bm, this!!.mModule!!).process()

                val thread = Thread(
                    Runnable {
                        val begin = System.nanoTime()
                        try{

                        var x: Int = 0
                        var y: Int = 0
                        var width: Int = bm.width
                        var height: Int = bm.height


                        val floatBuffer = Tensor.allocateFloatBuffer(3 * width * height)
                        if (bm != null) {
                            val pixelsCount = height * width
                            val pixels = IntArray(pixelsCount)
                            var outBufferOffset = 0
                            bm!!.getPixels(pixels, 0, width, x, y, width, height)
                            val offset_b = 2 * pixelsCount
                            for (i in 0 until pixelsCount) {
                                val c = pixels[i]
                                val r = (c shr 16 and 0xff) / 255.0f
                                val g = (c shr 8 and 0xff) / 255.0f
                                val b = (c and 0xff) / 255.0f
                                floatBuffer.put(outBufferOffset + i, r)
                                floatBuffer.put(outBufferOffset + pixelsCount + i, g)
                                floatBuffer.put(outBufferOffset + offset_b + i, b)
                            }
                        }
                        var inputTensor: Tensor = Tensor.fromBlob(
                            floatBuffer,
                            longArrayOf(1, 3, height.toLong(), width.toLong())
                        )


                        // outputTensor 생성 및 forward
                        var outputTensor = mModule!!.forward(IValue.from(inputTensor)).toTuple()

                        val dataAsFloatArray = outputTensor[1].toTensor().dataAsFloatArray

                        // bitmap으로 만들어서 반환


                        // Create empty bitmap in ARGB format
                        val bmp: Bitmap =
                            width?.let { Bitmap.createBitmap(it, height, Bitmap.Config.ARGB_8888) }
                        val _pixels: IntArray = IntArray(width * height!! * 4)

                        // mapping smallest value to 0 and largest value to 255
                        val maxValue = dataAsFloatArray.max() ?: 1.0f
                        val minValue = dataAsFloatArray.min() ?: -1.0f
                        val delta = maxValue - minValue

                        // Define if float min..max will be mapped to 0..255 or 255..0
                        val conversion =
                            { v: Float -> ((v - minValue) / delta * 255.0f).roundToInt() }

                        // copy each value from float array to RGB channels
                        if (width != null) {
                            for (i in 0 until width * height) {
                                val r = conversion(dataAsFloatArray[i])
                                val g = conversion(dataAsFloatArray[i + width * height])
                                val b = conversion(dataAsFloatArray[i + 2 * width * height])
                                _pixels[i] =
                                    Color.rgb(r, g, b) // you might need to import for rgb()
                            }
                        }
                        if (width != null) {
                            bmp.setPixels(_pixels, 0, width, 0, 0, width, height)
                        }

                            nextIntent.putExtra("resulturi", getImageUri(getApplicationContext(),bmp).toString())
                             runOnUiThread{
                                 binding.progressBar.visibility=View.INVISIBLE
                           }
                            val end = System.nanoTime()
                            Log.d("Elapsed time in nanoseconds: ", "${end-begin}")
                    }catch(e:Exception)
                       {
                           e.printStackTrace()
                       }
                    }
                )

                thread.run()

                // 결과 사진 저장 & 결과 화면 전송
                //savePhoto(outPutImage)
                //nextIntent.putExtra("output",outPutImage)

                //여기
                //nextIntent.putExtra("resulturi", getImageUri(getApplicationContext(),outPutImage).toString())
                startActivity(nextIntent)
            } else {
                Toast.makeText(this, "이미지를 선택해주세요.", Toast.LENGTH_SHORT).show()
            }
        }
    }


    fun blur(context : Context, sentBitmap : Bitmap, radius : Int) : Bitmap{

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN) {
            var bitmap : Bitmap = sentBitmap.copy(sentBitmap.getConfig(), true)

            val rs : RenderScript = RenderScript.create(context)
            val input : Allocation = Allocation.createFromBitmap(rs, sentBitmap, Allocation.MipmapControl.MIPMAP_NONE,
                Allocation.USAGE_SCRIPT)
            val output : Allocation = Allocation.createTyped(rs, input.getType())
            val script : ScriptIntrinsicBlur = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
            script.setRadius(radius.toFloat()) //0.0f ~ 25.0f
            script.setInput(input)
            script.forEach(output)
            output.copyTo(bitmap)
            return bitmap
        }
        else
            return sentBitmap

    }

    // 절대경로 -> uri
    fun getUriFromPath(filePath: String): Uri? {
        val cursor: Cursor? = contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            null, "_data = '$filePath'", null, null
        )
        if (cursor != null) {
            cursor.moveToNext()
        }
        val id: Int? = cursor?.getInt(cursor?.getColumnIndex("_id"))

        return id?.toLong()?.let {
            ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                it
            )
        }


    }

    //bitmap -> uri
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

    /**
     * model load
     */
    private fun loadModel(){
        try{
            mModule = Module.load(assetFilePath(this, "lowlight_model1.pt"))
            Log.d("Model", "Model Loaded Successfully")
        } catch (e: IOException){
            Log.e("UseModel", "Load Model Failed", e)
        }
    }

    /**
     * return : model의 절대경로
     */
    @Throws(IOException::class)
    fun assetFilePath(context: Context, assetName: String?): String? {
        val file = File(context.filesDir, assetName!!)
        if (file.exists() && file.length() > 0) {
            return file.absolutePath
        }
        context.assets.open(assetName).use { `is` ->
            FileOutputStream(file).use { os ->
                val buffer = ByteArray(4 * 1024)
                var read: Int
                while (`is`.read(buffer).also { read = it } != -1) {
                    os.write(buffer, 0, read)
                }
                os.flush()
            }
            return file.absolutePath
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

                Glide.with(getApplicationContext())
                    .load(Uri.fromFile(file))
                    .into(binding.ivPicture)
                //binding.ivPicture.setImageBitmap(bitmap) // 이미지 뷰에 촬영한 사진 기록
            } else {
                val decode = ImageDecoder.createSource(
                    this.contentResolver,
                    Uri.fromFile(file)
                )
                bitmap = ImageDecoder.decodeBitmap(decode)
                //binding.ivPicture.setImageBitmap(bitmap)
                Glide.with(getApplicationContext())
                    .load(Uri.fromFile(file))
                    .into(binding.ivPicture)
            }
            savePhoto(bitmap)

        }
        else if(requestCode == REQUEST_GALLERY_IMAGE && resultCode == Activity.RESULT_OK)
        {
            val uri: Uri? = data?.data
            val bitmap : Bitmap

            //bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
            // Log.d(TAG, String.valueOf(bitmap));
            //Toast.makeText(this,getPathFromUri(uri),Toast.LENGTH_SHORT).show()  // 저장 경로 확인
            getPathFromUri(uri)
            //binding.ivPicture.setImageBitmap(bitmap)
            Glide.with(getApplicationContext())
                .load(uri)
                .into(binding.ivPicture)
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
        //Toast.makeText(this , folderPath+ fileName, Toast.LENGTH_SHORT).show()
        filepath=folderPath+fileName
        // 저장 경로 확인

    }

    override fun onDestroy() {
        mBinding = null
        super.onDestroy()
    }
}