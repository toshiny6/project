package com. example.camera

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.example.camera.databinding.ActivityResultBinding
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt


class ResultActivity : AppCompatActivity() {
    private var mBinding : ActivityResultBinding? = null
    private  val binding get() = mBinding!!
    private var mModule: Module? = null
    var bm : Bitmap? = null;
    val REQUEST_GALLERY_IMAGE = 1 // 갤러리 이미지 불러오기

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivProgress.isVisible = false
        binding.ivOutput.isVisible = false

        loadModel()

        if (intent.hasExtra("filepath")) {
            Toast.makeText(this, intent.getStringExtra("filepath") ,Toast.LENGTH_SHORT).show()
            bm = BitmapFactory.decodeFile(intent.getStringExtra("filepath"))

            Log.d("intent", intent.getStringExtra("filepath").toString())
//            // 원본 이미지 리사이즈
           // var blurRadius : Int = 7 //.toFloat()
           // bm=blur(getApplicationContext(),bm,blurRadius)
           // bm = Bitmap.createScaledBitmap(bm,640,480,true)

            //savePhoto(bm)
            //Toast.makeText(this,intent.getStringExtra("uri"),Toast.LENGTH_LONG).show()
            //var uri : Uri = Uri.parse(intent.getStringExtra("uri"))


//             try {
//                 bm = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
//            } catch (e: FileNotFoundException) {
//                e.printStackTrace();
//            }


            if(bm!!.width !=640 || bm!!.height != 480) {
                var blurRadius: Int = 4 //.toFloat()
                bm = blur(getApplicationContext(), bm!!, blurRadius)
                bm = Bitmap.createScaledBitmap(bm!!, 640, 480, true)
            }



//            uri = bm?.let { this!!.getImageUri(getApplicationContext(), it) }!!
//
//            Log.d("uri",uri.toString())
//            Glide.with(getApplicationContext())
//                .load(uri)
//                .into(binding.ivInput)

            binding.ivInput.setImageBitmap(bm)
        }
        else {
            Toast.makeText(this, "filepathError!", Toast.LENGTH_SHORT).show()
        }

        binding.btnGallery.setOnClickListener{
            goToAlbum()
        }
        binding.btnConvert.setOnClickListener {
//            binding.ivProgress.isVisible = true
//            Glide.with(this).load(R.drawable.loading).into(binding.ivProgress)

            val thread = Thread(
                    Runnable {

                        val begin = System.nanoTime()
                        try{

                            var x: Int = 0
                            var y: Int = 0
                            var width: Int = bm!!.width
                            var height: Int = bm!!.height


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

//                            Glide.with(getApplicationContext())
//                                    .load(getImageUri(getApplicationContext(),bmp))
//                                    .into(binding.ivOutput)

                            runOnUiThread{

                                binding.btnConvert.isVisible=false
                                binding.btnGallery.isVisible=false
                                binding.ivOutput.isVisible = true
                                binding.ivOutput.setImageBitmap(bmp)
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

        }
//        if (intent.hasExtra("resulturi")) {
//
//            val resulturi : Uri = Uri.parse(intent.getStringExtra("resulturi"))
//
//            Glide.with(getApplicationContext())
//                .load(intent.getStringExtra("resulturi"))
//                .into(binding.ivOutput)
//
//        }
//        else {
//            Toast.makeText(this, "resultError!", Toast.LENGTH_SHORT).show()
//        }
        //setContentView(R.layout.activity_result)
    }

    private fun imgRotate(bmp : Bitmap) : Bitmap{
        var width : Int = bmp.getWidth();
        var height : Int = bmp.getHeight();
        val matrix = Matrix()
        matrix.postRotate(90f)

        val resizedBitmap = Bitmap.createBitmap(bmp, 0, 0, width, height, matrix, true)
        bmp.recycle()

        return resizedBitmap
    }


    private  fun goToAlbum() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = MediaStore.Images.Media.CONTENT_TYPE
        startActivityForResult(intent, REQUEST_GALLERY_IMAGE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        //startActivityForResult를 통해서 기본 카메라 앱으로부터 받아온 사진 결과 값
        super.onActivityResult(requestCode, resultCode, data)

        if(requestCode == REQUEST_GALLERY_IMAGE && resultCode == Activity.RESULT_OK)
        {
            var uri: Uri? = data?.data

            try {
                bm = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
            } catch (e: FileNotFoundException) {
                e.printStackTrace();
            }

            if(bm!!.width !=640 || bm!!.height != 480) {
                var blurRadius: Int = 4 //.toFloat()
                bm = blur(getApplicationContext(), bm!!, blurRadius)
                bm = Bitmap.createScaledBitmap(bm!!, 640, 480, true)
            }


            //uri = bm?.let { this!!.getImageUri(getApplicationContext(), it) }!!

//            Glide.with(getApplicationContext())
//                    .load(uri)
//                    .into(binding.ivInput)
            binding.ivInput.setImageBitmap(bm)
        }

    }


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

    }