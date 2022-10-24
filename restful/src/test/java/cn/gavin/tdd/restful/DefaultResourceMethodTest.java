package cn.gavin.tdd.restful;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.GenericEntity;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.mockito.Mockito;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class DefaultResourceMethodTest {

    private CallableResourceMethods resource;
    private ResourceContext context;
    private UriInfoBuilder builder;
    private UriInfo uriInfo;
    private MultivaluedHashMap<String, String> parameters;

    private LastCall lastCall;

    record LastCall(String name, List<Object> arguments) {
    }

    @BeforeEach
    public void before() {
        lastCall = null;
        resource = (CallableResourceMethods) Proxy.newProxyInstance(this.getClass().getClassLoader(),
                new Class[]{CallableResourceMethods.class}, (proxy, method, args) -> {
                    lastCall = new LastCall(getMethodName(method.getName(),
                            Arrays.stream(method.getParameters()).map(p -> p.getType()).toList()),
                            args != null ? List.of(args) : List.of());

                    return "getList".equals(method.getName()) ? new ArrayList<String>() : null;
                });

        context = Mockito.mock(ResourceContext.class);
        builder = Mockito.mock(UriInfoBuilder.class);
        uriInfo = Mockito.mock(UriInfo.class);
        parameters = new MultivaluedHashMap<>();

        Mockito.when(builder.getLastMatchedResource()).thenReturn(resource);
        Mockito.when(builder.createUriInfo()).thenReturn(uriInfo);
        Mockito.when(uriInfo.getPathParameters()).thenReturn(parameters);
        Mockito.when(uriInfo.getQueryParameters()).thenReturn(parameters);
    }

    private static String getMethodName(String name, List<? extends Class<?>> classStream) {
        return name + "(" + classStream.stream().map(Class::getSimpleName).collect(Collectors.joining(",")) + ")";
    }

    @Test
    public void should_call_resource_method() throws NoSuchMethodException {
        DefaultResourceMethod resourceMethod = getResourceMethod("get");
        resourceMethod.call(context, builder);
        assertEquals("get()", lastCall.name);
    }

    @Test
    public void should_call_resource_method_with_void_return_type() throws NoSuchMethodException {
        DefaultResourceMethod resourceMethod = getResourceMethod("post");

        assertNull(resourceMethod.call(context, builder));
    }

    @Test
    public void should_use_resource_method_generic_return_type() throws NoSuchMethodException {
        DefaultResourceMethod resourceMethod = getResourceMethod("getList");

        assertEquals(new GenericEntity(List.of(), CallableResourceMethods.class.getMethod("getList").getGenericReturnType()), resourceMethod.call(context, builder));
    }

    private DefaultResourceMethod getResourceMethod(String method, Class... types) throws NoSuchMethodException {
        return new DefaultResourceMethod(CallableResourceMethods.class.getMethod(method, types));
    }


    record InjectableTypeTestCase(Class<?> type, String string, Object value) {
    }

    @TestFactory
    public List<DynamicTest> injectableTypes() {
        List<DynamicTest> tests = new ArrayList<>();

        List<InjectableTypeTestCase> typeCases = List.of(
                new InjectableTypeTestCase(String.class, "string", "string"),
                new InjectableTypeTestCase(int.class, "1", 1),
                new InjectableTypeTestCase(double.class, "3.25", 3.25),
                new InjectableTypeTestCase(float.class, "3.25", 3.25f),
                new InjectableTypeTestCase(short.class, "128", (short) 128),
                new InjectableTypeTestCase(byte.class, "42", (byte) 42),
                new InjectableTypeTestCase(boolean.class, "true", true),
                new InjectableTypeTestCase(BigDecimal.class, "12345", new BigDecimal("12345"))
        );

        List<String> paramTypes = List.of("getPathParam", "getQueryParam");

        for (String type : paramTypes)
            for (InjectableTypeTestCase testCase : typeCases) {
                tests.add(DynamicTest.dynamicTest("should inject " + testCase.type.getSimpleName()
                        + " to " + type, () -> verifyResourceMethodCalled(type, testCase.type, testCase.string, testCase.value)));
            }

        return tests;
    }

    private void verifyResourceMethodCalled(String method, Class<?> type, String paramString, Object paramValue) throws NoSuchMethodException {
        DefaultResourceMethod resourceMethod = getResourceMethod(method, type);
        parameters.put("param", List.of(paramString));

        resourceMethod.call(context, builder);

        assertEquals(getMethodName(method, List.of(type)), lastCall.name);
        assertEquals(List.of(paramValue), lastCall.arguments);
    }

    // TODO: 10/24/22 using default converters for path, query, matrix,(uri) form ,header, cookie (request)
    // TODO: 10/24/22 default converters for class with converter constructor
    // TODO: 10/24/22 default converters for class with converter factory 
    // TODO: 10/24/22 default converters for List, Set ,SortSet 
    // TODO: 10/24/22 injection - get injectable from resource context 
    // TODO: 10/24/22 injection - can inject resource context itself 
    // TODO: 10/24/22 injection - can inject uri info built from uri info builder

    interface CallableResourceMethods {
        @POST
        void post();

        @GET
        String get();

        @GET
        List<String> getList();

        @GET
        String getPathParam(@PathParam("param") String value);

        @GET
        String getPathParam(@PathParam("param") int value);

        @GET
        String getPathParam(@PathParam("param") double value);

        @GET
        String getPathParam(@PathParam("param") float value);

        @GET
        String getPathParam(@PathParam("param") short value);

        @GET
        String getPathParam(@PathParam("param") byte value);

        @GET
        String getPathParam(@PathParam("param") boolean value);

        @GET
        String getPathParam(@PathParam("param") BigDecimal value);

        @GET
        String getQueryParam(@QueryParam("param") String value);

        @GET
        String getQueryParam(@QueryParam("param") int value);

        @GET
        String getQueryParam(@QueryParam("param") double value);

        @GET
        String getQueryParam(@QueryParam("param") float value);

        @GET
        String getQueryParam(@QueryParam("param") short value);

        @GET
        String getQueryParam(@QueryParam("param") byte value);

        @GET
        String getQueryParam(@QueryParam("param") boolean value);

        @GET
        String getQueryParam(@QueryParam("param") BigDecimal value);

    }
}
