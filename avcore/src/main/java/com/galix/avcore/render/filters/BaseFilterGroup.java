package com.galix.avcore.render.filters;

import com.galix.avcore.util.LogUtil;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

/**
 * 滤镜组
 *
 * @Author: Galis
 * @Date:2022.03.31
 */
public abstract class BaseFilterGroup extends BaseFilter {

    private Map<String, IFilter> mFilters = new HashMap<>();
    private IFilter mLastRenderFilter;
    private GLTexture mLastOutputTexture;

    public BaseFilterGroup() {
        super(null, null);
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
    public void render() {
        onRender();
        if (getParent() != null) {
            getParent().setLastFilter(this);
        }
    }

    @Override
    public GLTexture getOutputTexture() {
        return mLastOutputTexture;
    }

    public abstract void onRender();//定义child filter渲染顺序以及相关参数，必须重写

    /**
     * ++++++++++Filter相关方法++++++++++
     */
    public void addFilter(String tag, IFilter filter) {
        mFilters.put(tag, filter);
        getFilter(tag).setParent(this);
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

    public void addFilters(Object... tagAndFilter) {
        if (tagAndFilter == null || tagAndFilter.length % 2 != 0) {
            return;
        }
        for (int i = 0; i < tagAndFilter.length / 2; i++) {
            addFilter((String) tagAndFilter[2 * i], (Class) tagAndFilter[2 * i + 1]);
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

    public IFilter defineFilter(String tag, Class classFilter) {
        if (!mFilters.containsKey(tag)) {
            addFilter(tag, classFilter);
        }
        IFilter filter = mFilters.get(tag);
        if (!filter.isOpen()) {
            filter.open();
        }
        return filter;
    }

    public IFilter defineFilter(String tag, String vs, String fs) {
        try {
            if (!mFilters.containsKey(tag)) {
                Class<SimpleFilter> classFilter = SimpleFilter.class;
                Constructor<SimpleFilter> constructor = classFilter.getConstructor(String.class, String.class);
                addFilter(tag, constructor.newInstance(vs, fs));
            }
            IFilter filter = mFilters.get(tag);
            if (!filter.isOpen()) {
                filter.open();
            }
            return filter;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public IFilter defineFilter(String tag, int vs, int fs) {
        try {
            if (!mFilters.containsKey(tag)) {
                Class<SimpleFilter> classFilter = SimpleFilter.class;
                Constructor<SimpleFilter> constructor = classFilter.getConstructor(int.class, int.class);
                addFilter(tag, constructor.newInstance(vs, fs));
            }
            IFilter filter = mFilters.get(tag);
            if (!filter.isOpen()) {
                filter.open();
            }
            return filter;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void setLastFilter(IFilter lastFilter) {
        this.mLastRenderFilter = lastFilter;
        if (lastFilter.getOutputTexture() != null) {
            mLastOutputTexture = lastFilter.getOutputTexture();
        }
    }

}
