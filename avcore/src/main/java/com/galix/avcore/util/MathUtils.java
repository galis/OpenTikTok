package com.galix.avcore.util;

import android.graphics.Color;
import android.util.Size;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;

import java.nio.FloatBuffer;

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


    public static final Mat mIdentityMat = Mat.eye(3, 3, CV_32F);
    static Rect sRect = new Rect(0, 0, 3, 2);
    static MatOfPoint2f mSrcPoints = new MatOfPoint2f();//TODO 多线程环境下有问题。
    static MatOfPoint2f mDstPoints = new MatOfPoint2f();
    static Mat mHolderMat = new Mat();
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

    private static Point[] calMatrixSrc = new Point[]{new Point(), new Point(), new Point()};
    private static Point[] calMatrixDst = new Point[]{new Point(), new Point(), new Point()};
    private static Size calSize = new Size(0, 0);

    public static Mat calMatrix(android.graphics.Rect src, android.graphics.Rect dst) {

        calSize = new Size(src.width(), src.height());

        calMatrixSrc[0].x = 0;
        calMatrixSrc[0].y = 0;
        calMatrixSrc[1].x = src.width();
        calMatrixSrc[1].y = 0;
        calMatrixSrc[2].x = 0;
        calMatrixSrc[2].y = src.height();

        calMatrixDst[0].x = dst.left;
        calMatrixDst[0].y = dst.top;
        calMatrixDst[1].x = dst.right;
        calMatrixDst[1].y = dst.top;
        calMatrixDst[2].x = dst.left;
        calMatrixDst[2].y = dst.bottom;
        return getTransform(calMatrixSrc, calSize, calMatrixDst, calSize);
    }

    private static FloatBuffer cacheMatBuffer = FloatBuffer.allocate(9);

    public static FloatBuffer mat2FloatBuffer(Mat mat) {
        cacheMatBuffer.position(0);
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                cacheMatBuffer.put((float) (mat.get(i, j)[0] * 1.0f));
            }
        }
        cacheMatBuffer.position(0);
        return cacheMatBuffer;
    }

    public static FloatBuffer Int2Vec3(int bgColor) {
        FloatBuffer floatBuffer = FloatBuffer.allocate(3);
        floatBuffer.put(Color.red(bgColor) / 255.f).put(Color.green(bgColor) / 255.f).put(Color.blue(bgColor) / 255.f);
        floatBuffer.position(0);
        return floatBuffer;
    }

    public static int clamp(int min, int max, int value) {
        return Math.min(max, Math.max(value, min));
    }
}
