package com.galix.avcore.avcore;

import android.util.Size;

public interface IVideo {
    String getPath();

    Size getVideoSize();

    int getFrameRate();
}
