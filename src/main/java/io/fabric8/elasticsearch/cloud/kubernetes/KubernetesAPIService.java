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
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Setting.Property;
import org.elasticsearch.common.unit.TimeValue;


public interface KubernetesAPIService {

  String VERSION = "Elasticsearch/KubernetesCloud/1.0";
  Setting<String> NAME_SPACE_SETTING = Setting.simpleString("cloud.kubernetes.namespace", Property.NodeScope);
  Setting<String> SERVICE_NAME_SETTING = Setting.simpleString("cloud.kubernetes.service", Property.NodeScope);
  Setting<TimeValue> REFRESH_SETTING = Setting.timeSetting("cloud.kubernetes.refresh_interval", TimeValue.timeValueSeconds(0), Property.NodeScope);
  Setting<Boolean> RETRY_SETTING = Setting.boolSetting("cloud.kubernetes.retry", true, Property.NodeScope);
  Setting<TimeValue> MAX_WAIT_SETTING = Setting.timeSetting("cloud.kubernetes.max_wait", TimeValue.timeValueSeconds(-1), Property.NodeScope);

  /**
   * Return a collection of IP addresses for the service endpoints
   *
   * @return a collection of IP addresses for the service endpoints
   */
  Endpoints endpoints();

}
