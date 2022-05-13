package com.galix.avcore.render.filters;

import com.galix.avcore.util.LogUtil;

import java.util.HashMap;
import java.util.LinkedHashMap;
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

    private LinkedHashMap<String, IFilter> mFilters = new LinkedHashMap<>();
    private boolean mIsOpen = false;
    private Map<String, Object> mConfig = new HashMap<>();
    private Map<String, Object> mTempConfig = new HashMap<>();
    private IFilter mLastFilter;

    @Override
    public boolean isOpen() {
        return false;
    }

    @Override
    public void open() {
        for (String tag : mFilters.keySet()) {
            mFilters.get(tag).open();
        }
        markOpen(true);
    }

    @Override
    public void close() {
        clearFilters();
        markOpen(false);
    }

    @Override
    public void write(Map<String, Object> config) {
        onWrite(config);
        mConfig.putAll(config);
    }

    @Override
    public void write(Object... config) {
        if (config == null) return;
        mTempConfig.clear();
        for (int i = 0; i < config.length / 2; i++) {
            mTempConfig.put((String) config[2 * i], config[2 * i + 1]);
        }
        onWrite(mTempConfig);
        mConfig.putAll(mTempConfig);
    }

    @Override
    public void render() {
        onRender();
    }

    public void onWrite(Map<String, Object> config) {
    }

    public abstract void onRender();

    //以下是滤镜方法
    public void addFilter(String tag, IFilter filter) {
        mFilters.put(tag, filter);
        mLastFilter = filter;
    }

    public void addFilter(Class filter) {
        try {
            String tag = filter.getName();
            if (mFilters.containsKey(tag)) {
                LogUtil.logEngine("addFilter#" + tag + "#exit!!!");
                return;
            }
            addFilter(tag, (IFilter) filter.newInstance());
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        }
    }

    public void addFilter(String tag, Class filter) {
        try {
            addFilter(tag, (IFilter) filter.newInstance());
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        }
    }

    public void removeFilter(BaseFilter filter) {
        mFilters.remove(filter);
    }

    public IFilter getFilter(String tag) {
        return mFilters.get(tag);
    }

    public IFilter getFilter(Class filter) {
        return getFilter(filter.getName());
    }

    public void clearFilters() {
        for (String tag : mFilters.keySet()) {
            mFilters.get(tag).close();
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
        return mLastFilter.getOutputTexture();
    }

    @Override
    public Map<String, Object> getConfig() {
        return mConfig;
    }

    public Object getConfig(String key) {
        if (mConfig.containsKey(key)) {
            return mConfig.get(key);
        }
        return null;
    }
}
