package com.abook23.tv.ui.cache

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import com.abook23.tv.App
import com.abook23.tv.R
import com.abook23.tv.ben.CacheVideoBean
import com.abook23.tv.ben.MovieBen
import com.abook23.tv.ben.PlayData
import com.abook23.tv.dao.CacheVideoBeanDao
import com.abook23.tv.service.CacheVideoService
import com.abook23.tv.ui.MovieInfoActivity
import com.abook23.tv.ui.PlayActivity
import com.android.easy.app.base.BaseAppCompatActivity
import com.android.easy.base.util.AndroidUtils
import com.android.easy.base.widget.ButtonProgress
import com.android.easy.dialog.EasyDialog
import com.android.easy.play.DownloadVideoManager
import com.android.easy.play.MovieInfo
import com.bumptech.glide.Glide
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import kotlinx.android.synthetic.main.activity_cache_sub2.*
import java.io.File

class CacheSub2Activity : BaseAppCompatActivity() {

    private var checkButtonVisibility = false
    private var checkAll = false
    private var checkSet = HashSet<CacheVideoBean>()
    private var movieManagerView: View? = null

    lateinit var movieBen: MovieBen
    lateinit var adapter: Adapter
    var cacheVideoService: CacheVideoService? = null


    companion object {
        @JvmStatic
        fun start(context: Context, movieBen: MovieBen) {
            val starter = Intent(context, CacheSub2Activity::class.java)
                    .putExtra("data", movieBen)
            context.startActivity(starter)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cache_sub2)

        movieBen = intent.getSerializableExtra("data") as MovieBen

        defaultAppBar.setTitle(movieBen.v_name)
        defaultAppBar.setAppbarRightView("管理", object : View.OnClickListener {
            override fun onClick(v: View?) {
                checkButtonVisibility = !checkButtonVisibility
                if (checkButtonVisibility) {
                    movieManagerView?.visibility = View.VISIBLE
                } else {
                    movieManagerView?.visibility = View.GONE
                }
                adapter.notifyDataSetChanged()
            }
        })

        adapter = Adapter()
        recyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        recyclerView.adapter = adapter
        loadRequestData()
        floatingButton.setOnClickListener {
            MovieInfoActivity.starPlayActivity(this,movieBen)
        }
        addBottomView()
        cacheVideoService = App.getCacheVideoService()
    }

    fun loadRequestData() {
        val list = App.getDaoSession().cacheVideoBeanDao.queryBuilder()
                .where(CacheVideoBeanDao.Properties.V_id.eq(movieBen.v_id))
                .list()
        adapter.setNewData(list)
    }

    /**
     * 添加底部 view
     */
    private fun addBottomView() {
        movieManagerView = addContentBottomView(R.layout.movie_manager)
        movieManagerView?.visibility = View.GONE
        var checkBox = movieManagerView?.findViewById<CheckBox>(R.id.checkBox)
        var deleteView = movieManagerView?.findViewById<TextView>(R.id.deleteView)
        checkBox?.setOnClickListener {
            checkAll = checkBox.isChecked
            adapter.notifyDataSetChanged()
        }
        deleteView?.setOnClickListener {
            EasyDialog.Builder()
                    .title("提示")
                    .content("是否删除下载缓存")
                    .progress(true)
                    .positive("确定", object : EasyDialog.OnClickListener {
                        override fun onClick(dialog: EasyDialog, view: View) {
                            deleteCache()
                            dialog.dismiss()
                        }
                    })
                    .build().show(supportFragmentManager)

        }
    }

    fun deleteCache() {
        if (checkAll) {
            App.getDaoSession().cacheVideoBeanDao.deleteAll()
            AndroidUtils.deleteDirectory(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES))
        } else {
            checkSet.forEach {
                val cacheVideoBean = it
                App.getDaoSession().cacheVideoBeanDao.delete(cacheVideoBean)
                val videoPath = DownloadVideoManager.getCacheLocalPath(context, cacheVideoBean.url)
                AndroidUtils.deleteDirectory(File(videoPath))
            }
        }
        loadRequestData()
    }

    inner class Adapter() : BaseQuickAdapter<CacheVideoBean, BaseViewHolder>(R.layout.item_cache_video_sub) {
        override fun convert(helper: BaseViewHolder, item: CacheVideoBean) {
            var buttonProgress = helper.getView<ButtonProgress>(R.id.buttonProgress)
            val text1 = helper.getView<TextView>(R.id.text1)
            val text2 = helper.getView<TextView>(R.id.text2)
            helper.getView<TextView>(R.id.text0).text = "第 ${item.v_num + 1} 集"
            Glide.with(mContext).load(movieBen.v_pic).into(helper.getView(R.id.imageView))

            text1.text = "已缓存${getRate(item.download_progress.toFloat(), item.download_max.toFloat())}"
            text2.text = "已看至${if (item.play_rate == null) "0" else item.play_rate}%"
            buttonProgress.setMax(item.download_max)
            buttonProgress.setProgress(item.download_progress)

            if (item.download_complete) {
                buttonProgress.text = "完成"
            } else if (item.download_pause) {
                buttonProgress.text = "开始"
            } else {
                buttonProgress.text = "暂停"
            }

            helper.itemView.setOnClickListener {
                val list = ArrayList<MovieInfo>()
                data.forEach {
                    val movieInfo = MovieInfo("第${it.v_num}集", it.url, it.v_num + 1)
                    list.add(movieInfo)
                }
                val playData = PlayData(movieBen.v_id, movieBen.v_name + " 第${item.v_num + 1}集", item.url, list)
                PlayActivity.starPlayActivity(this@CacheSub2Activity, playData)
            }

            buttonProgress.setOnClickListener {
                if (!item.download_pause) {
                    buttonProgress.text = "开始"
                } else {
                    buttonProgress.text = "暂停"
                }
                cacheVideoService?.pause(item.url)
            }
            cacheVideoService?.addListener(item.url, object : DownloadVideoManager.Call {
                override fun onComplete(file: File) {
                    buttonProgress.text = "完成"
                }

                override fun onProgress(progress: Long, max: Long,bytes:Long) {
                    runOnUiThread {
                        text1.text = "已缓存${getRate(progress.toFloat(), max.toFloat())}"
                        buttonProgress.setMax(item.download_max)
                        buttonProgress.setProgress(item.download_progress)
                    }

                }

                override fun onStart(file: File, max: Long) {

                }
            })

            val checkBoxButton = helper.getView<CheckBox>(R.id.checkBoxButton)
            checkBoxButton.setOnClickListener {
                if (checkBoxButton.isChecked) {
                    checkSet.add(item)
                } else {
                    checkSet.remove(item)
                }
            }
            if (checkButtonVisibility) {
                checkBoxButton.visibility = View.VISIBLE
            } else {
                checkBoxButton.visibility = View.GONE
            }
            checkBoxButton.isChecked = checkAll
        }

        fun getRate(progress: Float, max: Float): String {
            if (max > 0) {
                return String.format("%.2f%s", progress / max * 100, "%")
            }
            return "0.00%"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cacheVideoService?.clearListener()
    }
}