package io.fabric8.elasticsearch.cloud.kubernetes;

import org.elasticsearch.common.inject.AbstractModule;

public class KubernetesModule extends AbstractModule {

  // pkg private so tests can override with mock
  static Class<? extends KubernetesAPIService> kubernetesAPIServiceImpl = KubernetesAPIServiceImpl.class;

  public static Class<? extends KubernetesAPIService> getComputeServiceImpl() {
    return kubernetesAPIServiceImpl;
  }

  @Override
  protected void configure() {
    bind(KubernetesAPIService.class).to(kubernetesAPIServiceImpl).asEagerSingleton();
  }
}
