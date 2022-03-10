//
// Created by galismac on 9/3/2022.
//

#ifndef OPENTIKTOK_SLCOLOR_H
#define OPENTIKTOK_SLCOLOR_H

namespace slutil {
    class SLColor {
    public:
        static int red(int color);

        static int green(int color);

        static int blue(int color);

        static int rgb(int r, int g, int b);

        static int argb(int a, int r, int g, int b);
    };
}


#endif //OPENTIKTOK_SLCOLOR_H
