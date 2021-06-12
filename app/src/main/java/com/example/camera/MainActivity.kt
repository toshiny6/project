package com.example.camera

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.*
import android.hardware.camera2.*
import android.media.ExifInterface
import android.media.Image
import android.media.ImageReader
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import com.example.camera.databinding.ActivityMainBinding
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.TedPermission
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import java.io.*
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
    var checkpreview : Boolean = false
    var checkphoto : Boolean = false

    lateinit var filepath : String
    lateinit var convertfilepath : String
    lateinit var _bm : Bitmap
    lateinit var prebm : Bitmap

    var resol : Int = 720
    var cameraid : Boolean = true
    //추가
    private var mTimerTask: TimerTask? = null

    val timer = Timer()
    val TT: TimerTask = object : TimerTask() {
        override fun run() {

            prebm = binding.texture.getBitmap()!!
            val width = prebm.width
            val height = prebm.height
            val pixels = IntArray(prebm.height * prebm.width)
            prebm.getPixels(pixels, 0, width, 0, 0, width, height)
            prebm = blur(getApplicationContext(), prebm, 4)
            prebm = Bitmap.createScaledBitmap(prebm, 200, 200, true)


            //      val thread = Thread(
            //             Runnable {

            val begin = System.nanoTime()
            try{

                var x: Int = 0
                var y: Int = 0
                var width: Int = prebm!!.width
                var height: Int = prebm!!.height

                val floatBuffer = Tensor.allocateFloatBuffer(3 * width * height)
                if (prebm != null) {
                    val pixelsCount = height * width
                    val pixels = IntArray(pixelsCount)
                    var outBufferOffset = 0
                    prebm!!.getPixels(pixels, 0, width, x, y, width, height)
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


                // Create empty bitma`p in ARGB format
                var bmp=width?.let { Bitmap.createBitmap(it, height, Bitmap.Config.ARGB_8888) }
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

                runOnUiThread{
                    binding.ivPre.setImageBitmap(bmp)
                }
                val end = System.nanoTime()
                Log.d("Elapsed time in nanoseconds: ", "${end-begin}")
            }catch(e:Exception)
            {
                e.printStackTrace()
            }
            //                    }
            //              )
//                thread.run()

            // 반복실행할 구문


        }
    }


    //카메라 프리뷰
    companion object {

        private const val TAG = "AndroidCameraApi"

        // private Button takePictureButton;
        //  private TextureView textureView;
        private val ORIENTATIONS = SparseIntArray()
        private const val REQUEST_CAMERA_PERMISSION = 200

        init {
            ORIENTATIONS.append(
                ExifInterface.ORIENTATION_NORMAL,
                0
            )
            ORIENTATIONS.append(
                ExifInterface.ORIENTATION_ROTATE_90,
                90
            )
            ORIENTATIONS.append(
                ExifInterface.ORIENTATION_ROTATE_180,
                180
            )
            ORIENTATIONS.append(
                ExifInterface.ORIENTATION_ROTATE_270,
                270
            )
        }
    }

    private var cameraId: String? = null
    protected var cameraDevice: CameraDevice? = null
    protected var cameraCaptureSessions: CameraCaptureSession? = null
    protected var captureRequest: CaptureRequest? = null
    protected var captureRequestBuilder: CaptureRequest.Builder? = null
    private var imageDimension: Size? = null
    private var imageReader: ImageReader? = null
    private val file: File? = null
    private val mFlashSupported = false
    private var mBackgroundHandler: Handler? = null
    private var mBackgroundThread: HandlerThread? = null

    var textureListener: TextureView.SurfaceTextureListener = object :
        TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(
            surface: SurfaceTexture,
            width: Int,
            height: Int
        ) {
            //open your camera here
            openCamera()
        }

        override fun onSurfaceTextureSizeChanged(
            surface: SurfaceTexture,
            width: Int,
            height: Int
        ) {
            // Transform you image captured size according to the surface width and height
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            return false
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    }
    private val stateCallback: CameraDevice.StateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            //This is called when the camera is open
            Log.e(TAG, "onOpened")
            cameraDevice = camera
            createCameraPreview()
        }

        override fun onDisconnected(camera: CameraDevice) {
            cameraDevice!!.close()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            cameraDevice!!.close()
            cameraDevice = null
        }
    }
    val captureCallbackListener: CameraCaptureSession.CaptureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            super.onCaptureCompleted(session, request, result)
            // Toast.makeText(MainActivity.this, "Saved:" + file, Toast.LENGTH_SHORT).show();
            createCameraPreview()
        }
    }

    protected fun startBackgroundThread() {
        mBackgroundThread = HandlerThread("Camera Background")
        mBackgroundThread!!.start()
        mBackgroundHandler = Handler(mBackgroundThread!!.looper)
    }

    protected fun stopBackgroundThread() {
        mBackgroundThread!!.quitSafely()
        try {
            mBackgroundThread!!.join()
            mBackgroundThread = null
            mBackgroundHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    protected fun takePicture() {
        if (null == cameraDevice) {
            Log.e(
                TAG,
                "cameraDevice is null"
            )
            return
        }
        val manager =
            getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val characteristics =
                manager.getCameraCharacteristics(cameraDevice!!.id)
            var jpegSizes: Array<Size>? = null
            if (characteristics != null) {
                jpegSizes =
                    characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        ?.getOutputSizes(ImageFormat.JPEG)
            }
            var width = resol
            var height = resol
            if (jpegSizes != null && 0 < jpegSizes.size) {
                width = resol//jpegSizes[0].width
                height = resol//jpegSizes[0].height
            }
            val reader =
                ImageReader.newInstance(width, height, ImageFormat.JPEG, 1)
            val outputSurfaces: MutableList<Surface> =
                ArrayList(2)
            outputSurfaces.add(reader.surface)
            outputSurfaces.add(Surface(binding!!.texture.surfaceTexture))
            val captureBuilder =
                cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder.addTarget(reader.surface)

            //MANUAL EXPOSURE
            captureBuilder.set(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_OFF
            );
            captureBuilder.set(
                    CaptureRequest.SENSOR_EXPOSURE_TIME,
                    1000000000L / 60
            );
            captureBuilder.set(
                    CaptureRequest.SENSOR_SENSITIVITY,
                    100
            )

            // Orientation
            val rotation = windowManager.defaultDisplay.rotation
            captureBuilder.set(
                CaptureRequest.JPEG_ORIENTATION,
                ORIENTATIONS[rotation]
            )
            Log.d("camera capture", "success");
            //경로 지정
            val absolutePath = "/storage/emulated/0/"
            val folderPath = "$absolutePath/Pictures/"

            var timestamp =  SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
            var fileName = "${timestamp}.jpg"
            var folder = File(folderPath)
            if(!folder.isDirectory) {//현재 해당 경로에 폴더가 존재하지 않는다면
                folder.mkdirs()
            }

            val file = File(
                folderPath + fileName
            )

            val readerListener: ImageReader.OnImageAvailableListener = object :
                ImageReader.OnImageAvailableListener {
                override fun onImageAvailable(reader: ImageReader) {
                    var image: Image? = null
                    try {
                        image = reader.acquireLatestImage()
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.capacity())
                        buffer[bytes]
                        save(bytes)
                    } catch (e: FileNotFoundException) {
                        e.printStackTrace()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    } finally {
                        image?.close()
                    }
                }



                @Throws(IOException::class)
                private fun save(bytes: ByteArray) {
                    var output: OutputStream? = null
                    try {
                        val options: BitmapFactory.Options = BitmapFactory.Options()
                        options.inPreferredConfig = Bitmap.Config.ARGB_8888
                        var bitmap: Bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                        var matrix = Matrix()
                        matrix.postRotate(90f);
                        bitmap =  Bitmap.createBitmap(bitmap, 0, 0, resol, resol, matrix, true);
                        val stream = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                        val currentData: ByteArray = stream.toByteArray()

                        output = FileOutputStream(file)
                        output.write(currentData)
                        //output.write(bytes)
                    } finally {
                        output?.close()
                        filepath=folderPath+fileName
                        Log.d("path",filepath)
                        //사진 찍을때 미디어스캐닝 x
                        //sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://"+filepath)))
                        }


                }

            }

            reader.setOnImageAvailableListener(readerListener, mBackgroundHandler)
            val captureListener: CameraCaptureSession.CaptureCallback = object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    super.onCaptureCompleted(session, request, result)
                    //Toast.makeText(this, "Saved:" + file, Toast.LENGTH_SHORT).show();
                    createCameraPreview()
                }
            }
            cameraDevice!!.createCaptureSession(
                outputSurfaces,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        try {
                            session.capture(
                                captureBuilder.build(),
                                captureListener,
                                mBackgroundHandler
                            )
                        } catch (e: CameraAccessException) {
                            e.printStackTrace()
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {}
                },
                mBackgroundHandler
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    protected fun createCameraPreview() {
        try {
            val texture = binding!!.texture.surfaceTexture!!
            texture.setDefaultBufferSize(imageDimension!!.width, imageDimension!!.height)
            val surface = Surface(texture)
            captureRequestBuilder =
                cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)

            captureRequestBuilder!!.addTarget(surface)
            cameraDevice!!.createCaptureSession(
                Arrays.asList(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                        //The camera is already closed
                        if (null == cameraDevice) {
                            return
                        }
                        // When the session is ready, we start displaying the preview.
                        cameraCaptureSessions = cameraCaptureSession

                        updatePreview()
                    }

                    override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                        //Toast.makeText(this, "Configuration change", Toast.LENGTH_SHORT).show();
                    }
                },
                null
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun openCamera() {
        val manager =
            getSystemService(Context.CAMERA_SERVICE) as CameraManager
        Log.e(TAG, "is camera open")
        try {
            var cameraId = manager.cameraIdList[0]
            if (cameraid==true)
                cameraId = manager.cameraIdList[0]
            else
                cameraId = manager.cameraIdList[1]
            val characteristics = manager.getCameraCharacteristics(cameraId)
            val map =
                characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
            imageDimension = map.getOutputSizes(SurfaceTexture::class.java)[0]

            // Add permission for camera and let user grant the permission
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ),
                    REQUEST_CAMERA_PERMISSION
                )
                return
            }

            manager.openCamera(cameraId, stateCallback, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
        Log.e(TAG, "openCamera X")
    }

    protected fun updatePreview() {
        if (null == cameraDevice) {
            Log.e(

                    TAG,
                    "updatePreview error, return"
            )
        }

        //MANUAL EXPOSURE
        captureRequestBuilder!!.set(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_OFF
        );
        // AE MODE OFF에서만 사용이 가능하다. ns
        captureRequestBuilder!!.set(
                CaptureRequest.SENSOR_EXPOSURE_TIME, // 각 픽셀이 빛에 노출되는 시간을 설정
                1000000000L / 60 /* 30일때 너무 밝음 */
        );

        Log.d(TAG, "nano seconds:"+(1000000000L/30));
        captureRequestBuilder!!.set(
                CaptureRequest.SENSOR_SENSITIVITY,
                100
        );
            try {
                cameraCaptureSessions!!.setRepeatingRequest(captureRequestBuilder!!.build(), null, mBackgroundHandler)
            } catch (e: CameraAccessException) {
                e.printStackTrace()
            }
    }

    private fun closeCamera() {
        if (null != cameraDevice) {
            cameraDevice!!.close()
            cameraDevice = null
        }
        if (null != imageReader) {
            imageReader!!.close()
            imageReader = null
        }
    }

    override fun onResume() {
        super.onResume()
        Log.e(TAG, "onResume")
        startBackgroundThread()
        if (binding!!.texture.isAvailable) {
            openCamera()
        } else {
            binding!!.texture.surfaceTextureListener = textureListener
        }
    }

    override fun onPause() {
        Log.e(TAG, "onPause")
        //closeCamera();
        stopBackgroundThread()
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        
        super.onCreate(savedInstanceState)
        //setContentView(R.layout.activity_main)

        mBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setPermission() // 권한을 체크하는 테스트 수행

        loadModel()


        //카메라 프리뷰
        binding!!.texture.surfaceTextureListener = textureListener
        binding!!.btnTakepicture.setOnClickListener {

            if (mTimerTask != null)
                mTimerTask!!.cancel()
            //수정
            takePicture()
            //timer.cancel()

            //////-binding.ivPre.isVisible=false
            //

            var num : Int=0
            while ((!(this::filepath.isInitialized))||filepath==null)
                num=1

        Log.d("path!",filepath)
        val nextIntent = Intent(this, ResultActivity::class.java)

        nextIntent.putExtra("filepath", filepath)
        nextIntent.putExtra("preview",checkpreview)
        checkphoto=false
            nextIntent.putExtra("photo",checkphoto)
            nextIntent.putExtra("resol",resol)

        convertfilepath=filepath
        resetField(this, "filepath")
        checkpreview = false

        startActivity(nextIntent)

            binding.ivPre.isVisible=false
    }



        binding.ivPre.isVisible=false

        binding.btnPreview.setOnClickListener {
            if(binding.ivPre.getVisibility() == View.VISIBLE)
            {
                if (mTimerTask != null)
                    mTimerTask!!.cancel()
                checkpreview=false
                binding.ivPre.isVisible = false

            }
            else
            {
                checkpreview=true
                mTimerTask = createTimerTask();
                timer.schedule(mTimerTask, 300, 500);

                binding.ivPre.isVisible = true
            }
        }

        binding.btnGallery.setOnClickListener {
            // 사진 불러오는 함수 실행
            goToAlbum()
            if (this::prebm.isInitialized)
                Log.d("prebm",(prebm.width).toString())
        }

        binding.re1080.setOnClickListener {
            resol =1080
        }
        binding.re720.setOnClickListener {
            resol =720
        }
        binding.re960.setOnClickListener {
            resol =960
        }
        binding.button.setOnClickListener {
            cameraid=!cameraid
            if (binding!!.texture.isAvailable)
                closeCamera()
            openCamera()

        }

    }

    fun createTimerTask() : TimerTask{
        val timerTask: TimerTask = object : TimerTask()  {
            override fun run() {
                prebm = binding.texture.getBitmap()!!
                val width = prebm.width
                val height = prebm.height
                val pixels = IntArray(prebm.height * prebm.width)
                prebm.getPixels(pixels, 0, width, 0, 0, width, height)
                prebm = blur(getApplicationContext(), prebm, 3)
                prebm = Bitmap.createScaledBitmap(prebm, 240, 240, true)

                //      val thread = Thread(
                //             Runnable {

                val begin = System.nanoTime()
                try{

                    var x: Int = 0
                    var y: Int = 0
                    var width: Int = prebm!!.width
                    var height: Int = prebm!!.height


                    val floatBuffer = Tensor.allocateFloatBuffer(3 * width * height)
                    if (prebm != null) {
                        val pixelsCount = height * width
                        val pixels = IntArray(pixelsCount)
                        var outBufferOffset = 0
                        prebm!!.getPixels(pixels, 0, width, x, y, width, height)
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
                    var bmp=width?.let { Bitmap.createBitmap(it, height, Bitmap.Config.ARGB_8888) }
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

                    runOnUiThread{
                        //binding.ivPre.isVisible = true
                        binding.ivPre.setImageBitmap(bmp)
                    }
                    val end = System.nanoTime()
                    Log.d("Elapsed time in nanoseconds: ", "${end-begin}")
                }catch(e:Exception)
                {
                    e.printStackTrace()
                }
            }
        }
        return timerTask;
    }
    fun resetField(target: Any, fieldName: String) {
        val field = target.javaClass.getDeclaredField(fieldName)

        with (field) {
            isAccessible = true
            set(target, null)
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
    fun getUriFromPath(filePath: String): Uri {
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
        }!!
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

        if(requestCode == REQUEST_GALLERY_IMAGE && resultCode == Activity.RESULT_OK)
        {
            val uri: Uri? = data?.data
            val bitmap : Bitmap

            getPathFromUri(uri)

            val nextIntent = Intent(this, ResultActivity::class.java)
            checkpreview=true
            nextIntent.putExtra("filepath", filepath)
            nextIntent.putExtra("preview",checkpreview)
            checkphoto=true
            nextIntent.putExtra("photo",checkphoto)
            nextIntent.putExtra("resol",resol)

            // 실행 결과를 저장하여 path 반환 받으면 nextIntent에 넣어서 결과 화면으로 전송
            var bm: Bitmap = BitmapFactory.decodeFile(nextIntent.getStringExtra("filepath"))
            startActivity(nextIntent)
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

    private fun loadModel(){
        try{
            mModule = Module.load(assetFilePath(this, "Bright_2_model.pt"))
            Log.d("Model", "Model Loaded Successfully")
        } catch (e: IOException){
            Log.e("UseModel", "Load Model Failed", e)
        }
    }

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