/**
 * Copyright (C) 2015 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8.elasticsearch.cloud.kubernetes;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.inject.AbstractModule;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;

public class KubernetesModule extends AbstractModule {

  protected final Settings settings;
  protected final Logger logger = Loggers.getLogger(KubernetesModule.class);

  public KubernetesModule(Settings settings) {
    this.settings = settings;
  }

  // pkg private so tests can override with mock
  static Class<? extends KubernetesAPIService> kubernetesAPIServiceImpl = KubernetesAPIServiceImpl.class;

  public static Class<? extends KubernetesAPIService> getKubernetesServiceImpl() {
    return kubernetesAPIServiceImpl;
  }

  @Override
  protected void configure() {
    logger.debug("configure KubernetesModule (bind Kubernetes API service)");
    bind(KubernetesAPIService.class).to(kubernetesAPIServiceImpl).asEagerSingleton();
  }

}
