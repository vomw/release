package com.qwe7002.telegram_sms_china.static_class;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.qwe7002.telegram_sms_china.resend_service;

import java.util.ArrayList;

import io.paperdb.Paper;

public class
resend_func {
    public static void add_resend_loop(Context context, String message) {
        ArrayList<String> resend_list;
        Paper.init(context);
        resend_list = Paper.book().read("resend_list", new ArrayList<>());
        resend_list.add(message);
        Paper.book().write("resend_list", resend_list);
        start_resend(context);
    }

    public static void start_resend(Context context) {
        Intent intent = new Intent(context, resend_service.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }
}
