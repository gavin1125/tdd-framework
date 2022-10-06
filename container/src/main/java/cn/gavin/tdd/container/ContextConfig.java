package cn.gavin.tdd.container;

import jakarta.inject.Provider;
import jakarta.inject.Qualifier;
import jakarta.inject.Scope;
import jakarta.inject.Singleton;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.text.MessageFormat;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static cn.gavin.tdd.container.ContextConfigException.duplicated;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;

public class ContextConfig {
    private Map<Component, ComponentProvider<?>> components = new HashMap<>();
    private Map<Class<?>, ScopeProvider> scopes = new HashMap<>();

    public ContextConfig() {
        scope(Singleton.class, SingletonProvider::new);
    }

    public <Type> void instance(Class<Type> type, Type instance) {
        bind(new Component(type, null), context -> instance);
    }

    public <Type> void instance(Class<Type> type, Type instance, Annotation... annotations) {
        bindInstance(type, instance, annotations);
    }

    public <Type, Implementation extends Type>
    void component(Class<Type> type, Class<Implementation> implementation, Annotation... annotations) {
        bindComponent(type, implementation, annotations);
    }

    public <ScopeType extends Annotation> void scope(Class<ScopeType> scope, ScopeProvider provider) {
        scopes.put(scope, provider);
    }

    public void from(Config config) {
        new DSL(config).bind();
    }

    public Context getContext() {
        components.keySet().forEach(component -> checkDependencies(component, new Stack<>()));
        HashMap<Component, ComponentProvider<?>> context = new HashMap<>(components);

        return new Context() {
            @Override
            public <ComponentType> Optional<ComponentType> get(ComponentRef<ComponentType> ref) {
                if (ref.isContainer()) {
                    if (ref.getContainer() != Provider.class) return Optional.empty();

                    return (Optional<ComponentType>) Optional.ofNullable(getProvider(ref))
                            .map(provider -> (Provider<Object>) () -> provider.get(this));
                }
                return Optional.ofNullable(getProvider(ref)).map(provider -> (ComponentType) provider.get(this));
            }

            private <ComponentType> ComponentProvider<?> getProvider(ComponentRef<ComponentType> ref) {
                return context.get(ref.component());
            }
        };
    }

    private void bindComponent(Class<?> type, Class<?> implementation, Annotation[] annotations) {
        Bindings bindings = Bindings.component(implementation, annotations);
        bind(type, bindings.qualifiers(), provider(implementation, bindings.scope()));
    }

    private void bindInstance(Class<?> type, Object instance, Annotation[] annotations) {
        bind(type, Bindings.instance(type, annotations).qualifiers(), context -> instance);
    }

    private <Type> void bind(Class<Type> type, List<Annotation> qualifiers, ComponentProvider<?> provider) {
        if (qualifiers.isEmpty()) components.put(new Component(type, null), provider);
        for (Annotation qualifier : qualifiers)
            bind(new Component(type, qualifier), provider);
    }

    private void bind(Component component, ComponentProvider<?> provider) {
        if (components.containsKey(component)) throw duplicated(component);
        components.put(component, provider);
    }

    private <Type> ComponentProvider<?> provider(Class<Type> implementation, Optional<Annotation> scope) {
        InjectionProvider<Type> injectionProvider = new InjectionProvider<>(implementation);
        return scope.<ComponentProvider<?>>map(s -> scoped(s, injectionProvider)).orElse(injectionProvider);
    }

    private ComponentProvider<?> scoped(Annotation scope, ComponentProvider<?> provider) {
        if (!scopes.containsKey(scope.annotationType()))
            throw ContextConfigException.unknownScope(scope.annotationType());
        return scopes.get(scope.annotationType()).create(provider);
    }

    private void checkDependencies(Component component, Stack<Component> visiting) {
        for (ComponentRef dependency : components.get(component).getDependencies()) {
            if (!components.containsKey(dependency.component()))
                throw ContextConfigError.unsatisfiedResolution(component, dependency.component());
            if (!dependency.isContainer()) {
                if (visiting.contains(dependency.component()))
                    throw ContextConfigError.circularDependencies(visiting, dependency.component());
                visiting.push(dependency.component());
                checkDependencies(dependency.component(), visiting);
                visiting.pop();
            }
        }
    }

    static class Bindings {
        public static Bindings component(Class<?> component, Annotation[] annotations) {
            return new Bindings(component, annotations, Qualifier.class, Scope.class);
        }

