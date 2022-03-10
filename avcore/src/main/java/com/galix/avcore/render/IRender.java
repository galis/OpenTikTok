package com.galix.avcore.render;

import com.galix.avcore.avcore.AVFrame;

public interface IRender {
    void open();

    void close();

    void write(Object config);

    void render(AVFrame avFrame);
}
