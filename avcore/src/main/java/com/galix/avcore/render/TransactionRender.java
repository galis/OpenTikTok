package com.galix.avcore.render;

import com.galix.avcore.avcore.AVFrame;

public class TransactionRender implements IRender {

    public static class TransactionConfig {
        public float alpha;
    }

    @Override
    public boolean isOpen() {
        return false;
    }

    @Override
    public void open() {

    }

    @Override
    public void close() {

    }

    @Override
    public void write(Object config) {

    }

    @Override
    public void render(AVFrame avFrame) {

    }
}
