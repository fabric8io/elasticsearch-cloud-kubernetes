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
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.util.List;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.component.AbstractLifecycleComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;

public class KubernetesAPIServiceImpl extends AbstractLifecycleComponent<KubernetesAPIService>
  implements KubernetesAPIService {

  private final String namespace;
  private final String serviceName;
  private final String podLabel;

  @Override
  public Endpoints endpoints() {
    logger.debug("get endpoints for service {}, namespace {}", serviceName, namespace);
    return client().endpoints().inNamespace(namespace).withName(serviceName).get();
  }

  @Override
  public List<Pod> pods() {
    logger.debug("get endpoints with pod label {}, namespace {}", podLabel, namespace);
    final String[] l = podLabel.split("=");
    return client().pods().inNamespace(namespace).withLabel(l[0], l[1]).list().getItems();
  }
  
  private KubernetesClient client;

  @Inject
  public KubernetesAPIServiceImpl(Settings settings) {
    super(settings);
    this.namespace = settings.get(Fields.NAMESPACE);
    this.serviceName = settings.get(Fields.SERVICE_NAME);
    this.podLabel = settings.get(Fields.POD_LABEL);
  }

  public synchronized KubernetesClient client() {
    if (client == null) {
      client = new DefaultKubernetesClient();
    }
    return client;
  }

  @Override
  protected void doStart() throws ElasticsearchException {
  }

  @Override
  protected void doStop() throws ElasticsearchException {
    if (client != null) {
      client.close();
    }
  }

  @Override
  protected void doClose() throws ElasticsearchException {
  }
}
