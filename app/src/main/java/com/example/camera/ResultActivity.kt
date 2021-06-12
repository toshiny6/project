package com. example.camera

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import com.example.camera.databinding.ActivityResultBinding
import github.com.st235.lib_expandablebottombar.ExpandableBottomBar
import github.com.st235.lib_expandablebottombar.MenuItemDescriptor
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


class ResultActivity : AppCompatActivity() {
    private var mBinding : ActivityResultBinding? = null
    private  val binding get() = mBinding!!
    private var mModule: Module? = null
    var bm : Bitmap? = null;
    lateinit var bmp : Bitmap
    val REQUEST_GALLERY_IMAGE = 1 // 갤러리 이미지 불러오기
    val loadingDialog = LoadingDialog(this)
    var checkpreview : Boolean = false
    var checkphoto : Boolean = false

    var origianlBrightness : Int =0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivInput.isVisible = false
        binding.ivOutput.isVisible = false
        binding.expandableBottomBar.isVisible = false
//        binding.btnSave.isEnabled = true
//        binding.btnSave.isVisible = true
//        binding.btnGallery2.isEnabled = true
//        binding.btnGallery2.isVisible = true
        loadModel()

        class logic(context: Context?) :
                AsyncTask<Bitmap?, Bitmap?, Bitmap?>() {
            private var mContext: Context? = null

            init {
                mContext = context
            }

            override fun doInBackground(vararg params: Bitmap?): Bitmap? {
                var img : Bitmap? = null
                try {
                    var x: Int = 0
                    var y: Int = 0
                    var width: Int = params[0]!!.width
                    var height: Int = params[0]!!.height


                    val floatBuffer = Tensor.allocateFloatBuffer(3 * width * height)
                    if (params[0] != null) {
                        val pixelsCount = height * width
                        val pixels = IntArray(pixelsCount)
                        var outBufferOffset = 0
                        params[0]!!.getPixels(pixels, 0, width, x, y, width, height)
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

                    // Create empty bitmap in ARGB format
                    bmp = width?.let { Bitmap.createBitmap(it, height, Bitmap.Config.ARGB_8888) }
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
                        img = bmp
                        publishProgress(bmp)
                    }
                } catch(e:Exception) {
                    e.printStackTrace()
                }
                return img
            }

            override fun onProgressUpdate(vararg bmp: Bitmap?) {
                //binding.btnConvert.isVisible = false
                //binding.ivOutput.isVisible = true
//                binding.btnSave.isEnabled = true
//                binding.btnSave.isVisible = true
//                binding.btnGallery2.isEnabled = true
//                binding.btnGallery2.isVisible = true
                binding.ivInput.isVisible = true
                binding.expandableBottomBar.isVisible = true
                binding.ivInput.setImageBitmap(bmp[0])
            }

            override fun onPostExecute(result: Bitmap?) {
                loadingDialog.dismissDialog()
            }

            override fun onPreExecute() {
                loadingDialog.startLoadingDialog()
            }
        }



        if (intent.hasExtra("filepath")) {
           // Toast.makeText(this, intent.getStringExtra("filepath") ,Toast.LENGTH_SHORT).show()
            bm = BitmapFactory.decodeFile(intent.getStringExtra("filepath"))
            var resol : Int = 720
            Log.d("intent", intent.getStringExtra("filepath").toString())
            if (intent.hasExtra("resol"))
                    resol  = intent.getIntExtra("resol",720)
            if(bm!!.width !=resol || bm!!.height != resol) {
                //블러 강도 결정
                var blurRadius: Int = 1 //.toFloat()
                bm = blur(getApplicationContext(), bm!!, blurRadius)
                bm = Bitmap.createScaledBitmap(bm!!, resol, resol, true)
            }
            origianlBrightness = calculateBrightness(bm!!)
            if (intent.hasExtra("preview"))
            {
            checkpreview = intent.getBooleanExtra("preview",true)
            checkphoto = intent.getBooleanExtra("photo",false)

            if (checkphoto) {
                var task = logic(this)
                task.execute(bm)
            }
            else {
                if (checkpreview) {
                    var task = logic(this)
                    task.execute(bm)
                    
                } else {
                    binding.ivInput.isVisible = true
                    binding.ivInput.setImageBitmap(bm)
                    binding.expandableBottomBar.isVisible = true
                    bmp = BitmapFactory.decodeFile(intent.getStringExtra("filepath"))

                }
            }
            }

        }
        else {
            Toast.makeText(this, "filepathError!", Toast.LENGTH_SHORT).show()
        }

        val colorView: View = findViewById(R.id.color)
        val bottomBar: ExpandableBottomBar = findViewById(R.id.expandable_bottom_bar)

        colorView.setBackgroundColor(ColorUtils.setAlphaComponent(Color.GRAY, 60))

        val menu = bottomBar.menu

        menu.add(
            MenuItemDescriptor.Builder(
                this,
                R.id.new_gallery,
                R.drawable.ic_photo_library_black_24dp,
                R.string.gallery, Color.MAGENTA
            )
                .build()
        )

        menu.add(
            MenuItemDescriptor.Builder(
                this,
                R.id.new_detail,
                R.drawable.ic_info_black_24dp,
                R.string.detail, Color.YELLOW
            )
                .build()
        )

        menu.add(
            MenuItemDescriptor.Builder(
                this,
                R.id.new_save,
                R.drawable.ic_save_alt_black_24dp,
                R.string.save,
                Color.parseColor("#58a5f0")
            )
                .build()
        )

