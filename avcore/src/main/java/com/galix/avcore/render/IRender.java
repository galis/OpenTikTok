package com.galix.avcore.render;

import com.galix.avcore.avcore.AVFrame;

import java.util.Map;

public interface IRender {
    boolean isOpen();

    void open();

    void close();

    void write(Map<String, Object> config);

    void render(AVFrame avFrame);
}
