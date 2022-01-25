//package com.galix.opentiktok.avcore;
//
///**
// * 转场组件
// *
// * @Author: Galis
// * @Date:2022.01.24
// */
//public class AVTransaction extends AVComponent {
//    private int transactionType;
//    private AVVideo avVideo1;
//    private AVVideo avVideo2;
//
//    public AVTransaction(long srcStartTime, long srcEndTime, int transactionType, AVVideo video1, AVVideo video2) {
//        super(srcStartTime, srcEndTime, AVComponentType.TRANSACTION);
//        this.avVideo1 = video1;
//        this.avVideo2 = video2;
//        this.transactionType = transactionType;
//    }
//
//    @Override
//    public void open() {
//        if (!avVideo1.isOpen()) {
//            avVideo1.open();
//        }
//        if (!avVideo2.isOpen()) {
//            avVideo2.open();
//        }
//    }
//
//    @Override
//    public void close() {
//        if (avVideo1.isOpen()) {
//            avVideo1.close();
//        }
//        if (avVideo2.isOpen()) {
//            avVideo2.close();
//        }
//    }
//
//    @Override
//    public AVFrame peekFrame() {
//        return null;
//    }
//
//    @Override
//    public AVFrame readFrame() {
//        if (!avVideo1.isOpen() || !avVideo2.isOpen()) {
//            return null;
//        }
//        return composition(avVideo1.readFrame(), avVideo2.readFrame());
//    }
//
//    @Override
//    public int seekFrame(long position) {
//        return 0;
//    }
//
//    @Override
//    public AVFrame seek(long position) {
//        if (!avVideo1.isOpen() || !avVideo2.isOpen()) {
//            return null;
//        }
//        return composition(avVideo1.seek(position), avVideo2.seek(position));
//    }
//
//    private AVFrame composition(AVFrame avFrame1, AVFrame avFrame2) {
//        return null;
//    }
//}