        bottomBar.onItemSelectedListener = { v, i, _ ->
            if(i.text == "gallery"){
                goToAlbum()
            }
            else if(i.text == "save"){
                savePhoto(bmp)
                Toast.makeText(this,"Saved!",Toast.LENGTH_SHORT).show()
            }
            else if(i.text=="details"){
                show()
            }

        }

        bottomBar.onItemReselectedListener = { _, i, _ ->
            if(i.text == "gallery"){
                goToAlbum()
            }
            else if(i.text == "save"){
                savePhoto(bmp)
                Toast.makeText(this,"Saved!",Toast.LENGTH_SHORT).show()
            }
            else if(i.text=="details"){
                show()
            }
        }

//        binding.btnGallery2.setOnClickListener {
//            // 사진 불러오는 함수 실행
//            goToAlbum()
//        }
//
//            binding.btnSave.setOnClickListener{
//                savePhoto(bmp)
//                Toast.makeText(this,"Saved!",Toast.LENGTH_SHORT).show()
//            }


    }

    fun show() {


        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle("Details")

        if (checkphoto) {
            builder.setMessage("Filepath : " + intent.getStringExtra("filepath").toString() + "\nResolution : " + bmp.height.toString() + "x" + bmp.width.toString()  + "\nOriginal Brightness : "+ origianlBrightness.toString()+"\nBrightness : "+ calculateBrightness(bmp).toString())
        }
        else {
            if (checkpreview) {
                builder.setMessage("Filepath : " + intent.getStringExtra("filepath").toString() + "\nResolution : " + bmp.height.toString() + "x" + bmp.width.toString()  + "\nOriginal Brightness : "+ origianlBrightness.toString()+"\nBrightness : "+ calculateBrightness(bmp).toString())

            } else {
                builder.setMessage("Filepath : " + intent.getStringExtra("filepath").toString() + "\nResolution : " + bmp.height.toString() + "x" + bmp.width.toString()  + "\nBrightness : "+ calculateBrightness(bmp).toString())

            }
        }

        builder.setPositiveButton("확인",
                object : DialogInterface.OnClickListener {
                    override fun onClick(dialog: DialogInterface?, which: Int) {
                       // Toast.makeText(applicationContext, "예를 선택했습니다.", Toast.LENGTH_LONG).show()
                    }
                })
       // builder.setNegativeButton("아니오",
       //         object : DialogInterface.OnClickListener {
       //             override fun onClick(dialog: DialogInterface?, which: Int) {
       //                 Toast.makeText(applicationContext, "아니오를 선택했습니다.", Toast.LENGTH_LONG).show()
        //            }
        //        })
        builder.show()
    }

    fun calculateBrightnessEstimate(bitmap: Bitmap, pixelSpacing: Int): Int {
        var R = 0
        var G = 0
        var B = 0
        val height = bitmap.height
        val width = bitmap.width
        var n = 0
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        var i = 0
        while (i < pixels.size) {
            val color = pixels[i]
            R += Color.red(color)
            G += Color.green(color)
            B += Color.blue(color)
            n++
            i += pixelSpacing
        }
        return (R + B + G) / (n * 3)
    }

    fun calculateBrightness(bitmap: Bitmap): Int {
        return calculateBrightnessEstimate(bitmap, 1)
    }

    private  fun goToAlbum() {

        val intent = Intent(Intent.ACTION_PICK)
        intent.type = MediaStore.Images.Media.CONTENT_TYPE
        startActivityForResult(intent, REQUEST_GALLERY_IMAGE)
    }


    private fun loadModel(){
        try{
            mModule = Module.load(assetFilePath(this, "Bright_2_model.pt"))
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
        val folderPath = "$absolutePath/Pictures/Result/"
        val timestamp =  SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val fileName = "${timestamp}.jpeg"
        val folder = File(folderPath)
        if(!folder.isDirectory) {//현재 해당 경로에 폴더가 존재하지 않는다면
            folder.mkdirs()
        }

        checkpreview = intent.getBooleanExtra("preview",true)
        checkphoto = intent.getBooleanExtra("photo",false)
        // 앨범/프리뷰 확인
        // 프리뷰 -> 인텐트 옮길때 받은 filepath에 저장하기
        // 앨범 -> folderPath+ fileName에 저장하기
        if(checkphoto)
        {
            val out =  FileOutputStream(folderPath + fileName)
            bitmap.compress(Bitmap.CompressFormat.JPEG,100,out)
            sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://"+folderPath+fileName)))
        }
        else
        {
            val out =  FileOutputStream(intent.getStringExtra("filepath"))
            bitmap.compress(Bitmap.CompressFormat.JPEG,100,out)
            sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://"+intent.getStringExtra("filepath"))))
        }
        // 실제적인 저장 처리
//        val out =  FileOutputStream(folderPath + fileName)
//        bitmap.compress(Bitmap.CompressFormat.JPEG,100,out)
//        sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://"+folderPath+fileName)))
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
        else return sentBitmap
    }

    fun RemoveNoise(bmap: Bitmap): Bitmap? {
        for (x in 0 until bmap.width) {
            for (y in 0 until bmap.height) {
                val pixel = bmap.getPixel(x, y)
                val R = Color.red(pixel)
                val G = Color.green(pixel)
                val B = Color.blue(pixel)
                if (R < 162 && G < 162 && B < 162) bmap.setPixel(
                    x,
                    y,
                    Color.BLACK
                )
            }
        }
        for (x in 0 until bmap.width) {
            for (y in 0 until bmap.height) {
                val pixel = bmap.getPixel(x, y)
                val R = Color.red(pixel)
                val G = Color.green(pixel)
                val B = Color.blue(pixel)
                if (R > 162 && G > 162 && B > 162) bmap.setPixel(
                    x,
                    y,
                    Color.WHITE
                )
            }
        }
        return bmap
    }
}