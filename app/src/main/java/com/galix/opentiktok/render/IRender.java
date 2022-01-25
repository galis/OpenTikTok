package com.galix.opentiktok.render;

import com.galix.opentiktok.avcore.AVFrame;

public interface IRender {
    void open();

    void close();

    void write(Object config);

    void render(AVFrame avFrame);
}
