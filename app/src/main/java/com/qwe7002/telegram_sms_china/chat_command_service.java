package com.qwe7002.telegram_sms_china;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.qwe7002.telegram_sms_china.config.proxy;
import com.qwe7002.telegram_sms_china.data_structure.polling_json;
import com.qwe7002.telegram_sms_china.data_structure.reply_markup_keyboard;
import com.qwe7002.telegram_sms_china.data_structure.request_message;
import com.qwe7002.telegram_sms_china.data_structure.sms_request_info;
import com.qwe7002.telegram_sms_china.static_class.log_func;
import com.qwe7002.telegram_sms_china.static_class.network_func;
import com.qwe7002.telegram_sms_china.static_class.other_func;
import com.qwe7002.telegram_sms_china.static_class.resend_func;
import com.qwe7002.telegram_sms_china.static_class.service_func;
import com.qwe7002.telegram_sms_china.static_class.sms_func;
import com.qwe7002.telegram_sms_china.static_class.ussd_func;
import com.qwe7002.telegram_sms_china.value.const_value;
import com.qwe7002.telegram_sms_china.value.notify_id;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.paperdb.Paper;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


public class chat_command_service extends Service {
    private static long offset = 0;
    private static int magnification = 1;
    private static int error_magnification = 1;
    private static SharedPreferences sharedPreferences;
    private static int send_sms_next_status = SEND_SMS_STATUS.STANDBY_STATUS;
    private static Thread thread_main;
    private static boolean first_request = true;

    private static class CALLBACK_DATA_VALUE {
        final static String SEND = "send";
        final static String CANCEL = "cancel";
    }
    private static class SEND_SMS_STATUS {
        static final int STANDBY_STATUS = -1;
        static final int PHONE_INPUT_STATUS = 0;
        static final int MESSAGE_INPUT_STATUS = 1;
        static final int WAITING_TO_SEND_STATUS = 2;
        static final int SEND_STATUS = 3;
    }

    private String chat_id;
    private String bot_token;
    private Context context;
    private OkHttpClient okhttp_client;
    private broadcast_receiver broadcast_receiver;
    private PowerManager.WakeLock wakelock;
    private WifiManager.WifiLock wifiLock;
    private String bot_username = "";
    private final String TAG = "chat_command_service";
    private boolean privacy_mode;

