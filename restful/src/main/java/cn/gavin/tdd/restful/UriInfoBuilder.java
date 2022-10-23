package cn.gavin.tdd.restful;

interface UriInfoBuilder {
    Object getLastMatchedResource();

    void addMatchedResource(Object resource);
}
