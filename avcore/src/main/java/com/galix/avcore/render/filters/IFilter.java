package com.galix.avcore.render.filters;

import java.util.Map;

public interface IFilter {

    boolean isOpen();

    void open();

    void close();

    void write(Map<String, Object> config);

    void render();

}