    private static boolean is_numeric(String str) {
        for (int i = 0; i < str.length(); i++) {
            System.out.println(str.charAt(i));
            if (!Character.isDigit(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }
    private void receive_handle(@NotNull JsonObject result_obj, boolean get_id_only) {
        long update_id = result_obj.get("update_id").getAsLong();
        offset = update_id + 1;
        if (get_id_only) {
            Log.d(TAG, "receive_handle: get_id_only");
            return;
        }
        String message_type = "";
        final request_message request_body = new request_message();
        request_body.chat_id = chat_id;
        JsonObject message_obj = null;

        if (result_obj.has("message")) {
            message_obj = result_obj.get("message").getAsJsonObject();
            message_type = message_obj.get("chat").getAsJsonObject().get("type").getAsString();
        }
        if (result_obj.has("channel_post")) {
            message_type = "channel";
            message_obj = result_obj.get("channel_post").getAsJsonObject();
        }
        String callback_data = null;
        if (result_obj.has("callback_query")) {
            message_type = "callback_query";
            JsonObject callback_query = result_obj.get("callback_query").getAsJsonObject();
            callback_data = callback_query.get("data").getAsString();
        }
        if (message_type.equals("callback_query") && send_sms_next_status != SEND_SMS_STATUS.STANDBY_STATUS) {
            int slot = Paper.book("send_temp").read("slot", -1);
            long message_id = Paper.book("send_temp").read("message_id", -1L);
            String to = Paper.book("send_temp").read("to", "");
            String content = Paper.book("send_temp").read("content", "");
            assert callback_data != null;
            if (!callback_data.equals(CALLBACK_DATA_VALUE.SEND)) {
                set_sms_send_status_standby();
                String request_uri = network_func.get_url(sharedPreferences.getString("api_address", ""), bot_token, "editMessageText");
                String dual_sim = other_func.get_dual_sim_card_display(context, slot, sharedPreferences.getBoolean("display_dual_sim_display_name", false));
                String send_content = "[" + dual_sim + context.getString(R.string.send_sms_head) + "]" + "\n" + context.getString(R.string.to) + to + "\n" + context.getString(R.string.content) + content;
                request_body.text = send_content + "\n" + context.getString(R.string.status) + context.getString(R.string.cancel_button);
                request_body.message_id = message_id;
                Gson gson = new Gson();
                String request_body_raw = gson.toJson(request_body);
                RequestBody body = RequestBody.create(request_body_raw, const_value.JSON);
                OkHttpClient okhttp_client = network_func.get_okhttp_obj(sharedPreferences.getBoolean("doh_switch", true), Paper.book("system_config").read("proxy_config", new proxy()));
                Request request = new Request.Builder().url(request_uri).method("POST", body).build();
                Call call = okhttp_client.newCall(request);
                try {
                    Response response = call.execute();
                    if (response.code() != 200 || response.body() == null) {
                        throw new IOException(String.valueOf(response.code()));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    log_func.write_log(context, "failed to send message:" + e.getMessage());
                }
                return;
            }
            int sub_id = -1;
            if (other_func.get_active_card(context) == 1) {
                slot = -1;
            } else {
                sub_id = other_func.get_sub_id(context, slot);
            }
            sms_func.send_sms(context, to, content, slot, sub_id, message_id);
            set_sms_send_status_standby();
            return;
        }
        if (message_obj == null) {
            log_func.write_log(context, "Request type is not allowed by security policy.");
            return;
        }
        JsonObject from_obj = null;
        final boolean message_type_is_private = message_type.equals("private");
        if (message_obj.has("from")) {
            from_obj = message_obj.get("from").getAsJsonObject();
            if (!message_type_is_private && from_obj.get("is_bot").getAsBoolean()) {
                Log.i(TAG, "receive_handle: receive from bot.");
                return;
            }
        }
        if (message_obj.has("chat")) {
            from_obj = message_obj.get("chat").getAsJsonObject();
        }

        assert from_obj != null;
        String from_id = from_obj.get("id").getAsString();
        if (!Objects.equals(chat_id, from_id)) {
            log_func.write_log(context, "Chat ID[" + from_id + "] not allow.");
            return;
        }
        String command = "";
        String command_bot_username = "";
        String request_msg = "";
        if (message_obj.has("text")) {
            request_msg = message_obj.get("text").getAsString();
        }
        if (message_obj.has("reply_to_message")) {
            sms_request_info save_item = Paper.book().read(message_obj.get("reply_to_message").getAsJsonObject().get("message_id").getAsString(), null);
            if (save_item != null && !request_msg.isEmpty()) {
                String phone_number = save_item.phone;
                int card_slot = save_item.card;
                send_sms_next_status = SEND_SMS_STATUS.WAITING_TO_SEND_STATUS;
                Paper.book("send_temp").write("slot", card_slot);
                Paper.book("send_temp").write("to", phone_number);
                Paper.book("send_temp").write("content", request_msg);
            }
            if (!message_type_is_private) {
                Log.i(TAG, "receive_handle: The message id could not be found, ignored.");
                return;
            }
        }
        if (message_obj.has("entities")) {
            String temp_command;
            String temp_command_lowercase;
            JsonArray entities_arr = message_obj.get("entities").getAsJsonArray();
            JsonObject entities_obj_command = entities_arr.get(0).getAsJsonObject();
            if (entities_obj_command.get("type").getAsString().equals("bot_command")) {
                int command_offset = entities_obj_command.get("offset").getAsInt();
                int command_end_offset = command_offset + entities_obj_command.get("length").getAsInt();
                temp_command = request_msg.substring(command_offset, command_end_offset).trim();
                temp_command_lowercase = temp_command.toLowerCase().replace("_", "");
                command = temp_command_lowercase;
                if (temp_command_lowercase.contains("@")) {
                    int command_at_location = temp_command_lowercase.indexOf("@");
                    command = temp_command_lowercase.substring(0, command_at_location);
                    command_bot_username = temp_command.substring(command_at_location + 1);
                }

            }
        }
        if (!message_type_is_private && privacy_mode && !command_bot_username.equals(bot_username)) {
            Log.i(TAG, "receive_handle: Privacy mode, no username found.");
            return;
        }

        Log.d(TAG, "receive_handle: " + command);
        boolean has_command = false;
        switch (command) {
            case "/help":
            case "/start":
            case "/commandlist":
                String sms_command = getString(R.string.sendsms);
                if (other_func.get_active_card(context) == 2) {
                    sms_command = getString(R.string.sendsms_dual);
                }
                sms_command += "\n" + getString(R.string.get_spam_sms);

                String ussd_command = "";
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        ussd_command = "\n" + getString(R.string.send_ussd_command);
                        if (other_func.get_active_card(context) == 2) {
                            ussd_command = "\n" + getString(R.string.send_ussd_dual_command);
                        }
                    }
                }

                if (command.equals("/commandlist")) {
                    request_body.text = (getString(R.string.available_command) + "\n" + sms_command + ussd_command).replace("/", "");
                    break;
                }

                String result = getString(R.string.system_message_head) + "\n" + getString(R.string.available_command) + "\n" + sms_command + ussd_command;

                if (!message_type_is_private && privacy_mode && !bot_username.equals("")) {
                    result = result.replace(" -", "@" + bot_username + " -");
                }
                request_body.text = result;
                has_command = true;
                break;
            case "/ping":
            case "/getinfo":
                String card_info = "";
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                    card_info = "\nSIM: " + other_func.get_sim_display_name(context, 0);
                    if (other_func.get_active_card(context) == 2) {
                        card_info = "\nSIM1: " + other_func.get_sim_display_name(context, 0) + "\nSIM2: " + other_func.get_sim_display_name(context, 1);
                    }
                }
                String spam_count = "";
                ArrayList<String> spam_list = Paper.book().read("spam_sms_list", new ArrayList<>());
                if (spam_list.size() != 0) {
                    spam_count = "\n" + getString(R.string.spam_count_title) + spam_list.size();
                }
                request_body.text = getString(R.string.system_message_head) + "\n" + context.getString(R.string.current_battery_level) + get_battery_info() + "\n" + getString(R.string.current_network_connection_status) + get_network_type() + spam_count + card_info;
                has_command = true;
                break;
            case "/log":
                String[] cmd_list = request_msg.split(" ");
                int line = 10;
                if (cmd_list.length == 2 && is_numeric(cmd_list[1])) {
                    assert cmd_list[1] != null;
                    //noinspection ConstantConditions
                    int line_command = Integer.getInteger(cmd_list[1]);
                    if (line_command > 50) {
                        line_command = 50;
                    }
                    line = line_command;
                }
                request_body.text = getString(R.string.system_message_head) + log_func.read_log(context, line);
                has_command = true;
                break;
            case "/sendussd":
            case "/sendussd1":
            case "/sendussd2":
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    //if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                        String[] command_list = request_msg.split(" ");
                        int sub_id = -1;
                        if (other_func.get_active_card(context) == 2) {
                            if (command.equals("/sendussd2")) {
                                sub_id = other_func.get_sub_id(context, 1);
                            }
                        }
                        if (command_list.length == 2) {
                            ussd_func.send_ussd(context, command_list[1], sub_id);
                            return;
                        }
                    //}
                }
                request_body.text = context.getString(R.string.system_message_head) + "\n" + getString(R.string.unknown_command);
                break;
            case "/getspamsms":
                ArrayList<String> spam_sms_list = Paper.book().read("spam_sms_list", new ArrayList<>());
                if (spam_sms_list.size() == 0) {
                    request_body.text = context.getString(R.string.system_message_head) + "\n" + getString(R.string.no_spam_history);
                    break;
                }
                new Thread(() -> {
                    if (network_func.check_network_status(context)) {
                        OkHttpClient okhttp_client = network_func.get_okhttp_obj(sharedPreferences.getBoolean("doh_switch", true), Paper.book("system_config").read("proxy_config", new proxy()));
                        for (String item : spam_sms_list) {
                            request_message send_sms_request_body = new request_message();
                            send_sms_request_body.chat_id = chat_id;
                            send_sms_request_body.text = item;
                            String request_uri = network_func.get_url(sharedPreferences.getString("api_address", ""), bot_token, "sendMessage");
                            String request_body_json = new Gson().toJson(send_sms_request_body);
                            RequestBody body = RequestBody.create(request_body_json, const_value.JSON);
                            Request request_obj = new Request.Builder().url(request_uri).method("POST", body).build();
                            Call call = okhttp_client.newCall(request_obj);
                            call.enqueue(new Callback() {
                                @Override
                                public void onFailure(@NotNull Call call, @NotNull IOException e) {
                                    Log.d(TAG, "onFailure: " + e.getMessage());
                                    e.printStackTrace();
                                    log_func.write_log(context, e.getMessage());
                                }

                                @Override
                                public void onResponse(@NotNull Call call, @NotNull Response response) {
                                    Log.d(TAG, "onResponse: " + response.code());
                                }
                            });
                            ArrayList<String> resend_list_local = Paper.book().read("spam_sms_list", new ArrayList<>());
                            resend_list_local.remove(item);
                            Paper.book().write("spam_sms_list", resend_list_local);
                        }
                    }
                    log_func.write_log(context, "Send spam message is complete.");
                }).start();
                return;
            case "/sendsms":
            case "/sendsms1":
            case "/sendsms2":
                String[] msg_send_list = request_msg.split("\n");
                if (msg_send_list.length > 2) {
                    String msg_send_to = other_func.get_send_phone_number(msg_send_list[1]);
                    if (other_func.is_phone_number(msg_send_to)) {
                        StringBuilder msg_send_content = new StringBuilder();
                        for (int i = 2; i < msg_send_list.length; ++i) {
                            if (msg_send_list.length != 3 && i != 2) {
                                msg_send_content.append("\n");
                            }
                            msg_send_content.append(msg_send_list[i]);
                        }
                        if (other_func.get_active_card(context) == 1) {
                            sms_func.send_sms(context, msg_send_to, msg_send_content.toString(), -1, -1);
                            return;
                        }
                        int send_slot = -1;
                        if (other_func.get_active_card(context) > 1) {
                            send_slot = 0;
                            if (command.equals("/sendsms2")) {
                                send_slot = 1;
                            }
                        }
                        int sub_id = other_func.get_sub_id(context, send_slot);
                        if (sub_id != -1) {
                            sms_func.send_sms(context, msg_send_to, msg_send_content.toString(), send_slot, sub_id);
                            return;
                        }
                    }
                } else {
                    send_sms_next_status = SEND_SMS_STATUS.PHONE_INPUT_STATUS;
                    int send_slot = -1;
                    if (other_func.get_active_card(context) > 1) {
                        send_slot = 0;
                        if (command.equals("/sendsms2")) {
                            send_slot = 1;
                        }
                    }
                    Paper.book("send_temp").write("slot", send_slot);
                }
                request_body.text = "[" + context.getString(R.string.send_sms_head) + "]" + "\n" + getString(R.string.failed_to_get_information);
                break;
            default:
                if (!message_type_is_private) {
                    Log.i(TAG, "receive_handle: The conversation is not Private and does not prompt an error.");
                    return;
                }
                request_body.text = context.getString(R.string.system_message_head) + "\n" + getString(R.string.unknown_command);
                break;
        }

        if (has_command) {
            set_sms_send_status_standby();
        }
        if (!has_command && send_sms_next_status != -1) {
            Log.i(TAG, "receive_handle: Enter the interactive SMS sending mode.");
            String dual_sim = "";
            int send_slot_temp = Paper.book("send_temp").read("slot", -1);
            if (send_slot_temp != -1) {
                dual_sim = "SIM" + (send_slot_temp + 1) + " ";
            }
            String head = "[" + dual_sim + context.getString(R.string.send_sms_head) + "]";
            String result_send = getString(R.string.failed_to_get_information);
            Log.d(TAG, "Sending mode status: " + send_sms_next_status);
            switch (send_sms_next_status) {
                case SEND_SMS_STATUS.PHONE_INPUT_STATUS:
                    send_sms_next_status = SEND_SMS_STATUS.MESSAGE_INPUT_STATUS;
                    result_send = getString(R.string.enter_number);
                    break;
                case SEND_SMS_STATUS.MESSAGE_INPUT_STATUS:
                    String temp_to = other_func.get_send_phone_number(request_msg);
                    if (other_func.is_phone_number(temp_to)) {
                        Paper.book("send_temp").write("to", temp_to);
                        result_send = getString(R.string.enter_content);
                        send_sms_next_status = SEND_SMS_STATUS.WAITING_TO_SEND_STATUS;
                    } else {
                        set_sms_send_status_standby();
                        result_send = getString(R.string.unable_get_phone_number);
                    }
                    break;
                case SEND_SMS_STATUS.WAITING_TO_SEND_STATUS:
                    Paper.book("send_temp").write("content", request_msg);
                    reply_markup_keyboard.keyboard_markup keyboardMarkup = new reply_markup_keyboard.keyboard_markup();
                    ArrayList<ArrayList<reply_markup_keyboard.InlineKeyboardButton>> inlineKeyboardButtons = new ArrayList<>();
                    inlineKeyboardButtons.add(reply_markup_keyboard.get_inline_keyboard_obj(context.getString(R.string.send_button), CALLBACK_DATA_VALUE.SEND));
                    inlineKeyboardButtons.add(reply_markup_keyboard.get_inline_keyboard_obj(context.getString(R.string.cancel_button), CALLBACK_DATA_VALUE.CANCEL));
                    keyboardMarkup.inline_keyboard = inlineKeyboardButtons;
                    request_body.reply_markup = keyboardMarkup;
                    result_send = context.getString(R.string.to) + Paper.book("send_temp").read("to") + "\n" + context.getString(R.string.content) + Paper.book("send_temp").read("content", "");
                    send_sms_next_status = SEND_SMS_STATUS.SEND_STATUS;
                    break;
            }
            request_body.text = head + "\n" + result_send;
        }

        String request_uri = network_func.get_url(sharedPreferences.getString("api_address", ""), bot_token, "sendMessage");
        RequestBody body = RequestBody.create(new Gson().toJson(request_body), const_value.JSON);
        Request send_request = new Request.Builder().url(request_uri).method("POST", body).build();
        Call call = okhttp_client.newCall(send_request);
        final String error_head = "Send reply failed:";
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                e.printStackTrace();
                log_func.write_log(context, error_head + e.getMessage());
                resend_func.add_resend_loop(context, request_body.text);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String response_string = Objects.requireNonNull(response.body()).string();
                if (response.code() != 200) {
                    assert response.body() != null;
                    log_func.write_log(context, error_head + response.code() + " " + response_string);
                    resend_func.add_resend_loop(context, request_body.text);
                }
                if (send_sms_next_status == SEND_SMS_STATUS.SEND_STATUS) {
                    Paper.book("send_temp").write("message_id", other_func.get_message_id(response_string));
                }
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = other_func.get_notification_obj(getApplicationContext(), getString(R.string.chat_command_service_name));
        startForeground(notify_id.CHAT_COMMAND, notification);
        return START_STICKY;
    }

