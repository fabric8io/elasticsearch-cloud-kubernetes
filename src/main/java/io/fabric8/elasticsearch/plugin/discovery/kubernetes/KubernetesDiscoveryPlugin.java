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
package io.fabric8.elasticsearch.plugin.discovery.kubernetes;

import io.fabric8.elasticsearch.cloud.kubernetes.KubernetesAPIService;
import io.fabric8.elasticsearch.cloud.kubernetes.KubernetesAPIServiceImpl;
import io.fabric8.elasticsearch.discovery.kubernetes.KubernetesUnicastHostsProvider;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.util.SetOnce;
import org.elasticsearch.SpecialPermission;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.logging.DeprecationLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.network.NetworkService;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.discovery.Discovery;
import org.elasticsearch.discovery.DiscoveryModule;
import org.elasticsearch.discovery.zen.UnicastHostsProvider;
import org.elasticsearch.discovery.zen.ZenDiscovery;
import org.elasticsearch.plugins.DiscoveryPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;


public class KubernetesDiscoveryPlugin extends Plugin implements DiscoveryPlugin {
  public static final String KUBERNETES = "kubernetes";

  private static Logger logger = Loggers.getLogger(KubernetesDiscoveryPlugin.class);
  private static final DeprecationLogger deprecationLogger = new DeprecationLogger(logger);

  private final Settings settings;
  private final SetOnce<KubernetesAPIServiceImpl> kubernetesAPIService = new SetOnce<>();

  static {
    SecurityManager sm = System.getSecurityManager();
    if (sm != null) {
      sm.checkPermission(new SpecialPermission());
    }
  }

  public KubernetesDiscoveryPlugin(Settings settings) {
    this.settings = settings;
    logger.trace("Starting Kubernetes discovery plugin...");
  }

  @Override
  public Map<String, Supplier<Discovery>> getDiscoveryTypes(ThreadPool threadPool, TransportService transportService,
                                                            NamedWriteableRegistry namedWriteableRegistry, ClusterService clusterService, UnicastHostsProvider hostsProvider) {
    // this is for backcompat with pre 5.1, where users would set discovery.type to use ec2 hosts provider
    return Collections.singletonMap(KUBERNETES, () ->
      new ZenDiscovery(settings, threadPool, transportService, namedWriteableRegistry, clusterService, hostsProvider));
  }

  @Override
  public Map<String, Supplier<UnicastHostsProvider>> getZenHostsProviders(TransportService transportService,
                                                                          NetworkService networkService) {
    return Collections.singletonMap(KUBERNETES, () -> {
      kubernetesAPIService.set(new KubernetesAPIServiceImpl(settings));
      return new KubernetesUnicastHostsProvider(settings, kubernetesAPIService.get(), transportService, networkService);
    });
  }

  @Override
  public List<Setting<?>> getSettings() {
    return Arrays.asList(
      // Register Kubernetes Settings
      KubernetesAPIService.NAME_SPACE_SETTING,
      KubernetesAPIService.SERVICE_NAME_SETTING,
      KubernetesAPIService.REFRESH_SETTING,
      KubernetesAPIService.RETRY_SETTING,
      KubernetesAPIService.MAX_WAIT_SETTING);
  }

  @Override
  public Settings additionalSettings() {
    // For 5.0, the hosts provider was "zen", but this was before the discovery.zen.hosts_provider
    // setting existed. This check looks for the legacy setting, and sets hosts provider if set
    final String discoveryType = DiscoveryModule.DISCOVERY_TYPE_SETTING.get(settings);
    if (discoveryType.equals(KUBERNETES)) {
      deprecationLogger.deprecated("Using " + DiscoveryModule.DISCOVERY_TYPE_SETTING.getKey() +
        " setting to set hosts provider is deprecated. " +
        "Set \"" + DiscoveryModule.DISCOVERY_HOSTS_PROVIDER_SETTING.getKey() + ": " + KUBERNETES + "\" instead");
      if (DiscoveryModule.DISCOVERY_HOSTS_PROVIDER_SETTING.exists(settings) == false) {
        return Settings.builder().put(DiscoveryModule.DISCOVERY_HOSTS_PROVIDER_SETTING.getKey(), KUBERNETES).build();
      }
    }
    return Settings.EMPTY;
  }

}
