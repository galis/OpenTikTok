package com.galix.avcore.util;

import java.util.HashMap;

public class OtherUtils {
    public static HashMap<String, Object> buildMap(Object... configs) {
        if (configs == null) return null;
        HashMap<String, Object> maps = new HashMap<>();
        for (int i = 0; i < configs.length / 2; i++) {
            maps.put((String) configs[2 * i], configs[2 * i + 1]);
        }
        return maps;
    }
}
