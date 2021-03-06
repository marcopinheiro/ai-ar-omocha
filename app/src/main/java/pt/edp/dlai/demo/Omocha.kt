package pt.edp.dlai.demo

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.cardview.widget.CardView
import pt.edp.dlai.demo.common.Globals

class Omocha : AppCompatActivity() {
    private val TAG = "MainActivity"

    private lateinit var toFloatImageNet: CardView
    private lateinit var toImageLabeler: CardView

    //private lateinit var toML: Button
    //private lateinit var toAr: Button
    //private lateinit var toSegmentation: Button

    private var globals: Globals? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_omocha)

        toFloatImageNet = findViewById(R.id.go_floatimagenet)
        toImageLabeler = findViewById(R.id.go_imagelabeler)

        //toML = findViewById(R.id.to_ml) as Button
        //toAr = findViewById(R.id.to_ar) as Button
        //toSegmentation = findViewById(R.id.to_segmentation) as Button

        toFloatImageNet.setOnClickListener(object: View.OnClickListener{
            override fun onClick(v: View?) {
                val intent = Intent(application, MLActivity::class.java)
                    .putExtra("algorithm", "Float ImageNet")
                startActivity(intent)
            }
        })
        toImageLabeler.setOnClickListener(object: View.OnClickListener{
            override fun onClick(v: View?) {
                val intent = Intent(application, MLActivity::class.java)
                    .putExtra("algorithm", "Image Labeler")
                startActivity(intent)
            }
        })
        /*
        toML.setOnClickListener(object: View.OnClickListener {
            override fun onClick(v: View?) {
                val intent = Intent(application, MLActivity::class.java)
                startActivity(intent)
            }
        })

        toAr.setOnClickListener(object:View.OnClickListener {
            override fun onClick(v: View?) {
                val intent = Intent(application, SceneformActivity::class.java)
                startActivity(intent)
            }
        })

        toSegmentation.setOnClickListener(object:View.OnClickListener {
            override fun onClick(v: View?) {
                val intent = Intent(application, SegmentationActivity::class.java)
                startActivity(intent)
            }
        })
        */
        initializeGlobals()
    }

    private fun initializeGlobals(){
        if (globals == null){
            globals = application as Globals
            globals!!.initialize(this)
        }
    }

}