        public static Bindings instance(Class<?> instance, Annotation... annotations) {
            return new Bindings(instance, annotations, Qualifier.class);
        }

        Class<?> type;
        Map<Class<?>, List<Annotation>> group;

        public Bindings(Class<?> type, Annotation[] annotations,
                        Class<? extends Annotation>... allowed) {
            this.type = type;
            this.group = parse(type, annotations, allowed);
        }

        private static Map<Class<?>, List<Annotation>> parse(Class<?> type, Annotation[] annotations, Class<? extends Annotation>... allowed) {
            Map<Class<?>, List<Annotation>> annotationGroups = stream(annotations).collect(Collectors.groupingBy(allow(allowed), Collectors.toList()));

            if (annotationGroups.containsKey(Illegal.class))
                throw ContextConfigException.illegalAnnotation(type, annotationGroups.get(Illegal.class));
            return annotationGroups;
        }

        private static <Type> List<Annotation> from(Class<Type> implementation, Class<? extends Annotation> annotation) {
            return stream(implementation.getAnnotations()).filter(a -> a.annotationType().isAnnotationPresent(annotation)).toList();
        }

        Optional<Annotation> scope() {
            List<Annotation> scopes = this.group.getOrDefault(Scope.class, from(type, Scope.class));
            if (scopes.size() > 1) throw ContextConfigException.illegalAnnotation(type, scopes);
            return scopes.stream().findFirst();
        }

        List<Annotation> qualifiers() {
            return this.group.getOrDefault(Qualifier.class, List.of());
        }


        private static Function<Annotation, Class<?>> allow(Class<? extends Annotation>... annotations) {
            return annotation -> Stream.of(annotations).filter(annotation.annotationType()::isAnnotationPresent).findFirst().orElse(Illegal.class);
        }

        private @interface Illegal {
        }
    }

    record Component(Class<?> type, Annotation qualifier) {
    }

    class DSL {
        private Config config;

        public DSL(Config config) {
            this.config = config;
        }

        void bind() {
            for (Declaration declaration : declarations())
                declaration.value().ifPresentOrElse(declaration::bindInstance, declaration::bindComponent);
        }

        private List<Declaration> declarations() {
            return stream(config.getClass().getDeclaredFields()).filter(f -> !f.isSynthetic()).map(Declaration::new).toList();
        }

        class Declaration {
            private Field field;

            Declaration(Field field) {
                this.field = field;
            }

            void bindInstance(Object instance) {
                ContextConfig.this.bindInstance(type(), instance, annotations());
            }

            void bindComponent() {
                ContextConfig.this.bindComponent(type(), field.getType(), annotations());
            }

            private Optional<Object> value() {
                try {
                    field.setAccessible(true);
                    return Optional.ofNullable(field.get(config));
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }

            private Class<?> type() {
                Config.Export export = field.getAnnotation(Config.Export.class);
                return export != null ? export.value() : field.getType();
            }

            private Annotation[] annotations() {
                return stream(field.getAnnotations()).filter(a -> a.annotationType() != Config.Export.class).toArray(Annotation[]::new);
            }
        }
    }
}


class ContextConfigException extends RuntimeException {
    static ContextConfigException illegalAnnotation(Class type, List annotations) {
        return new ContextConfigException(MessageFormat.format("Unqualified annotations: {0} of {1}", String.join(" , ", annotations.stream().map(Object::toString).toList()), type));
    }

    static ContextConfigException unknownScope(Class annotationType) {
        return new ContextConfigException(MessageFormat.format("Unknown scope: {0}", annotationType));
    }

    static ContextConfigException duplicated(ContextConfig.Component component) {
        return new ContextConfigException(MessageFormat.format("Duplicated: {0}", component));
    }

    ContextConfigException(String message) {
        super(message);
    }
}

class ContextConfigError extends Error {
    public static ContextConfigError unsatisfiedResolution(ContextConfig.Component component, ContextConfig.Component dependency) {
        return new ContextConfigError(MessageFormat.format("Unsatisfied resolution: {1} for {0} ", component, dependency));
    }

    public static ContextConfigError circularDependencies(Collection path, ContextConfig.Component circular) {
        return new ContextConfigError(MessageFormat.format("Circular dependencies: {0} -> [{1}]", path.stream().map(Objects::toString).collect(joining(" -> ")), circular));
    }

    ContextConfigError(String message) {
        super(message);
    }
}

