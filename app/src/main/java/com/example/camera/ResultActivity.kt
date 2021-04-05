package com.example.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.example.camera.databinding.ActivityMainBinding
import com.example.camera.databinding.ActivityResultBinding

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
            binding.ivInput.setImageBitmap(bm);
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

}