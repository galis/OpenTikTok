//
// Created by galismac on 9/3/2022.
//

#include "SLColor.h"

int slutil::SLColor::red(int color) {
    return (color >> 16) & 0xFF;
}

int slutil::SLColor::green(int color) {
    return (color >> 8) & 0xFF;
}

int slutil::SLColor::blue(int color) {
    return color & 0xFF;
}

int slutil::SLColor::rgb(int r, int g, int b) {
    return argb(0xFF, r, g, b);
}

int slutil::SLColor::argb(int a, int r, int g, int b) {
    return (a << 24) | (r << 16) | (g << 8) | b;
}
