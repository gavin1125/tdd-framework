package cn.gavin.tdd.restful;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SubResourceLocatorTest {

    SubResourceMethods resource;
    UriTemplate.MatchResult result;
    ResourceContext context;
    UriInfoBuilder builder;
    UriInfo uriInfo;

    private LastCall lastCall;
    private MultivaluedHashMap<String, String> parameters;

    record LastCall(String name, List<Object> arguments) {
    }

    @BeforeEach
    public void before() {
        lastCall = null;
        resource = (SubResourceMethods) Proxy.newProxyInstance(this.getClass().getClassLoader(),
                new Class[]{SubResourceMethods.class}, (proxy, method, args) -> {
                    lastCall = new LastCall(getMethodName(method.getName(),
                            Arrays.stream(method.getParameters()).map(p -> p.getType()).toList()),
                            args != null ? List.of(args) : List.of());

                    return new Message();
                });
        result = Mockito.mock(UriTemplate.MatchResult.class);
        context = Mockito.mock(ResourceContext.class);
        builder = Mockito.mock(UriInfoBuilder.class);
        uriInfo = Mockito.mock(UriInfo.class);
        parameters = new MultivaluedHashMap<>();

        Mockito.when(builder.getLastMatchedResource()).thenReturn(resource);
        Mockito.when(builder.createUriInfo()).thenReturn(uriInfo);
        Mockito.when(uriInfo.getPathParameters()).thenReturn(parameters);
    }

    @Test
    public void should_inject_string_path_param_to_sub_resource_method() throws NoSuchMethodException {
        Method method = SubResourceMethods.class.getMethod("getPathParam", String.class);
        SubResourceLocators.SubResourceLocator locator = new SubResourceLocators.SubResourceLocator(method);

        parameters.put("param", List.of("path"));

        locator.match(result, "GET", new String[0], context, builder);

        assertEquals("getPathParam(String)", lastCall.name);
        assertEquals(List.of("path"), lastCall.arguments);
    }

    private static String getMethodName(String name, List<? extends Class<?>> classStream) {
        return name + "(" + classStream.stream().map(Class::getSimpleName).collect(Collectors.joining(",")) + ")";
    }


    interface SubResourceMethods {
        @Path("/message/{param}")
        Message getPathParam(@PathParam("param") String path);
    }

    static class Message {
        @GET
        @Produces(MediaType.TEXT_PLAIN)
        public String content() {
            return "content";
        }
    }
}
