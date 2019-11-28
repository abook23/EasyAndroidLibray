package com.android.easy.app.mvp;

import android.content.Context;

public interface IBaseView {
    void showLoading();

    void dismissLoading();

    void showToast(String msg);

    Context getContext();
}
