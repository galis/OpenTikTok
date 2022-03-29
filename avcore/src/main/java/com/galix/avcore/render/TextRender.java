package com.galix.avcore.render;

import android.view.View;
import android.widget.EditText;

import com.galix.avcore.avcore.AVFrame;

import java.util.Map;

public class TextRender implements IRender {

    private EditText editText;

    public TextRender(EditText editText) {
        this.editText = editText;
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
    public void write(Map<String, Object> config) {

    }

    @Override
    public void render(AVFrame avFrame) {
        editText.post(new Runnable() {
            @Override
            public void run() {
                if (avFrame.isValid()) {
                    if (editText.getVisibility() != View.VISIBLE) {
                        editText.setVisibility(View.VISIBLE);
                    }
                } else {
                    editText.setVisibility(View.GONE);
                }
            }
        });
    }
}
