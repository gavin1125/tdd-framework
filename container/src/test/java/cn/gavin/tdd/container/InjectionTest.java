package cn.gavin.tdd.container;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.ParameterizedType;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Nested
public class InjectionTest {
    private Dependency dependency = mock(Dependency.class);
    private Context context = mock(Context.class);
    private Provider<Dependency> dependencyProvider = mock(Provider.class);
    private ParameterizedType dependencyProviderType;

    @BeforeEach
    public void setup() throws NoSuchFieldException {
        dependencyProviderType = (ParameterizedType) InjectionTest.class.getDeclaredField("dependencyProvider").getGenericType();
        when(context.get(eq(ComponentRef.of(Dependency.class)))).thenReturn(Optional.of(dependency));
        when(context.get(eq(ComponentRef.of(dependencyProviderType)))).thenReturn(Optional.of(dependencyProvider));
    }

    @Nested
    public class ConstructorInjection {
        static class InjectConstructor {
            Dependency dependency;

            @Inject
            public InjectConstructor(Dependency dependency) {
                this.dependency = dependency;
            }
        }

        @Nested
        class Injection {
            static class DefaultConstructor {

            }

            @Test
            public void should_call_default_constructor_if_no_inject_constructor() {
                DefaultConstructor instance = new InjectionProvider<>(DefaultConstructor.class).get(context);

                assertNotNull(instance);
            }

            @Test
            public void should_inject_dependency_via_inject_constructor() {
                InjectConstructor instance = new InjectionProvider<>(InjectConstructor.class).get(context);
                assertNotNull(instance);
                assertSame(dependency, instance.dependency);
            }

            @Test
            public void should_include_dependency_from_inject_constructor() {
                InjectionProvider<InjectConstructor> provider = new InjectionProvider<>(InjectConstructor.class);
                assertArrayEquals(new ComponentRef[]{ComponentRef.of(Dependency.class)},
                        provider.getDependencies().toArray(ComponentRef[]::new));
            }

            @Test
            public void should_include_provider_type_from_inject_constructor() {
                InjectionProvider<ProviderInjectConstructor> provider = new InjectionProvider<>(ProviderInjectConstructor.class);
                assertArrayEquals(new ComponentRef[]{ComponentRef.of(dependencyProviderType)},
                        provider.getDependencies().toArray(ComponentRef[]::new));
            }

            static class ProviderInjectConstructor {
                Provider<Dependency> dependency;

                @Inject
                public ProviderInjectConstructor(Provider<Dependency> dependencyProvider) {
                    this.dependency = dependencyProvider;
                }
            }

            @Test
            public void should_inject_provider_via_inject_constructor() {
                ProviderInjectConstructor instance = new InjectionProvider<>(ProviderInjectConstructor.class).get(context);
                assertSame(dependencyProvider, instance.dependency);
            }
        }

        @Nested
        class IllegalInjectConstructors {
            abstract class AbstractComponent implements TestComponent {
                @Inject
                public AbstractComponent() {
                }
            }

            @Test
            public void should_throw_exception_if_component_is_abstract() {
                assertThrows(ComponentError.class,
                        () -> new InjectionProvider<>(AbstractComponent.class));
            }

            @Test
            public void should_throw_exception_if_component_is_interface() {
                assertThrows(ComponentError.class, () -> new InjectionProvider<>(TestComponent.class));
            }

            static class MultiInjectConstructors implements TestComponent {
                @Inject
                public MultiInjectConstructors(AnotherDependency dependency) {
                }

                @Inject
                public MultiInjectConstructors(Dependency dependency) {
                }
            }

            @Test
            public void should_throw_exception_if_multi_inject_constructors_provided() {
                assertThrows(ComponentError.class, () -> new InjectionProvider<>(MultiInjectConstructors.class));
            }

            static class NoInjectNorDefaultConstructor implements TestComponent {
                public NoInjectNorDefaultConstructor(Dependency dependency) {
                }
            }

            @Test
            public void should_throw_exception_if_no_inject_nor_default_constructor_provided() {
                assertThrows(ComponentError.class, () -> new InjectionProvider<>(NoInjectNorDefaultConstructor.class));
            }
        }

        @Nested
        class WithQualifier {
            @BeforeEach
            public void before() {
                Mockito.reset(context);
                when(context.get(eq(ComponentRef.of(Dependency.class, new NameLiteral("ChosenOne"))))).thenReturn(Optional.of(dependency));
            }

            static class InjectConstructor {
                Dependency dependency;

                @Inject
                public InjectConstructor(@Named("ChosenOne") Dependency dependency) {
                    this.dependency = dependency;
                }
            }

            @Test
            public void should_inject_dependency_with_qualifier_via_constructor() {
                InjectionProvider<InjectConstructor> provider = new InjectionProvider<>(InjectConstructor.class);
                InjectConstructor component = provider.get(context);
                assertSame(dependency, component.dependency);
            }

