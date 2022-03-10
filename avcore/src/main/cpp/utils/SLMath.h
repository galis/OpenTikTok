#ifndef SL_MATH_H
#define SL_MATH_H

#include <math.h>
#include <opencv2/opencv.hpp>
#include <platform/SLPlatform.h>

using namespace std;

class LinePara {
public:
    float k;
    float b;
};

static const double PI = 3.14159265358979323846;

namespace slutil {

    class SLMath {
    public:
        static void getCrossPointF(LinePara para1, LinePara para2, SLPoint2f &result);

        // 获取直线参数
        static void getLinePara(SLPoint2f lp1, SLPoint2f lp2, LinePara &LP);

        //顶点,交点确定抛物线
        static void calParabolaCoeffs(SLPoint2f top, SLPoint2f px, float outResult[]);

        //sampleScale==top.y/left.x
        static void calParabolaCoeffsAndActualCoeffs(SLPoint2f top, SLPoint2f px, float sampleScale,
                                                     float outResult[], float *outCornerToCenter);


        static double toDegree(float angle);

        static void rotate_point_2d(int w, int h, SLPoint2f &point, int orientation);

        static SLPoint2f pointSub(SLPoint2f p1, SLPoint2f p2);

        static SLPoint2f pointAdd(SLPoint2f p1, SLPoint2f p2);

        static SLPoint2f pointDiv(SLPoint2f p1, SLPoint2f p2);

        static SLPoint2f pointRotate(SLPoint2f p1, SLPoint2f p2);

        static SLPoint2f pointMulti(SLPoint2f p1, SLPoint2f p2);

        static SLPoint2f pointMulti(SLPoint2f pointF, float scale);

        static SLPoint2f pointMiddle(SLPoint2f p1, SLPoint2f p2);

        static SLPoint2f pointMiddleInt(SLPoint2f p1, SLPoint2f p2);

        static float distance(SLPoint2f p1, SLPoint2f p2);

        static float clamp(float value, float minValue, float maxValue);

        static SLRect scale(SLRect &rect2F, float scale);

        static void gammaTransformation(cv::Mat &matInput, cv::Mat &matOutput, float fGamma, float fC /*= 1.0f*/);


        /**
         * 假设MA为CV求出的变换矩阵，它的坐标系是左上角坐标系；
         * MB为Opengl纹理坐标系变换矩阵，它的坐标系为左下角坐标系；
         * P'是目的位置，P是源位置，它们都是纹理坐标系，归一化；那么有
         * MB*P'=MA*(MB*P) => MB*p'=(MA*MB)*P => P=(MA*MB)`1*MB*P'
         * 结果返回 (MA*MB)`1 * MB 就可以了? AHH!!由于Opengl是按照列存储的，所以结果要倒置一下..
         * 最终返回 ((MA*MB)`1 * MB)T
         * @param srcPoints 源关键点数组
         * @param dstPoints 目的关键点数据
         * @param frameSize 目的图片大小
         * @return 用于Opengl的变换矩阵
         */
        static SLMat getTransformMat(SLPoint2f srcPoints[], SLPoint2f dstPoints[], SLSize srcSize, SLSize dstSize);

        static void getNormalColor(int color, float *colors);
    };
}
#endif