package cn.gavin.tdd.container;

import jakarta.inject.*;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

interface AnotherDependency {
}

interface Dependency {
}

interface TestComponent {
    default Dependency dependency() {
        return null;
    }
}

record TestLiteral() implements Test {

    @Override
    public Class<? extends Annotation> annotationType() {
        return Test.class;
    }
}

record NameLiteral(String value) implements jakarta.inject.Named {
    @Override
    public Class<? extends Annotation> annotationType() {
        return jakarta.inject.Named.class;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof jakarta.inject.Named named) return Objects.equals(value, named.value());
        return false;
    }

    @Override
    public int hashCode() {
        return "value".hashCode() * 127 ^ value.hashCode();
    }
}

@Documented
@Retention(RUNTIME)
@Qualifier
@interface Skywalker {
}

record SkywalkerLiteral() implements Skywalker {

    @Override
    public Class<? extends Annotation> annotationType() {
        return Skywalker.class;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Skywalker;
    }
}

record SingletonLiteral() implements Singleton {
    @Override
    public Class<? extends Annotation> annotationType() {
        return Singleton.class;
    }
}

@Scope
@Documented
@Retention(RUNTIME)
@interface Pooled {
}

record PooledLiteral() implements Pooled {

    @Override
    public Class<? extends Annotation> annotationType() {
        return Pooled.class;
    }
}

class PooledProvider<T> implements ComponentProvider<T> {
    static int MAX = 2;

    private List<T> pool = new ArrayList<>();
    int current;
    private ComponentProvider<T> provider;

    public PooledProvider(ComponentProvider<T> provider) {
        this.provider = provider;
    }

    @Override
    public T get(Context context) {
        if (pool.size() < MAX) pool.add(provider.get(context));
        return pool.get(current++ % MAX);
    }

    @Override
    public List<ComponentRef<?>> getDependencies() {
        return provider.getDependencies();
    }
}

class NotCyclicInjectConstructor implements Dependency {

    @Inject
    public NotCyclicInjectConstructor(@Skywalker Dependency dependency) {
    }
}

class SkywalkerInjectConstructor implements Dependency {
    @Inject
    public SkywalkerInjectConstructor(@jakarta.inject.Named("ChosenOne") Dependency dependency) {
    }
}

class NotCyclicInjectField implements Dependency {

    @Inject
    @Skywalker
    Dependency dependency;
}

class SkywalkerInjectField implements Dependency {
    @Inject
    @jakarta.inject.Named("ChosenOne")
    Dependency dependency;
}


class NotCyclicInjectMethod implements Dependency {

    @Inject
    void install(@Skywalker Dependency dependency) {
    }
}

class SkywalkerInjectMethod implements Dependency {
    @Inject
    void install(@jakarta.inject.Named("ChosenOne") Dependency dependency) {
    }
}

class InjectConstructor implements TestComponent {
    @Inject
    public InjectConstructor(@Skywalker Dependency dependency) {
    }
}

class InjectField implements TestComponent {
    @Inject
    @Skywalker
    Dependency dependency;
}

class InjectMethod implements TestComponent {
    @Inject
    void install(@Skywalker Dependency dependency) {
    }
}

class InjectConstructorProvider implements TestComponent {
    @Inject
    public InjectConstructorProvider(@Skywalker Provider<Dependency> dependency) {
    }
}

class InjectFieldProvider {
    @Inject
    @Skywalker
    Provider<Dependency> dependency;
}

class InjectMethodProvider {
    @Inject
    void install(@Skywalker Provider<Dependency> dependency) {
    }
}