            @Test
            public void should_include_dependency_with_qualifier() {
                InjectionProvider<InjectConstructor> provider = new InjectionProvider<>(InjectConstructor.class);
                assertArrayEquals(new ComponentRef<?>[]{ComponentRef.of(Dependency.class, new NameLiteral("ChosenOne"))},
                        provider.getDependencies().toArray());
            }

            static class MultiQualifierInjectConstructor {
                @Inject
                public MultiQualifierInjectConstructor(@Named("ChosenOne") @Skywalker Dependency dependency) {
                }
            }

            @Test
            public void should_throw_exception_if_multi_qualifiers_given() {
                assertThrows(ComponentError.class, () -> new InjectionProvider<>(MultiQualifierInjectConstructor.class));
            }
        }
    }

    @Nested
    public class FieldInjection {
        @Nested
        class Injection {
            static class ComponentWithFieldInjection {
                @Inject
                Dependency dependency;
            }

            static class SubclassWithFieldInjection extends ComponentWithFieldInjection {
            }

            @Test
            public void should_inject_dependency_via_field() {
                ComponentWithFieldInjection component = new InjectionProvider<>(ComponentWithFieldInjection.class).get(context);

                assertSame(dependency, component.dependency);
            }

            @Test
            public void should_inject_dependency_via_superclass_inject_field() {
                SubclassWithFieldInjection component = new InjectionProvider<>(SubclassWithFieldInjection.class).get(context);

                assertSame(dependency, component.dependency);
            }

            @Test
            public void should_include_dependency_from_field_dependencies() {
                InjectionProvider<ComponentWithFieldInjection> provider = new InjectionProvider<>(ComponentWithFieldInjection.class);
                assertArrayEquals(new ComponentRef[]{ComponentRef.of(Dependency.class)},
                        provider.getDependencies().toArray(ComponentRef[]::new));
            }

            @Test
            public void should_include_provider_type_from_inject_field() {
                InjectionProvider<ProviderInjectField> provider = new InjectionProvider<>(ProviderInjectField.class);
                assertArrayEquals(new ComponentRef[]{ComponentRef.of(dependencyProviderType)},
                        provider.getDependencies().toArray(ComponentRef[]::new));
            }

            static class ProviderInjectField {
                @Inject
                Provider<Dependency> dependency;
            }

            @Test
            public void should_inject_provider_via_inject_field() {
                FieldInjection.Injection.ProviderInjectField instance = new InjectionProvider<>(FieldInjection.Injection.ProviderInjectField.class).get(context);
                assertSame(dependencyProvider, instance.dependency);
            }
        }

        @Nested
        class IllegalInjectFields {
            static class FinalInjectField {
                @Inject
                final Dependency dependency = null;
            }

            @Test
            public void should_throw_exception_if_inject_field_is_final() {
                assertThrows(ComponentError.class,
                        () -> new InjectionProvider<>(FinalInjectField.class));
            }
        }

        @Nested
        class WithQualifier {
            @BeforeEach
            public void before() {
                Mockito.reset(context);
                when(context.get(eq(ComponentRef.of(Dependency.class, new NameLiteral("ChosenOne"))))).thenReturn(Optional.of(dependency));
            }

            static class InjectField {
                @Inject
                @Named("ChosenOne")
                Dependency dependency;
            }

            @Test
            public void should_inject_dependency_with_qualifier_via_field() {
                InjectionProvider<InjectField> provider = new InjectionProvider<>(InjectField.class);
                InjectField component = provider.get(context);
                assertSame(dependency, component.dependency);
            }

            @Test
            public void should_include_dependency_with_qualifier() {
                InjectionProvider<InjectField> provider = new InjectionProvider<>(InjectField.class);
                assertArrayEquals(new ComponentRef<?>[]{ComponentRef.of(Dependency.class, new NameLiteral("ChosenOne"))},
                        provider.getDependencies().toArray());
            }

            static class MultiQualifierInjectField {
                @Inject
                @Named("ChosenOne")
                @Skywalker
                Dependency dependency;
            }

            @Test
            public void should_throw_exception_if_multi_qualifiers_given() {
                assertThrows(ComponentError.class, () -> new InjectionProvider<>(MultiQualifierInjectField.class));
            }
        }
    }

    @Nested
    public class MethodInjection {

        @Nested
        class Injection {
            static class InjectMethodWithNoDependency {
                boolean called = false;

                @Inject
                void install() {
                    this.called = true;
                }

            }

            @Test
            public void should_call_inject_method_even_if_no_dependency_declared() {
                InjectMethodWithNoDependency component = new InjectionProvider<>(InjectMethodWithNoDependency.class).get(context);

                assertTrue(component.called);
            }

            static class InjectMethodWithDependency {
                Dependency dependency;

