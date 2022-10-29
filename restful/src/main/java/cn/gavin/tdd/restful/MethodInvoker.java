package cn.gavin.tdd.restful;

import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.UriInfo;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static cn.gavin.tdd.restful.MethodInvoker.ValueConverter.singeValued;
import static java.util.Arrays.stream;

class MethodInvoker {
    private static ValueProvider pathParam = (parameter, uriInfo) -> Optional.ofNullable(parameter.getAnnotation(PathParam.class)).map(annotation -> uriInfo.getPathParameters().get(annotation.value()));
    private static ValueProvider queryParam = (parameter, uriInfo) -> Optional.ofNullable(parameter.getAnnotation(QueryParam.class)).map(annotation -> uriInfo.getQueryParameters().get(annotation.value()));
    private static List<ValueProvider> providers = List.of(pathParam, queryParam);

    static Object invoke(Method method, ResourceContext resourceContext, UriInfoBuilder builder) {
        try {
            UriInfo uriInfo = builder.createUriInfo();

            return method.invoke(builder.getLastMatchedResource(),
                    stream(method.getParameters()).map(parameter ->
                            injectParameters(parameter, uriInfo)
                                    .or(() -> injectContext(parameter, resourceContext, uriInfo))
                                    .orElse(null)).toArray(Object[]::new));

        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof WebApplicationException) throw (WebApplicationException) e.getCause();
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static Optional<Object> injectContext(Parameter parameter, ResourceContext resourceContext, UriInfo uriInfo) {
        if (parameter.getType().equals(ResourceContext.class)) return Optional.of(resourceContext);
        if (parameter.getType().equals(UriInfo.class)) return Optional.of(uriInfo);
        return Optional.of(resourceContext.getResource(parameter.getType()));
    }

    private static Optional<Object> injectParameters(Parameter parameter, UriInfo uriInfo) {
        return providers.stream().map(provider -> provider.provide(parameter, uriInfo)).filter(Optional::isPresent)
                .findFirst()
                .flatMap(values -> values.flatMap(v -> convert(parameter, v)));
    }

    private static Optional<Object> convert(Parameter parameter, List<String> values) {
        return PrimitiveConverter.convert(parameter, values)
                .or(() -> ConverterConstructor.convert(parameter.getType(), values.get(0)))
                .or(() -> ConverterFactory.convert(parameter.getType(), values.get(0)));
    }

    interface ValueProvider {
        Optional<List<String>> provide(Parameter parameter, UriInfo uriInfo);
    }

    interface ValueConverter<T> {
        T fromString(List<String> values);

        static <T> ValueConverter<T> singeValued(Function<String, T> converter) {
            return values -> converter.apply(values.get(0));
        }
    }
}

class PrimitiveConverter {
    private static Map<Type, MethodInvoker.ValueConverter<Object>> primitives = Map.of(
            int.class, singeValued(Integer::parseInt),
            String.class, singeValued(s -> s),
            double.class, singeValued(Double::parseDouble),
            float.class, singeValued(Float::parseFloat),
            short.class, singeValued(Short::parseShort),
            byte.class, singeValued(Byte::parseByte),
            boolean.class, singeValued(Boolean::parseBoolean)
    );

    public static Optional<Object> convert(Parameter parameter, List<String> values) {
        return Optional.ofNullable(primitives.get(parameter.getType()))
                .map(c -> c.fromString(values));
    }
}

class ConverterConstructor {
    public static Optional<Object> convert(Class<?> converter, String value) {
        try {
            return Optional.ofNullable(converter.getConstructor(String.class).newInstance(value));
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                 NoSuchMethodException e) {
            return Optional.empty();
        }
    }
}

class ConverterFactory {
    public static Optional<Object> convert(Class<?> converter, String value) {
        try {
            return Optional.of(converter.getMethod("valueOf", String.class).invoke(null, value));
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            return Optional.empty();
        }
    }
}
