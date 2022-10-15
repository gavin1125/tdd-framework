package cn.gavin.tdd.restful;

import cn.gavin.tdd.container.Context;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.ext.Providers;

public interface Runtime {
    Providers getProviders();

    ResourceContext createResourceContext(HttpServletRequest request, HttpServletResponse response);

    UriInfoBuilder createUriInfoBuilder(HttpServletRequest request);

    Context getApplicationContext();

    ResourceRouter getResourceRouter();
}
