package com.galix.avcore.util;

import android.graphics.PointF;
import android.util.Log;
import android.util.Size;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import static org.opencv.core.CvType.CV_32F;
import static org.opencv.imgproc.Imgproc.getAffineTransform;

//static PLMat getTransformMat(PLPoint2f srcPoints[], PLPoint2f dstPoints[], PLSize srcSize, PLSize dstSize) {
//
//        /**
//         * 转换为目标图。
//         */
//        for (int i = 0; i < 3; i++) {
//        srcPoints[i] = PLPoint2f(srcPoints[i].x / srcSize.width * dstSize.width,
//        srcPoints[i].y / srcSize.height * dstSize.height);
//        }
//
//        /*求出MA*/
//        PLMat matA = PLMat::eye(3, 3, CV_32F);
//        PLMat doubleMat = cv::getAffineTransform(srcPoints, dstPoints);
//        doubleMat.convertTo(doubleMat, CV_32F);
//        doubleMat.copyTo(matA(PLRect(0, 0, 3, 2)));
//
//        /**
//         * MB => [ W,0,0        [ 1,0,0     => [  W,0,0
//         *         0,H,0    *     0,-1,1          0,-H,H
//         *         0,0,1]         0,0,1]          0,0,1]
//         */
//        PLMat matB = PLMat::eye(3, 3, CV_32F);
//        matB.at<float>(PLPoint(0, 0)) = dstSize.width;
//        matB.at<float>(PLPoint(1, 1)) = -dstSize.height;
//        matB.at<float>(PLPoint(2, 1)) = dstSize.height;
//
//        return ((matA * matB).inv() * matB).t();
//        }
public class MathUtils {
    static {
        OpenCVLoader.initDebug();
    }


    static Rect sRect = new Rect(0, 0, 3, 2);
    static MatOfPoint2f mSrcPoints = new MatOfPoint2f();
    static MatOfPoint2f mDstPoints = new MatOfPoint2f();
    static Mat mHolderMat = new Mat();
    static Mat mIdentityMat = Mat.eye(3, 3, CV_32F);
    static Mat mMatA = new Mat();
    static Mat mMatB = new Mat();

    public static Mat getTransform(Point[] srcPoints, Size srcSize, Point[] dstPoints, Size dstSize) {

        /**
         * 转换为目标图。
         */
        for (int i = 0; i < 3; i++) {
            srcPoints[i].x = srcPoints[i].x / srcSize.getWidth() * dstSize.getWidth();
            srcPoints[i].y = srcPoints[i].y / srcSize.getHeight() * dstSize.getHeight();
        }

//        doubleMat
//                [0.5515625, 0, 276;
//        0, 0.7824074074074074, 234]
//[1.8130312, -0, 0;
//        0, 1.2781065, 0;
//        -0.26062322, -0.0011834319, 1]

        /*求出MA*/
        mIdentityMat.copyTo(mMatA);
        mSrcPoints.fromArray(srcPoints);
        mDstPoints.fromArray(dstPoints);
        Mat doubleMat = getAffineTransform(mSrcPoints, mDstPoints);
        doubleMat.convertTo(doubleMat, CV_32F);
        doubleMat.copyTo(mMatA.submat(sRect));

        /**
         * MB => [ W,0,0        [ 1,0,0     => [  W,0,0
         *         0,H,0    *     0,-1,1          0,-H,H
         *         0,0,1]         0,0,1]          0,0,1]
         */
        mIdentityMat.copyTo(mMatB);
        mMatB.put(0, 0, dstSize.getWidth());
        mMatB.put(1, 1, -dstSize.getHeight());
        mMatB.put(1, 2, dstSize.getHeight());
        Mat dstMat = new Mat();
        Core.gemm(mMatA, mMatB, 1, mHolderMat, 0, dstMat);
        Core.gemm(dstMat.inv(), mMatB, 1, mHolderMat, 0, dstMat);

        return dstMat.t();
    }
}
