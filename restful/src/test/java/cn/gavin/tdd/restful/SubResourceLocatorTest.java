package cn.gavin.tdd.restful;

import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class SubResourceLocatorTest extends InjectableCallerTest {
    UriTemplate.MatchResult result;

    Map<String, String> matchedPathParameters = Map.of("param", "param");

    @BeforeEach
    public void before() {
        super.before();
        result = Mockito.mock(UriTemplate.MatchResult.class);
        Mockito.when(result.getMatchedPathParameters()).thenReturn(matchedPathParameters);
    }

    @Override
    protected Object initResource() {
        return Proxy.newProxyInstance(this.getClass().getClassLoader(),
                new Class[]{SubResourceMethods.class}, (proxy, method, args) -> {
                    lastCall = new LastCall(getMethodName(method.getName(),
                            Arrays.stream(method.getParameters()).map(p -> p.getType()).toList()),
                            args != null ? List.of(args) : List.of());

                    if (method.getName().equals("throwWebApplicationException"))
                        throw new WebApplicationException(300);
                    return new Message();
                });
    }


    @Override
    protected void callInjectable(String method, Class<?> type) throws NoSuchMethodException {
        SubResourceLocators.SubResourceLocator locator = new SubResourceLocators.SubResourceLocator(SubResourceMethods.class.getMethod(method, type));
        locator.match(result, "GET", new String[0], context, builder);
    }

    @Test
    public void should_add_matched_path_parameter_to_builder() throws NoSuchMethodException {
        parameters.put("param", List.of("param"));
        callInjectable("getPathParam", String.class);
        Mockito.verify(builder).addMatchedPathParameters(matchedPathParameters);
    }

    @Test
    public void should_not_wrap_around_web_application_exception() throws NoSuchMethodException {
        parameters.put("param", List.of("param"));

        try {
            callInjectable("throwWebApplicationException", String.class);
        } catch (WebApplicationException e) {
            assertEquals(300, e.getResponse().getStatus());
        } catch (Exception e) {
            fail();
        }
    }

    interface SubResourceMethods {
        @Path("/message/{param}")
        Message getPathParam(@PathParam("param") String path);

        @Path("/message/{param}")
        Message getPathParam(@PathParam("param") int path);

        @Path("/message/{param}")
        Message getPathParam(@PathParam("param") double path);

        @Path("/message/{param}")
        Message getPathParam(@PathParam("param") float path);

        @Path("/message/{param}")
        Message getPathParam(@PathParam("param") short path);

        @Path("/message/{param}")
        Message getPathParam(@PathParam("param") byte path);

        @Path("/message/{param}")
        Message getPathParam(@PathParam("param") boolean path);

        @Path("/message/{param}")
        Message getPathParam(@PathParam("param") BigDecimal path);

        @Path("/message/{param}")
        Message getPathParam(@PathParam("param") Converter path);

        @Path("/message/")
        Message getQueryParam(@QueryParam("param") String path);

        @Path("/message/")
        Message getQueryParam(@QueryParam("param") int path);

        @Path("/message/")
        Message getQueryParam(@QueryParam("param") double path);

        @Path("/message/")
        Message getQueryParam(@QueryParam("param") float path);

        @Path("/message/")
        Message getQueryParam(@QueryParam("param") short path);

        @Path("/message/")
        Message getQueryParam(@QueryParam("param") byte path);

        @Path("/message/")
        Message getQueryParam(@QueryParam("param") boolean path);

        @Path("/message/")
        Message getQueryParam(@QueryParam("param") BigDecimal path);

        @Path("/message/")
        Message getQueryParam(@QueryParam("param") Converter value);

        @Path("/message/")
        Message getContext(@Context SomeServiceInContext service);

        @Path("/message/")
        Message getContext(@Context ResourceContext context);

        @Path("/message/")
        Message getContext(@Context UriInfo context);

        @Path("/messages/{param}")
        Message throwWebApplicationException(@PathParam("param") String path);
    }

    static class Message {
        @GET
        @Produces(MediaType.TEXT_PLAIN)
        public String content() {
            return "content";
        }
    }
}
