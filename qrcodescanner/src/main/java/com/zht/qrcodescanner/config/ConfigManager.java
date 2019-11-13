package com.zht.qrcodescanner.config;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by ZhangHaitao on 2019/11/8
 */
public class ConfigManager {

    //一维码（商品）
    public static final String KEY_DECODE_1D_PRODUCT = "preferences_decode_1D_product";
    //一维码（工业）
    public static final String KEY_DECODE_1D_INDUSTRIAL = "preferences_decode_1D_industrial";
    //二维码
    public static final String KEY_DECODE_QR = "preferences_decode_QR";
    //Data Matrix
    public static final String KEY_DECODE_DATA_MATRIX = "preferences_decode_Data_Matrix";
    //Aztec
    public static final String KEY_DECODE_AZTEC = "preferences_decode_Aztec";
    //PDF417（测试）
    public static final String KEY_DECODE_PDF417 = "preferences_decode_PDF417";

    //自定义搜索网址
    public static final String KEY_CUSTOM_PRODUCT_SEARCH = "preferences_custom_product_search";

    //发出哔声
    public static final String KEY_PLAY_BEEP = "preferences_play_beep";
    //震动
    public static final String KEY_VIBRATE = "preferences_vibrate";
    //复制到剪切板
    public static final String KEY_COPY_TO_CLIPBOARD = "preferences_copy_to_clipboard";
    //闪光灯模式（是否开启闪光灯）
    public static final String KEY_FRONT_LIGHT_MODE = "preferences_front_light_mode";
    //批量扫描模式
    public static final String KEY_BULK_MODE = "preferences_bulk_mode";
    //保留重复记录
    public static final String KEY_REMEMBER_DUPLICATES = "preferences_remember_duplicates";
    //存入历史记录
    public static final String KEY_ENABLE_HISTORY = "preferences_history";
    //检索更多信息
    public static final String KEY_SUPPLEMENTAL = "preferences_supplemental";
    //自动对焦
    public static final String KEY_AUTO_FOCUS = "preferences_auto_focus";
    //反色（适用于黑背景白条码）
    public static final String KEY_INVERT_SCAN = "preferences_invert_scan";
    //搜索引擎国别
    public static final String KEY_SEARCH_COUNTRY = "preferences_search_country";
    //不自动旋转
    public static final String KEY_DISABLE_AUTO_ORIENTATION = "preferences_orientation";
    //不持续对焦（使用标准对焦模式）
    public static final String KEY_DISABLE_CONTINUOUS_FOCUS = "preferences_disable_continuous_focus";
    //不曝光
    public static final String KEY_DISABLE_EXPOSURE = "preferences_disable_exposure";
    //不使用距离测量
    public static final String KEY_DISABLE_METERING = "preferences_disable_metering";
    //不进行条形码场景匹配
    public static final String KEY_DISABLE_BARCODE_SCENE_MODE = "preferences_disable_barcode_scene_mode";
    //自动打开网页
    public static final String KEY_AUTO_OPEN_WEB = "preferences_auto_open_web";

    private Map<String, Object> configList;

    private ConfigManager() {
        initDefaultConfig();
    }

    private static ConfigManager mInstance;

    public static ConfigManager getInstance() {
        synchronized (ConfigManager.class) {
            if (mInstance == null) {
                mInstance = new ConfigManager();
            }
            return mInstance;
        }
    }

    private void initDefaultConfig() {
        configList = new HashMap<>();
//      //一维码（商品）
        configList.put(KEY_DECODE_1D_PRODUCT, new Boolean(true));
//      //一维码（工业）
        configList.put(KEY_DECODE_1D_INDUSTRIAL, new Boolean(true));
//      //二维码
        configList.put(KEY_DECODE_QR, new Boolean(true));
//      //Data Matrix
        configList.put(KEY_DECODE_DATA_MATRIX, new Boolean(true));
//      //Aztec
        configList.put(KEY_DECODE_AZTEC, new Boolean(false));
//      //PDF417（测试）
        configList.put(KEY_DECODE_PDF417, new Boolean(false));
//      //自定义搜索网址
//      configList.put(KEY_CUSTOM_PRODUCT_SEARCH, new Boolean(false));
//      //发出声音
        configList.put(KEY_PLAY_BEEP, new Boolean(true));
//      //震动
        configList.put(KEY_VIBRATE, new Boolean(false));
//      //复制到剪切板
//      configList.put(KEY_COPY_TO_CLIPBOARD, new Boolean(true));
//      //闪光灯模式（是否开启闪光灯）
        configList.put(KEY_FRONT_LIGHT_MODE, "OFF");
//      //批量扫描模式
//      configList.put(KEY_BULK_MODE, new Boolean(false));
//      //保留重复记录
//      configList.put(KEY_REMEMBER_DUPLICATES, new Boolean(false));
//      //存入历史记录
//      configList.put(KEY_ENABLE_HISTORY, new Boolean(true));
//      //检索更多信息
//      configList.put(KEY_SUPPLEMENTAL, new Boolean(true));
//      //自动对焦
        configList.put(KEY_AUTO_FOCUS, new Boolean(true));
//      //反色（适用于黑背景白条码）
        configList.put(KEY_INVERT_SCAN, new Boolean(false));
//      //搜索引擎国别
//      configList.put(KEY_SEARCH_COUNTRY, "-");
//      //不自动旋转
//      configList.put(KEY_DISABLE_AUTO_ORIENTATION, new Boolean(false));
//      //不持续对焦（使用标准对焦模式）
        configList.put(KEY_DISABLE_CONTINUOUS_FOCUS, new Boolean(false));
//      //不曝光
        configList.put(KEY_DISABLE_EXPOSURE, new Boolean(true));
//      //不使用距离测量
        configList.put(KEY_DISABLE_METERING, new Boolean(true));
//      //不进行条形码场景匹配
        configList.put(KEY_DISABLE_BARCODE_SCENE_MODE, new Boolean(true));
//      //自动打开网页
//      configList.put(KEY_AUTO_OPEN_WEB, new Boolean(false));


    }

    private Map<String, Object> getConfigList() {
        return configList;
    }

    public static String getString(String key, @Nullable String defValue) {
        Object value = getInstance().getConfigList().get(key);
        if (value != null && value instanceof String) {
            return (String) value;
        }
        return defValue;
    }

    public static Boolean getBoolean(String key, @Nullable boolean defValue) {
        Object value = getInstance().getConfigList().get(key);
        if (value != null && value instanceof Boolean) {
            return (Boolean) value;
        }
        return defValue;
    }

}
