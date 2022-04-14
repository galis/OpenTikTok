package com.galix.avcore.util;

import android.util.Log;

import java.util.HashMap;
import java.util.Map;

public class OtherUtils {
    public static HashMap<String, Object> buildMap(Object... configs) {
        if (configs == null) return null;
        HashMap<String, Object> maps = new HashMap<>();
        for (int i = 0; i < configs.length / 2; i++) {
            maps.put((String) configs[2 * i], configs[2 * i + 1]);
        }
        return maps;
    }

    private static Map<String, Long> mTimeRecordMap = new HashMap<>();

    public static void recordStart(String tag) {
        mTimeRecordMap.put(tag, System.currentTimeMillis());
    }

    public static void recordEnd(String tag) {
        if (mTimeRecordMap.containsKey(tag)) {
            LogUtil.log("record_time#" + tag + "#" + (System.currentTimeMillis() - mTimeRecordMap.get(tag)));
        }
    }
}
