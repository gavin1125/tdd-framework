package cn.gavin.tdd.restful;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.GenericEntity;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.RuntimeDelegate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Vector;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class ResourceDispatcherTest {
    private RuntimeDelegate delegate;

    private Runtime runtime;

    private HttpServletRequest request;
    private ResourceContext context;
    private UriInfoBuilder builder;

    @BeforeEach
    public void before() {
        runtime = Mockito.mock(Runtime.class);

        delegate = Mockito.mock(RuntimeDelegate.class);
        RuntimeDelegate.setInstance(delegate);
        when(delegate.createResponseBuilder()).thenReturn(new StudResponseBuilder());

        request = Mockito.mock(HttpServletRequest.class);
        context = mock(ResourceContext.class);
        when(request.getServletPath()).thenReturn("/users/1");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeaders(eq(HttpHeaders.ACCEPT))).thenReturn(new Vector<>(List.of(MediaType.WILDCARD)).elements());

        builder = Mockito.mock(UriInfoBuilder.class);
        when(runtime.createUriInfoBuilder(request)).thenReturn(builder);
    }

    @Test
    public void should_use_matched_root_resource() {
        GenericEntity entity = new GenericEntity("matched", String.class);

        ResourceRouter router = new DefaultResourceRouter(runtime, List.of(
                rootResource(matched("/users/1", result("/1")), returns(entity)),
                rootResource(unmatched("/users/1"))));

        OutboundResponse response = router.dispatch(request, context);
        assertSame(entity, response.getGenericEntity());
        assertEquals(200, response.getStatus());
    }

    @Test
    public void should_return_response_object_from_resource_method() {
        Response returnResponse = mock(OutboundResponse.class);
        when(returnResponse.getStatus()).thenReturn(304);
        GenericEntity entity = new GenericEntity(returnResponse, Response.class);

        ResourceRouter router = new DefaultResourceRouter(runtime, List.of(
                rootResource(matched("/users/1", result("/1")), returns(entity)),
                rootResource(unmatched("/users/1"))));

        OutboundResponse response = router.dispatch(request, context);

        assertEquals(304, response.getStatus());
    }

    @Test
    public void should_sort_matched_root_resource_descending_order() {
        GenericEntity entity1 = new GenericEntity("1", String.class);
        GenericEntity entity2 = new GenericEntity("2", String.class);

        ResourceRouter router = new DefaultResourceRouter(runtime, List.of(
                rootResource(matched("/users/1", result("/1", 2)), returns(entity2)),
                rootResource(matched("/users/1", result("/1", 1)), returns(entity1))));

        OutboundResponse response = router.dispatch(request, context);

        assertSame(entity1, response.getGenericEntity());
        assertEquals(200, response.getStatus());
    }

    @Test
    public void should_return_404_if_no_resource_matched() {
        ResourceRouter router = new DefaultResourceRouter(runtime, List.of(
                rootResource(unmatched("/users/1"))));

        OutboundResponse response = router.dispatch(request, context);

        assertNull(response.getGenericEntity());
        assertEquals(404, response.getStatus());
    }

    @Test
    public void should_return_404_if_no_resource_method_found() {
        ResourceRouter router = new DefaultResourceRouter(runtime, List.of(
                rootResource(matched("/users/1", result("/1", 2)))));

        OutboundResponse response = router.dispatch(request, context);

        assertNull(response.getGenericEntity());
        assertEquals(404, response.getStatus());
    }

    @Test
    public void should_return_204_if_method_return_null() {
        ResourceRouter router = new DefaultResourceRouter(runtime, List.of(
                rootResource(matched("/users/1", result("/1", 2)), returns(null))));

        OutboundResponse response = router.dispatch(request, context);

        assertNull(response.getGenericEntity());
        assertEquals(204, response.getStatus());
    }

    private ResourceRouter.Resource rootResource(StudUriTemplate stub) {
        ResourceRouter.Resource unMatched = Mockito.mock(ResourceRouter.Resource.class);
        when(unMatched.getUriTemplate()).thenReturn(stub.uriTemplate);
        when(unMatched.match(same(stub.result), eq("GET"), eq(new String[]{MediaType.WILDCARD}), same(context), eq(builder))).thenReturn(Optional.empty());
        return unMatched;
    }

    private StudUriTemplate unmatched(String path) {
        UriTemplate unMatchedUriTemplate = Mockito.mock(UriTemplate.class);
        when(unMatchedUriTemplate.match(eq(path))).thenReturn(Optional.empty());
        return new StudUriTemplate(unMatchedUriTemplate, null);
    }


    private ResourceRouter.Resource rootResource(StudUriTemplate stub, ResourceRouter.ResourceMethod method) {
        ResourceRouter.Resource matched = Mockito.mock(ResourceRouter.Resource.class);
        when(matched.getUriTemplate()).thenReturn(stub.uriTemplate);
        when(matched.match(same(stub.result), eq("GET"), eq(new String[]{MediaType.WILDCARD}), same(context), eq(builder))).thenReturn(Optional.of(method));
        return matched;
    }

    private ResourceRouter.ResourceMethod returns(GenericEntity entity) {
        ResourceRouter.ResourceMethod method = Mockito.mock(ResourceRouter.ResourceMethod.class);
        when(method.call(same(context), same(builder))).thenReturn(entity);
        return method;
    }

    private StudUriTemplate matched(String path, UriTemplate.MatchResult result) {
        UriTemplate matchedUriTemplate = Mockito.mock(UriTemplate.class);
        when(matchedUriTemplate.match(eq(path))).thenReturn(Optional.of(result));
        return new StudUriTemplate(matchedUriTemplate, result);
    }

    record StudUriTemplate(UriTemplate uriTemplate, UriTemplate.MatchResult result) {
    }

    private UriTemplate.MatchResult result(String path) {
        return new FakeMatchResult(path, 0);
    }

    private UriTemplate.MatchResult result(String path, int order) {
        return new FakeMatchResult(path, order);
    }

    class FakeMatchResult implements UriTemplate.MatchResult {
        private String remaining;
        private Integer order;

        public FakeMatchResult(String remaining, Integer order) {
            this.remaining = remaining;
            this.order = order;
        }

        @Override
        public String getMatched() {
            return null;
        }

        @Override
        public String getRemaining() {
            return remaining;
        }

        @Override
        public Map<String, String> getMatchedPathParameters() {
            return null;
        }

        @Override
        public int compareTo(UriTemplate.MatchResult o) {
            return order.compareTo(((FakeMatchResult) o).order);
        }
    }
}
