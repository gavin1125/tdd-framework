package cn.gavin.tdd.restful;

import jakarta.ws.rs.core.UriInfo;

interface UriInfoBuilder {
    Object getLastMatchedResource();

    void addMatchedResource(Object resource);

    UriInfo createUriInfo();
}
