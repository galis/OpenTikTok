package com.galix.opentiktok.avcore;

import android.media.MediaSync;

import java.util.LinkedList;

/**
 * 负责各个组件的同步，非常重要。
 */
public class AVSync {
    MediaSync mediaSync;

    private LinkedList<AVComponent> components;

    public void addComponent(AVComponent avComponent) {
        components.add(avComponent);
    }

    public void removeComponent(AVComponent avComponent) {
        components.remove(avComponent);
    }

}
