package cn.gavin.tdd.container;

interface ScopeProvider {
    ComponentProvider<?> create(ComponentProvider<?> provider);
}
