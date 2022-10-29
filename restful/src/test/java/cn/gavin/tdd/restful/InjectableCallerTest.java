package cn.gavin.tdd.restful;

import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.RuntimeDelegate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public abstract class InjectableCallerTest {
    protected ResourceContext context;
    protected UriInfoBuilder builder;
    protected UriInfo uriInfo;
    protected MultivaluedHashMap<String, String> parameters;
    protected DefaultResourceMethodTest.LastCall lastCall;
    SomeServiceInContext service;
    private Object resource;
    protected RuntimeDelegate delegate;

    @BeforeEach
    public void before() {
        lastCall = null;
        resource = initResource();

        context = Mockito.mock(ResourceContext.class);
        builder = Mockito.mock(UriInfoBuilder.class);
        uriInfo = Mockito.mock(UriInfo.class);
        parameters = new MultivaluedHashMap<>();
        service = Mockito.mock(SomeServiceInContext.class);

        Mockito.when(builder.getLastMatchedResource()).thenReturn(resource);
        Mockito.when(builder.createUriInfo()).thenReturn(uriInfo);
        Mockito.when(uriInfo.getPathParameters()).thenReturn(parameters);
        Mockito.when(uriInfo.getQueryParameters()).thenReturn(parameters);
        Mockito.when(context.getResource(eq(SomeServiceInContext.class))).thenReturn(service);

        delegate = Mockito.mock(RuntimeDelegate.class);
        RuntimeDelegate.setInstance(delegate);
        when(delegate.createResponseBuilder()).thenReturn(new StudResponseBuilder());
    }


    protected abstract Object initResource();

    protected static String getMethodName(String name, List<? extends Class<?>> classStream) {
        return name + "(" + classStream.stream().map(Class::getSimpleName).collect(Collectors.joining(",")) + ")";
    }

    @TestFactory
    public List<DynamicTest> inject_convertable_types() {
        List<DynamicTest> tests = new ArrayList<>();

        List<DefaultResourceMethodTest.InjectableTypeTestCase> typeCases = List.of(
                new DefaultResourceMethodTest.InjectableTypeTestCase(String.class, "string", "string"),
                new DefaultResourceMethodTest.InjectableTypeTestCase(int.class, "1", 1),
                new DefaultResourceMethodTest.InjectableTypeTestCase(double.class, "3.25", 3.25),
                new DefaultResourceMethodTest.InjectableTypeTestCase(float.class, "3.25", 3.25f),
                new DefaultResourceMethodTest.InjectableTypeTestCase(short.class, "128", (short) 128),
                new DefaultResourceMethodTest.InjectableTypeTestCase(byte.class, "42", (byte) 42),
                new DefaultResourceMethodTest.InjectableTypeTestCase(boolean.class, "true", true),
                new DefaultResourceMethodTest.InjectableTypeTestCase(BigDecimal.class, "12345", new BigDecimal("12345")),
                new DefaultResourceMethodTest.InjectableTypeTestCase(Converter.class, "Factory", Converter.Factory)
        );

        List<String> paramTypes = List.of("getPathParam", "getQueryParam");

        for (String type : paramTypes)
            for (DefaultResourceMethodTest.InjectableTypeTestCase testCase : typeCases) {
                tests.add(DynamicTest.dynamicTest("should inject " + testCase.type().getSimpleName()
                        + " to " + type, () -> verifyResourceMethodCalled(type, testCase.type(), testCase.string(), testCase.value())));
            }

        return tests;
    }

    @TestFactory
    public List<DynamicTest> inject_context_object() {
        List<DynamicTest> tests = new ArrayList<>();
        List<DefaultResourceMethodTest.InjectableTypeTestCase> typeCases = List.of(
                new DefaultResourceMethodTest.InjectableTypeTestCase(SomeServiceInContext.class, "N/A", service),
                new DefaultResourceMethodTest.InjectableTypeTestCase(ResourceContext.class, "N/A", context),
                new DefaultResourceMethodTest.InjectableTypeTestCase(UriInfo.class, "N/A", uriInfo)
        );

        for (DefaultResourceMethodTest.InjectableTypeTestCase testCase : typeCases) {
            tests.add(DynamicTest.dynamicTest("should inject " + testCase.type().getSimpleName()
                    + " to getContext", () -> verifyResourceMethodCalled("getContext", testCase.type(), testCase.string(), testCase.value())));
        }

        return tests;
    }

    private void verifyResourceMethodCalled(String method, Class<?> type, String paramString, Object paramValue) throws NoSuchMethodException {
        parameters.put("param", List.of(paramString));

        callInjectable(method, type);

        assertEquals(InjectableCallerTest.getMethodName(method, List.of(type)), lastCall.name());
        assertEquals(List.of(paramValue), lastCall.arguments());
    }

    protected abstract void callInjectable(String method, Class<?> type) throws NoSuchMethodException;

    record LastCall(String name, List<Object> arguments) {
    }

    record InjectableTypeTestCase(Class<?> type, String string, Object value) {
    }
}