    @SuppressLint({"InvalidWakeLockTag", "WakelockTimeout"})
    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        Paper.init(context);
        set_sms_send_status_standby();
        sharedPreferences = context.getSharedPreferences("data", MODE_PRIVATE);
        chat_id = sharedPreferences.getString("chat_id", "");
        bot_token = sharedPreferences.getString("bot_token", "");
        okhttp_client = network_func.get_okhttp_obj(sharedPreferences.getBoolean("doh_switch", true), Paper.book("system_config").read("proxy_config", new proxy()));
        privacy_mode = sharedPreferences.getBoolean("privacy_mode", false);
        wifiLock = ((WifiManager) Objects.requireNonNull(context.getApplicationContext().getSystemService(Context.WIFI_SERVICE))).createWifiLock(WifiManager.WIFI_MODE_FULL, "bot_command_polling_wifi");
        wakelock = ((PowerManager) Objects.requireNonNull(context.getSystemService(Context.POWER_SERVICE))).newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "bot_command_polling");
        wifiLock.setReferenceCounted(false);
        wakelock.setReferenceCounted(false);

        if (!wifiLock.isHeld()) {
            wifiLock.acquire();
        }
        if (!wakelock.isHeld()) {
            wakelock.acquire();
        }

        thread_main = new Thread(new thread_main_runnable());
        thread_main.start();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(const_value.BROADCAST_STOP_SERVICE);
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        broadcast_receiver = new broadcast_receiver();
        registerReceiver(broadcast_receiver, intentFilter);
    }

    private boolean get_me() {
        OkHttpClient okhttp_client_new = okhttp_client;
        String request_uri = network_func.get_url(sharedPreferences.getString("api_address", ""), bot_token, "getMe");
        Request request = new Request.Builder().url(request_uri).build();
        Call call = okhttp_client_new.newCall(request);
        Response response;
        try {
            response = call.execute();
        } catch (IOException e) {
            e.printStackTrace();
            log_func.write_log(context, "Get username failed:" + e.getMessage());
            return false;
        }
        if (response.code() == 200) {
            String result;
            try {
                result = Objects.requireNonNull(response.body()).string();
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
            JsonObject result_obj = JsonParser.parseString(result).getAsJsonObject();
            if (result_obj.get("ok").getAsBoolean()) {
                bot_username = result_obj.get("result").getAsJsonObject().get("username").getAsString();
                Paper.book().write("bot_username", bot_username);
                Log.d(TAG, "bot_username: " + bot_username);
                log_func.write_log(context, "Get the bot username: " + bot_username);
            }
            return true;
        }
        return false;
    }

    private void set_sms_send_status_standby() {
        send_sms_next_status = SEND_SMS_STATUS.STANDBY_STATUS;
        Paper.book("send_temp").destroy();
    }

    @Override
    public void onDestroy() {
        wifiLock.release();
        wakelock.release();
        unregisterReceiver(broadcast_receiver);
        stopForeground(true);
        super.onDestroy();
    }

    @SuppressWarnings("BusyWait")
    private class thread_main_runnable implements Runnable {
        @Override
        public void run() {
            Log.d(TAG, "run: thread main start");
            if (other_func.parse_string_to_long(chat_id) < 0) {
                bot_username = Paper.book().read("bot_username", null);
                if (bot_username == null) {
                    while (!get_me()) {
                        log_func.write_log(context, "Failed to get bot Username, Wait 5 seconds and try again.");
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                Log.i(TAG, "run: The Bot Username is loaded. The Bot Username is: " + bot_username);
            }
            while (true) {
                int timeout = 5 * magnification;
                int http_timeout = timeout + 5;
                OkHttpClient okhttp_client_new = okhttp_client.newBuilder()
                        .readTimeout(http_timeout, TimeUnit.SECONDS)
                        .writeTimeout(http_timeout, TimeUnit.SECONDS)
                        .build();
                Log.d(TAG, "run: Current timeout: " + timeout + "S");
                String request_uri = network_func.get_url(sharedPreferences.getString("api_address", ""), bot_token, "getUpdates");
                polling_json request_body = new polling_json();
                request_body.offset = offset;
                request_body.timeout = timeout;
                if (first_request) {
                    request_body.timeout = 0;
                    Log.d(TAG, "run: first_request");
                }
                RequestBody body = RequestBody.create(new Gson().toJson(request_body), const_value.JSON);
                Request request = new Request.Builder().url(request_uri).method("POST", body).build();
                Call call = okhttp_client_new.newCall(request);
                Response response;
                try {
                    response = call.execute();
                    error_magnification = 1;
                } catch (IOException e) {
                    e.printStackTrace();
                    if (!network_func.check_network_status(context)) {
                        log_func.write_log(context, "No network connections available, Wait for the network to recover.");
                        error_magnification = 1;
                        magnification = 1;
                        Log.d(TAG, "run: break loop.");
                        break;
                    }
                    int sleep_time = 5 * error_magnification;
                    log_func.write_log(context, "Connection to the Telegram API service failed, try again after " + sleep_time + " seconds.");
                    magnification = 1;
                    if (error_magnification <= 59) {
                        ++error_magnification;
                    }
                    try {
                        Thread.sleep(sleep_time * 1000L);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                    continue;
                }
                if (response.code() == 200) {
                    assert response.body() != null;
                    String result;
                    try {
                        result = Objects.requireNonNull(response.body()).string();
                    } catch (IOException e) {
                        e.printStackTrace();
                        continue;
                    }
                    JsonObject result_obj = JsonParser.parseString(result).getAsJsonObject();
                    if (result_obj.get("ok").getAsBoolean()) {
                        JsonArray result_array = result_obj.get("result").getAsJsonArray();
                        for (JsonElement item : result_array) {
                            receive_handle(item.getAsJsonObject(), first_request);
                        }
                        first_request = false;
                    }
                    if (magnification <= 11) {
                        ++magnification;
                    }
                } else {
                    Log.d(TAG, "response code: " + response.code());
                    if (response.code() == 401) {
                        assert response.body() != null;
                        String result;
                        try {
                            result = Objects.requireNonNull(response.body()).string();
                        } catch (IOException e) {
                            e.printStackTrace();
                            continue;
                        }
                        JsonObject result_obj = JsonParser.parseString(result).getAsJsonObject();
                        String result_message = getString(R.string.system_message_head) + "\n" + getString(R.string.error_stop_message) + "\n" + getString(R.string.error_message_head) + result_obj.get("description").getAsString() + "\n" + "Code: " + response.code();
                        sms_func.send_fallback_sms(context, result_message, -1);
                        service_func.stop_all_service(context);
                        break;
                    }
                }
            }
        }
    }


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @NotNull
    private String get_battery_info() {
        BatteryManager batteryManager = (BatteryManager) context.getSystemService(BATTERY_SERVICE);
        assert batteryManager != null;
        int battery_level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        if (battery_level > 100) {
            Log.i(TAG, "The previous battery is over 100%, and the correction is 100%.");
            battery_level = 100;
        }
        IntentFilter intentfilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, intentfilter);
        assert batteryStatus != null;
        int charge_status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        StringBuilder battery_string_builder = new StringBuilder().append(battery_level).append("%");
        switch (charge_status) {
            case BatteryManager.BATTERY_STATUS_CHARGING:
            case BatteryManager.BATTERY_STATUS_FULL:
                battery_string_builder.append(" (").append(context.getString(R.string.charging)).append(")");
                break;
            case BatteryManager.BATTERY_STATUS_DISCHARGING:
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                switch (batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
                    case BatteryManager.BATTERY_PLUGGED_AC:
                    case BatteryManager.BATTERY_PLUGGED_USB:
                    case BatteryManager.BATTERY_PLUGGED_WIRELESS:
                        battery_string_builder.append(" (").append(context.getString(R.string.not_charging)).append(")");
                        break;
                }
                break;
        }
        return battery_string_builder.toString();
    }

    private String get_network_type() {
        String net_type = "Unknown";
        ConnectivityManager connect_manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        assert connect_manager != null;
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        assert telephonyManager != null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Network[] networks = connect_manager.getAllNetworks();
            if (networks.length != 0) {
                for (Network network : networks) {
                    NetworkCapabilities network_capabilities = connect_manager.getNetworkCapabilities(network);
                    assert network_capabilities != null;
                    if (!network_capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                        if (network_capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                            net_type = "WIFI";
                        }
                        if (network_capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                            if (network_capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_IMS)) {
                                continue;
                            }
                            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                                Log.d("get_network_type", "No permission.");
                            }
                            net_type = check_cellular_network_type(telephonyManager.getDataNetworkType());
                        }
                        if (network_capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
                            net_type = "Bluetooth";
                        }
                        if (network_capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                            net_type = "Ethernet";
                        }
                    }
                }
            }
        } else {
            NetworkInfo network_info = connect_manager.getActiveNetworkInfo();
            if (network_info == null) {
                return net_type;
            }
            switch (network_info.getType()) {
                case ConnectivityManager.TYPE_WIFI:
                    net_type = "WIFI";
                    break;
                case ConnectivityManager.TYPE_MOBILE:
                    net_type = check_cellular_network_type(network_info.getSubtype());
                    break;
            }
        }

        return net_type;
    }

    private String check_cellular_network_type(int type) {
        String net_type = "Unknown";
        switch (type) {
            case TelephonyManager.NETWORK_TYPE_NR:
                net_type = "NR";
                break;
            case TelephonyManager.NETWORK_TYPE_LTE:
                net_type = "LTE";
                break;
            case TelephonyManager.NETWORK_TYPE_HSPAP:
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
            case TelephonyManager.NETWORK_TYPE_EHRPD:
            case TelephonyManager.NETWORK_TYPE_HSDPA:
            case TelephonyManager.NETWORK_TYPE_HSUPA:
            case TelephonyManager.NETWORK_TYPE_HSPA:
            case TelephonyManager.NETWORK_TYPE_TD_SCDMA:
            case TelephonyManager.NETWORK_TYPE_UMTS:
                net_type = "3G";
                break;
            case TelephonyManager.NETWORK_TYPE_GPRS:
            case TelephonyManager.NETWORK_TYPE_EDGE:
            case TelephonyManager.NETWORK_TYPE_CDMA:
            case TelephonyManager.NETWORK_TYPE_1xRTT:
            case TelephonyManager.NETWORK_TYPE_IDEN:
                net_type = "2G";
                break;
        }
        return net_type;
    }

    private class broadcast_receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, @NotNull Intent intent) {
            Log.d(TAG, "onReceive: " + intent.getAction());
            assert intent.getAction() != null;
            switch (intent.getAction()) {
                case const_value.BROADCAST_STOP_SERVICE:
                    Log.i(TAG, "Received stop signal, quitting now...");
                    stopSelf();
                    android.os.Process.killProcess(android.os.Process.myPid());
                    break;
                case ConnectivityManager.CONNECTIVITY_ACTION:
                    if (network_func.check_network_status(context)) {
                        if (!thread_main.isAlive()) {
                            log_func.write_log(context, "Network connections has been restored.");
                            thread_main = new Thread(new thread_main_runnable());
                            thread_main.start();
                        }
                    }
                    break;
            }
        }
    }

}

