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

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.settings.Settings;

import java.io.Closeable;

public class KubernetesAPIServiceImpl extends AbstractComponent implements KubernetesAPIService, Closeable {

  private final String namespace;
  private final String serviceName;

  @Override
  public Endpoints endpoints() {
    logger.debug("get endpoints for service {}, namespace {}", serviceName, namespace);
    return client().endpoints().inNamespace(namespace).withName(serviceName).get();
  }

  private KubernetesClient client;

  public KubernetesAPIServiceImpl(Settings settings) {
    super(settings);
    this.namespace = NAME_SPACE_SETTING.get(settings);
    this.serviceName = SERVICE_NAME_SETTING.get(settings);
  }

  public synchronized KubernetesClient client() {
    if (client == null) {
      client = new DefaultKubernetesClient();
    }
    return client;
  }

  @Override
  public void close() {
    if (client != null) {
      client.close();
    }
  }

}
