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
import org.elasticsearch.common.component.LifecycleComponent;

public interface KubernetesAPIService extends LifecycleComponent<KubernetesAPIService> {

  /**
   * Return a collection of IP addresses for the service endpoints
   *
   * @return a collection of IP addresses for the service endpoints
   */
  Endpoints endpoints();

  final class Fields {
    public static final String NAMESPACE = "cloud.kubernetes.namespace";
    public static final String SERVICE_NAME = "cloud.kubernetes.service";
    public static final String REFRESH = "cloud.kubernetes.refresh_interval";
    public static final String VERSION = "Elasticsearch/KubernetesCloud/1.0";

    public static final String RETRY = "cloud.kubernetes.retry";
    public static final String MAXWAIT = "cloud.kubernetes.max_wait";
  }

}
