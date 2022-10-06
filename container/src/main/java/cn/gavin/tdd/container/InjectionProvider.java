package cn.gavin.tdd.container;

import jakarta.inject.Inject;
import jakarta.inject.Qualifier;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Arrays.stream;
import static java.util.stream.Stream.concat;

class InjectionProvider<T> implements ComponentProvider<T> {
    private Injectable<Constructor<T>> injectConstructor;
    private Map<Class<?>, List<Injectable<Method>>> injectMethods;
    private Map<Class<?>, List<Injectable<Field>>> injectFields;
    private List<ComponentRef<?>> dependencies;
    private Collection<Class<?>> superClasses;

    public InjectionProvider(Class<T> component) {
        this.injectConstructor = getInjectConstructor(component);
        this.superClasses = allSuperClass(component);
        var injectMethods = getInjectMethods(component);
        var injectFields = getInjectFields(component);
        this.dependencies = concat(concat(Stream.of(injectConstructor), injectFields.stream()), injectMethods.stream())
                .flatMap(i -> stream(i.required)).toList();
        this.injectFields = groupByClass(injectFields);
        this.injectMethods = groupByClass(injectMethods);
    }

    @Override
    public T get(Context context) {
        try {
            T instance = injectConstructor.element().newInstance(injectConstructor.toDependencies(context));
            for (Class<?> c : superClasses) {
                for (Injectable<Field> field : injectFields.getOrDefault(c, List.of()))
                    field.element.set(instance, field.toDependencies(context)[0]);
                for (Injectable<Method> method : injectMethods.getOrDefault(c, List.of()))
                    method.element.invoke(instance, method.toDependencies(context));
            }

            return instance;
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<ComponentRef<?>> getDependencies() {
        return this.dependencies;
    }

    record Injectable<Element extends AccessibleObject>(Element element, ComponentRef<?>[] required) {
        private static <Element extends Executable> Injectable<Element> of(Element element) {
            element.setAccessible(true);
            return new Injectable<>(element, stream(element.getParameters()).map(Injectable::toComponentRef).toArray(ComponentRef<?>[]::new));
        }

        static Injectable<Field> of(Field field) {
            field.setAccessible(true);
            return new Injectable<>(field, new ComponentRef<?>[]{toComponentRef(field)});
        }

        Object[] toDependencies(Context context) {
            return stream(required).map(context::get).map(Optional::get).toArray();
        }

        private static ComponentRef<?> toComponentRef(Field field) {
            Annotation qualifier = getQualifier(field);
            return ComponentRef.of(field.getGenericType(), qualifier);
        }

        private static ComponentRef<?> toComponentRef(Parameter parameter) {
            return ComponentRef.of(parameter.getParameterizedType(), getQualifier(parameter));
        }

        private static Annotation getQualifier(AnnotatedElement element) {
            List<Annotation> qualifiers = stream(element.getAnnotations()).filter(a -> a.annotationType().isAnnotationPresent(Qualifier.class)).toList();
            if (qualifiers.size() > 1) throw ComponentError.ambiguousQualifiers(element, qualifiers);
            return qualifiers.stream().findFirst().orElse(null);
        }
    }

    private static <T> Injectable<Constructor<T>> getInjectConstructor(Class<T> component) {
        if (Modifier.isAbstract(component.getModifiers())) throw ComponentError.abstractComponent(component);

        List<Constructor<?>> injectConstructors = injectable(component.getDeclaredConstructors()).toList();

        if (injectConstructors.size() > 1) throw ComponentError.ambiguousInjectableConstructors(component);

        return Injectable.of((Constructor<T>) injectConstructors.stream().findFirst().orElseGet(() -> defaultConstructor(component)));
    }

    private static List<Injectable<Field>> getInjectFields(Class<?> component) {
        List<Injectable<Field>> injectableFields = InjectionProvider.<Field>traverse(component, (fields, current) -> injectable(current.getDeclaredFields()).toList()).stream().map(Injectable::of).toList();
        return check(component, injectableFields, InjectionProvider::notFinal, ComponentError::finalInjectFields);
    }

    private static List<Injectable<Method>> getInjectMethods(Class<?> component) {
        List<Method> injectMethods = traverse(component, (methods, current) -> injectable(current.getDeclaredMethods())
                .filter(m -> isOverrideByInjectMethod(methods, m))
                .filter(m -> isOverrideByNoInjectMethod(component, m))
                .toList());
        List<Injectable<Method>> injectableMethods = injectMethods.stream().map(Injectable::of).toList();
        return check(component, injectableMethods, InjectionProvider::noTypeParameter, ComponentError::injectMethodsWithTypeParameter);
    }

    private static <Type> Constructor<Type> defaultConstructor(Class<Type> implementation) {
        try {
            return implementation.getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            throw ComponentError.noDefaultConstructor(implementation);
        }
    }

    private static <E extends AccessibleObject> Map<Class<?>, List<Injectable<E>>> groupByClass(List<Injectable<E>> injectFields) {
        return injectFields.stream().collect(Collectors.groupingBy(i -> ((Member) i.element()).getDeclaringClass(), Collectors.toList()));
    }

    private static Collection<Class<?>> allSuperClass(Class<?> component) {
        List<Class<?>> result = new ArrayList<>();
        for (Class superClass = component;
             superClass != Object.class;
             superClass = superClass.getSuperclass())
            result.add(0, superClass);
        return result;
    }

    private static <T> List<T> traverse(Class<?> component, BiFunction<List<T>, Class<?>, List<T>> finder) {
        List<T> members = new ArrayList<>();
        Class<?> current = component;
        while (current != Object.class) {
            members.addAll(finder.apply(members, current));
            current = current.getSuperclass();
        }
        return members;
    }

    private static <T extends AnnotatedElement> Stream<T> injectable(T[] declaredFields) {
        return stream(declaredFields).filter(f -> f.isAnnotationPresent(Inject.class));
    }

    private static boolean isOverride(Method m, Method o) {
        boolean visible;
        if (m.getDeclaringClass().getPackageName().equals(o.getDeclaringClass().getPackageName()))
            visible = !Modifier.isPrivate(o.getModifiers()) && !Modifier.isPrivate(m.getModifiers());
        else visible = (Modifier.isPublic(o.getModifiers()) || Modifier.isProtected(o.getModifiers())) &&
                (Modifier.isPublic(m.getModifiers()) || Modifier.isProtected(m.getModifiers()));
        return visible && o.getName().equals(m.getName()) &&
                Arrays.equals(o.getParameterTypes(), m.getParameterTypes());
    }

    private static <T> boolean isOverrideByNoInjectMethod(Class<T> component, Method m) {
        return stream(component.getDeclaredMethods()).filter(m1 -> !m1.isAnnotationPresent(Inject.class)).noneMatch(o -> isOverride(m, o));
    }

    private static boolean isOverrideByInjectMethod(List<Method> injectMethods, Method m) {
        return injectMethods.stream().noneMatch(o -> isOverride(m, o));
    }

    private static <Element extends AccessibleObject> List<Injectable<Element>> check(Class<?> component, List<Injectable<Element>> target, Predicate<Element> predicate, BiFunction<Class<?>, List<Element>, ComponentError> error) {
        List<Element> found = target.stream().map(Injectable::element).filter(predicate).toList();
        if (found.size() > 0) throw error.apply(component, found);
        return target;
    }

    private static boolean noTypeParameter(Method m) {
        return m.getTypeParameters().length != 0;
    }

    private static boolean notFinal(Field f) {
        return Modifier.isFinal(f.getModifiers());
    }
}