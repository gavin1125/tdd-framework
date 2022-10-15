package cn.gavin.tdd.restful;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.GenericEntity;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.RuntimeDelegate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;
import java.util.Vector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        when(request.getServletPath()).thenReturn("/Users");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeaders(eq(HttpHeaders.ACCEPT))).thenReturn(new Vector<>(List.of(MediaType.WILDCARD)).elements());

        builder = Mockito.mock(UriInfoBuilder.class);
        when(runtime.createUriInfoBuilder(request)).thenReturn(builder);
    }

    // TODO: 10/15/22 根据Path匹配结果，降序排列RootResource，选择第一个的RootResource
    // TODO: 10/15/22 R1, R2, R1 matched ,R2 none R1 
    // TODO: 10/15/22 R1 ,R2 ,R1 ,R2 matched, R1 result < R2 result R1

    @Test
    public void should_use_matched_root_resource() {
        ResourceRouter.RootResource matched = Mockito.mock(ResourceRouter.RootResource.class);
        UriTemplate matchedUriTemplate = Mockito.mock(UriTemplate.class);
        UriTemplate.MatchResult result = Mockito.mock(UriTemplate.MatchResult.class);
        when(matched.getUriTemplate()).thenReturn(matchedUriTemplate);
        when(matchedUriTemplate.match(eq("/Users"))).thenReturn(Optional.of(result));
        ResourceRouter.ResourceMethod method = Mockito.mock(ResourceRouter.ResourceMethod.class);
        when(matched.match(eq("/Users"), eq("GET"), eq(new String[]{MediaType.WILDCARD}), eq(builder))).thenReturn(Optional.of(method));
        GenericEntity entity = new GenericEntity("matched", String.class);
        when(method.call(any(), any())).thenReturn(entity);

        ResourceRouter.RootResource unMatched = Mockito.mock(ResourceRouter.RootResource.class);
        UriTemplate unMatchedUriTemplate = Mockito.mock(UriTemplate.class);
        when(unMatched.getUriTemplate()).thenReturn(unMatchedUriTemplate);
        when(unMatchedUriTemplate.match(eq("/Users"))).thenReturn(Optional.empty());

        ResourceRouter router = new DefaultResourceRouter(runtime, List.of(matched, unMatched));
        OutboundResponse response = router.dispatch(request, context);
        assertSame(entity, response.getGenericEntity());
        assertEquals(200, response.getStatus());
    }

    // TODO: 10/15/22 如果没有匹配的RootResource，则构造404的Response
    // TODO: 10/15/22 如果返回的RootResource中无法匹配剩余Path，则构造404的Response
    // TODO: 10/15/22 如果ResourceMethod返回null，则构造204的Response
}
