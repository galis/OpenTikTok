package com.galix.avcore.render.filters;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * 滤镜组
 *
 * @Author: Galis
 * @Date:2022.03.31
 */
public abstract class BaseFilterGroup implements IFilter {

    private List<IFilter> mFilters = new LinkedList<>();
    private boolean mIsOpen = false;

    @Override
    public boolean isOpen() {
        return false;
    }

    @Override
    public void open() {
        for (IFilter filter : mFilters) {
            filter.open();
        }
        markOpen(true);
    }

    @Override
    public void close() {
        for (IFilter filter : mFilters) {
            filter.close();
        }
        markOpen(false);
    }

    @Override
    public void write(Map<String, Object> config) {
        for (IFilter filter : mFilters) {
            filter.write(config);
        }
        onWrite(config);
    }

    @Override
    public void render() {
        onRender();
    }

    public abstract void onWrite(Map<String, Object> config);

    public abstract void onRender();

    //以下是滤镜方法
    public void addFilter(IFilter filter) {
        mFilters.add(filter);
    }

    public void removeFilter(BaseFilter filter) {
        mFilters.remove(filter);
    }

    public void clearFilters() {
        for (IFilter filter : mFilters) {
            filter.close();
        }
        mFilters.clear();
    }

    public void markOpen(boolean open) {
        mIsOpen = open;
    }

    //返回最后一个Filter的OutputTexture
    @Override
    public GLTexture getOutputTexture() {
        if (mFilters.isEmpty()) {
            return null;
        }
        return mFilters.get(mFilters.size() - 1).getOutputTexture();
    }

}
