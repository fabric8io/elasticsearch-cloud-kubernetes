package io.fabric8.discovery.k8s;

import org.elasticsearch.discovery.Discovery;
import org.elasticsearch.discovery.zen.ZenDiscoveryModule;

/**
 *
 */
public class K8sDiscoveryModule extends ZenDiscoveryModule {

    @Override
    protected void bindDiscovery() {
        bind(Discovery.class).to(K8sDiscovery.class).asEagerSingleton();
    }
}
