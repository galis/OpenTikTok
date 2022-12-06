package com.galix.avcore.util;

import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PointF;
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

    public static Mat calTransform(Point[] srcPoints, Size srcSize, Point[] dstPoints, Size dstSize) {

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

        calMatrixSrc[0].x = src.left;
        calMatrixSrc[0].y = src.top;
        calMatrixSrc[1].x = src.right;
        calMatrixSrc[1].y = src.top;
        calMatrixSrc[2].x = src.left;
        calMatrixSrc[2].y = src.bottom;

        calMatrixDst[0].x = dst.left;
        calMatrixDst[0].y = dst.top;
        calMatrixDst[1].x = dst.right;
        calMatrixDst[1].y = dst.top;
        calMatrixDst[2].x = dst.left;
        calMatrixDst[2].y = dst.bottom;
        return calTransform(calMatrixSrc, calSize, calMatrixDst, calSize);
    }

    private static FloatBuffer cacheMatBuffer = FloatBuffer.allocate(9);

    public static FloatBuffer mat2FloatBuffer9(Mat mat) {
        cacheMatBuffer.position(0);
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                cacheMatBuffer.put((float) (mat.get(i, j)[0] * 1.0f));
            }
        }
        cacheMatBuffer.position(0);
        return cacheMatBuffer;
    }

    public static void mat2FloatBuffer9(Mat mat, FloatBuffer result) {
        result.position(0);
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                result.put((float) (mat.get(i, j)[0] * 1.0f));
            }
        }
        result.position(0);
    }

    private static float[] src = new float[2];
    private static float[] dst = new float[2];

    public static PointF pointApplyMatrix(PointF point, Matrix matrix) {
        src[0] = point.x;
        src[1] = point.y;
        matrix.mapPoints(dst, src);
        return new PointF(dst[0], dst[1]);
    }

    public static void pointApplyMatrix(PointF srcPoint, PointF resultPoint, Matrix matrix) {
        src[0] = srcPoint.x;
        src[1] = srcPoint.y;
        matrix.mapPoints(dst, src);
        resultPoint.set(dst[0], dst[1]);
    }

    private static Matrix gMatrix = new Matrix();

    public static PointF pointApplyMatrix(PointF point, FloatBuffer matrixBuffer) {
        gMatrix.setValues(matrixBuffer.array());
        return pointApplyMatrix(point, gMatrix);
    }

    public static FloatBuffer matrixFloatBuffer9(Matrix matrix) {
        cacheMatBuffer.position(0);
        matrix.getValues(cacheMatBuffer.array());
        cacheMatBuffer.position(0);
        return cacheMatBuffer;
    }

    public static FloatBuffer mat2FloatBuffer16(Mat mat) {
        cacheMatBuffer.position(0);
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
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

    public static FloatBuffer Int2Vec4(int bgColor) {
        FloatBuffer floatBuffer = FloatBuffer.allocate(4);
        floatBuffer.put(Color.red(bgColor) / 255.f)
                .put(Color.green(bgColor) / 255.f)
                .put(Color.blue(bgColor) / 255.f)
                .put(Color.alpha(bgColor) / 255.f);
        floatBuffer.position(0);
        return floatBuffer;
    }

    public static void Int2Vec4(int bgColor, FloatBuffer floatBuffer) {
        if (floatBuffer.capacity() < 4) {
            floatBuffer = FloatBuffer.allocate(4);
        }
        floatBuffer.position(0);
        floatBuffer.put(Color.red(bgColor) / 255.f)
                .put(Color.green(bgColor) / 255.f)
                .put(Color.blue(bgColor) / 255.f)
                .put(Color.alpha(bgColor) / 255.f);
        floatBuffer.position(0);
    }


    public static int clamp(int min, int max, int value) {
        return Math.min(max, Math.max(value, min));
    }

    public static boolean IsClamp(int minV, int maxV, int value) {
        return value >= minV && value <= maxV;
    }

    public static Size calCompositeSize(String type, Size videoSize, int targetHeight) {
        int width = 0;
        if (type.equalsIgnoreCase("原始")) {
            width = (int) (targetHeight * videoSize.getWidth() * 1.f / videoSize.getHeight());
        } else if (type.equalsIgnoreCase("4:3")) {
            width = targetHeight * 4 / 3;
        } else if (type.equalsIgnoreCase("3:4")) {
            width = targetHeight * 3 / 4;
        } else if (type.equalsIgnoreCase("1:1")) {
            width = targetHeight;
        } else if (type.equalsIgnoreCase("16:9")) {
            width = targetHeight * 16 / 9;
        } else if (type.equalsIgnoreCase("9:16")) {
            width = targetHeight * 9 / 16;
        }
        return new Size(width, targetHeight);
    }

    public static Mat calMat(Size src, Size targetSize) {
        if (targetSize.getWidth() * src.getHeight() * 1.f / src.getWidth() > targetSize.getHeight()) {//高度铺满
            int targetWidth = (int) (targetSize.getHeight() * src.getWidth() * 1.f / src.getHeight());
            return MathUtils.calMatrix(new android.graphics.Rect(0, 0, targetSize.getWidth(), targetSize.getHeight()),
                    new android.graphics.Rect((targetSize.getWidth() - targetWidth) / 2, 0, targetSize.getWidth() - (targetSize.getWidth() - targetWidth) / 2, targetSize.getHeight()));
        } else {//宽度铺满
            int targetHeight = (int) (targetSize.getWidth() * src.getHeight() * 1.f / src.getWidth());
            return MathUtils.calMatrix(new android.graphics.Rect(0, 0, targetSize.getWidth(), targetSize.getHeight()),
                    new android.graphics.Rect(0, (targetSize.getHeight() - targetHeight) / 2, targetSize.getWidth(), targetSize.getHeight() - (targetSize.getHeight() - targetHeight) / 2));
        }
    }
}
