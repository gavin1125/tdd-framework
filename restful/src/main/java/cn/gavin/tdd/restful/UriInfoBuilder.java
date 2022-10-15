package cn.gavin.tdd.restful;

interface UriInfoBuilder {
    void addMatchedPath(String path);

    void addParameter(String name, String value);

    String getUnmatchedPath();
}
