package cn.gavin.tdd.restful;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.GenericEntity;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

interface ResourceRouter {
    OutboundResponse dispatch(HttpServletRequest request, ResourceContext resourceContext);

    interface Resource {
        Optional<ResourceMethod> match(String path, String method, String[] mediaTypes, UriInfoBuilder builder);
    }

    interface RootResource extends Resource {
        UriTemplate getUriTemplate();
    }

    interface ResourceMethod {
        GenericEntity<?> call(ResourceContext resourceContext, UriInfoBuilder builder);
    }
}

class DefaultResourceRouter implements ResourceRouter {

    private Runtime runtime;
    private List<RootResource> rootResources;

    public DefaultResourceRouter(Runtime runtime, List<RootResource> rootResources) {
        this.runtime = runtime;
        this.rootResources = rootResources;
    }

    @Override
    public OutboundResponse dispatch(HttpServletRequest request, ResourceContext resourceContext) {
        String path = request.getServletPath();
        UriInfoBuilder uri = runtime.createUriInfoBuilder(request);

        Optional<ResourceMethod> method = rootResources.stream().map(resource -> match(path, resource))
                .filter(Result::isMatched).sorted().findFirst().flatMap(result -> result.findResourceMethod(request, uri));

        if (method.isEmpty()) return (OutboundResponse) Response.status(Response.Status.NOT_FOUND).build();

        return (OutboundResponse) method.map(m -> m.call(resourceContext, uri)).map(entity -> Response.ok(entity).build())
                .orElseGet(() -> Response.noContent().build());
    }

    private static Result match(String path, RootResource resource) {
        return new Result(resource.getUriTemplate().match(path), resource);
    }

    record Result(Optional<UriTemplate.MatchResult> matched, RootResource resource) implements Comparable<Result> {
        private boolean isMatched() {
            return this.matched.isPresent();
        }

        @Override
        public int compareTo(Result o) {
            return matched.flatMap(x -> o.matched.map(x::compareTo)).orElse(0);
        }

        private Optional<ResourceMethod> findResourceMethod(HttpServletRequest request, UriInfoBuilder uri) {
            return matched.flatMap(result -> resource.match(result.getRemaining(), request.getMethod(),
                    Collections.list(request.getHeaders(HttpHeaders.ACCEPT)).toArray(String[]::new), uri));
        }
    }
}
