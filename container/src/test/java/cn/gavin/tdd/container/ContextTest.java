package cn.gavin.tdd.container;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class ContextTest {
    private ContextConfig config;
    TestComponent instance;
    Dependency dependency;

    @BeforeEach
    public void setup() {
        config = new ContextConfig();
        instance = new TestComponent() {
        };
        dependency = new Dependency() {
        };
    }

    @Nested
    class TypeBinding {
        @Test
        public void should_bind_type_to_a_specific_instance() {
            config.instance(TestComponent.class, instance);

            Context context = config.getContext();
            assertSame(instance, context.get(ComponentRef.of(TestComponent.class)).get());
        }

        @ParameterizedTest(name = "supporting {0}")
        @MethodSource
        public void should_bind_type_to_an_injectable_component(Class<? extends TestComponent> componentType) {
            config.instance(Dependency.class, dependency);
            config.component(TestComponent.class, componentType);

            Optional<TestComponent> component = config.getContext().get(ComponentRef.of(TestComponent.class));

            assertTrue(component.isPresent());
            assertSame(dependency, component.get().dependency());
        }

        public static Stream<Arguments> should_bind_type_to_an_injectable_component() {
            return Stream.of(Arguments.of(Named.of("Constructor Injection", TypeBinding.ConstructorInjection.class)), Arguments.of(Named.of("Field Injection", TypeBinding.FieldInjection.class)), Arguments.of(Named.of("Method Injection", TypeBinding.MethodInjection.class)));
        }

        static class ConstructorInjection implements TestComponent {
            private Dependency dependency;

            @Inject
            public ConstructorInjection(Dependency dependency) {
                this.dependency = dependency;
            }

            @Override
            public Dependency dependency() {
                return dependency;
            }
        }

        static class FieldInjection implements TestComponent {
            @Inject
            Dependency dependency;

            @Override
            public Dependency dependency() {
                return dependency;
            }
        }

        static class MethodInjection implements TestComponent {
            private Dependency dependency;

            @Inject
            void install(Dependency dependency) {
                this.dependency = dependency;
            }

            @Override
            public Dependency dependency() {
                return dependency;
            }
        }

        @Test
        public void should_retrieve_empty_for_unbind_type() {
            Optional<TestComponent> component = config.getContext().get(ComponentRef.of(TestComponent.class));
            assertTrue(component.isEmpty());
        }

        @Test
        public void should_retrieve_bind_type_as_provider() {
            config.instance(TestComponent.class, instance);

            Context context = config.getContext();

            Provider<TestComponent> provider =
                    context.get(new ComponentRef<Provider<TestComponent>>() {
                    }).get();
            assertSame(instance, provider.get());
        }

        @Test
        public void should_not_retrieve_bind_type_as_unsupported_container() {
            config.instance(TestComponent.class, instance);

            Context context = config.getContext();

            assertFalse(context.get(new ComponentRef<List<TestComponent>>() {
            }).isPresent());
        }

        @Nested
        public class WithQualifier {
            @Test
            public void should_bind_instance_with_multi_qualifiers() {
                config.instance(TestComponent.class, instance, new NameLiteral("ChosenOne"), new SkywalkerLiteral());

                Context context = config.getContext();
                TestComponent chosenOne = context.get(ComponentRef.of(TestComponent.class, new NameLiteral("ChosenOne"))).get();
                TestComponent skywalker = context.get(ComponentRef.of(TestComponent.class, new SkywalkerLiteral())).get();
                assertSame(instance, chosenOne);
                assertSame(instance, skywalker);
            }

            @Test
            public void should_bind_component_with_multi_qualifiers() {
                config.instance(Dependency.class, dependency);
                config.component(ConstructorInjection.class, ConstructorInjection.class, new NameLiteral(
                        "ChosenOne"), new SkywalkerLiteral());

                Context context = config.getContext();
                ConstructorInjection chosenOne = context.get(ComponentRef.of(ConstructorInjection.class, new NameLiteral("ChosenOne"))).get();
                ConstructorInjection skywalker = context.get(ComponentRef.of(ConstructorInjection.class, new SkywalkerLiteral())).get();
                assertSame(dependency, chosenOne.dependency);
                assertSame(dependency, skywalker.dependency);
            }

            @Test
            public void should_retrieve_bind_type_as_provider() {
                config.instance(TestComponent.class, instance, new NameLiteral("ChosenOne"), new SkywalkerLiteral());

                Optional<Provider<TestComponent>> provider = config.getContext().get(new ComponentRef<>(new SkywalkerLiteral()) {
                });
                assertTrue(provider.isPresent());
            }

            @Test
            public void should_retrieve_empty_if_no_matched_qualifiers() {
                config.instance(TestComponent.class, instance);
                Optional<TestComponent> component = config.getContext().get(ComponentRef.of(TestComponent.class, new SkywalkerLiteral()));
                assertTrue(component.isEmpty());
            }

            @Test
            public void should_throw_exception_if_illegal_qualifier_given_to_instance() {
                TestComponent instance = new TestComponent() {
                };
                assertThrows(ContextConfigException.class, () -> config.instance(TestComponent.class, instance, new TestLiteral()));
            }

            @Test
            public void should_throw_exception_if_illegal_qualifier_given_to_component() {
                assertThrows(ContextConfigException.class, () -> config.component(InjectionTest.ConstructorInjection.InjectConstructor.class,
                        InjectionTest.ConstructorInjection.InjectConstructor.class, new TestLiteral()));
            }
        }

        @Nested
        public class WithScope {
            static class NotSingleton {
            }

            @Test
            public void should_not_be_singleton_scope_by_default() {
                config.component(NotSingleton.class, NotSingleton.class);
                Context context = config.getContext();
                assertNotSame(context.get(ComponentRef.of(NotSingleton.class)).get(), context.get(ComponentRef.of(NotSingleton.class)).get());
            }

            @Test
            public void should_bind_component_as_singleton_scoped() {
                config.component(NotSingleton.class, NotSingleton.class, new SingletonLiteral());
                Context context = config.getContext();
                assertSame(context.get(ComponentRef.of(NotSingleton.class)).get(), context.get(ComponentRef.of(NotSingleton.class)).get());
            }

            @Test
            public void should_retrieve_scope_annotation_from_component() {
                config.component(Dependency.class, SingletonAnnotated.class);

                Context context = config.getContext();
                assertSame(context.get(ComponentRef.of(Dependency.class)).get(), context.get(ComponentRef.of(Dependency.class)).get());
            }

            @Singleton
            static class SingletonAnnotated implements Dependency {

            }

            @Test
            public void should_bind_component_as_customized_scope() {
                config.scope(Pooled.class, PooledProvider::new);
                config.component(NotSingleton.class, NotSingleton.class, new PooledLiteral());

                Context context = config.getContext();

                List<NotSingleton> instances = IntStream.range(0, 5).mapToObj(i -> context.get(ComponentRef.of(NotSingleton.class)).get()).toList();
                assertEquals(PooledProvider.MAX, new HashSet<>(instances).size());
            }

            @Test
            public void should_throw_exception_if_multi_scope_provides() {
                assertThrows(ContextConfigException.class, () -> config.component(NotSingleton.class, NotSingleton.class, new SingletonLiteral(), new PooledLiteral()));
            }

            @Singleton
            @Pooled
            static class MultiScopeAnnotated {
            }

            @Test
            public void should_throw_exception_if_multi_scope_annotated() {
                assertThrows(ContextConfigException.class, () -> config.component(MultiScopeAnnotated.class, MultiScopeAnnotated.class));
            }

            @Test
            public void should_throw_exception_if_scope_undefined() {
                assertThrows(ContextConfigException.class, () -> config.component(NotSingleton.class, NotSingleton.class,
                        new PooledLiteral()));
            }

            @Nested
            public class WithQualifier {
                @Test
                public void should_not_be_singleton_scope_by_default() {
                    config.component(NotSingleton.class, NotSingleton.class, new SkywalkerLiteral());
                    Context context = config.getContext();
                    assertNotSame(context.get(ComponentRef.of(NotSingleton.class, new SkywalkerLiteral())).get(), context.get(ComponentRef.of(NotSingleton.class, new SkywalkerLiteral())).get());
                }

                @Test
                public void should_bind_component_as_singleton_scoped() {
                    config.component(NotSingleton.class, NotSingleton.class, new SingletonLiteral(), new SkywalkerLiteral());
                    Context context = config.getContext();
                    assertSame(context.get(ComponentRef.of(NotSingleton.class, new SkywalkerLiteral())).get(), context.get(ComponentRef.of(NotSingleton.class, new SkywalkerLiteral())).get());
                }

                @Test
                public void should_retrieve_scope_annotation_from_component() {
                    config.component(Dependency.class, SingletonAnnotated.class, new SkywalkerLiteral());

                    Context context = config.getContext();
                    assertSame(context.get(ComponentRef.of(Dependency.class, new SkywalkerLiteral())).get(), context.get(ComponentRef.of(Dependency.class, new SkywalkerLiteral())).get());
                }
            }
        }
    }

    @Nested
    public class DependencyCheck {
        @ParameterizedTest
        @MethodSource
        public void should_throw_exception_if_dependency_not_found(Class<? extends TestComponent> component) {
            config.component(TestComponent.class, component);

            assertThrows(ContextConfigError.class, () -> config.getContext());
        }

        public static Stream<Arguments> should_throw_exception_if_dependency_not_found() {
            return Stream.of(
                    Arguments.of(Named.of("Inject Constructor", MissingDependencyConstructor.class)),
                    Arguments.of(Named.of("Inject Field", MissingDependencyField.class)),
                    Arguments.of(Named.of("Inject Method", MissingDependencyMethod.class)),
                    Arguments.of(Named.of("Provider Inject Constructor", MissingDependencyProviderConstructor.class)),
                    Arguments.of(Named.of("Provider Inject Field", MissingDependencyProviderField.class)),
                    Arguments.of(Named.of("Provider Inject Method", MissingDependencyProviderMethod.class)),
                    Arguments.of(Named.of("Scoped", MissingDependencyScoped.class)),
                    Arguments.of(Named.of("Scoped Provider", MissingDependencyProviderScoped.class))
            );
        }

        static class MissingDependencyConstructor implements TestComponent {
            @Inject
            public MissingDependencyConstructor(Dependency dependency) {

            }
        }

        static class MissingDependencyField {
            @Inject
            Dependency dependency;
        }

        static class MissingDependencyMethod {
            @Inject
            void install(Dependency dependency) {
            }
        }

        static class MissingDependencyProviderConstructor implements TestComponent {
            @Inject
            public MissingDependencyProviderConstructor(Provider<Dependency> dependency) {
            }
        }

        static class MissingDependencyProviderField implements TestComponent {
            @Inject
            Provider<Dependency> dependency;
        }

        static class MissingDependencyProviderMethod implements TestComponent {
            @Inject
            void install(Provider<Dependency> dependency) {
            }
        }

        @Singleton
        static class MissingDependencyScoped implements TestComponent {
            @Inject
            Dependency dependency;
        }

        @Singleton
        static class MissingDependencyProviderScoped implements TestComponent {
            @Inject
            Provider<Dependency> dependency;
        }


        @ParameterizedTest(name = "cyclic dependency between {0} and {1}")
        @MethodSource
        public void should_throw_exception_if_cyclic_dependencies_found(Class<? extends TestComponent> component, Class<? extends Dependency> dependency) {
            config.component(TestComponent.class, component);
            config.component(Dependency.class, dependency);

            assertThrows(ContextConfigError.class, () -> config.getContext());
        }

        public static Stream<Arguments> should_throw_exception_if_cyclic_dependencies_found() {
            List<Arguments> arguments = new ArrayList<>();
            for (Named component : List.of(Named.of("Inject Constructor", DependencyCheck.CyclicComponentInjectConstructor.class), Named.of("Inject Field", DependencyCheck.CyclicComponentInjectField.class), Named.of("Inject Method", DependencyCheck.CyclicComponentInjectMethod.class))) {
                for (Named dependency : List.of(Named.of("Inject Constructor", DependencyCheck.CyclicDependencyInjectConstructor.class), Named.of("Inject Field", DependencyCheck.CyclicDependencyInjectField.class), Named.of("Inject Method", DependencyCheck.CyclicDependencyInjectMethod.class)))
                    arguments.add(Arguments.of(component, dependency));
            }
            return arguments.stream();
        }

        static class CyclicComponentInjectConstructor implements TestComponent {
            @Inject
            public CyclicComponentInjectConstructor(Dependency dependency) {
            }
        }

        static class CyclicComponentInjectField implements TestComponent {
            @Inject
            Dependency dependency;
        }

        static class CyclicComponentInjectMethod implements TestComponent {
            @Inject
            void install(Dependency dependency) {
            }
        }

        static class CyclicDependencyInjectConstructor implements Dependency {
            @Inject
            public CyclicDependencyInjectConstructor(TestComponent component) {
            }
        }

        static class CyclicDependencyInjectField implements Dependency {
            @Inject
            TestComponent component;
        }

        static class CyclicDependencyInjectMethod implements Dependency {
            @Inject
            void install(TestComponent component) {
            }
        }

        @ParameterizedTest(name = "indirect cyclic dependency between {0}, {1} and {2}")
        @MethodSource
        public void should_throw_exception_if_transitive_cyclic_dependencies_found(Class<? extends TestComponent> component, Class<? extends Dependency> dependency, Class<? extends AnotherDependency> anotherDependency) {
            config.component(TestComponent.class, component);
            config.component(Dependency.class, dependency);
            config.component(AnotherDependency.class, anotherDependency);
            assertThrows(ContextConfigError.class, () -> config.getContext());
        }

        public static Stream<Arguments> should_throw_exception_if_transitive_cyclic_dependencies_found() {
            List<Arguments> arguments = new ArrayList<>();
            for (Named component : List.of(Named.of("Inject Constructor", DependencyCheck.CyclicComponentInjectConstructor.class), Named.of("Inject Field", DependencyCheck.CyclicComponentInjectField.class), Named.of("Inject Method", DependencyCheck.CyclicComponentInjectMethod.class))) {
                for (Named dependency : List.of(Named.of("Inject Constructor", DependencyCheck.IndirectCyclicDependencyInjectConstructor.class), Named.of("Inject Field", DependencyCheck.IndirectCyclicDependencyInjectField.class), Named.of("Inject Method", DependencyCheck.IndirectCyclicDependencyInjectMethod.class))) {
                    for (Named anotherDependency : List.of(Named.of("Inject Constructor", DependencyCheck.IndirectCyclicAnotherDependencyInjectConstructor.class), Named.of("Inject Field", DependencyCheck.IndirectCyclicAnotherDependencyInjectField.class), Named.of("Inject Method", DependencyCheck.IndirectCyclicAnotherDependencyInjectMethod.class)))
                        arguments.add(Arguments.of(component, dependency, anotherDependency));

                }
            }
            return arguments.stream();
        }

        static class IndirectCyclicDependencyInjectConstructor implements Dependency {
            @Inject
            public IndirectCyclicDependencyInjectConstructor(AnotherDependency anotherDependency) {
            }
        }

        static class IndirectCyclicDependencyInjectField implements Dependency {
            @Inject
            AnotherDependency anotherDependency;
        }

        static class IndirectCyclicDependencyInjectMethod implements Dependency {
            @Inject
            public void install(AnotherDependency anotherDependency) {
            }
        }

        static class IndirectCyclicAnotherDependencyInjectConstructor {
            @Inject
            public IndirectCyclicAnotherDependencyInjectConstructor(TestComponent component) {
            }
        }

        static class IndirectCyclicAnotherDependencyInjectField {
            @Inject
            TestComponent component;
        }

        static class IndirectCyclicAnotherDependencyInjectMethod {
            @Inject
            public void install(TestComponent component) {
            }
        }

        static class CyclicDependencyProviderConstructor implements Dependency {
            @Inject
            public CyclicDependencyProviderConstructor(Provider<TestComponent> component) {
            }
        }

        @Test
        public void should_not_throw_exception_if_cyclic_dependency_via_provider() {
            config.component(TestComponent.class, CyclicComponentInjectConstructor.class);
            config.component(Dependency.class, CyclicDependencyProviderConstructor.class);
            Context context = config.getContext();
            assertTrue(context.get(ComponentRef.of(TestComponent.class)).isPresent());
            assertTrue(context.get(ComponentRef.of(Dependency.class)).isPresent());
        }

        @Nested
        public class WithQualifier {
            @ParameterizedTest
            @MethodSource
            public void should_throw_exception_if_dependency_with_qualifier_not_found(Class<? extends TestComponent> component) {
                config.instance(Dependency.class, dependency);

                config.component(TestComponent.class, component, new NameLiteral("Owner"));

                assertThrows(ContextConfigError.class, () -> config.getContext());
            }

            public static Stream<Arguments> should_throw_exception_if_dependency_with_qualifier_not_found() {
                return Stream.of(Named.of("Inject Constructor with Qualifier", InjectConstructor.class),
                        Named.of("Inject Field with Qualifier", InjectField.class),
                        Named.of("Inject Method with Qualifier", InjectMethod.class),
                        Named.of("Provider in Inject Constructor with Qualifier", InjectConstructorProvider.class),
                        Named.of("Provider in Inject Constructor with Qualifier", InjectFieldProvider.class),
                        Named.of("Provider in  Inject Constructor with Qualifier", InjectMethodProvider.class)
                ).map(Arguments::of);
            }


            @ParameterizedTest(name = "{1} -> @Skywalker({0}) -> @Named(\"ChosenOne\") not cyclic dependencies")
            @MethodSource
            public void should_not_throw_cyclic_exception_if_component_with_same_type_tagged_with_different_qualifier(Class<? extends Dependency> skywalker,
                                                                                                                      Class<? extends Dependency> notCyclic) {
                config.instance(Dependency.class, dependency, new NameLiteral("ChosenOne"));
                config.component(Dependency.class, skywalker, new SkywalkerLiteral());
                config.component(Dependency.class, notCyclic);

                Assertions.assertDoesNotThrow(() -> config.getContext());
            }

            public static Stream<Arguments> should_not_throw_cyclic_exception_if_component_with_same_type_tagged_with_different_qualifier() {
                List<Arguments> arguments = new ArrayList<>();
                for (Named skywalker : List.of(Named.of("Inject Constructor", SkywalkerInjectConstructor.class),
                        Named.of("Inject Field", SkywalkerInjectField.class),
                        Named.of("Inject Method", SkywalkerInjectMethod.class)))
                    for (Named notCyclic : List.of(Named.of("Inject Constructor", NotCyclicInjectConstructor.class),
                            Named.of("Inject Field", NotCyclicInjectField.class),
                            Named.of("Inject Method", NotCyclicInjectMethod.class)))
                        arguments.add(Arguments.of(skywalker, notCyclic));

                return arguments.stream();
            }
        }
    }

    @Nested
    class DSL {
        interface Api {

        }

        static class Implementation implements Api {

        }

        @Test
        void should_bind_instance_as_its_declaration_type() {
            Implementation instance = new Implementation();
            config.from(new Config() {
                Implementation implementation = instance;
            });
            Context context = config.getContext();
            Implementation implementation = context.get(ComponentRef.of(Implementation.class)).get();
            assertSame(instance, implementation);
        }

        @Test
        void should_bind_component_as_its_own_type() {
            config.from(new Config() {
                Implementation implementation;
            });
            Context context = config.getContext();
            assertTrue(context.get(ComponentRef.of(Implementation.class)).isPresent());
        }

        @Test
        void should_bind_instance_using_export_type() {
            Implementation instance = new Implementation();
            config.from(new Config() {
                @Export(Api.class)
                Implementation implementation = instance;
            });

            Context context = config.getContext();
            assertSame(instance, context.get(ComponentRef.of(Api.class)).get());
        }

        @Test
        void should_bind_component_using_export_type() {
            config.from(new Config() {
                @Export(Api.class)
                Implementation implementation;
            });

            Context context = config.getContext();
            assertTrue(context.get(ComponentRef.of(Api.class)).isPresent());
        }

        @Test
        void should_bind_instance_with_qualifier() {
            Implementation instance = new Implementation();
            config.from(new Config() {
                @Skywalker
                Api implementation = instance;
            });

            Context context = config.getContext();
            assertSame(instance, context.get(ComponentRef.of(Api.class, new SkywalkerLiteral())).get());
        }


        @Test
        void should_bind_component_with_qualifier() {
            config.from(new Config() {
                @Skywalker
                Implementation implementation;
            });

            Context context = config.getContext();
            assertTrue(context.get(ComponentRef.of(Implementation.class, new SkywalkerLiteral())).isPresent());
        }

    }
}


