package com.galix.avcore.util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class OtherUtils {
    private static Map<String, RecordTag> mTimeRecordMap = new HashMap<>();
    private static StringBuilder result = new StringBuilder();
    private static final long MAX_FRAME_COUNT = 100;

    private static class RecordTag {
        public String tag;
        public long recordStart;
        public long recordEnd;
        public long lastDuration;
        public long recordTotal;
        public long num;
        public long recordMax;
        public long recordHistoryMax;
    }

    public static HashMap<String, Object> BuildMap(Object... configs) {
        if (configs == null) return null;
        HashMap<String, Object> maps = new HashMap<>();
        for (int i = 0; i < configs.length / 2; i++) {
            maps.put((String) configs[2 * i], configs[2 * i + 1]);
        }
        return maps;
    }

    public static void RecordStart(String tag) {
        if (!mTimeRecordMap.containsKey(tag)) {
            RecordTag recordTag = new RecordTag();
            recordTag.recordStart = System.currentTimeMillis();
            mTimeRecordMap.put(tag, recordTag);
        } else {
            RecordTag recordTag = mTimeRecordMap.get(tag);
            recordTag.recordStart = System.currentTimeMillis();
        }
    }

    public static void RecordEnd(String tag) {
        if (!mTimeRecordMap.containsKey(tag)) {
            return;
        }
        RecordTag recordTag = mTimeRecordMap.get(tag);
        recordTag.recordEnd = System.currentTimeMillis();
        recordTag.num++;
        recordTag.lastDuration = recordTag.recordEnd - recordTag.recordStart;
        recordTag.recordTotal += recordTag.lastDuration;
        recordTag.recordMax = Math.max(recordTag.lastDuration, recordTag.recordMax);
        recordTag.recordHistoryMax = Math.max(recordTag.lastDuration, recordTag.recordHistoryMax);
        if (recordTag.num == MAX_FRAME_COUNT) {
            recordTag.recordTotal = 0;
            recordTag.num = 0;
            recordTag.recordMax = 0;
        }
        LogUtil.log("record_time#" + tag + "#" + (recordTag.recordEnd - recordTag.recordStart));
    }

    //log日志拼接
    public static String LogStr(String... filter) {
        result.delete(0, result.length());
        Iterator<String> iter = mTimeRecordMap.keySet().iterator();
        while (iter.hasNext()) {
            String key = iter.next();
            boolean needPrint = filter.length == 0;
            for (String f : filter) {
                needPrint = needPrint || key.contains(f);
            }
            if (needPrint) {
                result.append(key + ":" + mTimeRecordMap.get(key).lastDuration +
                        " average:" + Math.floor(mTimeRecordMap.get(key).recordTotal * 1.f / mTimeRecordMap.get(key).num)
                        + "#max:" + Math.floor(mTimeRecordMap.get(key).recordMax)
//                        + "#h_max:" + Math.floor(mTimeRecordMap.get(key).recordHistoryMax)
                        + "\n\n");
            }
        }
        return result.toString();
    }

    public void Clear() {
        mTimeRecordMap.clear();
    }

}