                @Inject
                void install(Dependency dependency) {
                    this.dependency = dependency;
                }
            }

            @Test
            public void should_inject_dependency_via_inject_method() {
                InjectMethodWithDependency component = new InjectionProvider<>(InjectMethodWithDependency.class).get(context);

                assertSame(dependency, component.dependency);
            }

            static class SuperClassWithInjectMethod {
                int superCalled = 0;

                @Inject
                void install() {
                    superCalled++;
                }
            }

            static class SubclassWithInjectMethod extends SuperClassWithInjectMethod {
                int subCalled = 0;

                @Inject
                void installAnother() {
                    subCalled = superCalled + 1;
                }
            }

            @Test
            public void should_inject_dependencies_via_inject_method_from_superclass() {
                SubclassWithInjectMethod component = new InjectionProvider<>(SubclassWithInjectMethod.class).get(context);
                assertEquals(2, component.subCalled);
                assertEquals(1, component.superCalled);
            }

            static class SubclassOverrideSuperclassWithInject extends SubclassWithInjectMethod {
                @Inject
                void install() {
                    super.install();
                }
            }

            @Test
            public void should_only_call_once_if_subclass_override_inject_method_with_inject() {
                SubclassOverrideSuperclassWithInject component = new InjectionProvider<>(SubclassOverrideSuperclassWithInject.class).get(context);
                assertEquals(1, component.superCalled);
            }

            static class SubclassOverrideSuperClassWithNoInject extends SubclassWithInjectMethod {
                void install() {
                    super.install();
                }
            }

            @Test
            public void should_not_call_inject_method_if_override_with_no_inject() {
                SubclassOverrideSuperClassWithNoInject component =
                        new InjectionProvider<>(SubclassOverrideSuperClassWithNoInject.class).get(context);
                assertEquals(0, component.superCalled);
            }

            @Test
            public void should_include_dependencies_from_inject_method() {
                InjectionProvider<InjectMethodWithDependency> provider = new InjectionProvider<>(InjectMethodWithDependency.class);

                assertArrayEquals(new ComponentRef[]{ComponentRef.of(Dependency.class)},
                        provider.getDependencies().toArray(ComponentRef[]::new));
            }

            @Test
            public void should_include_provider_type_from_inject_method() {
                InjectionProvider<ProviderInjectMethod> provider = new InjectionProvider<>(ProviderInjectMethod.class);
                assertArrayEquals(new ComponentRef[]{ComponentRef.of(dependencyProviderType)},
                        provider.getDependencies().toArray(ComponentRef[]::new));
            }

            static class ProviderInjectMethod {
                Provider<Dependency> dependency;

                @Inject
                void install(Provider<Dependency> dependencyProvider) {
                    this.dependency = dependencyProvider;
                }
            }

            @Test
            public void should_inject_provider_via_inject_method() {
                MethodInjection.Injection.ProviderInjectMethod instance = new InjectionProvider<>(MethodInjection.Injection.ProviderInjectMethod.class).get(context);
                assertSame(dependencyProvider, instance.dependency);
            }
        }

        @Nested
        class IllegalInjectMethods {
            static class InjectMethodWithTypeParameter {
                @Inject
                <T> void install() {
                }
            }

            @Test
            public void should_throw_exception_if_inject_method_has_type_method() {
                assertThrows(ComponentError.class,
                        () -> new InjectionProvider<>(InjectMethodWithTypeParameter.class));
            }
        }

        @Nested
        class WithQualifier {
            @BeforeEach
            public void before() {
                Mockito.reset(context);
                when(context.get(eq(ComponentRef.of(Dependency.class, new NameLiteral("ChosenOne"))))).thenReturn(Optional.of(dependency));
            }

            static class InjectMethod {
                Dependency dependency;

                @Inject
                void install(@Named("ChosenOne") Dependency dependency) {
                    this.dependency = dependency;
                }
            }

            @Test
            public void should_inject_dependency_with_qualifier_via_method() {
                InjectionProvider<InjectMethod> provider = new InjectionProvider<>(InjectMethod.class);
                InjectMethod component = provider.get(context);
                assertSame(dependency, component.dependency);
            }

            @Test
            public void should_include_dependency_with_qualifier() {
                InjectionProvider<InjectMethod> provider = new InjectionProvider<>(InjectMethod.class);
                assertArrayEquals(new ComponentRef<?>[]{ComponentRef.of(Dependency.class, new NameLiteral("ChosenOne"))},
                        provider.getDependencies().toArray());
            }

            static class MultiQualifierInjectMethod {
                @Inject
                void install(@Named("ChosenOne") @Skywalker Dependency dependency) {
                }
            }

            @Test
            public void should_throw_exception_if_multi_qualifiers_given() {
                assertThrows(ComponentError.class, () -> new InjectionProvider<>(MultiQualifierInjectMethod.class));
            }
        }
    }
}


