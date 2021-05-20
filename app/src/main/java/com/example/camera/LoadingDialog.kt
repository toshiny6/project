package com.example.camera

import android.app.Activity
import android.app.AlertDialog

class LoadingDialog internal constructor(var activity: Activity) {
    var dialog: AlertDialog? = null
    fun startLoadingDialog() {
        val builder = AlertDialog.Builder(activity)
        val inflater = activity.layoutInflater
        builder.setView(inflater.inflate(R.layout.loading, null))
        builder.setCancelable(true)
        dialog = builder.create()
        dialog?.show()
    }

    fun dismissDialog() {
        dialog!!.dismiss()
    }

}