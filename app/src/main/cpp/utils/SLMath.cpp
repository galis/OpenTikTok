#include "SLMath.h"
#include "SLColor.h"

void slutil::SLMath::getCrossPointF(LinePara para1, LinePara para2, SLPoint2f &result) {
    result.x = (para2.b - para1.b) / (para1.k - para2.k);
    result.y = para1.k * result.x + para1.b;
}

// 获取直线参数
void slutil::SLMath::getLinePara(SLPoint2f lp1, SLPoint2f lp2, LinePara &LP) {
    double m = lp2.x - lp1.x;
    if (0 == m) {
        LP.k = 10000.0f;
        LP.b = lp1.y - LP.k * lp1.x;
    } else {
        LP.k = (lp2.y - lp1.y) / (lp2.x - lp1.x);
        LP.b = lp1.y - LP.k * lp1.x;
    }
}

//顶点,交点确定抛物线
void slutil::SLMath::calParabolaCoeffs(SLPoint2f top, SLPoint2f px, float outResult[]) {
    float coeffC = top.y;
    float coeffA = -coeffC / (px.x * px.x);
    outResult[0] = coeffA;
    outResult[1] = coeffC;
}

//sampleScale==top.y/left.x
void slutil::SLMath::calParabolaCoeffsAndActualCoeffs(SLPoint2f top, SLPoint2f px, float sampleScale,
                                                      float outResult[], float *outCornerToCenter) {
    float actualCoeffC = top.y;
    float actualCoeffA = -actualCoeffC / (px.x * px.x);
    float actualSampleCoeffC = px.x * sampleScale;
    float actualSampleCoeffA = -actualSampleCoeffC / (px.x * px.x);
    float actualSquareCornerToCenter = px.x * px.x;
    outResult[0] = actualSampleCoeffA;
    outResult[1] = actualSampleCoeffC;
    outResult[2] = actualCoeffA;
    outResult[3] = actualCoeffC;
    *outCornerToCenter = actualSquareCornerToCenter;
//        outResult[5] = actualSquareCornerToCenter;
}


double slutil::SLMath::toDegree(float angle) {
    return angle * 180.0 / PI;
}

void slutil::SLMath::rotate_point_2d(int w, int h, SLPoint2f &point,
                                     int orientation) {
    int tmp;
    switch (orientation % 360) {
        case 90:
            tmp = point.x;
            point.x = h - point.y;
            point.y = tmp;
            break;
        case 180:
            point.x = w - point.x;
            point.y = h - point.y;
            break;
        case 270:
            tmp = point.x;
            point.x = point.y;
            point.y = w - tmp;
            break;
        default:
            break;
    }
}


SLPoint2f slutil::SLMath::pointSub(SLPoint2f p1, SLPoint2f p2) {
    return SLPoint2f(p1.x - p2.x, p1.y - p2.y);
}

SLPoint2f slutil::SLMath::pointAdd(SLPoint2f p1, SLPoint2f p2) {
    return SLPoint2f(p1.x + p2.x, p1.y + p2.y);
}

SLPoint2f slutil::SLMath::pointDiv(SLPoint2f p1, SLPoint2f p2) {
    return SLPoint2f(p1.x / p2.x, p1.y / p2.y);
}


SLPoint2f slutil::SLMath::pointRotate(SLPoint2f p1, SLPoint2f p2) {
    return SLPoint2f(
            p1.x * p2.y - p1.y * p2.x,
            p1.x * p2.x + p1.y * p2.y
    );
}

SLPoint2f slutil::SLMath::pointMulti(SLPoint2f p1, SLPoint2f p2) {
    return SLPoint2f(
            p1.x * p2.x,
            p1.y * p2.y
    );
}

SLPoint2f slutil::SLMath::pointMulti(SLPoint2f pointF, float scale) {
    return SLPoint2f(
            pointF.x * scale,
            pointF.y * scale
    );
}

SLPoint2f slutil::SLMath::pointMiddle(SLPoint2f p1, SLPoint2f p2) {
    return SLPoint2f((p1.x + p2.x) / 2, (p1.y + p2.y) / 2);
}

SLPoint2f slutil::SLMath::pointMiddleInt(SLPoint2f p1, SLPoint2f p2) {
    return SLPoint2f((int) ((p1.x + p2.x) / 2), (int) ((p1.y + p2.y) / 2));
}

float slutil::SLMath::distance(SLPoint2f p1, SLPoint2f p2) {
    return sqrt((p1.x - p2.x) * (p1.x - p2.x) + (p1.y - p2.y) * (p1.y - p2.y));
}

float slutil::SLMath::clamp(float value, float minValue, float maxValue) {
    return max(min(value, maxValue), minValue);
}

SLRect slutil::SLMath::scale(SLRect &rect2F, float scale) {
    return SLRect2f(rect2F.x * scale, rect2F.y * scale, rect2F.width * scale,
                    rect2F.height * scale);
}

void slutil::SLMath::gammaTransformation(cv::Mat &matInput, cv::Mat &matOutput, float fGamma, float fC /*= 1.0f*/) {
    assert(matInput.elemSize() == 1);
    //构造输出图像
    matOutput = cv::Mat::zeros(matInput.rows, matInput.cols, matInput.type());

    //循环中尽量避免除法
    float fNormalFactor = 1.0f / 255.0f;
    for (size_t r = 0; r < (size_t) matInput.rows; r++) {
        unsigned char *pInput = matInput.data + r * matInput.step[0];
        unsigned char *pOutput = matOutput.data + r * matOutput.step[0];
        for (size_t c = 0; c < (size_t) matInput.cols; c++) {
            //gamma变换
            float fOutput = std::pow(pInput[c] * fNormalFactor, fGamma) * fC;
            //数值溢出判断
            fOutput = fOutput > 1.0f ? 1.0f : fOutput;
            //输出
            pOutput[c] = static_cast<unsigned char>(fOutput * 255.0f);
        }
    }
}


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
SLMat slutil::SLMath::getTransformMat(SLPoint2f srcPoints[], SLPoint2f dstPoints[], SLSize srcSize, SLSize dstSize) {

    /**
     * 转换为目标图。
     */
    for (int i = 0; i < 3; i++) {
        srcPoints[i] = SLPoint2f(srcPoints[i].x / srcSize.width * dstSize.width,
                                 srcPoints[i].y / srcSize.height * dstSize.height);
    }

    /*求出MA*/
    SLMat matA = SLMat::eye(3, 3, CV_32F);
    SLMat doubleMat = cv::getAffineTransform(srcPoints, dstPoints);
    doubleMat.convertTo(doubleMat, CV_32F);
    doubleMat.copyTo(matA(SLRect(0, 0, 3, 2)));

    /**
     * MB => [ W,0,0        [ 1,0,0     => [  W,0,0
     *         0,H,0    *     0,-1,1          0,-H,H
     *         0,0,1]         0,0,1]          0,0,1]
     */
    SLMat matB = SLMat::eye(3, 3, CV_32F);
    matB.at<float>(SLPoint2f(0, 0)) = dstSize.width;
    matB.at<float>(SLPoint2f(1, 1)) = -dstSize.height;
    matB.at<float>(SLPoint2f(2, 1)) = dstSize.height;

    return ((matA * matB).inv() * matB).t();
}

void slutil::SLMath::getNormalColor(int color, float *colors) {
    colors[0] = slutil::SLColor::red(color) / 255.0f;
    colors[1] = slutil::SLColor::green(color) / 255.0f;
    colors[2] = slutil::SLColor::blue(color) / 255.0f;
}

